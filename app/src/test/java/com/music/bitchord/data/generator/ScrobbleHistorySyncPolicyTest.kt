package com.music.bitchord.data.generator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleHistorySyncPolicyTest {
    @Test
    fun firstSuccessfulLoginDoesNotEnableHistoryImportWithoutConsent() {
        assertFalse(shouldResumeScrobbleHistorySync(hasSavedChoice = false, enabled = false))
    }

    @Test
    fun explicitOptOutSurvivesLaterLogins() {
        assertFalse(shouldResumeScrobbleHistorySync(hasSavedChoice = true, enabled = false))
    }

    @Test
    fun previouslyEnabledSyncResumesAfterLogin() {
        assertTrue(shouldResumeScrobbleHistorySync(hasSavedChoice = true, enabled = true))
    }
}
