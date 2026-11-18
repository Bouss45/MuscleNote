package com.musclenote.android.ui.preview

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.musclenote.android.data.AppContainer
import com.musclenote.android.ui.MuscleNoteApp
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.journal.JournalScreen
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.ui.workout.EditWorkoutScreen
import com.musclenote.android.ui.workout.EntryTarget
import com.musclenote.android.ui.workout.WorkoutScreen
import kotlinx.coroutines.flow.emptyFlow

/*
 * Écrans principaux, avec les données d'exemple de PreviewData (horloge fixe : jeudi 12 novembre
 * 2026, 18 h 30). Les onglets sont rendus sans la barre de navigation, montrée par AppPreview.
 */

/** Modèle de vue de la sauvegarde, sur le conteneur de l'aperçu (Paramètres, application). */
@Composable
internal fun rememberBackupViewModel(container: AppContainer): BackupViewModel {
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
    PreviewHomeScreen()
}

/** Accueil pendant une séance : surface « En cours » avec le chrono et le repos. */
@PhonePreviews
@Composable
private fun HomeScreenLivePreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    PreviewHomeScreen()
}

/** Première ouverture : aucune séance, « Nouvelle séance » et transfert depuis l'ancienne app. */
@PhonePreviews
@Composable
private fun HomeScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    PreviewHomeScreen()
}

@Composable
private fun PreviewHomeScreen() = HomeScreen(
    onOpenSettings = {},
    onStartWorkout = { _, _ -> },
    onOpenWorkout = {},
    onOpenSession = {},
    onEditWorkout = {},
    onOpenJournal = {},
    insets = PreviewTabInsets,
)

/** Journal (§ 15.8.4) : totaux, puis six semaines de séances, toute la liste. */
@ScreenPreviews
@Composable
private fun JournalScreenPreview() = ScreenPreview {
    JournalScreen(onOpenSession = {}, onEditWorkout = {}, onStartWorkout = {}, onOpenWorkout = {}, insets = PreviewTabInsets)
}

/** Journal sans séance terminée : état vide. */
@PhonePreviews
@Composable
private fun JournalScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    JournalScreen(onOpenSession = {}, onEditWorkout = {}, onStartWorkout = {}, onOpenWorkout = {}, insets = PreviewTabInsets)
}

/** Écran d'onglet sans la barre d'onglets : l'inset de la barre d'état de la maquette seul. */
internal val PreviewTabInsets = ScreenInsets(top = 40.dp, bottom = 0.dp)

/** Séance en cours depuis 42 min, repos de 2 min 30 en cours : toute la liste, barre « Terminer ». */
@ScreenPreviews
@Composable
private fun WorkoutScreenPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    WorkoutScreen(onBack = {}, onOpenHistory = {}, onFinished = {})
}

/**
 * 🔄 D46, D47 (§ 15.20.2, § 15.21.2) : série de chauffe « C » du développé couché, développé incliné lié
 * aux élévations latérales (trait « Supersérie »), repos après le premier tour.
 */
@ScreenPreviews
@Composable
private fun WorkoutScreenSupersetPreview() = ScreenPreview(PreviewDataSet.WithSuperset) {
    WorkoutScreen(onBack = {}, onOpenHistory = {}, onFinished = {})
}

/**
 * Ce qu'un téléphone montre, comme l'écran « en direct » de la maquette : repos en cours, pavé
 * ouvert sur la charge de la 4e série du développé couché.
 */
@PhonePreviews
@Composable
private fun WorkoutScreenKeypadPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    WorkoutScreen(
        onBack = {},
        onOpenHistory = {},
        onFinished = {},
        initialEntry = EntryTarget("bench", "cur-bench-3", SetEntryField.Weight),
    )
}

/** Éditeur d'une séance terminée (§ 15.11.3) : type, poids de corps, exercices et séries, « Enregistrer ». */
@ScreenPreviews
@Composable
private fun EditWorkoutScreenPreview() = ScreenPreview {
    EditWorkoutScreen(workoutId = PreviewData.workouts.first { it.name == "Push" }.id, onClose = {}, insets = PreviewFicheInsets)
}

/** Éditeur, pavé ouvert sur la charge de la 2e série du premier exercice. */
@PhonePreviews
@Composable
private fun EditWorkoutScreenKeypadPreview() = ScreenPreview {
    val workout = PreviewData.workouts.first { it.name == "Push" }
    val exercise = workout.exercises.first()
    EditWorkoutScreen(
        workoutId = workout.id,
        onClose = {},
        insets = PreviewFicheInsets,
        initialEntry = EntryTarget(exercise.exerciseId, exercise.sets[1].id, SetEntryField.Weight),
    )
}

/** Écran plein (fiche) : insets de la maquette, 40 en haut et 24 en bas. */
internal val PreviewFicheInsets = ScreenInsets(top = 40.dp, bottom = 24.dp)
