package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
    /** Creation time for generated/local playlists; zero means legacy data. */
    val createdAtEpochMillis: Long = 0L,
    /** Stable generator mode identifier, null for imported/manual playlists. */
    val generationMode: String? = null,
    val isPinned: Boolean = false,
    /** Persisted content URI chosen by the user; null uses the first track's artwork. */
    val customCoverUri: String? = null,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    @Synchronized
    fun reload() = load()

    @Synchronized
    private fun load() {
        if (!this::prefs.isInitialized) {
            _loadError.value = "Local playlist storage is not initialized"
            return
        }
        try {
            val raw = prefs.getString(KEY_PLAYLISTS, null)
            _playlists.value = if (raw.isNullOrBlank()) emptyList() else LocalPlaylistCodec.decode(raw)
            _loadError.value = null
        } catch (error: Exception) {
            _loadError.value = error.message ?: "Couldn't load local playlists"
        }
    }

    @Synchronized
    fun savePlaylist(
        title: String,
        songs: List<Song>,
        generationMode: String? = null,
    ): LocalPlaylist {
        val playlist = LocalPlaylist(
            id = "sp_local_" + UUID.randomUUID(),
            title = title,
            songs = songs.toList(),
            createdAtEpochMillis = System.currentTimeMillis(),
            generationMode = generationMode,
        )
        val updated = listOf(playlist) + _playlists.value
        check(persist(updated)) { "Couldn't save playlist to device storage." }
        _playlists.value = updated
        return playlist
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val current = _playlists.value
        val updated = current.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        if (updated.size == current.size) return false
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    @Synchronized
    fun renamePlaylist(id: String, newTitle: String): Boolean {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val current = _playlists.value
        val index = current.indexOfFirst { it.id == cleanId || it.id == id || it.browseId == id }
        if (index < 0) return false
        val updated = current.toMutableList().apply {
            this[index] = this[index].copy(title = newTitle)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    /**
     * Replaces a playlist's tracks without changing its identity or generation metadata.
     * Returns false when the playlist is missing or the persisted write fails.
     */
    @Synchronized
    fun updatePlaylistSongs(id: String, songs: List<Song>): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = this[index].copy(songs = songs.toList())
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    /**
     * Adds one catalogue track without duplicating its YouTube video identity.
     * Returns false if the playlist is missing, the track is duplicate/invalid, or persistence fails.
     */
    @Synchronized
    fun addSongIfAbsent(id: String, song: Song): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val existing = _playlists.value[index]
        val updatedSongs = appendUniquePlaylistSong(existing.songs, song) ?: return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = existing.copy(songs = updatedSongs)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    /** Pin state is metadata only; existing playlist order is left untouched. */
    @Synchronized
    fun setPinned(id: String, pinned: Boolean): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = this[index].copy(isPinned = pinned)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    /** Stores a user-selected artwork URI without changing playlist identity or tracks. */
    @Synchronized
    fun setCustomCover(id: String, uri: String?): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = this[index].copy(customCoverUri = uri)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    /** Moves a playlist within the current order, clamping the destination safely. */
    @Synchronized
    fun movePlaylist(id: String, destinationIndex: Int): Boolean {
        val cleanId = normalizeId(id)
        val current = _playlists.value
        val from = current.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (from < 0 || current.isEmpty()) return false
        val to = destinationIndex.coerceIn(0, current.lastIndex)
        if (from == to) return true
        val updated = current.toMutableList()
        val moved = updated.removeAt(from)
        updated.add(to, moved)
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    @Synchronized
    fun moveSong(id: String, sourceIndex: Int, expectedVideoId: String, direction: Int): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val current = _playlists.value[index]
        val songs = movePlaylistSong(current.songs, sourceIndex, expectedVideoId, direction) ?: return false
        if (songs == current.songs) return true
        val updated = _playlists.value.toMutableList().apply {
            this[index] = current.copy(songs = songs)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    @Synchronized
    fun removeSong(id: String, sourceIndex: Int, expectedVideoId: String): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val current = _playlists.value[index]
        val songs = removePlaylistSong(current.songs, sourceIndex, expectedVideoId) ?: return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = current.copy(songs = songs)
        }
        if (!persist(updated)) return false
        _playlists.value = updated
        return true
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    private fun normalizeId(id: String): String =
        id.removePrefix("local:playlist:").removePrefix("VL")

    @Synchronized
    private fun persist(list: List<LocalPlaylist>): Boolean {
        if (!this::prefs.isInitialized) return false
        return runCatching {
            prefs.edit().putString(KEY_PLAYLISTS, LocalPlaylistCodec.encode(list)).commit()
        }.getOrDefault(false)
    }
}


/**
 * Appends a catalogue track to a local playlist, rejecting empty ids, video
 * uploads and duplicate YouTube identities while preserving existing order.
 */
internal fun movePlaylistSong(
    songs: List<Song>,
    sourceIndex: Int,
    expectedVideoId: String,
    direction: Int,
): List<Song>? {
    val from = if (songs.getOrNull(sourceIndex)?.videoId == expectedVideoId) {
        sourceIndex
    } else {
        songs.indexOfFirst { it.videoId == expectedVideoId }
    }
    if (from < 0) return null
    val to = (from + direction).coerceIn(0, songs.lastIndex)
    if (from == to) return songs
    return songs.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun removePlaylistSong(
    songs: List<Song>,
    sourceIndex: Int,
    expectedVideoId: String,
): List<Song>? {
    val index = if (songs.getOrNull(sourceIndex)?.videoId == expectedVideoId) {
        sourceIndex
    } else {
        songs.indexOfFirst { it.videoId == expectedVideoId }
    }
    if (index < 0) return null
    return songs.toMutableList().apply { removeAt(index) }
}

internal fun appendUniquePlaylistSong(existing: List<Song>, candidate: Song): List<Song>? {
    val videoId = candidate.videoId.trim()
    if (videoId.isEmpty() || candidate.isVideo || existing.any { it.videoId == videoId }) return null
    return existing + candidate
}


/** CSV/M3U serializers for a local playlist; kept pure for unit testing. */
internal object LocalPlaylistExport {
    fun csv(playlist: LocalPlaylist): String = buildString {
        appendLine(csvRow(listOf("title", "artist", "video_id", "duration", "video_url")))
        playlist.songs.forEach { song ->
            appendLine(
                csvRow(
                    listOf(
                        song.title,
                        song.artist,
                        song.videoId,
                        song.durationText.orEmpty(),
                        "https://music.youtube.com/watch?v=${song.videoId}",
                    ),
                ),
            )
        }
    }

    fun m3u(playlist: LocalPlaylist): String = buildString {
        appendLine("#EXTM3U")
        playlist.songs.filter { it.videoId.isNotBlank() && !it.isVideo }.forEach { song ->
            val duration = song.durationText?.split(':')?.map { it.toLongOrNull() }
                ?.takeIf { values -> values.isNotEmpty() && values.all { it != null } }
                ?.let { values ->
                    when (values.size) {
                        2 -> values[0]!! * 60 + values[1]!!
                        3 -> values[0]!! * 3_600 + values[1]!! * 60 + values[2]!!
                        else -> -1L
                    }
                } ?: -1L
            val label = "${song.artist} - ${song.title}".replace('\r', ' ').replace('\n', ' ')
            appendLine("#EXTINF:$duration,$label")
            appendLine("https://music.youtube.com/watch?v=${song.videoId}")
        }
    }

    fun fileName(title: String, extension: String): String {
        val ext = when (extension.lowercase(java.util.Locale.ROOT).removePrefix(".")) {
            "csv" -> "csv"
            "m3u" -> "m3u"
            else -> "txt"
        }
        val invalid = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        val safeTitle = title.map { if (it in invalid) '_' else it }
            .joinToString("").trim().trim('.').take(80).ifBlank { "Playlist" }
        return "$safeTitle.$ext"
    }

    private fun csvRow(values: List<String>): String = values.joinToString(",") { value ->
        "\"" + value.replace("\"", "\"\"") + "\""
    }
}
