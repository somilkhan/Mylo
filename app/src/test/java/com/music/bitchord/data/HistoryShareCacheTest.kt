package com.music.bitchord.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryShareCacheTest {

    private class FakeClock(var t: Long = 0L)

    @Test
    fun sameKeyWithinWindowReturnsValue() {
        val clock = FakeClock()
        val cache = HistoryShareCache<String>(windowMs = 5_000L, now = { clock.t })
        cache.put("acc-1", "rows")
        clock.t = 4_999L
        assertEquals("rows", cache.get("acc-1"))
    }

    @Test
    fun expiresAfterWindow() {
        val clock = FakeClock()
        val cache = HistoryShareCache<String>(windowMs = 5_000L, now = { clock.t })
        cache.put("acc-1", "rows")
        clock.t = 5_000L
        assertNull(cache.get("acc-1"))
    }

    @Test
    fun differentAccountNeverGetsOtherRows() {
        val clock = FakeClock()
        val cache = HistoryShareCache<String>(windowMs = 5_000L, now = { clock.t })
        cache.put("acc-1", "rows-of-1")
        assertNull(cache.get("acc-2"))
    }

    @Test
    fun clearDropsValue() {
        val clock = FakeClock()
        val cache = HistoryShareCache<String>(windowMs = 5_000L, now = { clock.t })
        cache.put("acc-1", "rows")
        cache.clear()
        assertNull(cache.get("acc-1"))
    }
}
