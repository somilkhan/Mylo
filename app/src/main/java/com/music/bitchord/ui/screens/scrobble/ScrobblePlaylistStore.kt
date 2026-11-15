package com.music.bitchord.ui.screens.scrobble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class SavedScrobblePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Song>,
    val createdAtMillis: Long,
    val pinned: Boolean = false,
    val customCoverUri: String? = null,
)

@Serializable
private data class StoredScrobbleSnapshot(
    val version: Int = 1,
    val playlists: List<StoredScrobblePlaylist> = emptyList(),
)

@Serializable
private data class StoredScrobblePlaylist(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val tracks: List<StoredScrobbleSong> = emptyList(),
    val createdAtMillis: Long = 0L,
    val pinned: Boolean = false,
    val customCoverUri: String? = null,
)

@Serializable
private data class StoredScrobbleSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val artistId: String? = null,
    val artists: List<StoredScrobbleArtist> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val isVideo: Boolean = false,
    val isVideoOrigin: Boolean = isVideo,
    val localUri: String? = null,
    val localPath: String? = null,
    val trackNumber: Int? = null,
    val sourceQuality: String? = null,
    val sourceHiRes: Boolean = false,
    val isExplicit: Boolean? = null,
)

@Serializable
private data class StoredScrobbleArtist(
    val name: String,
    val browseId: String? = null,
)

/** Versioned codec; missing fields in old snapshots use safe defaults. */
internal object ScrobblePlaylistCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(playlists: List<SavedScrobblePlaylist>): String =
        json.encodeToString(StoredScrobbleSnapshot(playlists = playlists.map { it.toStored() }))

    fun decode(raw: String): List<SavedScrobblePlaylist> {
        val stored = if (raw.trimStart().startsWith("[")) {
            // The first port snapshots were bare arrays; keep them readable.
            json.decodeFromString<List<StoredScrobblePlaylist>>(raw)
        } else {
            val snapshot = json.decodeFromString<StoredScrobbleSnapshot>(raw)
            require(snapshot.version == 1) { "Unsupported playlist snapshot version" }
            snapshot.playlists
        }
        return stored.map { it.toDomain() }
    }

    private fun SavedScrobblePlaylist.toStored() = StoredScrobblePlaylist(
        id = id,
        title = title,
        subtitle = subtitle,
        tracks = tracks.map { it.toStored() },
        createdAtMillis = createdAtMillis,
        pinned = pinned,
        customCoverUri = customCoverUri,
    )

    private fun Song.toStored() = StoredScrobbleSong(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        artists = artists.map { StoredScrobbleArtist(it.name, it.browseId) },
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        isVideoOrigin = isVideoOrigin,
        localUri = localUri,
        localPath = localPath,
        trackNumber = trackNumber,
        sourceQuality = sourceQuality,
        sourceHiRes = sourceHiRes,
        isExplicit = isExplicit,
    )

    private fun StoredScrobblePlaylist.toDomain() = SavedScrobblePlaylist(
        id = id,
        title = title,
        subtitle = subtitle,
        tracks = tracks.map { it.toDomain() },
        createdAtMillis = createdAtMillis,
        pinned = pinned,
        customCoverUri = customCoverUri,
    )

    private fun StoredScrobbleSong.toDomain() = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        artists = artists.map { ArtistRef(it.name, it.browseId) },
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        isVideoOrigin = isVideoOrigin,
        localUri = localUri,
        localPath = localPath,
        trackNumber = trackNumber,
        sourceQuality = sourceQuality,
        sourceHiRes = sourceHiRes,
        isExplicit = isExplicit,
        playbackSource = "Scrobble Playlist",
    )
}

/**
 * LastWave playlist actions adapted to Mylo's existing SharedPreferences
 * persistence. Writes are serialized and committed off the main thread;
 * corrupt snapshots are never silently overwritten by a later edit.
 */
object ScrobblePlaylistStore {
    private const val TAG = "ScrobblePlaylistStore"
    private const val PREF_NAME = "mylo_scrobble_playlists"
    private const val KEY_PLAYLISTS = "playlists_v1"
    const val MAX_PLAYLISTS = 20

