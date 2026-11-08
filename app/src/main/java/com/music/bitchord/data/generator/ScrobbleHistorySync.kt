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
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.ZoneId

internal fun shouldResumeScrobbleHistorySync(hasExplicitConsent: Boolean, enabled: Boolean): Boolean =
    hasExplicitConsent && enabled

/** Only a dated, non-empty import may move the incremental history cursor. */
internal fun shouldAdvanceScrobbleHistoryCursor(status: String): Boolean =
    status == ScrobbleHistorySync.STATUS_SUCCESS

/** Explicitly opt-in sync of YouTube listening history on any connected network. */
object ScrobbleHistorySync {
    const val PREFS_NAME = "scrobble_history_sync"
    const val KEY_ENABLED = "youtube_auto_sync_enabled"
    private const val KEY_CONSENT_RECORDED = "youtube_history_sync_consent_recorded"
    const val KEY_LAST_SYNC_AT = "youtube_last_sync_at"
    const val KEY_LAST_ATTEMPT_AT = "youtube_last_sync_attempt_at"
    const val KEY_LAST_ADDED = "youtube_last_sync_added"
    const val KEY_LAST_STATUS = "youtube_last_sync_status"
    private const val KEY_SESSION_GENERATION = "youtube_sync_session_generation"
    internal const val KEY_SESSION_SUSPENDED = "youtube_sync_session_suspended"
    internal const val KEY_SIGNED_OUT = "youtube_sync_signed_out"
    internal const val KEY_AUTH_REQUIRED = "youtube_sync_auth_required"

    private val sessionLock = Any()

