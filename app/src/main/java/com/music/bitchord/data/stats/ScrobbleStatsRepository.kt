package com.music.bitchord.data.stats

import com.music.bitchord.data.scrobbling.LastFM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Last.fm chart row, kept separate from Song because it is not playable yet. */
data class ScrobbleChartEntry(
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val playCount: Long = 0L,
    val listeners: Long? = null,
    val artworkUrl: String? = null,
)

data class ScrobbleStatsSnapshot(
    val username: String,
    val period: String,
    val totalScrobbles: Long,
    val registeredAtSeconds: Long?,
    val tracks: List<ScrobbleChartEntry>,
    val artists: List<ScrobbleChartEntry>,
    val albums: List<ScrobbleChartEntry>,
    val tags: List<ScrobbleChartEntry>,
)

object ScrobbleStatsRepository {
    val supportedPeriods = listOf("overall", "12month", "6month", "3month", "1month", "7day")

    private const val REQUEST_TIMEOUT_MS = 10_000L
    private const val CHART_LIMIT = 50

    /**
     * LastWave Stats data-source port. Five independent Last.fm endpoints are
     * fetched concurrently; a single failed chart does not blank the others.
     */
    suspend fun load(username: String, period: String): ScrobbleStatsSnapshot =
        withContext(Dispatchers.IO) {
            val cleanUser = username.trim()
            require(cleanUser.isNotBlank()) { "Sign in to Last.fm to load scrobble charts" }
            val selectedPeriod = period.takeIf { it in supportedPeriods } ?: "overall"

            supervisorScope {
                val info = async { safeCall("user.getinfo", cleanUser) }
                val tracks = async { safeCall("user.gettoptracks", cleanUser, selectedPeriod) }
                val artists = async { safeCall("user.gettopartists", cleanUser, selectedPeriod) }
                val albums = async { safeCall("user.gettopalbums", cleanUser, selectedPeriod) }
                val tags = async { safeCall("user.gettoptags", cleanUser, selectedPeriod) }

                val infoJson = info.await()
                val user = infoJson?.get("user") as? JsonObject
                ScrobbleStatsSnapshot(
                    username = cleanUser,
                    period = selectedPeriod,
                    totalScrobbles = user?.objectAt("playcount")?.longAt("playcount")
                        ?: user?.longAt("playcount") ?: 0L,
                    registeredAtSeconds = user?.objectAt("registered")?.longAt("unixtime"),
                    tracks = tracks.await()?.chartAt("toptracks", "track").orEmpty().mapNotNull(::parseTrack),
                    artists = artists.await()?.chartAt("topartists", "artist").orEmpty().mapNotNull(::parseArtist),
                    albums = albums.await()?.chartAt("topalbums", "album").orEmpty().mapNotNull(::parseAlbum),
                    tags = tags.await()?.chartAt("toptags", "tag").orEmpty().mapNotNull(::parseTag),
                )
            }
        }

    private suspend fun safeCall(
        method: String,
        username: String,
        period: String? = null,
    ): JsonObject? = try {
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            LastFM.apiCall(
                method = method,
                extra = buildMap {
                    put("user", username)
                    if (period != null && method != "user.getinfo" && method != "user.gettoptags") {
                        put("period", period)
                    }
                    put("limit", CHART_LIMIT.toString())
                },
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.chartAt(parent: String, child: String): List<JsonElement> =
        (this[parent] as? JsonObject)?.get(child).asElementList()

    private fun JsonElement?.asElementList(): List<JsonElement> = when (this) {
        is JsonArray -> this.toList()
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    private fun parseTrack(element: JsonElement): ScrobbleChartEntry? {
        val obj = element as? JsonObject ?: return null
        val name = obj.stringAt("name")?.takeIf(String::isNotBlank) ?: return null
        val artist = obj.objectAt("artist")?.stringAt("name") ?: obj.stringAt("artist")
        return ScrobbleChartEntry(
            name = name,
            artist = artist,
            album = obj.objectAt("album")?.stringAt("#text")
                ?: obj.objectAt("album")?.stringAt("title"),
            playCount = obj.longAt("playcount") ?: 0L,
            listeners = obj.longAt("listeners"),
            artworkUrl = obj.bestImageUrl(),
        )
    }

    private fun parseArtist(element: JsonElement): ScrobbleChartEntry? {
        val obj = element as? JsonObject ?: return null
        val name = obj.stringAt("name")?.takeIf(String::isNotBlank) ?: return null
        return ScrobbleChartEntry(
            name = name,
            playCount = obj.longAt("playcount") ?: 0L,
            listeners = obj.longAt("listeners"),
            artworkUrl = obj.bestImageUrl(),
        )
    }

    private fun parseAlbum(element: JsonElement): ScrobbleChartEntry? {
        val obj = element as? JsonObject ?: return null
        val name = obj.stringAt("name")?.takeIf(String::isNotBlank) ?: return null
        return ScrobbleChartEntry(
            name = name,
            artist = obj.objectAt("artist")?.stringAt("name") ?: obj.stringAt("artist"),
            playCount = obj.longAt("playcount") ?: 0L,
            artworkUrl = obj.bestImageUrl(),
        )
    }

    private fun parseTag(element: JsonElement): ScrobbleChartEntry? {
        val obj = element as? JsonObject ?: return null
        val name = obj.stringAt("name")?.takeIf(String::isNotBlank) ?: return null
        return ScrobbleChartEntry(name = name, playCount = obj.longAt("count") ?: 0L)
    }

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.stringAt(key: String): String? = when (val value = this[key]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.stringAt("#text") ?: value.stringAt("name")
        else -> null
    }

    private fun JsonObject.longAt(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.bestImageUrl(): String? {
        val images = this["image"].asElementList().mapNotNull { it as? JsonObject }
        val priority = listOf("extralarge", "large", "medium", "small")
        return priority.firstNotNullOfOrNull { size ->
            images.firstOrNull { it.stringAt("size") == size }
                ?.stringAt("#text")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        } ?: images.firstNotNullOfOrNull { image ->
            image.stringAt("#text")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        }
    }
}
