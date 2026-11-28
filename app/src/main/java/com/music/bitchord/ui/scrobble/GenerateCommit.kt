package com.music.bitchord.ui.scrobble

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs suspending candidate work, then performs its side effect only while the
 * caller is still active. The commit callback may suspend for durable storage.
 */
internal suspend fun <T, R> generateAndCommit(
    generate: suspend () -> T,
    commit: suspend (T) -> R,
): R {
    val result = generate()
    currentCoroutineContext().ensureActive()
    val committed = commit(result)
    // Durable storage can finish non-cancellably; cancellation still must not start playback/navigation.
    currentCoroutineContext().ensureActive()
    return committed
}
