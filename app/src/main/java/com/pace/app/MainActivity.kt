package com.pace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val appContainer: PaceAppContainer by lazy { (application as PaceApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer.authRepository.handleAuthCallback(intent)
        val safeLaunch = intent.action == android.content.Intent.ACTION_MAIN && intent.data == null
        setContent {
            PaceAppRoot(container = appContainer, forceHome = !safeLaunch)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appContainer.authRepository.handleAuthCallback(intent)
    }
}
