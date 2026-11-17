package com.pace.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaceWelcomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeTitleIsDisplayed() {
        composeRule.onNodeWithText("Your pace, your progress").assertIsDisplayed()
    }

    @Test
    fun buildInformationShowsVersionAndVariant() {
        composeRule.onNodeWithText("Build information").assertIsDisplayed()
        composeRule.onNodeWithText("Build variant: debug").assertIsDisplayed()
    }

    @Test
    fun developerSwitchShowsDeterministicSampleActivities() {
        composeRule.onNodeWithText("Show deterministic sample data")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("sample-data-toggle").performClick()
        composeRule.onNodeWithText("Developer sample data").assertIsDisplayed()
        composeRule.onNodeWithText("Sample run · 5.0 km · 31 min").assertIsDisplayed()
        composeRule.onNodeWithText("Sample ride · 18.2 km · 48 min").assertIsDisplayed()
    }
}