    private lateinit var preferences: SharedPreferences
    private var loadFailed = false
    private val mutex = Mutex()
    private val _playlists = MutableStateFlow<List<SavedScrobblePlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun init(context: Context) {
        if (this::preferences.isInitialized) return
        preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = preferences.getString(KEY_PLAYLISTS, null) ?: return
        try {
            _playlists.value = sort(ScrobblePlaylistCodec.decode(raw))
        } catch (error: Exception) {
            loadFailed = true
            Log.e(TAG, "Saved playlist snapshot is unreadable; preserving it until recovery", error)
        }
    }

    fun get(id: String): SavedScrobblePlaylist? =
        _playlists.value.firstOrNull { it.id == id }

    suspend fun create(
        title: String?,
        subtitle: String,
        tracks: List<Song>,
    ): SavedScrobblePlaylist {
        val cleanTracks = tracks.distinctScrobbleSongs()
        require(cleanTracks.isNotEmpty()) { "A playlist needs at least one playable track" }
        return mutate { current ->
            require(current.size < MAX_PLAYLISTS) { "You can save up to $MAX_PLAYLISTS playlists" }
            val cleanTitle = title?.trim().orEmpty().ifBlank {
                ScrobblePlaylistNamer.generateUniqueName(current.map { it.title })
            }
            require(current.none { it.title.equals(cleanTitle, ignoreCase = true) }) {
                "A playlist with this name already exists"
            }
            val created = SavedScrobblePlaylist(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                subtitle = subtitle,
                tracks = cleanTracks,
                createdAtMillis = System.currentTimeMillis(),
            )
            (listOf(created) + current) to created
        }
    }

    suspend fun rename(id: String, title: String) {
        val clean = title.trim()
        require(clean.isNotBlank()) { "Playlist name cannot be empty" }
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            require(current.none { it.id != id && it.title.equals(clean, ignoreCase = true) }) {
                "A playlist with this name already exists"
            }
            current.map { if (it.id == id) it.copy(title = clean) else it } to Unit
        }
    }

    suspend fun delete(id: String) {
        mutate { current -> current.filterNot { it.id == id } to Unit }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            current.map { if (it.id == id) it.copy(pinned = pinned) else it } to Unit
        }
    }

    suspend fun setCustomCover(id: String, uri: String?) {
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            current.map { if (it.id == id) it.copy(customCoverUri = uri) else it } to Unit
        }
    }

    suspend fun addTracks(id: String, tracks: List<Song>) {
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            current.map { playlist ->
                if (playlist.id != id) playlist else {
                    val additions = tracks.filterNot { candidate ->
                        playlist.tracks.any { it.sameScrobbleSongAs(candidate) }
                    }
                    playlist.copy(tracks = (playlist.tracks + additions).distinctScrobbleSongs())
                }
            } to Unit
        }
    }

    suspend fun removeTrack(id: String, videoId: String) {
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            current.map { playlist ->
                if (playlist.id == id) playlist.copy(
                    tracks = playlist.tracks.filterNot { it.videoId == videoId },
                ) else playlist
            } to Unit
        }
    }

    suspend fun moveTrack(id: String, fromIndex: Int, toIndex: Int) {
        mutate { current ->
            require(current.any { it.id == id }) { "Playlist not found" }
            current.map { playlist ->
                if (playlist.id != id || fromIndex !in playlist.tracks.indices ||
                    toIndex !in playlist.tracks.indices || fromIndex == toIndex
                ) playlist else {
                    val reordered = playlist.tracks.toMutableList()
                    val track = reordered.removeAt(fromIndex)
                    reordered.add(toIndex, track)
                    playlist.copy(tracks = reordered)
                }
            } to Unit
        }
    }

    private suspend fun <T> mutate(
        transform: (List<SavedScrobblePlaylist>) -> Pair<List<SavedScrobblePlaylist>, T>,
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(this@ScrobblePlaylistStore::preferences.isInitialized) {
                "ScrobblePlaylistStore has not been initialized"
            }
            check(!loadFailed) {
                "Saved playlists could not be read; refusing to overwrite the original snapshot"
            }
            val (updated, result) = transform(_playlists.value)
            val sorted = sort(updated)
            val saved = preferences.edit()
                .putString(KEY_PLAYLISTS, ScrobblePlaylistCodec.encode(sorted))
                .commit()
            check(saved) { "Couldn't save playlist changes" }
            _playlists.value = sorted
            result
        }
    }

    private fun sort(playlists: List<SavedScrobblePlaylist>) =
        playlists.sortedWith(compareByDescending<SavedScrobblePlaylist> { it.pinned }
            .thenByDescending { it.createdAtMillis })
}
