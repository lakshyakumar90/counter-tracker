package com.pace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val safeLaunch = intent.action == android.content.Intent.ACTION_MAIN && intent.data == null
        val container = (application as PaceApplication).container
        setContent {
            PaceAppRoot(container = container, forceHome = !safeLaunch)
        }
    }
}
