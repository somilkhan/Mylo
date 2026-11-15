package com.music.bitchord.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A generated mix saved on this device. */
internal data class SavedScrobblePlaylist(
    val title: String,
    val reason: String,
    val songs: List<Song>,
)

/**
 * Device-persistent snapshots of generated mixes. This store is deliberately
 * separate from listening history: removing a playlist never edits a listen.
 */
internal object ScrobblePlaylistStore {
    private const val PREF_NAME = "mylo_scrobble_playlists"
    private const val KEY_PLAYLISTS = "saved_playlists_v1"
    private const val MAX_SAVED_PLAYLISTS = 20
    private const val MAX_TRACKS_PER_PLAYLIST = 50

    private var preferences: SharedPreferences? = null
    val playlists = mutableStateListOf<SavedScrobblePlaylist>()

    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = preferences?.getString(KEY_PLAYLISTS, null) ?: return
        val restored = ScrobblePlaylistCodec.decodeOrNull(raw) ?: return
        playlists.clear()
        playlists.addAll(restored)
    }

    fun save(title: String, reason: String, songs: List<Song>): Boolean {
        if (title.isBlank() || songs.isEmpty()) return false
        val cleanTitle = title.trim()
        val snapshot = songs.distinctBy(::songKey).take(MAX_TRACKS_PER_PLAYLIST)
        val sameSnapshot = playlists.firstOrNull { existing ->
            (existing.title == cleanTitle || existing.title.startsWith("$cleanTitle ")) && existing.songs.map(::songKey) == snapshot.map(::songKey)
        }
        if (sameSnapshot != null) return true
        if (playlists.size >= MAX_SAVED_PLAYLISTS) return false

        // Never silently replace a previously saved snapshot when a fresh mix
        // has the same generated title. Keep both, with a predictable suffix.
        var finalTitle = cleanTitle
        var suffix = 2
        while (playlists.any { it.title.equals(finalTitle, ignoreCase = true) }) {
            finalTitle = "$cleanTitle $suffix"
            suffix++
        }
        playlists.add(0, SavedScrobblePlaylist(finalTitle, reason, snapshot))
        persist()
        return true
    }

    private fun songKey(song: Song): String =
        song.localUri?.takeIf(String::isNotBlank)?.let { "local:$it" }
            ?: song.localPath?.takeIf(String::isNotBlank)?.let { "path:$it" }
            ?: song.videoId.takeIf(String::isNotBlank)?.let { "video:$it" }
            ?: "${song.title.lowercase()}|${song.artist.lowercase()}"

    /**
     * Renames one saved snapshot without changing its tracks or order.
     * Names are unique ignoring case so playlist identity remains unambiguous
     * in search, detail navigation and persistence.
     */
    fun rename(oldTitle: String, newTitle: String): Boolean {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) return false
        val index = playlists.indexOfFirst { it.title == oldTitle }
        if (index < 0) return false
        if (playlists.withIndex().any { (otherIndex, playlist) ->
                otherIndex != index && playlist.title.equals(cleanTitle, ignoreCase = true)
            }
        ) return false

        val existing = playlists[index]
        if (existing.title == cleanTitle) return true
        playlists[index] = existing.copy(title = cleanTitle)
        persist()
        return true
    }

    fun remove(title: String) {
        if (playlists.removeAll { it.title == title }) persist()
    }

    private fun persist() {
        val prefs = preferences ?: return
        prefs.edit()
            .putString(KEY_PLAYLISTS, ScrobblePlaylistCodec.encode(playlists.toList()))
            .apply()
    }
}

/** Versioned JSON snapshot; queue/session-only fields are intentionally not persisted. */
internal object ScrobblePlaylistCodec {
    private const val CURRENT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PlaylistFile(
        val version: Int = CURRENT_VERSION,
        val playlists: List<PlaylistRecord> = emptyList(),
    )

    @Serializable
    private data class PlaylistRecord(
        val title: String,
        val reason: String,
        val songs: List<SongRecord>,
    )

    @Serializable
    private data class ArtistRecord(
        val name: String,
        val browseId: String? = null,
    )

    @Serializable
    private data class SongRecord(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String? = null,
        val durationText: String? = null,
        val artistId: String? = null,
        val artists: List<ArtistRecord> = emptyList(),
        val albumId: String? = null,
        val albumName: String? = null,
        val isVideo: Boolean = false,
        val isVideoOrigin: Boolean = isVideo,
        val localUri: String? = null,
        val localPath: String? = null,
        val downloadFormat: String? = null,
        val localDateAddedSeconds: Long? = null,
        val localDateModifiedSeconds: Long? = null,
        val trackNumber: Int? = null,
        val sourceQuality: String? = null,
        val sourceHiRes: Boolean = false,
        val isExplicit: Boolean? = null,
    )

    fun encode(playlists: List<SavedScrobblePlaylist>): String =
        json.encodeToString(
            PlaylistFile(
                playlists = playlists.map { playlist ->
                    PlaylistRecord(
                        title = playlist.title,
                        reason = playlist.reason,
                        songs = playlist.songs.map { it.toRecord() },
                    )
                },
            ),
        )

    fun decodeOrNull(raw: String): List<SavedScrobblePlaylist>? = runCatching {
        val file = json.decodeFromString<PlaylistFile>(raw)
        require(file.version == CURRENT_VERSION) { "Unsupported Scrobble playlist schema: ${file.version}" }
        file.playlists.map { playlist ->
            SavedScrobblePlaylist(
                title = playlist.title,
                reason = playlist.reason,
                songs = playlist.songs.map { it.toSong() },
            )
        }.filter { it.title.isNotBlank() && it.songs.isNotEmpty() }
    }.getOrNull()

    private fun Song.toRecord() = SongRecord(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        artists = artists.map { ArtistRecord(it.name, it.browseId) },
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        isVideoOrigin = isVideoOrigin,
        localUri = localUri,
        localPath = localPath,
        downloadFormat = downloadFormat,
        localDateAddedSeconds = localDateAddedSeconds,
        localDateModifiedSeconds = localDateModifiedSeconds,
        trackNumber = trackNumber,
        sourceQuality = sourceQuality,
        sourceHiRes = sourceHiRes,
        isExplicit = isExplicit,
    )

    private fun SongRecord.toSong() = Song(
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
        downloadFormat = downloadFormat,
        localDateAddedSeconds = localDateAddedSeconds,
        localDateModifiedSeconds = localDateModifiedSeconds,
        trackNumber = trackNumber,
        sourceQuality = sourceQuality,
        sourceHiRes = sourceHiRes,
        isExplicit = isExplicit,
    )
}
