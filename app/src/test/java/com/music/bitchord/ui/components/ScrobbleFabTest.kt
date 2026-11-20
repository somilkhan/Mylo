package com.music.bitchord.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleFabTest {
    @Test
    fun fabIsVisibleOnlyOnHomeWhenNoCompetingSurfaceIsActive() {
        assertTrue(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = false))
        assertFalse(scrobbleFabVisible(selectedTab = 1, homeTab = 0, overlayOpen = false))
        assertFalse(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = true))
        assertFalse(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = false, playerOpen = true))
        assertFalse(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = false, keyboardVisible = true))
    }

    @Test
    fun openHubKeepsCloseAffordanceVisibleIfUnderlyingTabChanges() {
        assertTrue(
            scrobbleFabVisible(
                selectedTab = 2,
                homeTab = 0,
                overlayOpen = false,
                hubOpen = true,
            ),
        )
    }

    @Test
    fun blockingSurfaceStillHidesFabWhileHubIsOpen() {
        assertFalse(
            scrobbleFabVisible(
                selectedTab = 0,
                homeTab = 0,
                overlayOpen = true,
                hubOpen = true,
            ),
        )
    }

    @Test
    fun fullPlayerAndKeyboardStillSuppressOpenHubFab() {
        assertFalse(
            scrobbleFabVisible(
                selectedTab = 0,
                homeTab = 0,
                overlayOpen = false,
                playerOpen = true,
                hubOpen = true,
            ),
        )
        assertFalse(
            scrobbleFabVisible(
                selectedTab = 0,
                homeTab = 0,
                overlayOpen = false,
                keyboardVisible = true,
                hubOpen = true,
            ),
        )
    }
}
