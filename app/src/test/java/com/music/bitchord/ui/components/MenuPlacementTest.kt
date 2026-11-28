package com.music.bitchord.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement rules for [IosMenu]. These are asserted here because the bug they
 * fix was geometric, not visual: a ⋮ near the foot of a Scrobble list opened
 * its panel at the foot of the safe area instead of beside the row.
 */
class MenuPlacementTest {

    private val top = 100
    private val bottom = 2000
    private val gap = 20
    private val panel = 400

    @Test fun anchorInUpperHalfOpensBelowIt() {
        val p = menuPlacement(anchorTop = 300, anchorBottom = 360, panelHeight = panel, top = top, bottom = bottom, gap = gap)
        assertTrue(p.hangsDown)
        assertEquals(380, p.y)
    }

    @Test fun anchorNearTheFootOpensAboveIt() {
        val p = menuPlacement(anchorTop = 1800, anchorBottom = 1860, panelHeight = panel, top = top, bottom = bottom, gap = gap)
        assertFalse(p.hangsDown)
        assertEquals(1800 - gap - panel, p.y)
    }

    @Test fun panelStaysBesideItsAnchorAndNeverAtTheFoot() {
        // The old code pinned every anchor below the window's midpoint to the
        // foot of the box; the panel must be within a gap of the row instead.
        val rowTop = 900
        val rowBottom = 960
        val p = menuPlacement(rowTop, rowBottom, panel, top, bottom, gap)
        assertTrue(p.y == rowBottom + gap || p.y + panel == rowTop - gap)
    }

    @Test fun neitherSideFitsSoItTakesTheRoomierOne() {
        // 1,000px box, 900px panel: neither the 320px below nor the -220px
        // above can hold it, so it prefers below and is clamped to the box.
        val boxBottom = 1100
        val p = menuPlacement(anchorTop = 700, anchorBottom = 760, panelHeight = 900, top = top, bottom = boxBottom, gap = gap)
        assertTrue(p.hangsDown)
        assertEquals(boxBottom - 900, p.y)
        assertTrue(p.y + 900 <= boxBottom)
    }

    @Test fun roomierSideWinsWhenNeitherFits() {
        // 80px above, 70px below: neither holds an 800px panel, and the
        // shallower side loses.
        val p = menuPlacement(anchorTop = 1000, anchorBottom = 1060, panelHeight = 800, top = top, bottom = 1150, gap = gap)
        assertFalse(p.hangsDown)
        assertEquals(1000 - gap - 800, p.y)
    }

    @Test fun panelTallerThanTheBoxIsClampedToTheTop() {
        val p = menuPlacement(anchorTop = 500, anchorBottom = 560, panelHeight = 5000, top = top, bottom = bottom, gap = gap)
        assertEquals(top, p.y)
    }

    @Test fun noAnchorTakesTheBottomEnd() {
        val p = menuPlacement(anchorTop = null, anchorBottom = null, panelHeight = panel, top = top, bottom = bottom, gap = gap)
        assertTrue(p.hangsDown)
        assertEquals(bottom - panel, p.y)
    }

    @Test fun clearanceKeepsThePanelAboveTheFloatingChrome() {
        // Scrobble reserves 96dp for the tab bar and mini player; the panel's
        // foot must sit above that line, not over it.
        val clearance = 200
        val raisedBottom = bottom - clearance
        val p = menuPlacement(anchorTop = 1500, anchorBottom = 1560, panelHeight = panel, top = top, bottom = raisedBottom, gap = gap)
        assertTrue(p.y + panel <= raisedBottom)
    }
}
