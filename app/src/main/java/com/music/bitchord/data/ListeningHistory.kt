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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local listening log, separate from YouTube's account history. */
object ListeningHistory {
    data class Entry(val song: Song, val startedAt: Long)

    private const val PREFS = "listening_history"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_RECORDING_ENABLED = "recording_enabled"
    private const val FILE_NAME = "listening-history.jsonl"
    const val MIN_HEARD_MS = 2_000L
    private val _recordingEnabled = MutableStateFlow(true)
    val recordingEnabled: StateFlow<Boolean> = _recordingEnabled.asStateFlow()

    /** History includes a short skip after two seconds, independently of play-count thresholds. */
    fun shouldRecord(playedMs: Long, counted: Boolean): Boolean =
        counted || playedMs >= MIN_HEARD_MS

    private lateinit var appContext: Context
    private val historyFile: File get() = File(appContext.filesDir, FILE_NAME)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serialize writes at enqueue time, not only when a coroutine eventually
    // reaches the file lock. This makes clear() a true barrier: every record
    // queued before it finishes first, and later records run after the delete.
    private val enqueueLock = Any()
    private var writeTail: Job? = null

    private fun enqueueWrite(action: suspend () -> Unit): Job = synchronized(enqueueLock) {
        val previous = writeTail
        writer.launch {
            previous?.join()
            action()
        }.also { writeTail = it }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        _recordingEnabled.value = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RECORDING_ENABLED, true)
        migrateLegacyPreferences()
    }

    /**
     * Move the old capped SharedPreferences array into an append-only private file.
     * JSON-lines avoids the SharedPreferences XML size limit and avoids rewriting the
     * complete listening log every time a track is played.
     */
    private fun migrateLegacyPreferences() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_ENTRIES, null) ?: return
        if (historyFile.exists()) {
            prefs.edit().remove(KEY_ENTRIES).apply()
            return
        }
        val temporary = File(historyFile.parentFile, FILE_NAME + ".migration")
        runCatching {
            val rows = JSONArray(legacy)
            historyFile.parentFile?.mkdirs()
            temporary.bufferedWriter().use { writer ->
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    writer.append(row.toString())
                    writer.newLine()
                }
            }
            if (!temporary.renameTo(historyFile)) {
                temporary.copyTo(historyFile, overwrite = true)
                temporary.delete()
            }
            // Remove the legacy value only after the complete file is in place.
            prefs.edit().remove(KEY_ENTRIES).apply()
        }.onFailure {
            temporary.delete()
            android.util.Log.e("ListeningHistory", "Legacy history migration failed", it)
        }
    }

    /** Pause/resume device-local logging without affecting YouTube Music account history. */
    fun setRecordingEnabled(enabled: Boolean) {
        if (!this::appContext.isInitialized) return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
        _recordingEnabled.value = enabled
    }

    fun record(song: Song, startedAt: Long) {
        if (!this::appContext.isInitialized || !_recordingEnabled.value || song.videoId.isBlank()) return
        enqueueWrite {
            historyFile.parentFile?.mkdirs()
            val row = JSONObject()
                .put("id", song.videoId)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("art", song.thumbnailUrl)
                .put("duration", song.durationText)
                .put("at", startedAt)
            historyFile.appendText(row.toString() + "\n")
        }
    }

    /** Clears only this device's listening log; YouTube Music history is untouched. */
    suspend fun clear() {
        if (!this::appContext.isInitialized) return
        // Enqueue the delete behind all writes already accepted. New playback
        // records are queued behind this job and therefore cannot resurrect a
        // row that the user just cleared.
        enqueueWrite {
            historyFile.delete()
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ENTRIES).apply()
        }.join()
    }

    fun read(context: Context): List<Entry> {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.isFile) {
            // Before init/migration has run, still expose any legacy entries.
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rows = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
            return parseRows((0 until rows.length()).mapNotNull { rows.optJSONObject(it) })
        }
        return runCatching {
            file.useLines { lines ->
                parseRows(lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }.toList())
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRows(rows: List<JSONObject>): List<Entry> = buildList(rows.size) {
        for (row in rows.asReversed()) {
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
