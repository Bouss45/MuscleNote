package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.SessionInsights
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.designsystem.components.RestBanner
import com.musclenote.android.ui.workout.EntryTarget
import com.musclenote.android.ui.workout.ExerciseOptionsSheetContent
import com.musclenote.android.ui.workout.LiveExerciseBlock
import com.musclenote.android.ui.workout.SetEntryState
import com.musclenote.android.ui.workout.WarmupSheetContent
import com.musclenote.android.ui.workout.WorkoutKeypad
import java.util.Locale

/*
 * Pièces de l'écran de séance en cours migré au design system (SPEC § 15.7) : bloc d'exercice
 * (« Préc. », suggestion, repos, saisie), pavé, bottom sheets. Données de PreviewData.
 */

private val french: Locale = Locale.FRENCH

private fun current(id: String): WorkoutExercise = PreviewData.currentWorkout.exercises.first { it.exerciseId == id }

/** Développé couché : 3 séries validées, repos après la 3e, 4e en saisie sur la charge, +2,5 kg. */
@StudioComponentPreviews
@Composable
private fun LiveExerciseBlockPreview() = StudioComponentPreview {
    val exercise = current("bench")
    val reference = SessionInsights.reference(PreviewData.workouts, "bench")
    LiveExerciseBlock(
        name = "Développé couché",
        exercise = exercise,
        isCardio = false,
        reference = reference,
        referenceDate = "jeu. 5 nov.",
        suggestion = SessionInsights.suggestion(reference, isCardio = false),
        entry = SetEntryState(EntryTarget("bench", "cur-bench-3", SetEntryField.Weight), "82,", fresh = false),
        locale = french,
        restAnchor = "cur-bench-2",
        rest = { RestBanner("Repos", "1:35", "sur 2:30", 0.37f, "−30 s", "+30 s", "Passer", {}, {}, {}) },
        onOpenDetails = {},
        onMore = {},
        onApplySuggestion = {},
        onFieldClick = { _, _ -> },
        onPreviousClick = { _, _ -> },
        onCheck = {},
        onAddSet = {},
    )
}

/** Tapis de course : colonnes Min · Km, « Préc. » en min · km, pas de suggestion. */
@StudioComponentPreviews
@Composable
private fun LiveExerciseBlockCardioPreview() = StudioComponentPreview {
    val reference = SessionInsights.reference(PreviewData.workouts, "treadmill")
    LiveExerciseBlock(
        name = "Tapis de course",
        exercise = WorkoutExercise(
            "treadmill",
            sets = listOf(
                WorkoutSet("tm-0", 0, 0.0, true, duration = 15.0, distance = 2.5, speed = 10.0, incline = 2.0),
                WorkoutSet("tm-1", 0, 0.0, false, duration = 10.0, distance = 0.0),
            ),
        ),
        isCardio = true,
        reference = reference,
        referenceDate = "mar. 10 nov.",
        suggestion = SessionInsights.suggestion(reference, isCardio = true),
        entry = SetEntryState(),
        locale = french,
        restAnchor = null,
        rest = {},
        onOpenDetails = {},
        onMore = {},
        onApplySuggestion = {},
        onFieldClick = { _, _ -> },
        onPreviousClick = { _, _ -> },
        onCheck = {},
        onAddSet = {},
    )
}

/** Pavé : charge (« Suivant ») puis reps (« Valider la série »). */
@StudioComponentPreviews
@Composable
private fun WorkoutKeypadPreview() = StudioComponentPreview {
    val set = current("bench").sets.last()
    WorkoutKeypad(
        set, 4, isCardio = false, entry = SetEntryState(EntryTarget("bench", set.id, SetEntryField.Weight), "80"),
        field = SetEntryField.Weight, locale = french, bottomInset = 0.dp,
        onSelectField = {}, onDigit = {}, onBackspace = {}, onStep = {}, onAction = {}, onDelete = {}, onHide = {},
    )
    WorkoutKeypad(
        set, 4, isCardio = false, entry = SetEntryState(EntryTarget("bench", set.id, SetEntryField.Reps), "8"),
        field = SetEntryField.Reps, locale = french, bottomInset = 0.dp,
        onSelectField = {}, onDigit = {}, onBackspace = {}, onStep = {}, onAction = {}, onDelete = {}, onHide = {},
    )
}

/** Pavé cardio sur la vitesse : champ héros au-dessus des touches, « Suivant ». */
@StudioComponentPreviews
@Composable
private fun WorkoutKeypadCardioPreview() = StudioComponentPreview {
    val set = WorkoutSet("tm-1", 0, 0.0, false, duration = 30.0, distance = 5.0, speed = 9.5)
    WorkoutKeypad(
        set, 2, isCardio = true, entry = SetEntryState(EntryTarget("treadmill", set.id, SetEntryField.Speed), "9,5"),
        field = SetEntryField.Speed, locale = french, bottomInset = 0.dp,
        onSelectField = {}, onDigit = {}, onBackspace = {}, onStep = {}, onAction = {}, onDelete = {}, onHide = {},
    )
}

/** Bottom sheets : échauffement (2 sur 5) et options d'un exercice. */
@StudioComponentPreviews
@Composable
private fun WorkoutSheetsPreview() = StudioComponentPreview {
    WarmupSheetContent(done = 0b00011, onToggle = {}, onClose = {}, bottomInset = 0.dp)
    ExerciseOptionsSheetContent("Développé couché", "Prise un peu plus serrée", {}, {}, {}, bottomInset = 0.dp)
}
