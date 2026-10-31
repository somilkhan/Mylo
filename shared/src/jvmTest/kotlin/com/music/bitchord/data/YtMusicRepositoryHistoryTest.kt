package com.music.bitchord.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class YtMusicRepositoryHistoryTest {

    @Test
    fun signedOutRecentsDoNotRequestHistory() = runBlocking {
        var requested = false

        val result = fetchRecentHistory<String>(null) {
            requested = true
            listOf("track")
        }

        assertEquals(emptyList(), result)
        assertEquals(false, requested)
    }

    @Test
    fun authenticatedRecentsPreserveHistoryApiFailures() = runBlocking {
        val failure = IllegalStateException("UNAUTHENTICATED: LOGIN_REQUIRED")

        val thrown = assertFailsWith<IllegalStateException> {
            fetchRecentHistory<String>("session-cookie") { throw failure }
        }

        assertEquals(failure, thrown)
    }

    @Test
    fun authenticatedRecentsReturnAnActuallyEmptyHistory() = runBlocking {
        val result = fetchRecentHistory("session-cookie") { emptyList<String>() }

        assertEquals(emptyList(), result)
    }

    @Test
    fun cancellableFetchRethrowsCancellationInsteadOfReturningFallback() = runBlocking {
        val cancellation = CancellationException("caller navigated away")

        val thrown = assertFailsWith<CancellationException> {
            runCatchingCancellable<String> { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun cancellableFetchKeepsOrdinaryFailuresRecoverable() = runBlocking {
        val failure = IllegalStateException("temporary network failure")

        val result = runCatchingCancellable<String> { throw failure }

        assertEquals(failure, result.exceptionOrNull())
    }
}
