package com.musclenote.android.ui.preview

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.musclenote.android.data.AppContainer
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.history.ExerciseHistoryScreen
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.journal.JournalScreen
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.ui.designsystem.theme.StudioTheme
import com.musclenote.android.ui.theme.MuscleNoteTheme
import com.musclenote.android.ui.workout.EditWorkoutScreen
import com.musclenote.android.ui.workout.EntryTarget
import com.musclenote.android.ui.workout.ExercisePickerScreen
import com.musclenote.android.ui.workout.StartWorkoutScreen
import com.musclenote.android.ui.workout.WorkoutScreen
import kotlinx.coroutines.flow.emptyFlow

/*
 * Écrans principaux, avec les données d'exemple de PreviewData (horloge fixe : jeudi 12 novembre
 * 2026, 18 h 30). Les onglets sont rendus sans la barre de navigation, montrée par AppPreview.
 */

/** Modèle de vue de la sauvegarde, sur le conteneur de l'aperçu (Paramètres, application). */
@Composable
private fun rememberBackupViewModel(container: AppContainer): BackupViewModel {
    val application = LocalContext.current.applicationContext as Application
    return remember(container) { BackupViewModel(application, container) }
}

/** L'app entière, sur l'Accueil : barre de navigation comprise, à la taille d'un téléphone. */
@PhonePreviews
@Composable
private fun AppPreview() = ScreenPreview { container ->
    MuscleNoteApp(rememberBackupViewModel(container), emptyFlow())
}

/** Accueil au gabarit hub (§ 15.8.2) : prochaine séance suggérée, dernière séance, cette semaine. */
@PhonePreviews
@Composable
private fun HomeScreenPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { PreviewHomeScreen() }
}

/** Accueil pendant une séance : surface « En cours » avec le chrono et le repos. */
@PhonePreviews
@Composable
private fun HomeScreenLivePreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { PreviewHomeScreen() }
}

/** Première ouverture : aucune séance, « Nouvelle séance » et transfert depuis l'ancienne app. */
@PhonePreviews
@Composable
private fun HomeScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { PreviewHomeScreen() }
}

@Composable
private fun PreviewHomeScreen() = HomeScreen(
    onOpenSettings = {},
    onStartWorkout = { _, _ -> },
    onOpenWorkout = {},
    onEditWorkout = {},
    onOpenJournal = {},
    insets = PreviewTabInsets,
)

/** Journal (§ 15.8.4) : totaux, puis six semaines de séances, toute la liste. */
@ScreenPreviews
@Composable
private fun JournalScreenPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        JournalScreen(onEditWorkout = {}, onStartWorkout = {}, onOpenWorkout = {}, insets = PreviewTabInsets)
    }
}

/** Journal sans séance terminée : état vide. */
@PhonePreviews
@Composable
private fun JournalScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        JournalScreen(onEditWorkout = {}, onStartWorkout = {}, onOpenWorkout = {}, insets = PreviewTabInsets)
    }
}

/** Écran d'onglet sans la barre d'onglets : l'inset de la barre d'état de la maquette seul. */
private val PreviewTabInsets = ScreenInsets(top = 40.dp, bottom = 0.dp)

/** Séance en cours depuis 42 min, repos de 2 min 30 en cours : toute la liste, barre « Terminer ». */
@ScreenPreviews
@Composable
private fun WorkoutScreenPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        WorkoutScreen(onBack = {}, onAddExercise = {}, onOpenHistory = {}, onFinished = {})
    }
}

/**
 * Ce qu'un téléphone montre, comme l'écran « en direct » de la maquette : repos en cours, pavé
 * ouvert sur la charge de la 4e série du développé couché.
 */
@PhonePreviews
@Composable
private fun WorkoutScreenKeypadPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        WorkoutScreen(
            onBack = {},
            onAddExercise = {},
            onOpenHistory = {},
            onFinished = {},
            initialEntry = EntryTarget("bench", "cur-bench-3", SetEntryField.Weight),
        )
    }
}

@PhonePreviews
@Composable
private fun StartWorkoutScreenPreview() = ScreenPreview {
    StartWorkoutScreen(onClose = {}, onStarted = {})
}

@PhonePreviews
@Composable
private fun ExercisePickerScreenPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    ExercisePickerScreen(onClose = {})
}

@ScreenPreviews
@Composable
private fun ExerciseHistoryScreenPreview() = ScreenPreview {
    ExerciseHistoryScreen(exerciseId = "bench", onBack = {})
}

@ScreenPreviews
@Composable
private fun EditWorkoutScreenPreview() = ScreenPreview {
    EditWorkoutScreen(workoutId = PreviewData.workouts.first { it.name == "Push" }.id, onClose = {})
}

@ScreenPreviews
@Composable
private fun ProgressScreenPreview() = ScreenPreview {
    ProgressScreen(onOpenExerciseHistory = {})
}

@ScreenPreviews
@Composable
private fun NutritionScreenPreview() = ScreenPreview {
    NutritionScreen()
}

@TallScreenPreviews
@Composable
private fun SettingsScreenPreview() = ScreenPreview { container ->
    SettingsScreen(rememberBackupViewModel(container))
}
