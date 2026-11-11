package com.musclenote.android

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.musclenote.android.notifications.NotificationChannels
import com.musclenote.android.ui.ExternalDestination
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Activité unique. AppCompatActivity (et non ComponentActivity) pour la langue par
 * application via AppCompatDelegate (§ 9.4). Reçoit aussi les fichiers JSON partagés ou
 * ouverts avec MuscleNote (§ 5.4, § 5.5) et les touchers de notification (§ 7.2).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var backupViewModel: BackupViewModel

    /** Écrans demandés de l'extérieur (notifications), consommés par la navigation. */
    private val externalDestinations = Channel<ExternalDestination>(Channel.CONFLATED)
    private val externalDestinationFlow = externalDestinations.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as MuscleNoteApplication
        // Noms des canaux dans la langue de l'app : l'activité est recréée à chaque changement (§ 7.1).
        NotificationChannels.createOrUpdate(this)
        backupViewModel = ViewModelProvider(this, BackupViewModel.Factory(app))[BackupViewModel::class.java]

        // Une recréation (rotation, langue) ou une reprise depuis les récents ne relance ni l'import
        // ni la navigation demandée par une notification.
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) handleIntent(intent)

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
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                MuscleNoteTheme(darkTheme = darkTheme) {
                    MuscleNoteApp(backupViewModel, externalDestinationFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Toucher d'une notification (`ACTION_OPEN_WORKOUT`, `ACTION_OPEN_NUTRITION`), `ACTION_SEND`
     * (`EXTRA_STREAM`, ou à défaut `EXTRA_TEXT`) ou `ACTION_VIEW` d'un JSON.
     */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_WORKOUT -> externalDestinations.trySend(ExternalDestination.Workout)
            ACTION_OPEN_NUTRITION -> externalDestinations.trySend(ExternalDestination.Nutrition)
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    uri != null -> backupViewModel.onFileReceived(uri, fromOtherApp = true)
                    !text.isNullOrBlank() -> backupViewModel.onTextReceived(text)
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { backupViewModel.onFileReceived(it, fromOtherApp = true) }
        }
    }

    companion object {
        /** Ouvre l'écran de la séance en cours (notifications du minuteur, § 7.2). */
        const val ACTION_OPEN_WORKOUT = "com.musclenote.android.action.OPEN_WORKOUT"

        /** Ouvre l'onglet Nutrition (notifications des rappels de compléments, § 7.3). */
        const val ACTION_OPEN_NUTRITION = "com.musclenote.android.action.OPEN_NUTRITION"
    }
}
