package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WeighInReminder
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.settings.AppLanguage
import com.musclenote.android.ui.settings.AutoBackupSheetContent
import com.musclenote.android.ui.settings.FeedbackSheetContent
import com.musclenote.android.ui.settings.HealthConnectSheetContent
import com.musclenote.android.ui.settings.LanguageSheetContent
import com.musclenote.android.ui.settings.RestTimerSheetContent
import com.musclenote.android.ui.settings.SupersetRestSheetContent
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.settings.ThemeSheetContent
import com.musclenote.android.ui.settings.WeighInReminderSheetContent

/*
 * Paramètres (SPEC § 15.10.3) : la liste groupée avec les données de PreviewData (sauvegarde
 * automatique activée ce matin) et les formulaires de ses réglages.
 */

/** Paramètres en entier (sans la barre d'onglets). */
@TallScreenPreviews
@Composable
private fun SettingsScreenPreview() = ScreenPreview { container ->
    SettingsScreen(rememberBackupViewModel(container), onBack = {}, insets = PreviewTabInsets)
}

/** Ce qu'un téléphone montre de Paramètres. */
@PhonePreviews
@Composable
private fun SettingsScreenPhonePreview() = ScreenPreview { container ->
    SettingsScreen(rememberBackupViewModel(container), onBack = {}, insets = PreviewTabInsets)
}

/** Rappel de pesée (§ 15.13.3) : activé, 07:30, en semaine ; puis désactivé, réglé par défaut. */
@StudioComponentPreviews
@Composable
private fun WeighInReminderSheetPreview() = StudioComponentPreview {
    WeighInReminderSheetContent(reminder = PreviewData.weighInReminder, onToggle = {}, onTime = { _, _ -> }, onToggleDay = {}, onClose = {})
    StateCaption("Désactivé (réglage par défaut)")
    WeighInReminderSheetContent(reminder = WeighInReminder.DEFAULT, onToggle = {}, onTime = { _, _ -> }, onToggleDay = {}, onClose = {})
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

/** 🔄 D46 (§ 15.20.4) : repos dans une supersérie, 0 s par défaut, puis 15 s choisies. */
@StudioComponentPreviews
@Composable
private fun SupersetRestSheetPreview() = StudioComponentPreview {
    SupersetRestSheetContent(selected = 0, onSelect = {})
    StateCaption("15 s")
    SupersetRestSheetContent(selected = 15, onSelect = {})
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
