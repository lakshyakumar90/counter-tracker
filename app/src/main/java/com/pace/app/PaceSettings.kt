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
    val backupOwnerUserId = stringPreferencesKey("backup_owner_user_id")
    val backupEnabled = booleanPreferencesKey("backup_enabled")
    val lastSuccessfulSyncEpochMillis = androidx.datastore.preferences.core.longPreferencesKey("last_successful_sync_epoch_millis")
    val allowMeteredBackup = booleanPreferencesKey("allow_metered_backup")
    val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
    val aiEnabled = booleanPreferencesKey("ai_enabled")
    val aiOwnerUserId = stringPreferencesKey("ai_owner_user_id")
    val highContrast = booleanPreferencesKey("high_contrast")
    val reducedMotion = booleanPreferencesKey("reduced_motion")
    val cachedAiResponse = stringPreferencesKey("cached_ai_response")
    val cachedAiKind = stringPreferencesKey("cached_ai_kind")
    val cachedAiOwnerUserId = stringPreferencesKey("cached_ai_owner_user_id")
}

data class PacePreferences(
    val onboardingComplete: Boolean = false,
    val preferredSports: Set<PaceSport> = PaceSport.entries.toSet(),
    val units: PaceUnits = PaceUnits.Metric,
    val theme: PaceThemeChoice = PaceThemeChoice.Light,
    val profileName: String = "",
    val lastSelectedTab: String = "home",
    val lastUsedSport: PaceSport = PaceSport.Running,
    val insightsPeriod: String = "WEEK",
    val backupOwnerUserId: String? = null,
    val backupEnabled: Boolean = false,
    val lastSuccessfulSyncEpochMillis: Long? = null,
    val allowMeteredBackup: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val aiEnabled: Boolean = false,
    val aiOwnerUserId: String? = null,
    val cachedAiResponse: String? = null,
    val cachedAiKind: String? = null,
    val cachedAiOwnerUserId: String? = null,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
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
            ?.takeIf { it in setOf("home", "record", "maps", "plan", "insights") }
            ?: defaults.lastSelectedTab,
        lastUsedSport = stored[PacePreferenceKeys.lastUsedSport]
            ?.let { runCatching { PaceSport.valueOf(it) }.getOrNull() }
            ?: defaults.lastUsedSport,
        backupOwnerUserId = stored[PacePreferenceKeys.backupOwnerUserId]?.takeIf { it.isNotBlank() },
        backupEnabled = stored[PacePreferenceKeys.backupEnabled] ?: false,
        lastSuccessfulSyncEpochMillis = stored[PacePreferenceKeys.lastSuccessfulSyncEpochMillis],
        allowMeteredBackup = stored[PacePreferenceKeys.allowMeteredBackup] ?: false,
        notificationsEnabled = stored[PacePreferenceKeys.notificationsEnabled] ?: true,
        aiEnabled = stored[PacePreferenceKeys.aiEnabled] ?: false,
        aiOwnerUserId = stored[PacePreferenceKeys.aiOwnerUserId]?.takeIf { it.isNotBlank() },
        cachedAiResponse = stored[PacePreferenceKeys.cachedAiResponse]?.takeIf { it.length <= 16_384 },
        cachedAiKind = stored[PacePreferenceKeys.cachedAiKind]?.takeIf { it in setOf("insight", "workout") },
        cachedAiOwnerUserId = stored[PacePreferenceKeys.cachedAiOwnerUserId]?.takeIf { it.isNotBlank() },
        highContrast = stored[PacePreferenceKeys.highContrast] ?: false,
        reducedMotion = stored[PacePreferenceKeys.reducedMotion] ?: false,
        insightsPeriod = stored[stringPreferencesKey("insights_period")]
            ?.takeIf { it in setOf("WEEK", "MONTH", "YEAR") }
            ?: defaults.insightsPeriod,
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
            value.backupOwnerUserId?.let { stored[PacePreferenceKeys.backupOwnerUserId] = it }
            stored[PacePreferenceKeys.backupEnabled] = value.backupEnabled
            value.lastSuccessfulSyncEpochMillis?.let { stored[PacePreferenceKeys.lastSuccessfulSyncEpochMillis] = it }
            stored[PacePreferenceKeys.allowMeteredBackup] = value.allowMeteredBackup
            stored[PacePreferenceKeys.notificationsEnabled] = value.notificationsEnabled
            stored[PacePreferenceKeys.aiEnabled] = value.aiEnabled
            value.aiOwnerUserId?.let { stored[PacePreferenceKeys.aiOwnerUserId] = it }
            value.cachedAiResponse?.takeIf { it.length <= 16_384 }?.let { stored[PacePreferenceKeys.cachedAiResponse] = it }
            value.cachedAiKind?.takeIf { it in setOf("insight", "workout") }?.let { stored[PacePreferenceKeys.cachedAiKind] = it }
            value.cachedAiOwnerUserId?.let { stored[PacePreferenceKeys.cachedAiOwnerUserId] = it }
            stored[PacePreferenceKeys.highContrast] = value.highContrast
            stored[PacePreferenceKeys.reducedMotion] = value.reducedMotion
            stored[stringPreferencesKey("insights_period")] = value.insightsPeriod.takeIf { it in setOf("WEEK", "MONTH", "YEAR") } ?: "WEEK"
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun enableBackupForSignedInAccount(userId: String) {
        dataStore.edit { stored ->
            val owner = stored[PacePreferenceKeys.backupOwnerUserId]
            if (owner == null || owner == userId) {
                stored[PacePreferenceKeys.backupOwnerUserId] = userId
                stored[PacePreferenceKeys.backupEnabled] = true
            }
        }
    }

    private companion object {
        const val DATASTORE_NAME = "pace_settings"
        const val LEGACY_PREFERENCES_NAME = "pace_local_settings"
        val VALID_TABS = setOf("home", "record", "maps", "insights")
    }
}
