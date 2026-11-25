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

    data class RecentTrack(
        val name: String,
        val artist: String,
        val artworkUrl: String?,
        val timestamp: Long,
    )

    data class PagedResult<T>(
        val items: List<T>,
        val totalPages: Int,
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
        headers.append(HttpHeaders.UserAgent, "Mylo (https://github.com/somilkhan/Mylo)")
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
        statsResult {
            postAndDecode<TokenResponse>(
                method = "auth.getToken",
            )
        }

    suspend fun getSession(token: String) =
        statsResult {
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
    ) = statsResult {
        postAndDecode<Authentication>(
            method = "auth.getMobileSession",
            extra = mapOf("username" to username, "password" to password),
        )
    }

    class LastFmException(
        val code: Int,
        override val message: String,
    ) : Exception(message)

    class ScrobbleRejectedException(
        val ignoredCode: Int,
        val retryable: Boolean,
        override val message: String,
    ) : Exception(message)

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null,
    ) = statsResult {
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
    ) = statsResult {
        val response = postAndRead(
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
        validateLastFmScrobbleResponse(response)
        response
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

    /** Public Last.fm similar-track graph used when YouTube radio is thin. */
    suspend fun getSimilarTracks(
        track: String,
        artist: String,
        limit: Int = 100,
    ): Result<List<RankedTrack>> = statsResult {
        val name = track.trim()
        val by = artist.trim()
        require(name.isNotEmpty()) { "Track name is missing" }
        require(by.isNotEmpty()) { "Artist name is missing" }
        val response = postAndRead(
            "track.getsimilar",
            extra = mapOf("track" to name, "artist" to by, "limit" to limit.coerceIn(1, 100).toString()),
        )
        parseLastFmSimilarTracks(response)
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

    /** All-time top tracks, exposed page-by-page for exact statistics merging. */
    suspend fun getTopTracksPage(
        username: String,
        page: Int = 1,
        limit: Int = 100,
    ): Result<PagedResult<RankedTrack>> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        val response = postAndRead(
            "user.gettoptracks",
            extra = mapOf(
                "user" to user,
                "period" to "overall",
                "limit" to limit.coerceIn(1, 100).toString(),
                "page" to page.coerceAtLeast(1).toString(),
            ),
        )
        parseLastFmTopTracksPage(response)
    }

    /** Calendar-range scrobbles; timestamps are Unix seconds and contain no listened duration. */
    suspend fun getRecentTracksPage(
        username: String,
        from: Long,
        to: Long,
        page: Int = 1,
        limit: Int = 200,
    ): Result<PagedResult<RecentTrack>> = statsResult {
        val user = username.trim()
        require(user.isNotEmpty()) { "Last.fm username is missing" }
        require(from < to) { "Invalid Last.fm history range" }
        val response = postAndRead(
            "user.getrecenttracks",
            extra = mapOf(
                "user" to user,
                "from" to from.toString(),
                "to" to to.toString(),
                "page" to page.coerceAtLeast(1).toString(),
                "limit" to limit.coerceIn(1, 200).toString(),
            ),
        )
        parseLastFmRecentTracksPage(response)
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
        // HTTPS only: the session key and request signature travel in this endpoint's requests.
        require(scheme == "https")
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
        lastFmErrorOrNull(responseText)?.let { error ->
            throw LastFmException(error.error, error.message)
        }
        return responseText
    }

    /**
     * Last.fm reports API errors as a top-level `{"error": N, "message": "..."}` object.
     * Only that shape counts: a track or artist literally named "error" must not be treated
     * as a failure. A top-level "error" we cannot decode is treated as temporary (code 16),
     * so the entry is retried instead of dropped.
     */
    internal fun lastFmErrorOrNull(raw: String): LastFmError? {
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        if (root["error"] == null) return null
        return runCatching { json.decodeFromString<LastFmError>(raw) }.getOrNull()
            ?: LastFmError(error = 16, message = "Unreadable Last.fm error response")
    }

    const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
    const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
    const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180
}


internal fun validateLastFmScrobbleResponse(raw: String) {
    val root = Json.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalStateException("Last.fm returned an invalid scrobble response")
    val scrobbles = root["scrobbles"] as? JsonObject
        ?: throw IllegalStateException("Last.fm returned no scrobble result")
    val attributes = scrobbles["@attr"] as? JsonObject
    val accepted = attributes?.text("accepted")?.toIntOrNull()
    val ignored = attributes?.text("ignored")?.toIntOrNull()

    if (accepted == null && ignored == null) {
        throw IllegalStateException("Last.fm returned no scrobble acceptance status")
    }
    if ((accepted ?: 0) > 0 && (ignored ?: 0) == 0) return

    val rows = when (val value = scrobbles["scrobble"]) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(value)
        else -> emptyList()
    }
    val ignoredCode = rows.firstNotNullOfOrNull { row ->
        (row["ignoredMessage"] as? JsonObject)?.text("code")?.toIntOrNull()
    } ?: 0
    val message = when (ignoredCode) {
        1 -> "Last.fm ignored the artist"
        2 -> "Last.fm rejected the track"
        3 -> "Last.fm rejected an old scrobble timestamp"
        4 -> "Last.fm rejected a future scrobble timestamp"
        5 -> "Last.fm daily scrobble limit exceeded"
        else -> "Last.fm did not accept the scrobble"
    }
    throw LastFM.ScrobbleRejectedException(
        ignoredCode = ignoredCode,
        retryable = ignoredCode == 5,
        message = message,
    )
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

internal fun parseLastFmSimilarTracks(raw: String): List<LastFM.RankedTrack> =
    parseLastFmChart(raw, "similartracks", "track").mapNotNull { row ->
        val name = row.text("name")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val artist = row["artist"].artistName()?.trim().orEmpty()
        if (artist.isEmpty()) return@mapNotNull null
        LastFM.RankedTrack(name, artist, row.long("playcount"), row.artwork())
    }

internal fun parseLastFmTopTracks(raw: String): List<LastFM.RankedTrack> =
    parseLastFmChart(raw, "toptracks", "track").mapNotNull { row ->
        val name = row.text("name") ?: return@mapNotNull null
        val artist = row["artist"].artistName().orEmpty()
        LastFM.RankedTrack(name, artist, row.long("playcount"), row.artwork())
    }

internal fun parseLastFmTopTracksPage(raw: String): LastFM.PagedResult<LastFM.RankedTrack> {
    val root = Json.parseToJsonElement(raw) as? JsonObject
        ?: error("Last.fm top tracks response is not a JSON object")
    val chart = root["toptracks"] as? JsonObject
        ?: error("Last.fm top tracks response is missing toptracks")
    val items = chart.rows("track").mapNotNull { row ->
        val name = row.text("name")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val artist = row["artist"].artistName()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        LastFM.RankedTrack(name, artist, row.long("playcount"), row.artwork())
    }
    return LastFM.PagedResult(items, chart.totalPages())
}

internal fun parseLastFmRecentTracksPage(raw: String): LastFM.PagedResult<LastFM.RecentTrack> {
    val root = Json.parseToJsonElement(raw) as? JsonObject
        ?: error("Last.fm recent tracks response is not a JSON object")
    val chart = root["recenttracks"] as? JsonObject
        ?: error("Last.fm recent tracks response is missing recenttracks")
    val items = chart.rows("track").mapNotNull { row ->
        val attributes = row["@attr"] as? JsonObject
        if (attributes?.text("nowplaying") == "true") return@mapNotNull null
        val timestamp = (row["date"] as? JsonObject)?.text("uts")?.toLongOrNull()
            ?: return@mapNotNull null
        val name = row.text("name")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val artist = row["artist"].artistName()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        LastFM.RecentTrack(name, artist, row.artwork(), timestamp)
    }
    return LastFM.PagedResult(items, chart.totalPages())
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

private fun JsonObject.rows(key: String): List<JsonObject> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(value)
    else -> emptyList()
}

private fun JsonObject.totalPages(): Int =
    (this["@attr"] as? JsonObject)?.text("totalPages")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

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
    is JsonObject -> text("name") ?: text("#text")
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    else -> null
}
