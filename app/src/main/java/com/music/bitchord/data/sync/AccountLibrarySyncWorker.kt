package com.music.bitchord.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException

/** Periodic entry point for the same account-sync transaction used by the foreground UI. */
class AccountLibrarySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!AppSettings.autoAccountSync.value) return Result.success()
        val session = AuthStore(applicationContext).activeSession ?: return Result.success()
        val profileId = session.activeProfileId
            ?: session.profiles.firstOrNull()?.profileId
            ?: return Result.success()
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return Result.retry()
        val network = connectivity.activeNetwork ?: return Result.retry()
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return Result.retry()
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val powerSave = applicationContext.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val metered = runCatching { connectivity.isActiveNetworkMetered }.getOrNull()

        return try {
            val outcome = AccountLibrarySyncRunner(applicationContext).run(
                session = session,
                profileId = profileId,
                force = false,
                powerSave = powerSave,
                metered = metered,
                hasValidatedInternet = hasInternet && validated,
            )
            when (outcome.result) {
                SyncResult.FAILED -> Result.retry()
                SyncResult.SYNCED, SyncResult.NOTHING, SyncResult.BUSY -> Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
