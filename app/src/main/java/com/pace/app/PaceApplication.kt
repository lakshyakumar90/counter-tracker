package com.pace.app

import android.app.Application
import android.content.Context

class PaceApplication : Application() {
    lateinit var container: PaceAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PaceAppContainer(applicationContext)
    }
}

class PaceAppContainer(context: Context) {
    val settingsRepository = PaceSettingsRepository(context)
}
