package com.music.bitchord.ui.scrobble

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs suspending candidate work, then performs its side effect only while the
 * caller is still active. The commit callback must remain synchronous.
 */
internal suspend fun <T, R> generateAndCommit(
    generate: suspend () -> T,
    commit: (T) -> R,
): R {
    val result = generate()
    currentCoroutineContext().ensureActive()
    return commit(result)
}
