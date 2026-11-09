package com.music.bitchord.data.sync

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Account-scoped copy of the remote library and history.
 *
 * Mylo reads YouTube Music directly rather than mirroring it into a Room database like InterTune.
 * This snapshot is the local mirror for offline startup; a failed or incomplete refresh never
 * replaces the last complete copy.
 */
data class AccountLibrarySnapshot(
    val library: LibraryPage,
    val historySections: List<InnertubeParser.HistorySection>,
    val recentSongs: List<Song>,
    val savedAt: Long,
)

class AccountLibrarySnapshotStore(private val directory: File) {
    private fun fileFor(accountKey: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(directory, "library-$hash.json")
    }

    fun read(accountKey: String): AccountLibrarySnapshot? {
        val file = fileFor(accountKey)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optString("account") != accountKey) return null
            val library = root.getJSONObject("library")
            AccountLibrarySnapshot(
                library = LibraryPage(
                    likedSongs = readSongs(library.optJSONArray("liked")),
                    librarySongs = readSongs(library.optJSONArray("songs")),
                    shelves = readShelves(library.optJSONArray("shelves")),
                    likedContinuation = null,
                ),
                historySections = readHistory(root.optJSONArray("history")),
                recentSongs = readSongs(root.optJSONArray("recent")),
                savedAt = root.optLong("savedAt"),
            )
        }.getOrNull()
    }

    fun write(accountKey: String, snapshot: AccountLibrarySnapshot) {
        directory.mkdirs()
        val file = fileFor(accountKey)
        val temporary = File(directory, file.name + ".tmp")
        val root = JSONObject()
            .put("account", accountKey)
            .put("savedAt", snapshot.savedAt)
            .put("library", JSONObject()
                .put("liked", songsJson(snapshot.library.likedSongs))
                .put("songs", songsJson(snapshot.library.librarySongs))
                .put("shelves", shelvesJson(snapshot.library.shelves)))
            .put("history", historyJson(snapshot.historySections))
            .put("recent", songsJson(snapshot.recentSongs))
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun songsJson(songs: List<Song>) = JSONArray().apply {
        songs.forEach { song ->
            put(JSONObject()
                .put("id", song.videoId)
                .put("title", song.title)
                .put("artist", song.artist)
                .putNullable("art", song.thumbnailUrl)
                .putNullable("duration", song.durationText)
                .putNullable("artistId", song.artistId)
                .put("artists", JSONArray().apply {
                    song.artists.forEach { artist ->
                        put(JSONObject().put("name", artist.name).putNullable("id", artist.browseId))
                    }
                })
                .putNullable("albumId", song.albumId)
                .putNullable("albumName", song.albumName)
                .put("isVideo", song.isVideo)
                .put("isVideoOrigin", song.isVideoOrigin)
                .putNullable("setVideoId", song.setVideoId))
        }
    }

    private fun readSongs(array: JSONArray?): List<Song> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val id = row.optString("id").takeIf(String::isNotBlank) ?: continue
            val artists = buildList {
                val refs = row.optJSONArray("artists") ?: return@buildList
                for (i in 0 until refs.length()) {
                    val artist = refs.optJSONObject(i) ?: continue
                    val name = artist.optString("name").takeIf(String::isNotBlank) ?: continue
                    add(ArtistRef(name, artist.stringOrNull("id")))
                }
            }
            add(Song(
                videoId = id,
                title = row.optString("title", "Unknown title"),
                artist = row.optString("artist", "Unknown artist"),
                thumbnailUrl = row.stringOrNull("art"),
                durationText = row.stringOrNull("duration"),
                artistId = row.stringOrNull("artistId"),
                artists = artists,
                albumId = row.stringOrNull("albumId"),
                albumName = row.stringOrNull("albumName"),
                isVideo = row.optBoolean("isVideo"),
                isVideoOrigin = row.optBoolean("isVideoOrigin"),
                setVideoId = row.stringOrNull("setVideoId"),
            ))
        }
    }

    private fun shelvesJson(shelves: List<HomeShelf>) = JSONArray().apply {
        shelves.forEach { shelf ->
            put(JSONObject()
                .put("title", shelf.title)
                .put("subtitle", shelf.subtitle)
                .putNullable("moreBrowseId", shelf.moreBrowseId)
                .putNullable("moreParams", shelf.moreParams)
                .put("items", JSONArray().apply {
                    shelf.items.forEach { item ->
                        put(JSONObject()
                            .put("title", item.title)
                            .put("subtitle", item.subtitle)
                            .putNullable("art", item.thumbnailUrl)
                            .putNullable("videoId", item.videoId)
                            .putNullable("browseId", item.browseId))
                    }
                }))
        }
    }

    private fun readShelves(array: JSONArray?): List<HomeShelf> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val items = buildList {
                val values = row.optJSONArray("items") ?: return@buildList
                for (i in 0 until values.length()) {
                    val item = values.optJSONObject(i) ?: continue
                    add(ShelfItem(
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                        thumbnailUrl = item.stringOrNull("art"),
                        videoId = item.stringOrNull("videoId"),
                        browseId = item.stringOrNull("browseId"),
                    ))
                }
            }
            add(HomeShelf(
                title = row.optString("title"),
                items = items,
                subtitle = row.optString("subtitle"),
                moreBrowseId = row.stringOrNull("moreBrowseId"),
                moreParams = row.stringOrNull("moreParams"),
            ))
        }
    }

    private fun historyJson(sections: List<InnertubeParser.HistorySection>) = JSONArray().apply {
        sections.forEach { section ->
            put(JSONObject().put("title", section.title).put("songs", songsJson(section.songs)))
        }
    }

    private fun readHistory(array: JSONArray?): List<InnertubeParser.HistorySection> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            add(InnertubeParser.HistorySection(row.optString("title"), readSongs(row.optJSONArray("songs"))))
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}
