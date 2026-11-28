package com.music.bitchord.ui.scrobble

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag

import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * [ScrobbleTabSwitcher]'s two promises: a tab change is a transition rather
 * than a cut, and a tab keeps its scroll while it is off screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-night-xxhdpi")
class ScrobbleTabSwitcherTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** The shape the two real tabs have: a heading and a scrolled list. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun harness(
        tab: () -> Int,
        reduceMotion: Boolean = false,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        BitChordTheme {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                // Hoisted here, as ScrobbleHost does, so it outlives a switch.
                val holder = rememberSaveableStateHolder()
                ScrobbleTabSwitcher(tab = tab(), reduceMotion = reduceMotion, stateHolder = holder) { current ->
                    Column(Modifier.fillMaxSize()) {
                        Text("tab $current heading", style = MaterialTheme.typography.titleLarge)
                        val listState = rememberLazyListState()
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("list")) {
                            items((0 until 60).toList()) { index -> Text("row $index") }
                        }
                    }
                }
            }
        }
    }

    /** Advances the test clock a frame at a time; one long step under-runs the transition. */
    private fun frames(millis: Int) {
        repeat(millis / 20) { compose.mainClock.advanceTimeBy(20) }
    }

    @Test fun eachTabKeepsItsScrollWhileAway() {
        var tab by mutableStateOf(1)
        compose.setContent(harness({ tab }))

        compose.onNodeWithText("row 0").assertIsDisplayed()
        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.onNodeWithText("row 30").assertIsDisplayed()

        compose.runOnUiThread { tab = 2 }
        compose.waitForIdle()
        compose.onNodeWithText("tab 2 heading").assertIsDisplayed()
        // The other tab starts at the top; it is not inheriting this one's offset.
        compose.onNodeWithText("row 0").assertIsDisplayed()

        compose.runOnUiThread { tab = 1 }
        compose.waitForIdle()
        compose.onNodeWithText("tab 1 heading").assertIsDisplayed()
        compose.onNodeWithText("row 30").assertIsDisplayed()
        compose.onNodeWithText("row 0").assertDoesNotExist()
    }

    @Test fun aSwitchIsATransitionNotACut() {
        var tab by mutableStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent(harness({ tab }))
        compose.mainClock.advanceTimeBy(600)

        compose.runOnUiThread { tab = 2 }
        compose.waitForIdle()
        frames(80)
        // Mid-flight both are on screen, which is what lets the generated
        // playlist fly across to its place in the list.
        compose.onNodeWithText("tab 1 heading").assertExists()
        compose.onNodeWithText("tab 2 heading").assertExists()

        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("tab 2 heading").assertIsDisplayed()
        compose.onNodeWithText("tab 1 heading").assertDoesNotExist()
    }

    @Test fun reduceMotionStillChangesTab() {
        var tab by mutableStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent(harness({ tab }, reduceMotion = true))
        compose.mainClock.advanceTimeBy(600)

        compose.runOnUiThread { tab = 2 }
        compose.waitForIdle()
        frames(200)
        compose.onNodeWithText("tab 2 heading").assertExists()
        compose.onNodeWithText("tab 1 heading").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("tab 2 heading").assertIsDisplayed()
    }
}
