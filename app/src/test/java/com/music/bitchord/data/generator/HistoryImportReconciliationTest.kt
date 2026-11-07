package com.music.bitchord.data.generator

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryImportReconciliationTest {
    @Test
    fun preservesRepeatedListensAndOnlyRemovesRepresentedOccurrences() {
        assertEquals(
            listOf("b", "a", "c"),
            unmatchedHistoryVideoIds(
                datedVideoIds = listOf("a", "a", "b"),
                rawVideoIds = listOf("a", "b", "a", "b", "c"),
            ),
        )
    }

    @Test
    fun blankVideoIdsAreIgnored() {
        assertEquals(
            listOf("valid"),
            unmatchedHistoryVideoIds(listOf("valid"), listOf("", "valid", "  ")),
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
