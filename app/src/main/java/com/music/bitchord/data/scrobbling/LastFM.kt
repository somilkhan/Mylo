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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

internal fun parseLastFmSimilarArtists(response: String, limit: Int = 8): List<String> {
    if (limit <= 0) return emptyList()
    val root = Json.parseToJsonElement(response) as? JsonObject ?: return emptyList()
    val similarArtists = root["similarartists"] as? JsonObject ?: return emptyList()
    val rawArtists = similarArtists["artist"] ?: return emptyList()
    val entries = when (rawArtists) {
        is JsonArray -> rawArtists
        is JsonObject -> listOf(rawArtists)
        else -> emptyList()
    }
    return entries.mapNotNull { element ->
        val artist = element as? JsonObject ?: return@mapNotNull null
        (artist["name"] as? JsonPrimitive)?.contentOrNull?.trim()
    }
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(limit.coerceAtMost(20))
}

internal fun parseLastFmTagTracks(
    response: String,
    limit: Int = 12,
): List<LastFM.DiscoveryTrack> {
    if (limit <= 0) return emptyList()
    val root = Json.parseToJsonElement(response) as? JsonObject ?: return emptyList()
    val tracks = root["tracks"] as? JsonObject ?: return emptyList()
    val rawTracks = tracks["track"] ?: return emptyList()
    val entries = when (rawTracks) {
        is JsonArray -> rawTracks
        is JsonObject -> listOf(rawTracks)
        else -> emptyList()
    }
    return entries.mapNotNull { element ->
        val track = element as? JsonObject ?: return@mapNotNull null
        val name = (track["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val artistElement = track["artist"]
        val artist = when (artistElement) {
            is JsonObject -> (artistElement["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            is JsonPrimitive -> artistElement.contentOrNull?.trim().orEmpty()
            else -> ""
        }
        if (name.isBlank() || artist.isBlank()) null else LastFM.DiscoveryTrack(name, artist)
    }
        .distinctBy { "${it.name.lowercase(Locale.ROOT)}|${it.artist.lowercase(Locale.ROOT)}" }
        .take(limit.coerceAtMost(50))
}


/** Parse Last.fm track.getsimilar responses without assuming an array payload. */
internal fun parseLastFmSimilarTracks(
    response: String,
    limit: Int = 50,
): List<LastFM.DiscoveryTrack> {
    if (limit <= 0) return emptyList()
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull()
        ?: return emptyList()
    val rows = root["similartracks"] as? JsonObject ?: return emptyList()
    return parseLastFmObjects(rows["track"]).mapNotNull { track ->
        val name = track.textValue("name")
        val artistObject = track["artist"] as? JsonObject
        val artist = artistObject?.textValue("name")
            ?: (track["artist"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (name.isBlank() || artist.isBlank()) null else LastFM.DiscoveryTrack(name, artist)
    }.distinctBy { "${it.name.lowercase(Locale.ROOT)}|${it.artist.lowercase(Locale.ROOT)}" }
        .take(limit.coerceAtMost(100))
}

internal fun parseLastFmObjects(value: JsonElement?): List<JsonObject> = when (value) {
    is JsonArray -> value.mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(value)
    else -> emptyList()
}

private fun JsonObject.textValue(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.lastFmArtwork(): String? {
    val images = this["image"] as? JsonArray ?: return null
    return images.mapNotNull { item ->
        val image = item as? JsonObject ?: return@mapNotNull null
        val url = image.textValue("#text")
        url.takeIf { it.isNotBlank() && !it.contains("2a96cbd8b46e442fc41c2b86b821562f") }
    }.lastOrNull()
}

internal fun parseLastFmUserStats(response: String): LastFM.UserStats? {
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull() ?: return null
    val user = root["user"] as? JsonObject ?: return null
    return LastFM.UserStats(
        playCount = user.textValue("playcount").toLongOrNull() ?: 0L,
        trackCount = user.textValue("track_count").toLongOrNull() ?: 0L,
        artistCount = user.textValue("artist_count").toLongOrNull() ?: 0L,
        albumCount = user.textValue("album_count").toLongOrNull() ?: 0L,
        avatarUrl = user.lastFmArtwork(),
    )
}

internal fun parseLastFmTopTracks(response: String, limit: Int = 50): List<LastFM.RankedTrack> {
    if (limit <= 0) return emptyList()
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull() ?: return emptyList()
    val rows = root["toptracks"] as? JsonObject ?: return emptyList()
    return parseLastFmObjects(rows["track"]).mapNotNull { track ->
        val name = track.textValue("name")
        val artistObject = track["artist"] as? JsonObject
        val artist = artistObject?.textValue("name")
            ?: (track["artist"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (name.isBlank() || artist.isBlank()) null else LastFM.RankedTrack(
            name = name,
            artist = artist,
            playCount = track.textValue("playcount").toLongOrNull() ?: 0L,
            artworkUrl = track.lastFmArtwork(),
        )
    }.distinctBy { "${it.name.lowercase(Locale.ROOT)}|${it.artist.lowercase(Locale.ROOT)}" }.take(limit)
}

internal fun parseLastFmTopArtists(response: String, limit: Int = 30): List<LastFM.RankedArtist> {
    if (limit <= 0) return emptyList()
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull() ?: return emptyList()
    val rows = root["topartists"] as? JsonObject ?: return emptyList()
    return parseLastFmObjects(rows["artist"]).mapNotNull { artist ->
        val name = artist.textValue("name")
        if (name.isBlank()) null else LastFM.RankedArtist(
            name = name,
            playCount = artist.textValue("playcount").toLongOrNull() ?: 0L,
            artworkUrl = artist.lastFmArtwork(),
        )
    }.distinctBy { it.name.lowercase(Locale.ROOT) }.take(limit)
}

internal fun parseLastFmTopAlbums(response: String, limit: Int = 20): List<LastFM.RankedAlbum> {
    if (limit <= 0) return emptyList()
    val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull() ?: return emptyList()
    val rows = root["topalbums"] as? JsonObject ?: return emptyList()
    return parseLastFmObjects(rows["album"]).mapNotNull { album ->
        val name = album.textValue("name")
        val artistObject = album["artist"] as? JsonObject
        val artist = artistObject?.textValue("name")
            ?: (album["artist"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (name.isBlank() || artist.isBlank()) null else LastFM.RankedAlbum(
            name = name,
            artist = artist,
            playCount = album.textValue("playcount").toLongOrNull() ?: 0L,
            artworkUrl = album.lastFmArtwork(),
        )
    }.distinctBy { "${it.name.lowercase(Locale.ROOT)}|${it.artist.lowercase(Locale.ROOT)}" }.take(limit)
}

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

    data class DiscoveryTrack(val name: String, val artist: String)
    data class UserStats(
        val playCount: Long,
        val trackCount: Long,
        val artistCount: Long,
        val albumCount: Long,
        val avatarUrl: String?,
    )
    data class RankedTrack(val name: String, val artist: String, val playCount: Long, val artworkUrl: String?)
    data class RankedArtist(val name: String, val playCount: Long, val artworkUrl: String?)
    data class RankedAlbum(val name: String, val artist: String, val playCount: Long, val artworkUrl: String?)

    private val supportedStatsPeriods = setOf("overall", "7day", "1month", "3month", "6month", "12month")

    /** LastWave-compatible Last.fm profile totals; this read requires an API key, not a write session. */
    suspend fun getUserStats(username: String): Result<UserStats> = discoveryResult {
        require(isInitialized()) { "Last.fm API credentials are not configured" }
        val user = username.trim()
        require(user.isNotBlank()) { "Connect a Last.fm username to load global statistics" }
        val response = postAndRead("user.getinfo", extra = mapOf("user" to user))
        parseLastFmUserStats(response) ?: error("Last.fm returned an invalid user profile")
    }

    suspend fun getTopTracks(username: String, period: String = "overall", limit: Int = 50): Result<List<RankedTrack>> =
        discoveryResult {
            require(isInitialized()) { "Last.fm API credentials are not configured" }
            val user = username.trim()
            require(user.isNotBlank()) { "Connect a Last.fm username to load global statistics" }
            require(period in supportedStatsPeriods) { "Unsupported Last.fm statistics period" }
            val response = postAndRead(
                "user.gettoptracks",
                extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
            )
            parseLastFmTopTracks(response, limit)
        }

    suspend fun getTopArtists(username: String, period: String = "overall", limit: Int = 30): Result<List<RankedArtist>> =
        discoveryResult {
            require(isInitialized()) { "Last.fm API credentials are not configured" }
            val user = username.trim()
            require(user.isNotBlank()) { "Connect a Last.fm username to load global statistics" }
            require(period in supportedStatsPeriods) { "Unsupported Last.fm statistics period" }
            val response = postAndRead(
                "user.gettopartists",
                extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
            )
            parseLastFmTopArtists(response, limit)
        }

    suspend fun getTopAlbums(username: String, period: String = "overall", limit: Int = 20): Result<List<RankedAlbum>> =
        discoveryResult {
            require(isInitialized()) { "Last.fm API credentials are not configured" }
            val user = username.trim()
            require(user.isNotBlank()) { "Connect a Last.fm username to load global statistics" }
            require(period in supportedStatsPeriods) { "Unsupported Last.fm statistics period" }
            val response = postAndRead(
                "user.gettopalbums",
                extra = mapOf("user" to user, "period" to period, "limit" to limit.coerceIn(1, 100).toString()),
            )
            parseLastFmTopAlbums(response, limit)
        }

    /** Public Last.fm discovery endpoint; does not require a user session. */
    suspend fun getSimilarArtists(artist: String, limit: Int = 8): Result<List<String>> =
        discoveryResult {
            require(isInitialized()) { "Last.fm API credentials are not configured for this build" }
            val cleanArtist = artist.trim()
            require(cleanArtist.isNotBlank()) { "Choose an artist first" }
            val response = postAndRead(
                method = "artist.getsimilar",
                extra = mapOf("artist" to cleanArtist, "limit" to limit.coerceIn(1, 20).toString()),
            )
            parseLastFmSimilarArtists(response, limit)
        }

    /** Public Last.fm similar-track fallback; callers resolve these to playable catalogue tracks. */
    suspend fun getSimilarTracks(
        track: String,
        artist: String,
        limit: Int = 50,
    ): Result<List<DiscoveryTrack>> = discoveryResult {
        require(isInitialized()) { "Last.fm API credentials are not configured for this build" }
        val cleanTrack = track.trim()
        val cleanArtist = artist.trim()
        require(cleanTrack.isNotBlank() && cleanArtist.isNotBlank()) { "Choose a track first" }
        val response = postAndRead(
            method = "track.getsimilar",
            extra = mapOf(
                "track" to cleanTrack,
                "artist" to cleanArtist,
                "limit" to limit.coerceIn(1, 100).toString(),
            ),
        )
        parseLastFmSimilarTracks(response, limit)
    }

    /** Public Last.fm tag chart; results are later resolved to playable YouTube Music tracks. */
    suspend fun getTagTopTracks(tag: String, limit: Int = 12): Result<List<DiscoveryTrack>> =
        discoveryResult {
            require(isInitialized()) { "Last.fm API credentials are not configured for this build" }
            val cleanTag = tag.trim()
            require(cleanTag.isNotBlank()) { "Enter a genre or tag first" }
            val response = postAndRead(
                method = "tag.gettoptracks",
                extra = mapOf("tag" to cleanTag, "limit" to limit.coerceIn(1, 50).toString()),
            )
            parseLastFmTagTracks(response, limit)
        }

    private suspend fun <T> discoveryResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
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
