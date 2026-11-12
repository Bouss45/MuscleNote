package com.musclenote.android

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.data.model.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.musclenote.android.notifications.NotificationChannels
import com.musclenote.android.ui.ExternalDestination
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.theme.MuscleNoteTheme
import com.musclenote.android.ui.theme.SystemNightMode

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
        // Noms des canaux dans la langue de l'app : l'activité est recréée à chaque changement (§ 7.1),
        // et la notification de la séance en cours est republiée dans cette langue (§ 15.5).
        NotificationChannels.createOrUpdate(this)
        app.container.launchMutation { app.container.refreshWorkoutNotification() }
        // AppCompat connaît la langue choisie une fois l'activité créée : copie pour les
        // notifications construites hors activité avant Android 13 (§ 9.4).
        val languages = AppCompatDelegate.getApplicationLocales()
        app.container.rememberAppLanguage(if (languages.isEmpty) null else languages.toLanguageTags())
        backupViewModel = ViewModelProvider(this, BackupViewModel.Factory(app))[BackupViewModel::class.java]

        // Une recréation (rotation, langue) ou une reprise depuis les récents ne relance ni l'import
        // ni la navigation demandée par une notification.
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) handleIntent(intent)

        setContent {
            val preferences by app.container.preferences.preferences.collectAsStateWithLifecycle()
            val preferencesLoaded by app.container.preferences.loaded.collectAsStateWithLifecycle()
            // Choix Système / Clair / Sombre (🔄 D10), appliqué tout de suite à l'interface.
            val darkTheme = when (preferences.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Puis transmis au système (Android 12+) pour l'écran de démarrage et le fond de
            // fenêtre (§ 9.1) — seulement une fois le vrai choix lu, pas la valeur par défaut.
            LaunchedEffect(preferencesLoaded, preferences.theme) {
                if (preferencesLoaded) SystemNightMode.apply(this@MainActivity, preferences.theme)
            }
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
        keepSplashUntilPreferencesLoaded(app)
    }

    /**
     * Retarde le premier rendu jusqu'à la lecture des préférences (quelques ms), pour ne pas
     * afficher l'interface dans le mauvais thème puis la redessiner. L'écran de démarrage du
     * système reste affiché entre-temps ; au-delà d'une seconde, on dessine quand même.
     */
    private fun keepSplashUntilPreferencesLoaded(app: MuscleNoteApplication) {
        val content = findViewById<View>(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = app.container.preferences.loaded.value || SystemClock.uptimeMillis() - start > SPLASH_TIMEOUT_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
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

        private const val SPLASH_TIMEOUT_MS = 1_000L

        /** Ouvre l'onglet Nutrition (notifications des rappels de compléments, § 7.3). */
        const val ACTION_OPEN_NUTRITION = "com.musclenote.android.action.OPEN_NUTRITION"
    }
}
