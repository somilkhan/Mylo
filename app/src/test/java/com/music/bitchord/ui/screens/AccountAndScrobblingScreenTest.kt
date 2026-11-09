package com.music.bitchord.ui.screens

import com.music.bitchord.data.generator.ScrobbleHistorySync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAndScrobblingScreenTest {
    @Test
    fun syncingAndEmptyDatedHistoryStatusesAreExplicit() {
        assertEquals(
            "Syncing YouTube Music history…",
            historySyncSubtitle(true, ScrobbleHistorySync.STATUS_SYNCING, 0L, 0),
        )
        assertEquals(
            "No date-labelled rows found on last sync",
            historySyncSubtitle(true, ScrobbleHistorySync.STATUS_NO_DATED_ROWS, 0L, 0),
        )
        assertEquals("Paused", historySyncSubtitle(false, null, 0L, 0))
    }

    @Test
    fun successfulSyncDistinguishesFetchedRowsFromNewlyAddedRows() {
        val subtitle = historySyncSubtitle(
            enabled = true,
            status = ScrobbleHistorySync.STATUS_SUCCESS,
            lastSyncAt = 1_762_000_000_000L,
            lastAdded = 0,
            fetchedRows = 7,
        )

        assertTrue(subtitle.contains("fetched 7, added 0"))
    }

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
