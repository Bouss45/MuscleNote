package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.designsystem.theme.StudioTheme
import com.musclenote.android.ui.settings.AppLanguage
import com.musclenote.android.ui.settings.AutoBackupSheetContent
import com.musclenote.android.ui.settings.FeedbackSheetContent
import com.musclenote.android.ui.settings.HealthConnectSheetContent
import com.musclenote.android.ui.settings.LanguageSheetContent
import com.musclenote.android.ui.settings.RestTimerSheetContent
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.settings.ThemeSheetContent
import com.musclenote.android.ui.theme.MuscleNoteTheme

/*
 * Paramètres (SPEC § 15.10.3) : la liste groupée avec les données de PreviewData (sauvegarde
 * automatique activée ce matin) et les formulaires de ses réglages.
 */

/** Paramètres en entier (sans la barre d'onglets). */
@TallScreenPreviews
@Composable
private fun SettingsScreenPreview() = ScreenPreview { container ->
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        SettingsScreen(rememberBackupViewModel(container), onBack = {}, insets = PreviewTabInsets)
    }
}

/** Ce qu'un téléphone montre de Paramètres. */
@PhonePreviews
@Composable
private fun SettingsScreenPhonePreview() = ScreenPreview { container ->
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        SettingsScreen(rememberBackupViewModel(container), onBack = {}, insets = PreviewTabInsets)
    }
}

/** Formulaires des préférences : thème (Sombre par défaut), langue, minuteur de repos. */
@StudioComponentPreviews
@Composable
private fun PreferenceSheetsPreview() = StudioComponentPreview {
    StateCaption("Thème")
    ThemeSheetContent(selected = ThemeMode.Dark, onSelect = {})
    StateCaption("Langue")
    LanguageSheetContent(selected = AppLanguage.System, onSelect = {})
    StateCaption("Minuteur de repos")
    RestTimerSheetContent(selected = 150, onSelect = {})
}

/** Sauvegarde automatique activée, dernière sauvegarde ce matin (PreviewData). */
@StudioComponentPreviews
@Composable
private fun AutoBackupSheetPreview() = ScreenPreview {
    StudioComponentPreview { AutoBackupSheetContent() }
}

/** Health Connect désactivé (la case demande l'autorisation). */
@StudioComponentPreviews
@Composable
private fun HealthConnectSheetPreview() = ScreenPreview {
    StudioComponentPreview { HealthConnectSheetContent() }
}

/** « Ton avis » : brouillon vide, « Envoyer » inactif. */
@StudioComponentPreviews
@Composable
private fun FeedbackSheetPreview() = ScreenPreview {
    StudioComponentPreview { FeedbackSheetContent() }
}
