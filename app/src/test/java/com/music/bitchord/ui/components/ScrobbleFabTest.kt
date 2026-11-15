package com.music.bitchord.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleFabTest {
    @Test
    fun visibleOnlyOnHomeWhenNoCompetingSurfaceIsOpen() {
        assertTrue(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = false))
        assertFalse(scrobbleFabVisible(selectedTab = 1, homeTab = 0, overlayOpen = false))
        assertFalse(scrobbleFabVisible(selectedTab = 0, homeTab = 0, overlayOpen = true))
    }

    @Test
    fun hidesForFullPlayerAndKeyboard() {
        assertFalse(scrobbleFabVisible(0, 0, overlayOpen = false, playerOpen = true))
        assertFalse(scrobbleFabVisible(0, 0, overlayOpen = false, keyboardVisible = true))
        assertTrue(scrobbleFabVisible(0, 0, overlayOpen = false, playerOpen = false, keyboardVisible = false))
    }
}
