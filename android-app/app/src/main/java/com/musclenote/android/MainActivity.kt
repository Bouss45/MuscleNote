package com.musclenote.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Activité unique. AppCompatActivity (et non ComponentActivity) pour la langue par
 * application via AppCompatDelegate (§ 9.4).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            // Icônes des barres système claires en thème sombre, foncées en thème clair (§ 9.1).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            MuscleNoteTheme(darkTheme = darkTheme) {
                MuscleNoteApp()
            }
        }
    }
}
