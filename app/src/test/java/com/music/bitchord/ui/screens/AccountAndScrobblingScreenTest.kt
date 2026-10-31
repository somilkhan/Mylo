package com.music.bitchord.ui.screens

import com.music.bitchord.data.generator.ScrobbleHistorySync
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAndScrobblingScreenTest {
    @Test
    fun reconnectActionIsOnlyOfferedForAnEnabledSignedInSessionNeedingAuth() {
        assertTrue(
            shouldOfferYouTubeReconnect(
                signedIn = true,
                syncEnabled = true,
                status = ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN,
            ),
        )
        assertFalse(
            shouldOfferYouTubeReconnect(
                signedIn = false,
                syncEnabled = true,
                status = ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN,
            ),
        )
        assertFalse(
            shouldOfferYouTubeReconnect(
                signedIn = true,
                syncEnabled = false,
                status = ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN,
            ),
        )
        assertFalse(
            shouldOfferYouTubeReconnect(
                signedIn = true,
                syncEnabled = true,
                status = ScrobbleHistorySync.STATUS_SUCCESS,
            ),
        )
    }
}
