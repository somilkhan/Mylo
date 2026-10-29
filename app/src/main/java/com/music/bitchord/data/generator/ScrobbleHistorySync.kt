package com.music.bitchord.data.generator

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Explicitly opt-in, network-constrained sync of date-labelled YouTube history. */
object ScrobbleHistorySync {
    const val PREFS_NAME = "scrobble_history_sync"
    const val KEY_ENABLED = "youtube_auto_sync_enabled"
    const val KEY_LAST_SYNC_AT = "youtube_last_sync_at"
    const val KEY_LAST_ATTEMPT_AT = "youtube_last_sync_attempt_at"
    const val KEY_LAST_ADDED = "youtube_last_sync_added"
    const val KEY_LAST_STATUS = "youtube_last_sync_status"

    const val STATUS_SUCCESS = "success"
    const val STATUS_NO_DATED_ROWS = "no_dated_rows"
    const val STATUS_WAITING_FOR_SIGN_IN = "waiting_for_sign_in"
    const val STATUS_RETRYING = "retrying"

    private const val UNIQUE_WORK_NAME = "mylo-youtube-history-sync"
    private const val IMMEDIATE_WORK_NAME = "$UNIQUE_WORK_NAME-immediate"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workManager = WorkManager.getInstance(appContext)
        val periodic = PeriodicWorkRequestBuilder<ScrobbleHistorySyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )

        val immediate = OneTimeWorkRequestBuilder<ScrobbleHistorySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, immediate)
    }

    /** Re-register the persistent periodic work after process/app recreation, without an extra immediate fetch. */
    fun ensurePeriodic(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ScrobbleHistorySyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    fun cancel(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(UNIQUE_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
    }
}

/**
 * One worker implementation is shared by the immediate first sync and the
 * periodic refresh. All writes happen off the playback/UI threads.
 */
class ScrobbleHistorySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            ScrobbleHistorySync.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        if (!prefs.getBoolean(ScrobbleHistorySync.KEY_ENABLED, false)) return Result.success()

        val now = System.currentTimeMillis()
        if (Innertube.cookie.isNullOrBlank()) {
            prefs.edit()
                .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN)
                .apply()
            return Result.success()
        }

        return try {
            val response = YtMusicRepository.historyWithDates()
            if (response.isFailure) {
                val error = response.exceptionOrNull()
                    ?: IllegalStateException("YouTube history import failed without an exception")
                if (error is CancellationException) throw error
                prefs.edit()
                    .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                    .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_RETRYING)
                    .apply()
                DebugLog.w(TAG, "Background YouTube history sync failed; WorkManager will retry", error)
                return Result.retry()
            }

            val entries = response.getOrThrow()
            val incoming = entries.map { entry ->
                Play(
                    videoId = entry.song.videoId,
                    title = entry.song.title,
                    artist = entry.song.artist,
                    source = PlaySource.YOUTUBE_MUSIC,
                    atMillis = entry.playedAtMillis,
                    exact = entry.exact,
                    localUri = entry.song.localUri,
                )
            }
            val added = withContext(Dispatchers.IO) { ScrobbleHistory.append(incoming) }
            prefs.edit()
                .putLong(ScrobbleHistorySync.KEY_LAST_SYNC_AT, now)
                .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                .putInt(ScrobbleHistorySync.KEY_LAST_ADDED, added)
                .putString(
                    ScrobbleHistorySync.KEY_LAST_STATUS,
                    if (entries.isEmpty()) ScrobbleHistorySync.STATUS_NO_DATED_ROWS
                    else ScrobbleHistorySync.STATUS_SUCCESS,
                )
                .apply()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            prefs.edit()
                .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_RETRYING)
                .apply()
            DebugLog.w(TAG, "Background YouTube history sync failed; WorkManager will retry", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ScrobbleHistorySync"
    }
}
