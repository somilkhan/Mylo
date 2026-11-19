package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFMScrobbleResponseTest {

    @Test
    fun acceptsSuccessfulScrobbleResponse() {
        validateLastFmScrobbleResponse(
            """{"scrobbles":{"@attr":{"accepted":"1","ignored":"0"},"scrobble":{"ignoredMessage":{"code":"0","#text":""}}}}""",
        )
    }

    @Test
    fun rejectsTrackIgnoredByLastFm() {
        val error = try {
            validateLastFmScrobbleResponse(
                """{"scrobbles":{"@attr":{"accepted":"0","ignored":"1"},"scrobble":{"ignoredMessage":{"code":"2","#text":"Track ignored"}}}}""",
            )
            null
        } catch (expected: LastFM.ScrobbleRejectedException) {
            expected
        }

        assertEquals(2, error?.ignoredCode)
        assertFalse(error?.retryable ?: true)
    }

    @Test
    fun treatsDailyScrobbleLimitAsRetryable() {
        val error = try {
            validateLastFmScrobbleResponse(
                """{"scrobbles":{"@attr":{"accepted":"0","ignored":"1"},"scrobble":{"ignoredMessage":{"code":"5","#text":"Daily limit"}}}}""",
            )
            null
        } catch (expected: LastFM.ScrobbleRejectedException) {
            expected
        }

        assertEquals(5, error?.ignoredCode)
        assertTrue(error?.retryable ?: false)
    }
}
