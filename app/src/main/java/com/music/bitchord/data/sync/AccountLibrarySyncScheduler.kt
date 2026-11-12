package com.music.bitchord.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.music.bitchord.data.settings.AppSettings
import java.util.concurrent.TimeUnit

/** One periodic work request; account/category cooldowns decide when it performs a fetch. */
object AccountLibrarySyncScheduler {
    private const val UNIQUE_WORK_NAME = "mylo-account-library-sync"

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        if (!AppSettings.autoAccountSync.value) {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val requiredNetwork = if (AppSettings.accountSyncBatteryMode.value == AutomaticSyncMode.SAVER) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .build()
        val request = PeriodicWorkRequestBuilder<AccountLibrarySyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
