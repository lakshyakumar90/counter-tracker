package com.pace.app

import android.app.Application
import android.content.Context
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.repository.RoomActivityRepository
import com.pace.app.data.local.repository.RoomGoalRepository
import com.pace.app.data.local.repository.RoomMapPackRepository
import com.pace.app.data.local.repository.RoomSyncOperationRepository
import com.pace.app.data.local.repository.RoomWorkoutRepository
import com.pace.app.auth.LocalOnlyAuthRepository
import com.pace.app.auth.PaceAuthRepository
import com.pace.app.auth.SupabaseAuthRepository

class PaceApplication : Application() {
    lateinit var container: PaceAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PaceAppContainer(applicationContext)
    }
}

class PaceAppContainer(context: Context) {
    val authRepository: PaceAuthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            SupabaseAuthRepository(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        } else {
            LocalOnlyAuthRepository()
        }
    }
    val settingsRepository = PaceSettingsRepository(context)
    val database: PaceDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PaceDatabase.create(context) }
    val activityRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomActivityRepository(database) }
    val workoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomWorkoutRepository(database) }
    val goalRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomGoalRepository(database) }
    val mapPackRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomMapPackRepository(database) }
    val syncOperationRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomSyncOperationRepository(database) }
}
