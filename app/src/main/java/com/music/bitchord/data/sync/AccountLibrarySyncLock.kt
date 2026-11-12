package com.music.bitchord.data.sync

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide coordination for account snapshot refreshes.
 *
 * The lock is keyed by the same account/profile identity used by the snapshot store.
 * It must cover the complete read → fetch → merge → write transaction, not just write(),
 * otherwise two independent sync entry points can both read an old snapshot and the
 * slower one can overwrite the newer result.
 *
 * Keep this shared by foreground and background sync runners; WorkManager executes in
 * this app process and therefore participates in the same coordination.
 */
object AccountLibrarySyncLock {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun lockFor(accountKey: String): Mutex =
        locks.computeIfAbsent(accountKey) { Mutex() }
}
