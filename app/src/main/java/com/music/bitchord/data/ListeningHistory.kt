package com.music.bitchord.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.music.bitchord.data.model.Song
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Device-local listening log, separate from YouTube's account history. */
object ListeningHistory {
    data class Entry(val song: Song, val startedAt: Long)

    private const val PREFS = "listening_history"
    private const val KEY_ENTRIES = "entries"
    const val MIN_HEARD_MS = 2_000L

    /** History includes a short skip after two seconds, independently of play-count thresholds. */
    fun shouldRecord(playedMs: Long, counted: Boolean): Boolean =
        counted || playedMs >= MIN_HEARD_MS

    private const val MAX_ENTRIES = 2_000
    private lateinit var appContext: Context
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun record(song: Song, startedAt: Long) {
        if (!this::appContext.isInitialized || song.videoId.isBlank()) return
        writer.launch {
            writeLock.withLock {
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val rows = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
                rows.put(
                    JSONObject()
                        .put("id", song.videoId)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("art", song.thumbnailUrl)
                        .put("duration", song.durationText)
                        .put("at", startedAt),
                )
                val trimmed = JSONArray()
                val start = (rows.length() - MAX_ENTRIES).coerceAtLeast(0)
                for (i in start until rows.length()) trimmed.put(rows.getJSONObject(i))
                prefs.edit().putString(KEY_ENTRIES, trimmed.toString()).apply()
            }
        }
    }

    /** Clears only this device's listening log; YouTube Music history is untouched. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        if (!this@ListeningHistory::appContext.isInitialized) return@withContext
        writeLock.withLock {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ENTRIES).apply()
        }
    }

    fun read(context: Context): List<Entry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rows = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
        return buildList(rows.length()) {
            for (i in rows.length() - 1 downTo 0) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id").takeIf(String::isNotBlank) ?: continue
                add(
                    Entry(
                        Song(
                            videoId = id,
                            title = row.optString("title", "Unknown title"),
                            artist = row.optString("artist", "Unknown artist"),
                            thumbnailUrl = row.optString("art").takeIf(String::isNotBlank),
                            durationText = row.optString("duration").takeIf(String::isNotBlank),
                        ),
                        row.optLong("at"),
                    ),
                )
            }
        }
    }

    fun sections(entries: List<Entry>, now: LocalDate = LocalDate.now()): List<com.music.bitchord.data.innertube.InnertubeParser.HistorySection> {
        val zone = ZoneId.systemDefault()
        return entries.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .map { (date, rows) ->
                val title = when (date) {
                    now -> "Today"
                    now.minusDays(1) -> "Yesterday"
                    else -> date.format(
                        DateTimeFormatter.ofPattern(
                            if (date.year == now.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy",
                            Locale.getDefault(),
                        ),
                    )
                }
                com.music.bitchord.data.innertube.InnertubeParser.HistorySection(title, rows.map { it.song })
            }
    }
}
