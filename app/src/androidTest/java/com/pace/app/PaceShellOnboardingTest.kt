package com.pace.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun resetLocalPreferences(): Unit = runBlocking {
        container = (composeRule.activity.application as PaceApplication).container
        container.settingsRepository.clear()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun firstLaunchOffersLocalOnlySetupAndSkipCompletesIt() {
        composeRule.onNodeWithText("A private place for your training").assertIsDisplayed()
        composeRule.onNodeWithText("Skip setup").performClick()

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        assert(runBlocking { container.settingsRepository.preferences.first().onboardingComplete })
    }

    @Test
    fun threeStepOnboardingPersistsSportAndUnits() {
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Choose your activities").assertIsDisplayed()
        composeRule.onNodeWithText("Cycling").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Choose your units").assertIsDisplayed()
        composeRule.onNodeWithText("Imperial · mi, min/mi").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Choose your units").assertIsDisplayed()
        composeRule.onNodeWithText("Use Pace locally").performClick()

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        val stored = runBlocking { container.settingsRepository.preferences.first() }
        assert(stored.onboardingComplete)
        assert(stored.units == PaceUnits.Imperial)
        assert(stored.theme == PaceThemeChoice.Light)
        assert(PaceSport.Cycling !in stored.preferredSports)
    }

    @Test
    fun selectedTabAndPreferencesSurviveActivityRecreation() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Insights").performClick()
        composeRule.onNodeWithText("Record an activity to see your trends.").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Record an activity to see your trends.").assertIsDisplayed()
        assert(runBlocking { container.settingsRepository.preferences.first().lastSelectedTab == "insights" })
    }

    @Test
    fun planIsAvailableFromTheShell() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Plan").assertIsDisplayed()
    }

    @Test
    fun corruptSettingsRecoverToSafeDefaults() {
        val corrupt = mutablePreferencesOf(stringPreferencesKey("onboarding_complete") to "corrupt")
        val recovered = decodePacePreferences(corrupt)

        assert(!recovered.onboardingComplete)
        assert(recovered.units == PaceUnits.Metric)
        assert(recovered.theme == PaceThemeChoice.Light)
    }

    @Test
    fun dataStoreMigratesP04SharedPreferencesToLocalSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.currentTimeMillis().toString()
        val legacyName = "pace_migration_test_$suffix"
        val storeName = "pace_migration_test_$suffix"
        context.getSharedPreferences(legacyName, Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_complete", true)
            .putString("measurement_units", PaceUnits.Imperial.name)
            .putString("profile_name", "Test runner")
            .putString("last_selected_tab", "insights")
            .putStringSet("preferred_sports", setOf(PaceSport.Cycling.name))
            .commit()

        val migrationRepository = PaceSettingsRepository(context, storeName, legacyName)
        val migrated = migrationRepository.preferences.first()

        assert(migrated.onboardingComplete)
        assert(migrated.units == PaceUnits.Imperial)
        assert(migrated.profileName == "Test runner")
        assert(migrated.lastSelectedTab == "insights")
        assert(migrated.preferredSports == setOf(PaceSport.Cycling))
        migrationRepository.clear()
    }

    @Test
    fun recordAndMapsTabsOpenLocalOnlyDestinations() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onNodeWithText("Record an activity").assertIsDisplayed()
        composeRule.onNodeWithText("Start recording").assertIsDisplayed()
        listOf("Cycling", "Running", "Jogging", "Walking").forEach { sport ->
            composeRule.onNodeWithText(sport).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithText("Map coverage is unavailable").assertIsDisplayed()
    }

    @Test
    fun profileSettingsAreReachableFromTheShell() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithContentDescription("Profile and settings").performClick()
        composeRule.onNodeWithText("Profile and settings").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun systemBackFromHomeWorkoutsReturnsToHome() {
        composeRule.onNodeWithText("Skip setup").performClick()
        composeRule.onNodeWithText("Home workouts").performScrollTo().performClick()
        composeRule.onNodeWithText("Build a workout").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun accountBackupEnablesForItsOwnerWithoutReplacingADifferentOwner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.nanoTime().toString()
        val repository = PaceSettingsRepository(context, "pace_backup_$suffix", "pace_legacy_$suffix")
        repository.enableBackupForSignedInAccount("owner-one")
        repository.enableBackupForSignedInAccount("owner-two")
        val saved = repository.preferences.first()
        assert(saved.backupEnabled)
        assert(saved.backupOwnerUserId == "owner-one")
    }
}
