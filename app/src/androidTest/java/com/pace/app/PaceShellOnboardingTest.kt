package com.pace.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.pace.app.ui.components.PaceSport

@RunWith(AndroidJUnit4::class)
class PaceShellOnboardingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var container: PaceAppContainer

    @Before
    fun resetLocalPreferences() {
        container = (composeRule.activity.application as PaceApplication).container
        container.settingsRepository.clear()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun firstLaunchOffersLocalOnlySetupAndSkipCompletesIt() {
        composeRule.onNodeWithText("A private place for your training").assertIsDisplayed()
        composeRule.onNodeWithText("Skip setup").performClick()

        composeRule.onNodeWithText("Your training, at your pace").assertIsDisplayed()
        assert(container.settingsRepository.load().onboardingComplete)
    }

    @Test
    fun onboardingPersistsSportUnitsThemeAndOptionalProfile() {
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Choose your activities").assertIsDisplayed()
        composeRule.onNodeWithText("Cycling").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Choose your units").assertIsDisplayed()
        composeRule.onNodeWithText("Imperial · mi, min/mi").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Make Pace yours").assertIsDisplayed()
        composeRule.onNodeWithText("Use device theme").performClick()
        composeRule.onNodeWithText("Name (optional)").performTextInput("Lakshya")
        composeRule.onNodeWithText("Use Pace locally").performClick()

        composeRule.onNodeWithText("Your training, at your pace").assertIsDisplayed()
        val stored = container.settingsRepository.load()
        assert(stored.onboardingComplete)
        assert(stored.units == PaceUnits.Imperial)
        assert(stored.theme == PaceThemeChoice.System)
        assert("Lakshya" == stored.profileName)
        assert(PaceSport.Cycling !in stored.preferredSports)
    }

    @Test
    fun selectedTabAndPreferencesSurviveActivityRecreation() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Insights").performClick()
        composeRule.onNodeWithText("Record an activity to see local summaries and trends here.").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Record an activity to see local summaries and trends here.").assertIsDisplayed()
        assert(container.settingsRepository.load().lastSelectedTab == "insights")
    }

    @Test
    fun planRemainsOutOfDefaultV1Navigation() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Your training, at your pace").assertIsDisplayed()
        composeRule.onAllNodesWithText("Plan").assertCountEquals(0)
    }

    @Test
    fun corruptSettingsRecoverToSafeDefaults() {
        composeRule.activity.getSharedPreferences("pace_local_settings", 0)
            .edit()
            .putString("onboarding_complete", "corrupt")
            .commit()

        val recovered = container.settingsRepository.load()

        assert(!recovered.onboardingComplete)
        assert(recovered.units == PaceUnits.Metric)
        assert(recovered.theme == PaceThemeChoice.System)
    }

    @Test
    fun recordAndMapsTabsOpenLocalOnlyDestinations() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onNodeWithText("Record an activity").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithText("Map coverage is unavailable").assertIsDisplayed()
    }

    @Test
    fun profileSettingsAreReachableFromTheShell() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Profile and settings").performClick()
        composeRule.onNodeWithText("Local profile · this device").assertIsDisplayed()
        composeRule.onNodeWithText("No account is connected. Your local settings remain available offline.").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Your training, at your pace").assertIsDisplayed()
    }
}
