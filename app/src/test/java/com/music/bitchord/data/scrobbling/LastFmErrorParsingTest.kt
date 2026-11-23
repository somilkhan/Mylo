package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastFmErrorParsingTest {

    @Test
    fun trackNamedErrorIsNotAnError() {
        val raw = """{"track":{"name":"error","artist":{"name":"Artist"}}}"""
        assertNull(LastFM.lastFmErrorOrNull(raw))
    }

    @Test
    fun topLevelErrorObjectIsParsed() {
        val error = LastFM.lastFmErrorOrNull("""{"error":9,"message":"Invalid session key"}""")
        assertEquals(9, error?.error)
        assertEquals("Invalid session key", error?.message)
    }

    @Test
    fun undecodableTopLevelErrorIsTemporary() {
        val error = LastFM.lastFmErrorOrNull("""{"error":"weird","message":"x"}""")
        assertEquals(16, error?.error)
    }

    @Test
    fun nonObjectResponseIsNotAnError() {
        assertNull(LastFM.lastFmErrorOrNull("""["error"]"""))
        assertNull(LastFM.lastFmErrorOrNull("not json"))
    }
}
