package com.pace.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pace.app.ui.theme.PaceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaceComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buttonStatesExposeDisabledAndLoadingActions() {
        composeRule.setContent {
            PaceTheme {
                androidx.compose.foundation.layout.Column {
                    PacePrimaryButton("Ready", onClick = {})
                    PacePrimaryButton("Saving", onClick = {}, loading = true)
                    PaceSecondaryButton("Unavailable", onClick = {}, enabled = false)
                }
            }
        }

        composeRule.onNodeWithText("Ready").assertIsEnabled()
        composeRule.onNodeWithText("Saving").assertIsNotEnabled()
        composeRule.onNodeWithText("Unavailable").assertIsNotEnabled()
    }

    @Test
    fun segmentedControlReflectsSelectionChanges() {
        composeRule.setContent {
            PaceTheme {
                androidx.compose.foundation.layout.Column {
                    var selected by remember { mutableStateOf("week") }
                    PaceSegmentedControl(
                        options = listOf(PaceSegment("week", "Week"), PaceSegment("month", "Month")),
                        selected = selected,
                        onSelected = { selected = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Week").assertIsSelected()
        composeRule.onNodeWithText("Month").performClick().assertIsSelected()
    }

    @Test
    fun offlineAndMissingDataStatesKeepTheirExplanationsVisible() {
        composeRule.setContent {
            PaceTheme {
                androidx.compose.foundation.layout.Column {
                    PaceOfflineBanner("Offline · saved activities remain available")
                    PaceStatisticComparison(
                        label = "Elevation",
                        currentValue = "—",
                        currentUnit = "m",
                        comparison = PaceComparison.Missing("Not available for this activity"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Offline · saved activities remain available").assertIsDisplayed()
        composeRule.onNodeWithText("Not available for this activity").assertIsDisplayed()
    }

    @Test
    fun destructiveActionRequiresConfirmation() {
        var confirmed = false
        composeRule.setContent {
            PaceTheme {
                PaceDestructiveAction(
                    label = "Delete activity",
                    dialogTitle = "Delete this activity?",
                    dialogMessage = "This action cannot be undone.",
                    confirmLabel = "Delete",
                    dismissLabel = "Cancel",
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeRule.onNodeWithText("Delete activity").performClick()
        composeRule.onNodeWithText("Delete this activity?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assert(!confirmed)
    }
}
