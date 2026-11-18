package com.music.bitchord.data.scrobbling

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

object LastFM {
    const val DEFAULT_API_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    const val LIBREFM_API_ENDPOINT = "https://libre.fm/2.0/"

    @Serializable
    data class Session(val name: String, val key: String, val subscriber: Int)

    @Serializable
    data class Authentication(val session: Session)

    @Serializable
    data class TokenResponse(val token: String)

    @Serializable
    data class LastFmError(val error: Int, val message: String)

    data class RuntimeConfig(
        val endpoint: String,
        val apiKey: String,
        val secret: String,
        val sessionKey: String?,
    )

    data class UserStats(
        val username: String,
        val realName: String?,
        val playCount: Long,
        val trackCount: Long,
        val artistCount: Long,
        val albumCount: Long,
        val avatarUrl: String?,
    )

    data class RankedTrack(
        val name: String,
        val artist: String,
        val playCount: Long,
        val artworkUrl: String?,
    )

    data class RankedArtist(
        val name: String,
        val playCount: Long,
        val artworkUrl: String?,
    )

    data class RankedAlbum(
        val name: String,
        val artist: String,
        val playCount: Long,
        val artworkUrl: String?,
    )

    @Volatile
    private var runtimeConfig =
        RuntimeConfig(
            endpoint = DEFAULT_API_ENDPOINT,
            apiKey = "",
            secret = "",
            sessionKey = null,
        )

    var sessionKey: String?
        get() = runtimeConfig.sessionKey
        set(value) {
            runtimeConfig = runtimeConfig.copy(sessionKey = value)
        }

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            expectSuccess = false
        }
    }

    private fun Map<String, String>.apiSig(secret: String): String {
        val sorted = toSortedMap()
        val toHash = sorted.entries.joinToString("") { it.key + it.value } + secret
        val digest = MessageDigest.getInstance("MD5").digest(toHash.toByteArray())
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun HttpRequestBuilder.lastfmParams(
        method: String,
        apiKey: String,
        secret: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap(),
        format: String = "json",
    ) {
        headers.append(HttpHeaders.UserAgent, "BitChord (https://github.com/kushagrasinghx/BitChord)")
        val paramsForSig =
            mutableMapOf(
                "method" to method,
                "api_key" to apiKey,
            ).apply {
                sessionKey?.let { put("sk", it) }
                putAll(extra)
            }
        val apiSig = paramsForSig.apiSig(secret)

        setBody(
            FormDataContent(
                Parameters.build {
                    paramsForSig.forEach { (key, value) -> append(key, value) }
                    append("api_sig", apiSig)
                    append("format", format)
                },
            ),
        )
    }

    suspend fun getToken() =
        runCatching {
            postAndDecode<TokenResponse>(
                method = "auth.getToken",
            )
        }

    suspend fun getSession(token: String) =
        runCatching {
            postAndDecode<Authentication>(
                method = "auth.getSession",
                extra = mapOf("token" to token),
            )
        }

    fun getAuthUrl(token: String): String {
        val config = runtimeConfig
        return if (config.endpoint == LIBREFM_API_ENDPOINT) {
            "https://libre.fm/api/auth?api_key=${config.apiKey}&token=$token"
        } else {
            "https://www.last.fm/api/auth/?api_key=${config.apiKey}&token=$token"
        }
    }

    suspend fun getMobileSession(
        username: String,
        password: String,
    ) = runCatching {
        postAndDecode<Authentication>(
            method = "auth.getMobileSession",
            extra = mapOf("username" to username, "password" to password),
        )
    }

    class LastFmException(
        val code: Int,
        override val message: String,
    ) : Exception(message)

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null,
    ) = runCatching {
        postAndRead(
            method = "track.updateNowPlaying",
            sessionKey = requireSessionKey(),
            extra =
                buildMap {
                    put("artist", artist)
                    put("track", track)
                    album?.let { put("album", it) }
                    trackNumber?.let { put("trackNumber", it.toString()) }
                    duration?.let { put("duration", it.toString()) }
                },
        )
    }

    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null,
    ) = runCatching {
        postAndRead(
            method = "track.scrobble",
            sessionKey = requireSessionKey(),
            extra =
                buildMap {
                    put("artist[0]", artist)
                    put("track[0]", track)
                    put("timestamp[0]", timestamp.toString())
                    album?.let { put("album[0]", it) }
                    trackNumber?.let { put("trackNumber[0]", it.toString()) }
                    duration?.let { put("duration[0]", it.toString()) }
                },
        )
    }

    fun initialize(
        apiKey: String,
        secret: String,
    ) {
        require(apiKey.isNotBlank() && secret.isNotBlank()) {
            "Last.fm API credentials are not configured for this build"
        }
        configure(
            endpoint = runtimeConfig.endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = runtimeConfig.sessionKey,
        )
    }

    fun configure(
        endpoint: String,
        apiKey: String,
        secret: String,
        sessionKey: String? = runtimeConfig.sessionKey,
    ) {
        runtimeConfig =
            RuntimeConfig(
                endpoint = normalizeEndpoint(endpoint),
                apiKey = apiKey,
                secret = secret,
                sessionKey = sessionKey,
            )
    }

    fun currentConfig(): RuntimeConfig = runtimeConfig

    fun isInitialized(): Boolean = runtimeConfig.apiKey.isNotEmpty() && runtimeConfig.secret.isNotEmpty()

    /** Remote Last.fm profile totals; never merge these with local Replay totals. */
    suspend fun getUserStats(username: String): Result<UserStats> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        val response = postAndRead("user.getinfo", extra = mapOf("user" to user))
        parseLastFmUserStats(response) ?: error("Last.fm returned an invalid user profile")
    }

    /** Similar-artist discovery uses the public Last.fm graph; no user session is required. */
    suspend fun getSimilarArtists(
        artist: String,
        limit: Int = 20,
    ): Result<List<String>> = statsResult {
        val name = artist.trim()
        require(name.isNotEmpty()) { "Artist name is missing" }
        val response = postAndRead(
            "artist.getsimilar",
            extra = mapOf("artist" to name, "limit" to limit.coerceIn(1, 100).toString()),
        )
        parseLastFmSimilarArtists(response)
    }

    suspend fun getTopTracks(
        username: String,
        period: String = "overall",
        limit: Int = 10,
    ): Result<List<RankedTrack>> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        require(period in LASTFM_STATS_PERIODS) { "Unsupported Last.fm statistics period" }
        val response = postAndRead(
            "user.gettoptracks",
            extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
        )
        parseLastFmTopTracks(response)
    }

    suspend fun getTopArtists(
        username: String,
        period: String = "overall",
        limit: Int = 10,
    ): Result<List<RankedArtist>> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        require(period in LASTFM_STATS_PERIODS) { "Unsupported Last.fm statistics period" }
        val response = postAndRead(
            "user.gettopartists",
            extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
        )
        parseLastFmTopArtists(response)
    }

    suspend fun getTopAlbums(
        username: String,
        period: String = "overall",
        limit: Int = 10,
    ): Result<List<RankedAlbum>> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        require(period in LASTFM_STATS_PERIODS) { "Unsupported Last.fm statistics period" }
        val response = postAndRead(
            "user.gettopalbums",
            extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
        )
        parseLastFmTopAlbums(response)
    }

    private suspend fun <T> statsResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private val LASTFM_STATS_PERIODS = setOf("overall", "7day", "1month", "3month", "6month", "12month")

    fun normalizeEndpoint(endpoint: String): String {
        val uri = URI(endpoint.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.query == null && uri.fragment == null)

        val path =
            uri.path
                ?.takeIf { it.isNotBlank() && it != "/" }
                ?.trimEnd('/')
                ?: "/2.0"

        return URI(
            scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            "$path/",
            null,
            null,
        ).toString()
    }

    private fun requireSessionKey(): String = runtimeConfig.sessionKey ?: throw LastFmException(9, "Session key missing")

    private suspend inline fun <reified T> postAndDecode(
        method: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): T =
        json.decodeFromString(
            postAndRead(
                method = method,
                sessionKey = sessionKey,
                extra = extra,
            ),
        )

    private suspend fun postAndRead(
        method: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val config = runtimeConfig
        val response =
            client.post(config.endpoint) {
                lastfmParams(
                    method = method,
                    apiKey = config.apiKey,
                    secret = config.secret,
                    sessionKey = sessionKey,
                    extra = extra,
                )
            }

        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw LastFmException(response.status.value, response.status.description)
        }
        if (responseText.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseText)
            throw LastFmException(error.error, error.message)
        }
        return responseText
    }

    const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
    const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
    const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180
}


