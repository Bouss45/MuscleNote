package com.musclenote.android.ui.preview

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.musclenote.android.data.AppContainer
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.history.ExerciseHistoryScreen
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.workout.EditWorkoutScreen
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

@ScreenPreviews
@Composable
private fun HomeScreenPreview() = ScreenPreview {
    HomeScreen(onStartWorkout = {}, onOpenWorkout = {}, onEditWorkout = {})
}

/** Première ouverture : aucune séance, carte de transfert depuis l'ancienne app. */
@PhonePreviews
@Composable
private fun HomeScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    HomeScreen(onStartWorkout = {}, onOpenWorkout = {}, onEditWorkout = {})
}

/** Séance en cours depuis 42 min, repos de 2 min 30 en cours. */
@ScreenPreviews
@Composable
private fun WorkoutScreenPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    WorkoutScreen(onBack = {}, onAddExercise = {}, onOpenHistory = {}, onFinished = {})
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
