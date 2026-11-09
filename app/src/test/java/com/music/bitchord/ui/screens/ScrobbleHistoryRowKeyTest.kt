package com.music.bitchord.ui.screens

import com.music.bitchord.data.generator.Play
import com.music.bitchord.data.generator.PlaySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScrobbleHistoryRowKeyTest {
    @Test
    fun repeatedImportedEventsOnSameDayHaveDistinctLazyListKeys() {
        val first = imported("account:video@2026-11-09:1")
        val second = imported("account:video@2026-11-09:0")

        assertNotEquals(scrobbleHistoryRowKey(first), scrobbleHistoryRowKey(second))
    }

    @Test
    fun sameImportedEventKeepsItsKeyAcrossRecomposition() {
        val event = imported("account:video@2026-11-09:1")

        assertEquals(scrobbleHistoryRowKey(event), scrobbleHistoryRowKey(event.copy(title = "Updated metadata")))
    }

    private fun imported(eventKey: String) = Play(
        videoId = "video",
        title = "Track",
        artist = "Artist",
        source = PlaySource.YOUTUBE_MUSIC,
        atMillis = 1_794_211_200_000L,
        exact = false,
        sourceEventKey = eventKey,
    )
}
