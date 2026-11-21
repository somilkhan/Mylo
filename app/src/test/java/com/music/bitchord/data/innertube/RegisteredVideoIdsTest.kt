package com.music.bitchord.data.innertube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisteredVideoIdsTest {

    @Test
    fun unknownIdIsNotRegistered() {
        assertFalse(RegisteredVideoIds().contains("abcdefghijk"))
    }

    @Test
    fun addedIdIsRegistered() {
        val ids = RegisteredVideoIds()
        ids.add("abcdefghijk")
        assertTrue(ids.contains("abcdefghijk"))
    }

    @Test
    fun oldestIdsAreDroppedAtCapacity() {
        val ids = RegisteredVideoIds(capacity = 2)
        ids.add("first")
        ids.add("second")
        ids.add("third")
        assertFalse(ids.contains("first"))
        assertTrue(ids.contains("second"))
        assertTrue(ids.contains("third"))
    }

    @Test
    fun readdingMovesIdToNewest() {
        val ids = RegisteredVideoIds(capacity = 2)
        ids.add("first")
        ids.add("second")
        ids.add("first")
        ids.add("third")
        assertTrue(ids.contains("first"))
        assertFalse(ids.contains("second"))
    }
}
