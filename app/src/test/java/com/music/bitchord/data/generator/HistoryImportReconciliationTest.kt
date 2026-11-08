package com.music.bitchord.data.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryImportReconciliationTest {
    @Test
    fun rawFallbackRunsOnlyForFullInitialImport() {
        assertTrue(shouldFetchUndatedHistoryFallback(null))
        assertFalse(shouldFetchUndatedHistoryFallback(1L))
    }

    @Test
    fun preservesRepeatedListensAndOnlyRemovesRepresentedOccurrences() {
        assertEquals(
            listOf("b", "c"),
            unmatchedHistoryVideoIds(
                datedVideoIds = listOf("a", "a", "b"),
                rawVideoIds = listOf("a", "b", "a", "b", "c"),
            ),
        )
    }

    @Test
    fun blankVideoIdsAreIgnoredAndWhitespaceIsNormalized() {
        assertEquals(
            emptyList<String>(),
            unmatchedHistoryVideoIds(listOf(" valid "), listOf("", "valid", "  ")),
        )
    }

    @Test
    fun fullyDatedHistoryHasNoUndatedRows() {
        assertEquals(
            emptyList<String>(),
            unmatchedHistoryVideoIds(listOf("a", "b"), listOf("a", "b")),
        )
    }
}
