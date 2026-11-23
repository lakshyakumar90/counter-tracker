package com.pace.app

import android.app.Application
import android.content.Context
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.repository.RoomActivityRepository
import com.pace.app.data.local.repository.RoomGoalRepository
import com.pace.app.data.local.repository.RoomMapPackRepository
import com.pace.app.data.local.repository.RoomHomeWorkoutDraftRepository
import com.pace.app.data.local.repository.RoomSyncOperationRepository
import com.pace.app.data.local.repository.RoomWorkoutRepository
import com.pace.app.data.local.repository.RoomSavedRouteRepository
import com.pace.app.auth.LocalOnlyAuthRepository
import com.pace.app.auth.PaceAuthRepository
import com.pace.app.auth.PaceAccountState
import com.pace.app.auth.SupabaseAuthRepository
import com.pace.app.data.sync.PaceSyncScheduler
import com.pace.app.tracking.steps.PaceStepWatchdog
import com.pace.app.tracking.steps.PaceSteps
import com.pace.app.ui.maps.PaceMapNetworkPolicy
import org.maplibre.android.MapLibre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PaceApplication : Application() {
    lateinit var container: PaceAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        PaceMapNetworkPolicy.configure()
        container = PaceAppContainer(applicationContext)
        // Repair step tracking whenever the process starts. Foreground service starts are only
        // attempted when the app is in the foreground; otherwise the watchdog handles it later.
        if (PaceSteps.enabled(this)) {
            PaceStepWatchdog.schedule(this)
            PaceSteps.ensureRunning(this)
        }
    }
}

class PaceAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val authRepository: PaceAuthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            SupabaseAuthRepository(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        } else {
            LocalOnlyAuthRepository()
        }
    }
    val settingsRepository = PaceSettingsRepository(context)
    val database: PaceDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PaceDatabase.create(context) }
    val currentOwnerId: () -> String = { (authRepository.accountState.value as? PaceAccountState.SignedIn)?.userId ?: "local" }
    val activityRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomActivityRepository(database, currentOwnerId) }
    val workoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomWorkoutRepository(database, currentOwnerId) }
    val goalRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomGoalRepository(database, currentOwnerId) }
    val mapPackRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomMapPackRepository(database) }
    val homeWorkoutDraftRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomHomeWorkoutDraftRepository(database) }
    val savedRouteRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomSavedRouteRepository(database, currentOwnerId) }
    val syncOperationRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomSyncOperationRepository(database) }

    init {
        backgroundScope.launch {
            authRepository.accountState.map { (it as? PaceAccountState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collect { userId -> if (userId != null) settingsRepository.enableBackupForSignedInAccount(userId) }
        }
        backgroundScope.launch {
            combine(authRepository.accountState, settingsRepository.preferences) { account, preferences -> account to preferences }
                .collectLatest { (account, preferences) ->
                    val signedIn = account as? PaceAccountState.SignedIn ?: return@collectLatest
                    if (!preferences.backupEnabled || preferences.backupOwnerUserId != signedIn.userId) return@collectLatest
                    database.syncOperationDao().observeQueuedCount(signedIn.userId).collect { queued ->
                        if (queued > 0) PaceSyncScheduler.enqueue(appContext, preferences.allowMeteredBackup)
                    }
                }
        }
    }
}
