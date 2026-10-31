package com.music.bitchord.data.generator

import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleSessionIdentityTest {
    private fun account(
        id: String = "account-a",
        cookie: String = "SAPISID=session-a",
        profileId: String = "profile-a",
        dataSyncId: String = "datasync-a",
    ) = GoogleAccountSession(
        accountId = id,
        cookie = cookie,
        profiles = listOf(
            YouTubeProfile(
                profileId = profileId,
                name = "Music profile",
                dataSyncId = dataSyncId,
                authUser = "0",
            ),
        ),
        activeProfileId = profileId,
    )

    @Test
    fun unchangedAccountProfileAndCookieKeepTheSameIdentity() {
        val first = account().scrobbleIdentity("profile-a")
        val second = account().copy(name = "Updated display name").scrobbleIdentity("profile-a")
        assertEquals(first, second)
    }

    @Test
    fun accountSwitchInvalidatesAnInFlightImport() {
        assertNotEquals(
            account().scrobbleIdentity("profile-a"),
            account(id = "account-b", cookie = "SAPISID=session-b").scrobbleIdentity("profile-a"),
        )
    }

    @Test
    fun profileSwitchInvalidatesAnInFlightImportEvenWhenCookieIsShared() {
        val otherProfile = account().copy(
            profiles = listOf(YouTubeProfile("profile-b", "Brand", dataSyncId = "datasync-b")),
            activeProfileId = "profile-b",
        )
        assertNotEquals(
            account().scrobbleIdentity("profile-a"),
            otherProfile.scrobbleIdentity("profile-b"),
        )
    }

    @Test
    fun cookieRotationInvalidatesAnInFlightImport() {
        assertNotEquals(
            account().scrobbleIdentity("profile-a"),
            account(cookie = "SAPISID=rotated-session").scrobbleIdentity("profile-a"),
        )
    }

    @Test
    fun channelContextIsPartOfTheIdentity() {
        val first = account().scrobbleIdentity("profile-a")
        val changedChannel = account().copy(
            profiles = listOf(
                YouTubeProfile("profile-a", "Music profile", dataSyncId = "different-channel", authUser = "0"),
            ),
        ).scrobbleIdentity("profile-a")
        assertNotEquals(first, changedChannel)
    }

    @Test
    fun wrapped401And403ErrorsRequireReconnection() {
        assertTrue(IllegalStateException("request failed: 401 Unauthorized").isYouTubeAuthenticationFailure())
        assertTrue(IllegalStateException("outer", IllegalStateException("403 Forbidden")).isYouTubeAuthenticationFailure())
        assertTrue(IllegalStateException("LOGIN_REQUIRED").isYouTubeAuthenticationFailure())
    }

    @Test
    fun transientNetworkErrorsRemainRetryable() {
        assertFalse(IllegalStateException("socket timeout").isYouTubeAuthenticationFailure())
    }
}
