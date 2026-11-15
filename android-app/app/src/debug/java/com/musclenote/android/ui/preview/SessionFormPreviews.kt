package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.workout.CustomExerciseActions
import com.musclenote.android.ui.workout.EditBodyWeightContent
import com.musclenote.android.ui.workout.ExercisePickerContent
import com.musclenote.android.ui.workout.RenameSheetContent
import com.musclenote.android.ui.workout.StartStage
import com.musclenote.android.ui.workout.StartWorkoutForm

/*
 * Formulaires des séances (SPEC § 15.11) : « Démarrer une séance » (type, puis poids de corps),
 * sélecteur d'exercices, renommage et poids de corps de l'éditeur. Données de PreviewData.
 */

/** « Démarrer une séance », étape « Type de séance » : rien de choisi, « Suivant » inactif. */
@PhonePreviews
@Composable
private fun StartWorkoutTypePreview() = ScreenPreview {
    StartWorkoutForm(initialPreset = null, initialName = null, onStarted = {}, bottomInset = 24.dp)
}

/** Étape « Poids de corps », ouverte depuis l'Accueil avec Legs : dernier poids prérempli. */
@PhonePreviews
@Composable
private fun StartWorkoutWeightPreview() = ScreenPreview {
    StartWorkoutForm(initialPreset = PresetId.Legs, initialName = null, onStarted = {}, bottomInset = 24.dp, initialStage = StartStage.Weight)
}

/** Sélecteur de la séance en cours : développé couché et rowing déjà ajoutés, « + » des exercices perso. */
@PhonePreviews
@Composable
private fun ExercisePickerPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    val definitions = remember { exerciseDefinitions(PreviewData.customExercises).filterNot { it.archived } }
    ExercisePickerContent(
        title = "Ajouter un exercice",
        definitions = definitions,
        addedIds = PreviewData.currentWorkout.exercises.mapTo(HashSet()) { it.exerciseId },
        onAdd = {},
        custom = CustomExerciseActions(create = { _, _ -> }, update = { _, _, _ -> }, delete = {}),
        bottomInset = 24.dp,
    )
}

/** Sélecteur filtré par la recherche « dév » : sans accent ni casse. */
@PhonePreviews
@Composable
private fun ExercisePickerSearchPreview() = ScreenPreview {
    val definitions = remember { exerciseDefinitions(PreviewData.customExercises).filterNot { it.archived } }
    ExercisePickerContent(
        title = "Ajouter un exercice",
        definitions = definitions,
        addedIds = emptySet(),
        onAdd = {},
        custom = null,
        bottomInset = 24.dp,
        initialSearch = "dev",
    )
}

/** Renommer une séance de l'éditeur : Push choisi. */
@StudioComponentPreviews
@Composable
private fun RenameSheetPreview() = StudioComponentPreview {
    RenameSheetContent(preset = PresetId.Push, name = "Push", onConfirm = { _, _ -> }, bottomInset = 0.dp)
}

/** Poids de corps d'une séance de l'éditeur, au pavé. */
@StudioComponentPreviews
@Composable
private fun EditBodyWeightPreview() = StudioComponentPreview {
    EditBodyWeightContent(initial = 78.4, help = "Push", onSave = {}, bottomInset = 0.dp)
}
