package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Version-tolerant JSON boundary for local playlists.
 *
 * Missing metadata is expected for playlists saved by older app versions.
 * Keep this codec free of Android framework APIs so its compatibility contract
 * can be exercised by ordinary JVM tests.
 */
internal object LocalPlaylistCodec {

    fun decode(raw: String): List<LocalPlaylist> {
        val jsonArray = JSONArray(raw)
        return buildList(jsonArray.length()) {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val songsArray = obj.getJSONArray("songs")
                val songs = buildList(songsArray.length()) {
                    for (j in 0 until songsArray.length()) {
                        val song = songsArray.getJSONObject(j)
                        add(
                            Song(
                                videoId = song.optString("videoId", ""),
                                title = song.optString("title", ""),
                                artist = song.optString("artist", ""),
                                thumbnailUrl = song.optionalString("thumbnailUrl"),
                                durationText = song.optionalString("durationText"),
                                artistId = song.optionalString("artistId"),
                                albumId = song.optionalString("albumId"),
                                albumName = song.optionalString("albumName"),
                                isVideo = song.optBoolean("isVideo", false),
                            ),
                        )
                    }
                }
                add(
                    LocalPlaylist(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        songs = songs,
                        createdAtEpochMillis = obj.optLong("createdAtEpochMillis", 0L),
                        generationMode = obj.optionalString("generationMode"),
                        isPinned = obj.optBoolean("isPinned", false),
                        customCoverUri = obj.optionalString("customCoverUri"),
                    ),
                )
            }
        }
    }

    fun encode(playlists: List<LocalPlaylist>): String {
        val jsonArray = JSONArray()
        playlists.forEach { playlist ->
            val playlistJson = JSONObject()
                .put("id", playlist.id)
                .put("title", playlist.title)
                .put("createdAtEpochMillis", playlist.createdAtEpochMillis)
                .put("generationMode", playlist.generationMode)
                .put("isPinned", playlist.isPinned)
                .put("customCoverUri", playlist.customCoverUri)
            val songsArray = JSONArray()
            playlist.songs.forEach { song ->
                songsArray.put(
                    JSONObject()
                        .put("videoId", song.videoId)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("thumbnailUrl", song.thumbnailUrl)
                        .put("durationText", song.durationText)
                        .put("artistId", song.artistId)
                        .put("albumId", song.albumId)
                        .put("albumName", song.albumName)
                        .put("isVideo", song.isVideo),
                )
            }
            playlistJson.put("songs", songsArray)
            jsonArray.put(playlistJson)
        }
        return jsonArray.toString()
    }

    private fun JSONObject.optionalString(name: String): String? =
        optString(name, null).takeUnless { it.isNullOrBlank() || it == "null" }
}
