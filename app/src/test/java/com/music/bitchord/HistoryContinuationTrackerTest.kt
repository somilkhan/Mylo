package com.music.bitchord

import com.music.bitchord.data.sync.HistoryContinuationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryContinuationTrackerTest {
    @Test
    fun resetRegistersFirstTokenAndAllowsFreshTraversal() {
        val tracker = HistoryContinuationTracker()
        assertEquals("A", tracker.reset("A"))
        assertEquals("B", tracker.advance("A", "B"))
        assertEquals("A", tracker.reset("A"))
        assertEquals("B", tracker.advance("A", "B"))
    }

    @Test
    fun rejectsImmediateRepeatedToken() {
        val tracker = HistoryContinuationTracker()
        tracker.reset("A")
        assertNull(tracker.advance("A", "A"))
    }

    @Test
    fun rejectsEarlierTokenInMultiPageCycle() {
        val tracker = HistoryContinuationTracker()
        tracker.reset("A")
        assertEquals("B", tracker.advance("A", "B"))
        assertNull(tracker.advance("B", "A"))
    }

    @Test
    fun rejectsBlankTokensAndNullMeansExhausted() {
        val tracker = HistoryContinuationTracker()
        assertNull(tracker.reset("  "))
        assertNull(tracker.advance("A", null))
        assertNull(tracker.advance("A", " "))
    }
}
