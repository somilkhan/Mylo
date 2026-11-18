package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    @Synchronized
    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return
        runCatching {
            val list = mutableListOf<LocalPlaylist>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val songsArray = obj.getJSONArray("songs")
                val songs = mutableListOf<Song>()
                for (j in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(j)
                    val song = Song(
                        videoId = sObj.optString("videoId", ""),
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                list.add(
                    LocalPlaylist(
                        id = id,
                        title = title,
                        songs = songs,
                        createdAtEpochMillis = obj.optLong("createdAtEpochMillis", 0L),
                        generationMode = obj.optString("generationMode", null)
                            .takeIf { !it.isNullOrBlank() && it != "null" },
                        isPinned = obj.optBoolean("isPinned", false),
                    ),
                )
            }
            _playlists.value = list
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
        val updated = listOf(playlist) + _playlists.value.filterNot { it.id == id }
        _playlists.value = updated
        persist(updated)
        return playlist
    }

    @Synchronized
    fun deletePlaylist(id: String) {
        val updated = _playlists.value.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        _playlists.value = updated
        persist(updated)
    }

    @Synchronized
    fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(title = newTitle)
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    /**
     * Replaces a playlist's tracks without changing its identity or generation metadata.
     * Returns false when the playlist no longer exists.
     */
    @Synchronized
    fun updatePlaylistSongs(id: String, songs: List<Song>): Boolean {
        val cleanId = normalizeId(id)
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.browseId == id }
        if (index < 0) return false
        val updated = _playlists.value.toMutableList().apply {
            this[index] = this[index].copy(songs = songs.toList())
        }
        _playlists.value = updated
        persist(updated)
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
        _playlists.value = updated
        persist(updated)
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
        _playlists.value = updated
        persist(updated)
        return true
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    private fun normalizeId(id: String): String =
        id.removePrefix("local:playlist:").removePrefix("VL")

    @Synchronized
    private fun persist(list: List<LocalPlaylist>) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (playlist in list) {
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("title", playlist.title)
                pObj.put("createdAtEpochMillis", playlist.createdAtEpochMillis)
                pObj.put("generationMode", playlist.generationMode)
                pObj.put("isPinned", playlist.isPinned)
                val songsArray = JSONArray()
                for (song in playlist.songs) {
                    val sObj = JSONObject()
                    sObj.put("videoId", song.videoId)
                    sObj.put("title", song.title)
                    sObj.put("artist", song.artist)
                    sObj.put("thumbnailUrl", song.thumbnailUrl)
                    sObj.put("durationText", song.durationText)
                    sObj.put("artistId", song.artistId)
                    sObj.put("albumId", song.albumId)
                    sObj.put("albumName", song.albumName)
                    sObj.put("isVideo", song.isVideo)
                    songsArray.put(sObj)
                }
                pObj.put("songs", songsArray)
                jsonArray.put(pObj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        }
    }
}
