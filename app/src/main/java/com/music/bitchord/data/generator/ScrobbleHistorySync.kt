package com.music.bitchord.data.generator

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Explicitly opt-in, Wi-Fi-constrained sync of date-labelled YouTube history. */
object ScrobbleHistorySync {
    const val PREFS_NAME = "scrobble_history_sync"
    const val KEY_ENABLED = "youtube_auto_sync_enabled"
    const val KEY_LAST_SYNC_AT = "youtube_last_sync_at"
    const val KEY_LAST_ATTEMPT_AT = "youtube_last_sync_attempt_at"
    const val KEY_LAST_ADDED = "youtube_last_sync_added"
    const val KEY_LAST_STATUS = "youtube_last_sync_status"
    private const val KEY_SESSION_GENERATION = "youtube_sync_session_generation"
    private const val KEY_SESSION_SUSPENDED = "youtube_sync_session_suspended"
    private const val KEY_SIGNED_OUT = "youtube_sync_signed_out"

    private val sessionLock = Any()

    const val STATUS_SUCCESS = "success"
    const val STATUS_NO_DATED_ROWS = "no_dated_rows"
    const val STATUS_WAITING_FOR_SIGN_IN = "waiting_for_sign_in"
    const val STATUS_WAITING_FOR_WIFI = "waiting_for_wifi"
    const val STATUS_RETRYING = "retrying"

    private const val UNIQUE_WORK_NAME = "mylo-youtube-history-sync"
    private const val IMMEDIATE_WORK_NAME = "$UNIQUE_WORK_NAME-immediate"

    /**
     * Start a silent import on first successful sign-in. Once the listener has
     * explicitly disabled sync, subsequent sign-ins must respect that choice.
     */
    fun onLogin(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ENABLED) || prefs.getBoolean(KEY_ENABLED, false)) {
            schedule(context)
        }
    }

    /** Fence in-flight work before the authenticated account/profile changes. */
    fun onSessionChanging(context: Context) {
        val appContext = context.applicationContext
        synchronized(sessionLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_SESSION_GENERATION, prefs.getLong(KEY_SESSION_GENERATION, 0L) + 1L)
                .putBoolean(KEY_SESSION_SUSPENDED, true)
                .apply()
        }
        WorkManager.getInstance(appContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    /** Schedule a fresh silent import after the new account/profile is active. */
    fun onSessionChanged(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = synchronized(sessionLock) {
            prefs.edit()
                .putBoolean(KEY_SESSION_SUSPENDED, false)
                .putBoolean(KEY_SIGNED_OUT, false)
                .apply()
            prefs.getBoolean(KEY_ENABLED, false)
        }
        if (!enabled) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val immediate = OneTimeWorkRequestBuilder<ScrobbleHistorySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediate,
        )
    }

    /** Cancel on sign-out without clearing the listener's sync opt-in. */
    fun onSignedOut(context: Context) {
        val appContext = context.applicationContext
        onSessionChanging(appContext)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIGNED_OUT, true)
            .apply()
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Return only the identity of the currently restored, signing-capable account. */
    internal fun currentSessionIdentity(context: Context): ScrobbleSessionIdentity? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (
            prefs.getBoolean(KEY_SESSION_SUSPENDED, false) ||
            prefs.getBoolean(KEY_SIGNED_OUT, false)
        ) return null
        val authStore = AuthStore(appContext)
        val session = authStore.activeSession ?: return null
        if (!AuthStore.hasApiSid(session.cookie) || Innertube.cookie != session.cookie) return null
        return session.scrobbleIdentity(authStore.activeProfileId)
    }

    internal fun currentSessionGeneration(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SESSION_GENERATION, 0L)

    /** Revalidate and append atomically against session invalidation and sync opt-out. */
    internal fun appendIfCurrentSession(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
        incoming: List<Play>,
    ): Int? = synchronized(sessionLock) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (
            !prefs.getBoolean(KEY_ENABLED, false) ||
            prefs.getLong(KEY_SESSION_GENERATION, 0L) != expectedGeneration ||
            currentSessionIdentity(appContext) != expectedIdentity
        ) return@synchronized null
        ScrobbleHistory.append(incoming)
    }

    internal fun isCurrentSession(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
    ): Boolean = synchronized(sessionLock) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getBoolean(KEY_ENABLED, false) &&
            prefs.getLong(KEY_SESSION_GENERATION, 0L) == expectedGeneration &&
            currentSessionIdentity(appContext) == expectedIdentity
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val workManager = WorkManager.getInstance(appContext)
        val periodic = PeriodicWorkRequestBuilder<ScrobbleHistorySyncWorker>(6, TimeUnit.HOURS)
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
        val activeCookie = AuthStore(appContext).activeSession?.cookie
        val hasRestoredSession = activeCookie != null &&
            AuthStore.hasApiSid(activeCookie) && Innertube.cookie == activeCookie
        if (
            hasRestoredSession &&
            prefs.getBoolean(KEY_SESSION_SUSPENDED, false) &&
            !prefs.getBoolean(KEY_SIGNED_OUT, false)
        ) {
            // Recover a process death during an account/profile switch. A real
            // sign-out remains paused until the next successful login.
            synchronized(sessionLock) {
                prefs.edit().putBoolean(KEY_SESSION_SUSPENDED, false).apply()
            }
        }
        if (!prefs.contains(KEY_ENABLED) && hasRestoredSession && !prefs.getBoolean(KEY_SIGNED_OUT, false)) {
            // Existing signed-in installs get the same silent first import as a
            // fresh login after this feature is added.
            schedule(appContext)
            return
        }
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ScrobbleHistorySyncWorker>(6, TimeUnit.HOURS)
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
        val appContext = context.applicationContext
        synchronized(sessionLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_SESSION_GENERATION, prefs.getLong(KEY_SESSION_GENERATION, 0L) + 1L)
                .putBoolean(KEY_ENABLED, false)
                .apply()
        }
        WorkManager.getInstance(appContext).apply {
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
        if (!isOnWifi()) {
            prefs.edit()
                .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_WAITING_FOR_WIFI)
                .apply()
            return Result.success()
        }
        val generation = ScrobbleHistorySync.currentSessionGeneration(applicationContext)
        val identity = ScrobbleHistorySync.currentSessionIdentity(applicationContext)
        if (identity == null) {
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
                if (!ScrobbleHistorySync.isCurrentSession(applicationContext, generation, identity)) {
                    return Result.success()
                }
                if (error.isYouTubeAuthenticationFailure()) {
                    prefs.edit()
                        .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                        .putString(
                            ScrobbleHistorySync.KEY_LAST_STATUS,
                            ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN,
                        )
                        .apply()
                    DebugLog.w(TAG, "YouTube history sync needs the account to reconnect", error)
                    return Result.success()
                }
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
            val added = withContext(Dispatchers.IO) {
                ScrobbleHistorySync.appendIfCurrentSession(
                    applicationContext,
                    generation,
                    identity,
                    incoming,
                )
            } ?: return Result.success()
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
            if (!ScrobbleHistorySync.isCurrentSession(applicationContext, generation, identity)) {
                return Result.success()
            }
            prefs.edit()
                .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_RETRYING)
                .apply()
            DebugLog.w(TAG, "Background YouTube history sync failed; WorkManager will retry", error)
            Result.retry()
        }
    }

    private fun isOnWifi(): Boolean {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private companion object {
        const val TAG = "ScrobbleHistorySync"
    }
}
