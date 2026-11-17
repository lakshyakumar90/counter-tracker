package com.pace.app

import android.content.Context
import android.content.SharedPreferences
import com.pace.app.ui.components.PaceSport

enum class PaceUnits { Metric, Imperial }
enum class PaceThemeChoice { System, Light, Dark }

data class PacePreferences(
    val onboardingComplete: Boolean = false,
    val preferredSports: Set<PaceSport> = PaceSport.entries.toSet(),
    val units: PaceUnits = PaceUnits.Metric,
    val theme: PaceThemeChoice = PaceThemeChoice.System,
    val profileName: String = "",
    val lastSelectedTab: String = "home",
)

class PaceSettingsRepository(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PacePreferences = try {
        val defaults = PacePreferences()
        val savedSports = preferences.getStringSet(KEY_SPORTS, null)
            ?.mapNotNull { value -> runCatching { PaceSport.valueOf(value) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.preferredSports
        PacePreferences(
            onboardingComplete = preferences.getBoolean(KEY_ONBOARDING, defaults.onboardingComplete),
            preferredSports = savedSports,
            units = enumValue(preferences.getString(KEY_UNITS, null), defaults.units),
            theme = enumValue(preferences.getString(KEY_THEME, null), defaults.theme),
            profileName = preferences.getString(KEY_PROFILE_NAME, defaults.profileName).orEmpty(),
            lastSelectedTab = preferences.getString(KEY_LAST_TAB, defaults.lastSelectedTab)
                .takeIf { it in VALID_TABS }
                ?: defaults.lastSelectedTab,
        )
    } catch (_: ClassCastException) {
        recoverDefaults()
    } catch (_: IllegalStateException) {
        recoverDefaults()
    }

    fun save(value: PacePreferences) {
        preferences.edit()
            .putBoolean(KEY_ONBOARDING, value.onboardingComplete)
            .putStringSet(KEY_SPORTS, value.preferredSports.map { it.name }.toSet())
            .putString(KEY_UNITS, value.units.name)
            .putString(KEY_THEME, value.theme.name)
            .putString(KEY_PROFILE_NAME, value.profileName)
            .putString(KEY_LAST_TAB, value.lastSelectedTab.takeIf { it in VALID_TABS } ?: "home")
            .apply()
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun recoverDefaults(): PacePreferences {
        preferences.edit().clear().commit()
        return PacePreferences()
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val PREFS_NAME = "pace_local_settings"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_SPORTS = "preferred_sports"
        const val KEY_UNITS = "measurement_units"
        const val KEY_THEME = "theme_choice"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_LAST_TAB = "last_selected_tab"
        val VALID_TABS = setOf("home", "record", "maps", "insights")
    }
}
