package com.music.bitchord

import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTogetherIdleHealthTest {
    @Test
    fun `health probe fallback preserves cancellation`() = runBlocking {
        val cancellation = CancellationException("health probe superseded")
        val thrown = try {
            runCatchingCancellable { throw cancellation }
            null
        } catch (failure: CancellationException) {
            failure
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun `health probe converts ordinary failures into recoverable results`() = runBlocking {
        val result = runCatchingCancellable<Boolean> { error("server unavailable") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `screen closed never probes`() {
        assertTrue(ListenTogether.shouldSkipIdleProbe(false, false, false, null))
        assertTrue(ListenTogether.shouldSkipIdleProbe(false, false, true, false))
        assertTrue(ListenTogether.shouldSkipIdleProbe(false, true, true, false))
    }

    @Test
    fun `live socket never probes idle server`() {
        assertTrue(ListenTogether.shouldSkipIdleProbe(true, true, true, false))
        assertTrue(ListenTogether.shouldSkipIdleProbe(true, true, false, null))
    }

    @Test
    fun `metered with no party skips`() {
        assertTrue(ListenTogether.shouldSkipIdleProbe(true, false, false, true))
    }

    @Test
    fun `open screen probes on wifi or unknown network`() {
        assertFalse(ListenTogether.shouldSkipIdleProbe(true, false, false, false))
        assertFalse(ListenTogether.shouldSkipIdleProbe(true, false, false, null))
    }

    @Test
    fun `in party probes even on metered when socket down`() {
        assertFalse(ListenTogether.shouldSkipIdleProbe(true, false, true, true))
    }
}
