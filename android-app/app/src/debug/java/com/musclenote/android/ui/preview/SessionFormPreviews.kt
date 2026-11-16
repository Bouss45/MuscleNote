package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.catalog.MuscleSelection
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.workout.CustomExerciseActions
import com.musclenote.android.ui.workout.CustomExerciseForm
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

/**
 * Étape « Poids de corps », ouverte depuis l'Accueil avec Legs (§ 15.13.2) : champ vide, la dernière
 * pesée et son ancienneté en aide, « Passer ».
 */
@PhonePreviews
@Composable
private fun StartWorkoutWeightPreview() = ScreenPreview {
    StartWorkoutForm(initialPreset = PresetId.Legs, initialName = null, onStarted = {}, bottomInset = 24.dp, initialStage = StartStage.Weight)
}

/** Sans aucune pesée : « Ton poids aujourd'hui ? Facultatif. ». */
@PhonePreviews
@Composable
private fun StartWorkoutWeightFirstPreview() = ScreenPreview(PreviewDataSet.Empty) {
    StartWorkoutForm(initialPreset = PresetId.Push, initialName = null, onStarted = {}, bottomInset = 24.dp, initialStage = StartStage.Weight)
}

/** Déjà pesé aujourd'hui (§ 15.13.2) : pas de champ, le poids du jour en aide, « Commencer ». */
@PhonePreviews
@Composable
private fun StartWorkoutWeighedTodayPreview() = ScreenPreview(PreviewDataSet.WeighedToday) {
    StartWorkoutForm(initialPreset = PresetId.Legs, initialName = null, onStarted = {}, bottomInset = 24.dp, initialStage = StartStage.Weight)
}

/** Étape du type, déjà pesé aujourd'hui : « Suivant » devient « ✓ Commencer ». */
@PhonePreviews
@Composable
private fun StartWorkoutTypeWeighedTodayPreview() = ScreenPreview(PreviewDataSet.WeighedToday) {
    StartWorkoutForm(initialPreset = PresetId.Pull, initialName = null, onStarted = {}, bottomInset = 24.dp, initialStage = StartStage.Type)
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
        custom = CustomExerciseActions(create = { _, _, _ -> }, update = { _, _, _, _ -> }, delete = {}),
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

/** Sélecteur « Par muscle » (🔄 D43) : Pecs et Triceps choisis, la liste filtrée, principaux d'abord. */
@PhonePreviews
@Composable
private fun ExercisePickerByMusclePreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    val definitions = remember { exerciseDefinitions(PreviewData.customExercises).filterNot { it.archived } }
    ExercisePickerContent(
        title = "Ajouter un exercice",
        definitions = definitions,
        addedIds = PreviewData.currentWorkout.exercises.mapTo(HashSet()) { it.exerciseId },
        onAdd = {},
        custom = null,
        bottomInset = 24.dp,
        initialByMuscle = true,
        initialMuscles = setOf(Muscle.Pectoraux, Muscle.Triceps),
    )
}

/** « Par muscle » sans muscle choisi : la carte au repos, l'aide, toute la liste avec ses muscles. */
@PhonePreviews
@Composable
private fun ExercisePickerByMuscleEmptyPreview() = ScreenPreview {
    val definitions = remember { exerciseDefinitions(PreviewData.customExercises).filterNot { it.archived } }
    ExercisePickerContent(
        title = "Ajouter un exercice",
        definitions = definitions,
        addedIds = emptySet(),
        onAdd = {},
        custom = null,
        bottomInset = 24.dp,
        initialByMuscle = true,
    )
}

/** Sheet « Muscle » depuis le filtre : Pecs, lignes à cocher du sélecteur, « Retirer du filtre ». */
@PhonePreviews
@Composable
private fun ExercisePickerMuscleSheetPreview() = ScreenPreview(PreviewDataSet.WithCurrentWorkout) {
    val definitions = remember { exerciseDefinitions(PreviewData.customExercises).filterNot { it.archived } }
    ExercisePickerContent(
        title = "Ajouter un exercice",
        definitions = definitions,
        addedIds = PreviewData.currentWorkout.exercises.mapTo(HashSet()) { it.exerciseId },
        onAdd = {},
        custom = null,
        bottomInset = 24.dp,
        initialByMuscle = true,
        initialMuscles = setOf(Muscle.Pectoraux),
        initialMuscleSheet = Muscle.Pectoraux,
    )
}

/** « Nouvel exercice » avec ses muscles : Fessiers principal, Ischio-jambiers secondaire. */
@StudioComponentPreviews
@Composable
private fun CustomExerciseFormPreview() = StudioComponentPreview {
    CustomExerciseForm(
        title = "Nouvel exercice",
        name = "Hip thrust",
        onNameChange = {},
        group = MuscleGroup.Jambes,
        onGroupChange = {},
        muscles = MuscleSelection(primary = listOf(Muscle.Fessiers), secondary = listOf(Muscle.IschioJambiers)),
        onMusclesChange = {},
        submitLabel = "Ajouter",
        onSubmit = {},
        onCancel = {},
        bottomInset = 0.dp,
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
