package com.assist.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.assist.ui.theme.AssistTheme
import dagger.hilt.android.AndroidEntryPoint

/** Onboarding/home entry point: permission status, API-key entry, start button. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistTheme {
                OnboardingScreen()
            }
        }
    }
}
