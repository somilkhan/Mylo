package com.music.bitchord.ui.scrobble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsDashboardFoldTest {
    @Test fun compactOverviewOnlyExpandsOnDemand() = runTest {
        val fold = DashboardFold(1f, this, reduceMotion = true)
        fold.distance = 200f
        assertEquals(Offset.Zero, fold.connection.onPostScroll(Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput))
        fold.connection.onPostFling(Velocity.Zero, Velocity(0f, 500f))
        assertEquals(1f, fold.fraction, 0f)
        fold.expand()
        assertEquals(0f, fold.fraction, 0f)
        fold.collapse()
        assertEquals(1f, fold.fraction, 0f)
    }

    @Test fun motionPreferenceChangesDoNotRecreateTheFoldPosition() = runTest {
        val fold = DashboardFold(0.7f, this, reduceMotion = false)
        fold.setReduceMotion(true)
        assertEquals(0.7f, fold.fraction, 0f)
        fold.expand()
        assertEquals(0f, fold.fraction, 0f)
        fold.reset()
        assertEquals(1f, fold.fraction, 0f)
    }
}
