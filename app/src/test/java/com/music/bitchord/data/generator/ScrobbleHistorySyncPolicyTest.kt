package com.music.bitchord.data.generator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleHistorySyncPolicyTest {
    @Test
    fun firstSuccessfulLoginEnablesTheDefaultImport() {
        assertTrue(shouldScheduleScrobbleSyncOnLogin(hasSavedChoice = false, enabled = false))
    }

    @Test
    fun explicitOptOutSurvivesLaterLogins() {
        assertFalse(shouldScheduleScrobbleSyncOnLogin(hasSavedChoice = true, enabled = false))
    }

    @Test
    fun previouslyEnabledSyncResumesAfterLogin() {
        assertTrue(shouldScheduleScrobbleSyncOnLogin(hasSavedChoice = true, enabled = true))
    }
}
