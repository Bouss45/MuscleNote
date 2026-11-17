package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.workout.WorkoutSummaryRules
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.summary.WorkoutSummaryContent
import com.musclenote.android.ui.summary.WorkoutSummaryScreen

/*
 * Résumé de fin de séance (SPEC § 15.12) : à la fin d'une séance (« Terminer » seul), consulté depuis
 * le Journal (← et ⋮), première séance d'un programme (ni variation ni record), volume en baisse.
 * Données de PreviewData : six semaines de Push / Pull / Legs aux charges qui montent.
 */

/** Insets de la maquette (412 × 915 : barre d'état 40, barre de navigation 24). */
private val MockupInsets = ScreenInsets(top = 40.dp, bottom = 24.dp)

private val definitions = exerciseDefinitions(PreviewData.customExercises)

/** Juste après « Terminer la séance » : la dernière séance, « Terminer » en bas, rien en haut. */
@PhonePreviews
@Composable
private fun WorkoutSummaryScreenPreview() = ScreenPreview {
    WorkoutSummaryScreen(
        workoutId = PreviewData.workouts.first().id,
        finished = true,
        onDone = {},
        onBack = {},
        onEditWorkout = {},
        insets = MockupInsets,
    )
}

/** Ouvert depuis le Journal : une séance d'il y a deux semaines, ← et ⋮, sans barre d'action. */
@PhonePreviews
@Composable
private fun WorkoutSummaryFromJournalPreview() = ScreenPreview {
    WorkoutSummaryScreen(
        workoutId = PreviewData.workouts[6].id,
        finished = false,
        onDone = {},
        onBack = {},
        onEditWorkout = {},
        insets = MockupInsets,
    )
}

/** Première séance de ce programme : volume sans variation, aucun record (rien à battre). */
@PhonePreviews
@Composable
private fun WorkoutSummaryFirstSessionPreview() = ScreenPreview {
    val first = PreviewData.workouts.last()
    WorkoutSummaryContent(
        summary = WorkoutSummaryRules.summary(listOf(first), first.id, definitions)!!,
        finished = true,
        zone = PreviewData.zone,
        today = PreviewData.today,
        onDone = {},
        onBack = {},
        onOptions = {},
        insets = MockupInsets,
    )
}

/**
 * Séance avec des exercices au temps (§ 15.18.7) : planche et chaise contre le mur puis la dernière
 * séance — volume sans eux, « 3:45 sous tension », record de durée sur la planche.
 */
@PhonePreviews
@Composable
private fun WorkoutSummaryTimedPreview() = ScreenPreview {
    val last = PreviewData.workouts.first()
    fun hold(id: String, seconds: Int, weight: Double = 0.0) = WorkoutSet(id, 0, weight, true, durationSeconds = seconds)
    // Planche et chaise en tête, pour qu'elles se voient au-dessus de « Terminer ».
    val withHolds = last.copy(
        exercises = listOf(
            WorkoutExercise("plank", sets = listOf(hold("sum-p1", 75), hold("sum-p2", 60, 10.0))),
            WorkoutExercise("wallsit", sets = listOf(hold("sum-w1", 90))),
        ) + last.exercises,
    )
    val older = PreviewData.workouts[1].copy(
        exercises = PreviewData.workouts[1].exercises + WorkoutExercise("plank", sets = listOf(hold("old-p1", 70))),
    )
    val workouts = listOf(withHolds, older) + PreviewData.workouts.drop(2)
    WorkoutSummaryContent(
        summary = WorkoutSummaryRules.summary(workouts, withHolds.id, definitions)!!,
        finished = true,
        zone = PreviewData.zone,
        today = PreviewData.today,
        onDone = {},
        onBack = {},
        onOptions = {},
        insets = MockupInsets,
    )
}

/** Volume en baisse (deux séries de moins sur chaque exercice) : variation rouge, pas de record. */
@PhonePreviews
@Composable
private fun WorkoutSummaryDeclinePreview() = ScreenPreview {
    val last = PreviewData.workouts.first()
    val lighter = last.copy(exercises = last.exercises.map { e -> e.copy(sets = e.sets.take((e.sets.size - 2).coerceAtLeast(1))) })
    val workouts = listOf(lighter) + PreviewData.workouts.drop(1)
    WorkoutSummaryContent(
        summary = WorkoutSummaryRules.summary(workouts, lighter.id, definitions)!!,
        finished = true,
        zone = PreviewData.zone,
        today = PreviewData.today,
        onDone = {},
        onBack = {},
        onOptions = {},
        insets = MockupInsets,
    )
}
