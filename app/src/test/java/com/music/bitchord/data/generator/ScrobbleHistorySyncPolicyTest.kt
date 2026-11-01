package com.music.bitchord.data.generator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleHistorySyncPolicyTest {
    @Test
    fun firstSuccessfulLoginDoesNotEnableHistoryImportWithoutConsent() {
        assertFalse(shouldResumeScrobbleHistorySync(hasExplicitConsent = false, enabled = false))
    }

    @Test
    fun legacyEnabledValueWithoutExplicitConsentDoesNotResumeSync() {
        assertFalse(shouldResumeScrobbleHistorySync(hasExplicitConsent = false, enabled = true))
    }

    @Test
    fun explicitOptOutSurvivesLaterLogins() {
        assertFalse(shouldResumeScrobbleHistorySync(hasExplicitConsent = true, enabled = false))
    }

    @Test
    fun previouslyEnabledSyncResumesAfterLogin() {
        assertTrue(shouldResumeScrobbleHistorySync(hasExplicitConsent = true, enabled = true))
    }
}
