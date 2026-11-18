package com.pace.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pace.app.ui.components.PaceSport
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class PaceUnits { Metric, Imperial }
enum class PaceThemeChoice { System, Light, Dark }

internal object PacePreferenceKeys {
    val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    val preferredSports = stringSetPreferencesKey("preferred_sports")
    val units = stringPreferencesKey("measurement_units")
    val theme = stringPreferencesKey("theme_choice")
    val profileName = stringPreferencesKey("profile_name")
    val lastSelectedTab = stringPreferencesKey("last_selected_tab")
    val lastUsedSport = stringPreferencesKey("last_used_sport")
}

data class PacePreferences(
    val onboardingComplete: Boolean = false,
    val preferredSports: Set<PaceSport> = PaceSport.entries.toSet(),
    val units: PaceUnits = PaceUnits.Metric,
    val theme: PaceThemeChoice = PaceThemeChoice.System,
    val profileName: String = "",
    val lastSelectedTab: String = "home",
    val lastUsedSport: PaceSport = PaceSport.Running,
)

internal fun decodePacePreferences(stored: Preferences): PacePreferences = try {
    val defaults = PacePreferences()
    val sports = stored[PacePreferenceKeys.preferredSports]
        ?.mapNotNull { value -> runCatching { PaceSport.valueOf(value) }.getOrNull() }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: defaults.preferredSports
    PacePreferences(
        onboardingComplete = stored[PacePreferenceKeys.onboardingComplete] ?: defaults.onboardingComplete,
        preferredSports = sports,
        units = stored[PacePreferenceKeys.units]?.let { runCatching { PaceUnits.valueOf(it) }.getOrNull() } ?: defaults.units,
        theme = stored[PacePreferenceKeys.theme]?.let { runCatching { PaceThemeChoice.valueOf(it) }.getOrNull() } ?: defaults.theme,
        profileName = stored[PacePreferenceKeys.profileName].orEmpty(),
        lastSelectedTab = stored[PacePreferenceKeys.lastSelectedTab]
            ?.takeIf { it in setOf("home", "record", "maps", "insights") }
            ?: defaults.lastSelectedTab,
        lastUsedSport = stored[PacePreferenceKeys.lastUsedSport]
            ?.let { runCatching { PaceSport.valueOf(it) }.getOrNull() }
            ?: defaults.lastUsedSport,
    )
} catch (_: ClassCastException) {
    PacePreferences()
}

/** Local app configuration stored asynchronously with a one-time migration from P04 SharedPreferences. */
class PaceSettingsRepository internal constructor(
    context: Context,
    storeName: String = DATASTORE_NAME,
    legacyPreferencesName: String = LEGACY_PREFERENCES_NAME,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context.applicationContext, legacyPreferencesName)),
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.applicationContext.preferencesDataStoreFile(storeName) },
    )

    val preferences: Flow<PacePreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decodePacePreferences)

    suspend fun save(value: PacePreferences) {
        dataStore.edit { stored ->
            stored.clear()
            stored[PacePreferenceKeys.onboardingComplete] = value.onboardingComplete
            stored[PacePreferenceKeys.preferredSports] = value.preferredSports.map { it.name }.toSet()
            stored[PacePreferenceKeys.units] = value.units.name
            stored[PacePreferenceKeys.theme] = value.theme.name
            stored[PacePreferenceKeys.profileName] = value.profileName
            stored[PacePreferenceKeys.lastSelectedTab] = value.lastSelectedTab.takeIf { it in VALID_TABS } ?: "home"
            stored[PacePreferenceKeys.lastUsedSport] = value.lastUsedSport.name
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        const val DATASTORE_NAME = "pace_settings"
        const val LEGACY_PREFERENCES_NAME = "pace_local_settings"
        val VALID_TABS = setOf("home", "record", "maps", "insights")
    }
}
