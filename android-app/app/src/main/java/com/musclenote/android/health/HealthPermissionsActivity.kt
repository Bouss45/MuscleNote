package com.musclenote.android.health

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.R
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.templates.FicheLazyTemplate
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.designsystem.theme.StudioTheme

/**
 * Politique de confidentialité exigée par Health Connect (SPEC § 15.3) : ouverte depuis son écran
 * d'autorisation et ses réglages (Android 14+ : alias `VIEW_PERMISSION_USAGE` +
 * `HEALTH_PERMISSIONS` ; avant : `ACTION_SHOW_PERMISSIONS_RATIONALE`). Sans elle, Health Connect
 * refuse la demande d'autorisation sans rien afficher. Thème et langue de l'app ; gabarit fiche.
 */
class HealthPermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MuscleNoteApplication).container
        setContent {
            val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Icônes des barres système claires en thème sombre, foncées en thème clair (§ 9.1).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            StudioTheme(darkTheme = darkTheme) {
                HealthPrivacyScreen(onBack = ::finish)
            }
        }
    }
}

/** Les quatre paragraphes de la politique, sous le titre ; ← ferme l'écran. */
@Composable
internal fun HealthPrivacyScreen(onBack: () -> Unit) {
    FicheLazyTemplate(
        topBar = { FicheTopBar(onBack) },
        titleBlock = { FicheTitle(stringResource(R.string.health_privacy_title)) },
    ) {
        block("policy") {
            Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.m)) {
                for (text in PARAGRAPHS) Paragraph(text)
            }
        }
    }
}

private val PARAGRAPHS = listOf(
    R.string.health_privacy_written,
    R.string.health_privacy_when,
    R.string.health_privacy_nothing_else,
    R.string.health_privacy_stop,
)

@Composable
private fun Paragraph(@StringRes text: Int) {
    Text(stringResource(text), style = Studio.typography.paragraph, color = Studio.colors.textPrimary)
}