    /**
     * Older builds could set KEY_ENABLED during sign-in without a user action.
     * Such a value is not consent: migrate it to paused once, then wait for the
     * listener to enable the Settings switch explicitly.
     */
    private fun ensureConsentState(prefs: android.content.SharedPreferences) {
        if (!prefs.contains(KEY_CONSENT_RECORDED)) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_CONSENT_RECORDED, true)
                .apply()
        }
    }

    private fun hasExplicitlyEnabledSync(prefs: android.content.SharedPreferences): Boolean =
        shouldResumeScrobbleHistorySync(
            hasExplicitConsent = prefs.getBoolean(KEY_CONSENT_RECORDED, false),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
        )

    internal fun isSyncEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureConsentState(prefs)
        return hasExplicitlyEnabledSync(prefs)
    }

    const val STATUS_SUCCESS = "success"
    const val STATUS_SYNCING = "syncing"
    const val STATUS_NO_DATED_ROWS = "no_dated_rows"
    const val STATUS_UNDATED_ROWS = "undated_rows"
    const val STATUS_WAITING_FOR_SIGN_IN = "waiting_for_sign_in"
    const val STATUS_WAITING_FOR_WIFI = "waiting_for_wifi"
    const val STATUS_RETRYING = "retrying"

    private const val UNIQUE_WORK_NAME = "mylo-youtube-history-sync"
    private const val IMMEDIATE_WORK_NAME = "$UNIQUE_WORK_NAME-immediate"

    /**
     * Resume background import only after the listener has explicitly enabled it.
     * A first sign-in is not consent to retain and periodically import account history.
     */
    fun onLogin(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureConsentState(prefs)
        if (hasExplicitlyEnabledSync(prefs)) scheduleWork(appContext)
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
        ensureConsentState(prefs)
        val enabled = synchronized(sessionLock) {
            prefs.edit()
                .putBoolean(KEY_SESSION_SUSPENDED, false)
                .putBoolean(KEY_SIGNED_OUT, false)
                .putBoolean(KEY_AUTH_REQUIRED, false)
                .apply()
            hasExplicitlyEnabledSync(prefs)
        }
        val identity = currentSessionIdentity(appContext)
        // The single UI summary follows the active account/profile even while
        // syncing is paused. The import cursor remains identity-scoped below.
        if (identity != null) {
            val identityCursor = prefs.getLong(identity.historySyncCursorPreferenceKey(), 0L)
            val displayedSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
            // Process/app recreation must not erase the last successful result.
            // Reset the summary only when the active account/profile has a
            // different cursor (or has never completed a dated import).
            if (
                (identityCursor != displayedSyncAt || identityCursor == 0L) &&
                prefs.getString(KEY_LAST_STATUS, null) != STATUS_SYNCING
            ) {
                prefs.edit()
                    .putLong(KEY_LAST_SYNC_AT, identityCursor)
                    .putInt(KEY_LAST_ADDED, 0)
                    .remove(KEY_LAST_STATUS)
                    .apply()
            }
        }
        if (!enabled) return
        if (identity == null) {
            prefs.edit()
                .putString(KEY_LAST_STATUS, STATUS_WAITING_FOR_SIGN_IN)
                .apply()
            return
        }
        enqueuePeriodicWork(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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
        synchronized(sessionLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_SESSION_GENERATION, prefs.getLong(KEY_SESSION_GENERATION, 0L) + 1L)
                .putBoolean(KEY_SESSION_SUSPENDED, true)
                .putBoolean(KEY_SIGNED_OUT, true)
                .putString(KEY_LAST_STATUS, STATUS_WAITING_FOR_SIGN_IN)
                .apply()
        }
        WorkManager.getInstance(appContext).apply {
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    /** Pause periodic retries after a confirmed auth rejection until Mylo's existing login succeeds. */
    internal fun pauseForAuthentication(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
        attemptedAtMillis: Long,
    ) {
        val appContext = context.applicationContext
        val paused = synchronized(sessionLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (
                !hasExplicitlyEnabledSync(prefs) ||
                prefs.getLong(KEY_SESSION_GENERATION, 0L) != expectedGeneration ||
                currentSessionIdentity(appContext) != expectedIdentity
            ) {
                false
            } else {
                prefs.edit()
                    .putBoolean(KEY_AUTH_REQUIRED, true)
                    .putLong(KEY_LAST_ATTEMPT_AT, attemptedAtMillis)
                    .putString(KEY_LAST_STATUS, STATUS_WAITING_FOR_SIGN_IN)
                    .apply()
                true
            }
        }
        if (paused) {
            WorkManager.getInstance(appContext).apply {
                cancelUniqueWork(IMMEDIATE_WORK_NAME)
                cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }
    }

    /** Return only the identity of the currently restored, signing-capable account. */
    internal fun currentSessionIdentity(context: Context): ScrobbleSessionIdentity? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (
            prefs.getBoolean(KEY_SESSION_SUSPENDED, false) ||
            prefs.getBoolean(KEY_SIGNED_OUT, false) ||
            prefs.getBoolean(KEY_AUTH_REQUIRED, false)
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

    /**
     * Revalidate the active identity, append the history and publish its cursor
     * and UI summary under one lock. Stale responses are discarded, and a later
     * profile switch can safely publish the newly selected profile's status.
     */
    internal fun appendIfCurrentSession(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
        incoming: List<Play>,
        syncedAtMillis: Long,
        status: String,
    ): Int? = synchronized(sessionLock) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (
            !hasExplicitlyEnabledSync(prefs) ||
            prefs.getLong(KEY_SESSION_GENERATION, 0L) != expectedGeneration ||
            currentSessionIdentity(appContext) != expectedIdentity
        ) return@synchronized null

        val added = ScrobbleHistory.append(incoming)
        prefs.edit().apply {
            // Empty/unparseable dated responses are not completed imports.
            // Advancing here can permanently skip history if date headings recover.
            if (shouldAdvanceScrobbleHistoryCursor(status)) {
                putLong(expectedIdentity.historySyncCursorPreferenceKey(), syncedAtMillis)
                putLong(KEY_LAST_SYNC_AT, syncedAtMillis)
            }
            putLong(KEY_LAST_ATTEMPT_AT, syncedAtMillis)
            putInt(KEY_LAST_ADDED, added)
            putString(KEY_LAST_STATUS, status)
        }.apply()
        added
    }

    /** Update retry state only while the worker still owns the active identity. */
    internal fun updateStatusIfCurrentSession(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
        attemptedAtMillis: Long,
        status: String,
    ): Boolean = synchronized(sessionLock) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (
            !hasExplicitlyEnabledSync(prefs) ||
            prefs.getLong(KEY_SESSION_GENERATION, 0L) != expectedGeneration ||
            currentSessionIdentity(appContext) != expectedIdentity
        ) return@synchronized false
        prefs.edit()
            .putLong(KEY_LAST_ATTEMPT_AT, attemptedAtMillis)
            .putString(KEY_LAST_STATUS, status)
            .apply()
        true
    }

    internal fun isCurrentSession(
        context: Context,
        expectedGeneration: Long,
        expectedIdentity: ScrobbleSessionIdentity,
    ): Boolean = synchronized(sessionLock) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        hasExplicitlyEnabledSync(prefs) &&
            prefs.getLong(KEY_SESSION_GENERATION, 0L) == expectedGeneration &&
            currentSessionIdentity(appContext) == expectedIdentity
    }

    /** Called by the Settings switch; this is the only path that records consent. */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureConsentState(prefs)
        prefs.edit()
            .putBoolean(KEY_CONSENT_RECORDED, true)
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_STATUS, STATUS_SYNCING)
            .apply()
        onSessionChanged(appContext)
    }

    /** Resume already-consented work without changing the user's choice. */
    private fun scheduleWork(appContext: Context) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!hasExplicitlyEnabledSync(prefs) || prefs.getBoolean(KEY_SIGNED_OUT, false) ||
            currentSessionIdentity(appContext) == null
        ) return
        enqueuePeriodicWork(appContext)
        val immediateConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val immediate = OneTimeWorkRequestBuilder<ScrobbleHistorySyncWorker>()
            .setConstraints(immediateConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, immediate)
    }

    private fun enqueuePeriodicWork(appContext: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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

    /** Re-register the persistent periodic work after process/app recreation, without an extra immediate fetch. */
    fun ensurePeriodic(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureConsentState(prefs)
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
        if (!hasRestoredSession || prefs.getBoolean(KEY_SIGNED_OUT, false)) return
        if (prefs.getBoolean(KEY_AUTH_REQUIRED, false)) return
        if (!hasExplicitlyEnabledSync(prefs)) return
        enqueuePeriodicWork(appContext)
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        synchronized(sessionLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_SESSION_GENERATION, prefs.getLong(KEY_SESSION_GENERATION, 0L) + 1L)
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_CONSENT_RECORDED, true)
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
        if (!ScrobbleHistorySync.isSyncEnabled(applicationContext)) {
            DebugLog.i(TAG, "History worker skipped: consent disabled")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val generation = ScrobbleHistorySync.currentSessionGeneration(applicationContext)
        val identity = ScrobbleHistorySync.currentSessionIdentity(applicationContext)
        if (identity == null) {
            val session = AuthStore(applicationContext).activeSession
            val blockReason = when {
                prefs.getBoolean(ScrobbleHistorySync.KEY_SESSION_SUSPENDED, false) -> "session transition suspended"
                prefs.getBoolean(ScrobbleHistorySync.KEY_SIGNED_OUT, false) -> "explicit sign-out"
                prefs.getBoolean(ScrobbleHistorySync.KEY_AUTH_REQUIRED, false) -> "account reconnect required"
                session == null -> "AuthStore has no active session"
                !AuthStore.hasApiSid(session.cookie) -> "active session cookie has no API SID"
                Innertube.cookie != session.cookie -> "Innertube cookie differs from AuthStore active session"
                else -> "unknown identity validation failure"
            }
            DebugLog.w(TAG, "History worker blocked: $blockReason")
            if (
                !prefs.getBoolean(ScrobbleHistorySync.KEY_SESSION_SUSPENDED, false) &&
                !prefs.getBoolean(ScrobbleHistorySync.KEY_SIGNED_OUT, false)
            ) {
                prefs.edit()
                    .putLong(ScrobbleHistorySync.KEY_LAST_ATTEMPT_AT, now)
                    .putString(ScrobbleHistorySync.KEY_LAST_STATUS, ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN)
                    .apply()
            }
            return Result.success()
        }
        return try {
            // Import the feed's dated listen entries (not a track-only list).
            // Each row keeps its Today/Yesterday day anchor and repeated plays;
            // a one-day overlap catches late-arriving history without re-fetching
            // the complete account feed every six hours.
            val cursor = prefs.getLong(identity.historySyncCursorPreferenceKey(), 0L)
            DebugLog.i(TAG, "InterTune-style dated history import started: cursor=$cursor")
            val response = YtMusicRepository.historyWithDates(
                nowMillis = now,
                notBeforeMillis = cursor.takeIf { it > 0L },
            )
            if (response.isFailure) {
                val error = response.exceptionOrNull()
                    ?: IllegalStateException("YouTube Music history import failed without an exception")
                if (error is CancellationException) throw error
                if (!ScrobbleHistorySync.isCurrentSession(applicationContext, generation, identity)) {
                    return Result.success()
                }
                if (error.isYouTubeAuthenticationFailure()) {
                    ScrobbleHistorySync.pauseForAuthentication(
                        applicationContext, generation, identity, attemptedAtMillis = now,
                    )
                    DebugLog.w(TAG, "YouTube Music history import needs account reconnect", error)
                    return Result.success()
                }
                if (!ScrobbleHistorySync.updateStatusIfCurrentSession(
                    applicationContext, generation, identity,
                    attemptedAtMillis = now, status = ScrobbleHistorySync.STATUS_RETRYING,
                )) return Result.success()
                DebugLog.w(TAG, "YouTube Music history import failed; WorkManager will retry", error)
                return Result.retry()
            }

            val entries = response.getOrThrow()
            if (!ScrobbleHistorySync.isCurrentSession(applicationContext, generation, identity)) {
                DebugLog.i(TAG, "History import discarded: account changed during fetch")
                return Result.success()
            }
            val zone = ZoneId.systemDefault()
            // Feed order is newest-first. Numbering occurrences from the oldest
            // end keeps existing keys stable when a new listen is prepended.
            fun occurrenceGroup(entry: com.music.bitchord.data.model.YtMusicHistoryEntry): String =
                "${entry.song.videoId}@${Instant.ofEpochMilli(entry.playedAtMillis).atZone(zone).toLocalDate()}"
            val occurrenceCounts = entries.groupingBy(::occurrenceGroup).eachCount()
            val occurrenceSeen = HashMap<String, Int>()
            val importedIdentity = identity.historySyncCursorPreferenceKey()
            val incoming = entries.map { entry ->
                val song = entry.song
                val group = occurrenceGroup(entry)
                val seen = occurrenceSeen.getOrDefault(group, 0)
                occurrenceSeen[group] = seen + 1
                val ordinal = occurrenceCounts.getValue(group) - 1 - seen
                Play(
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    source = PlaySource.YOUTUBE_MUSIC,
                    atMillis = entry.playedAtMillis,
                    exact = entry.exact,
                    thumbnailUrl = song.thumbnailUrl,
                    durationText = song.durationText,
                    artistId = song.artistId,
                    albumId = song.albumId,
                    albumName = song.albumName,
                    isVideo = song.isVideo,
                    isVideoOrigin = song.isVideoOrigin,
                    sourceEventKey = "$importedIdentity:$group:$ordinal",
                )
            }
            val importStatus = if (incoming.isEmpty()) {
                ScrobbleHistorySync.STATUS_NO_DATED_ROWS
            } else {
                ScrobbleHistorySync.STATUS_SUCCESS
            }
            DebugLog.i(TAG, "InterTune-style dated history parsed: rows=${incoming.size}")
            val added = withContext(Dispatchers.IO) {
                ScrobbleHistorySync.appendIfCurrentSession(
                    applicationContext, generation, identity, incoming,
                    syncedAtMillis = now, status = importStatus,
                )
            } ?: return Result.success()
            DebugLog.i(TAG, "InterTune-style dated history import finished: rows=${incoming.size}, added=$added, status=$importStatus")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (
                !ScrobbleHistorySync.updateStatusIfCurrentSession(
                    applicationContext,
                    generation,
                    identity,
                    attemptedAtMillis = now,
                    status = ScrobbleHistorySync.STATUS_RETRYING,
                )
            ) return Result.success()
            DebugLog.w(TAG, "Background YouTube history sync failed; WorkManager will retry", error)
            Result.retry()
        }
    }


    private companion object {
        const val TAG = "ScrobbleHistorySync"
    }
}
