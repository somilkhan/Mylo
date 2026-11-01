package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrobbleFabComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleFabExposesAccessibleButtonAndInvokesClick() {
        var clickCount = 0
        composeRule.setContent {
            MaterialTheme {
                ScrobbleFab(
                    visible = true,
                    open = false,
                    onClick = { clickCount += 1 },
                    surface = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    reduceMotion = true,
                )
            }
        }

        val button = composeRule.onNodeWithContentDescription("Open Scrobble")
        button.assertIsDisplayed()
        button.assertHasClickAction()
        button.assertWidthIsAtLeast(48.dp)
        button.assertHeightIsAtLeast(48.dp)
        button.performClick()

        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }

    @Test
    fun hiddenFabIsRemovedFromAccessibilityTree() {
        composeRule.setContent {
            MaterialTheme {
                ScrobbleFab(
                    visible = false,
                    open = false,
                    onClick = {},
                    surface = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Scrobble").assertDoesNotExist()
    }

    @Test
    fun openFabAnnouncesItsCloseAction() {
        composeRule.setContent {
            MaterialTheme {
                ScrobbleFab(
                    visible = true,
                    open = true,
                    onClick = {},
                    surface = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    reduceMotion = true,
                )
            }
        }

        val button = composeRule.onNodeWithContentDescription("Close Scrobble")
        button.assertIsDisplayed()
        button.assertHasClickAction()
    }
}