internal fun parseLastFmUserStats(raw: String): LastFM.UserStats? = runCatching {
    val root = Json.parseToJsonElement(raw) as? JsonObject ?: return null
    val user = root["user"] as? JsonObject ?: return null
    val username = user.text("name") ?: return null
    LastFM.UserStats(
        username = username,
        realName = user.text("realname"),
        playCount = user.long("playcount"),
        trackCount = user.long("track_count"),
        artistCount = user.long("artist_count"),
        albumCount = user.long("album_count"),
        avatarUrl = user.artwork(),
    )
}.getOrNull()

internal fun parseLastFmSimilarArtists(raw: String): List<String> = runCatching {
    val root = Json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
    val chart = root["similarartists"] as? JsonObject ?: return emptyList()
    val rows = when (val value = chart["artist"]) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(value)
        else -> emptyList()
    }
    rows.mapNotNull { it.text("name")?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase(Locale.ROOT) }
}.getOrDefault(emptyList())

internal fun parseLastFmTopTracks(raw: String): List<LastFM.RankedTrack> =
    parseLastFmChart(raw, "toptracks", "track").mapNotNull { row ->
        val name = row.text("name") ?: return@mapNotNull null
        val artist = row["artist"].artistName().orEmpty()
        LastFM.RankedTrack(name, artist, row.long("playcount"), row.artwork())
    }

internal fun parseLastFmTopArtists(raw: String): List<LastFM.RankedArtist> =
    parseLastFmChart(raw, "topartists", "artist").mapNotNull { row ->
        val name = row.text("name") ?: return@mapNotNull null
        LastFM.RankedArtist(name, row.long("playcount"), row.artwork())
    }

internal fun parseLastFmTopAlbums(raw: String): List<LastFM.RankedAlbum> =
    parseLastFmChart(raw, "topalbums", "album").mapNotNull { row ->
        val name = row.text("name") ?: return@mapNotNull null
        LastFM.RankedAlbum(name, row["artist"].artistName().orEmpty(), row.long("playcount"), row.artwork())
    }

private fun parseLastFmChart(raw: String, chartKey: String, rowKey: String): List<JsonObject> =
    runCatching {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val chart = root[chartKey] as? JsonObject ?: return emptyList()
        when (val rows = chart[rowKey]) {
            is JsonArray -> rows.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(rows)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.long(key: String): Long =
    text(key)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

private fun JsonObject.artwork(): String? {
    val images = when (val value = this["image"]) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(value)
        else -> emptyList()
    }
    return images.asReversed().firstNotNullOfOrNull { image ->
        image.text("#text")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }
}

private fun JsonElement?.artistName(): String? = when (this) {
    is JsonObject -> text("name")
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    else -> null
}
