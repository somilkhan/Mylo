package com.music.bitchord.ui.scrobble

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Real production controls; no recreated UI, fabricated pixels, network or ViewModel setup. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-night-xxhdpi")
class ScrobbleControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var previousReduceAnimation = false

    @Before fun resetMotion() {
        previousReduceAnimation = AppSettings.reduceAnimation.value
        AppSettings.reduceAnimation.value = false
        compose.mainClock.autoAdvance = false
    }

    @After fun restoreMotion() {
        AppSettings.reduceAnimation.value = previousReduceAnimation
    }

    private fun content(
        darkTheme: Boolean = true,
        glass: Boolean = false,
        reducedMotion: Boolean = false,
        body: @Composable () -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLiquidGlassEnabled provides glass,
                LocalScrobbleReduceMotion provides reducedMotion,
            ) {
                BitChordTheme(darkTheme = darkTheme, content = body)
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    private fun assertMinimumTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = 48f * compose.activity.resources.displayMetrics.density
        assertTrue("Target width must be at least 48dp: $bounds", bounds.width >= minimum - 0.5f)
        assertTrue("Target height must be at least 48dp: $bounds", bounds.height >= minimum - 0.5f)
    }

    @Test fun compactPillsHaveStableTargetsIncludingOutsideTheVisualFill() {
        var primaryClicks = 0
        var secondaryClicks = 0
        content {
            Column {
                ScrobblePrimaryButton("Add", { primaryClicks++ }, Modifier.testTag("primary"), compact = true)
                ScrobbleSecondaryButton("Retry", { secondaryClicks++ }, Modifier.testTag("secondary"), compact = true)
            }
        }
        for (tag in listOf("primary", "secondary")) {
            val node = compose.onNodeWithTag(tag)
            assertMinimumTarget(node)
            // The outer 6dp band is clickable, although the compact fill remains 36dp tall.
            node.performTouchInput { click(Offset(center.x, 2f)) }
        }
        compose.runOnIdle {
            assertEquals(1, primaryClicks)
            assertEquals(1, secondaryClicks)
        }
    }

    @Test fun busyPrimaryRetainsItsNameWidthAndIndeterminateDisabledState() {
        val busy = mutableStateOf(false)
        var clicks = 0
        content {
            ScrobblePrimaryButton(
                "Generate playlist", { clicks++ }, Modifier.testTag("primary"),
                busy = busy.value, compact = true,
            )
        }
        val node = compose.onNodeWithTag("primary")
        node.assertIsEnabled()
        val initialBounds = node.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { busy.value = true }
        compose.mainClock.advanceTimeBy(32)
        node.assertIsNotEnabled().assertContentDescriptionEquals("Generate playlist")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        assertEquals(initialBounds, node.fetchSemanticsNode().boundsInRoot)
        node.performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun disabledControlsKeepButtonSemanticsAndDoNotInvokeCallbacks() {
        var clicks = 0
        content {
            Column {
                ScrobbleSecondaryButton("Retry", { clicks++ }, Modifier.testTag("secondary"), enabled = false, compact = true)
                ScrobbleIconButton(SfIcons.Add, "Add tracks", { clicks++ }, Modifier.testTag("icon"), enabled = false)
            }
        }
        for (tag in listOf("secondary", "icon")) {
            val node = compose.onNodeWithTag(tag)
            assertMinimumTarget(node)
            node.assertIsNotEnabled()
            node.performTouchInput { click() }
        }
        compose.onNodeWithTag("icon").assertContentDescriptionEquals("Add tracks")
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun activeIconExposesSelectedStateWithStableAnchorInLightGlassTheme() {
        val active = mutableStateOf(false)
        var anchor: androidx.compose.ui.geometry.Rect? = null
        content(darkTheme = false, glass = true) {
            ScrobbleIconButton(
                SfIcons.ChevronUpDown, "Sort playlists", { active.value = !active.value },
                Modifier.testTag("sort"), active = active.value, filled = false,
                onAnchor = { anchor = it },
            )
        }
        val node = compose.onNodeWithTag("sort")
        assertMinimumTarget(node)
        node.assertIsNotSelected()
        val initialAnchor = anchor
        node.performClick()
        compose.mainClock.advanceTimeBy(220)
        node.assertIsSelected().assertContentDescriptionEquals("Sort playlists")
        assertEquals(initialAnchor, anchor)
        node.performClick()
        compose.mainClock.advanceTimeBy(220)
        node.assertIsNotSelected()
    }

    @Test fun pointerPressUsesSubtleScaleAndReturnsWithoutChangingTargetBounds() {
        var scale = 1f
        content { PressProbe(onScale = { scale = it }) }
        val node = compose.onNodeWithTag("probe")
        val initialBounds = node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertEquals(0.97f, scale, 0.001f) }
        assertEquals(initialBounds, node.fetchSemanticsNode().boundsInRoot)
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(180)
        compose.runOnIdle { assertEquals(1f, scale, 0.001f) }
    }

    @Test fun hostReducedMotionKeepsPointerPressAtUnitScale() {
        var scale = 1f
        content(reducedMotion = true) { PressProbe(onScale = { scale = it }) }
        compose.onNodeWithTag("probe").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertEquals(1f, scale, 0f) }
        compose.onNodeWithTag("probe").performTouchInput { up() }
    }

    @Test fun appReducedMotionStopsAnAlreadyHeldPointerPress() {
        var scale = 1f
        content { PressProbe(onScale = { scale = it }) }
        compose.onNodeWithTag("probe").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle {
            assertEquals(0.97f, scale, 0.001f)
            AppSettings.reduceAnimation.value = true
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(1f, scale, 0f) }
        compose.onNodeWithTag("probe").performTouchInput { up() }
    }

    @Test fun focusedEnterPressInvokesClickWithoutAnyTransform() {
        var scale = 1f
        var clicks = 0
        var inputMode: InputModeManager? = null
        val focus = FocusRequester()
        content {
            inputMode = LocalInputModeManager.current
            PressProbe(onScale = { scale = it }, onClick = { clicks++ }, focus = focus)
        }
        compose.runOnIdle {
            assertTrue(inputMode!!.requestInputMode(InputMode.Keyboard))
            focus.requestFocus()
        }
        compose.mainClock.advanceTimeBy(32)
        val node = compose.onNodeWithTag("probe").assertIsFocused()
        node.performKeyInput { keyDown(Key.Enter) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertEquals(1f, scale, 0f) }
        node.performKeyInput { keyUp(Key.Enter) }
        compose.mainClock.advanceTimeBy(180)
        compose.runOnIdle {
            assertEquals(1f, scale, 0f)
            assertEquals(1, clicks)
        }
    }

    @Composable
    private fun PressProbe(
        onScale: (Float) -> Unit,
        onClick: () -> Unit = {},
        focus: FocusRequester? = null,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val scale = pressScale(interaction)
        SideEffect { onScale(scale) }
        // Production pressScale drives only a child surface, never the outer target.
        Box(
            Modifier.size(48.dp).testTag("probe")
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            Box(Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        }
    }
}
