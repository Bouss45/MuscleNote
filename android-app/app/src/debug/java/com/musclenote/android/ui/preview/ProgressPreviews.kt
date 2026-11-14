package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.progress.ChartPeriod
import com.musclenote.android.data.progress.DayValue
import com.musclenote.android.data.workout.ExerciseSession
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.theme.StudioTheme
import com.musclenote.android.ui.exercise.ExerciseFicheContent
import com.musclenote.android.ui.exercise.ExerciseFicheScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.progress.WeighInForm
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.LocalTime

/*
 * Fiche exercice et onglet Progrès (SPEC § 15.9) : courbe en hausse (données de PreviewData), en
 * baisse et période vide (historiques construits ici), poids du corps, sans séance, pesée.
 */

/** Insets de la maquette (412 × 915 : barre d'état 40, barre de navigation 24). */
private val MockupInsets = ScreenInsets(top = 40.dp, bottom = 24.dp)

private val bench = exerciseDefinitions(emptyList()).first { it.id == "bench" }

/**
 * Historique du développé couché sur [weeks] semaines, deux séances par semaine, la plus récente
 * d'abord ; [load] donne la charge de la séance n (0 = la plus ancienne). [weeksBefore] recule tout.
 */
private fun benchHistory(weeks: Int, weeksBefore: Long = 0, load: (Int) -> Double): List<ExerciseSession> {
    val count = weeks * 2
    return List(count) { n ->
        val day = PreviewData.today.minusWeeks(weeksBefore).minusDays(((count - 1 - n) * 7L) / 2)
        val date = day.atTime(LocalTime.of(18, 30)).atZone(PreviewData.zone).toInstant()
        val weight = load(n)
        ExerciseSession(
            workoutId = "fiche-$n",
            date = date,
            bodyWeight = 78.0,
            sets = List(4) { i -> WorkoutSet("fiche-$n-$i", reps = if (i == 3) 6 else 8, weight = weight, completed = true) },
        )
    }.asReversed()
}

/** Fiche en hausse : le développé couché de PreviewData, période « Tout » (maquette). */
@PhonePreviews
@Composable
private fun ExerciseFicheScreenPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ExerciseFicheScreen(exerciseId = "bench", onBack = {}, onEditWorkout = {}, insets = MockupInsets)
    }
}

/** Fiche en baisse : charge qui recule de 102,5 à 86,25 kg en douze semaines (variation rouge). */
@PhonePreviews
@Composable
private fun ExerciseFicheFallingPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ExerciseFicheContent(
            definition = bench,
            history = benchHistory(weeks = 12) { n -> 100.0 - (n / 2) * 1.25 + if (n % 3 == 0) 2.5 else 0.0 },
            today = PreviewData.today,
            zone = PreviewData.zone,
            onBack = {},
            onEditWorkout = {},
            insets = MockupInsets,
        )
    }
}

/** Période vide : dernière séance il y a trois mois, « 1M » choisi. */
@PhonePreviews
@Composable
private fun ExerciseFicheEmptyPeriodPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ExerciseFicheContent(
            definition = bench,
            history = benchHistory(weeks = 6, weeksBefore = 13) { n -> 80.0 + n * 1.25 },
            today = PreviewData.today,
            zone = PreviewData.zone,
            onBack = {},
            onEditWorkout = {},
            insets = MockupInsets,
            initialPeriod = ChartPeriod.OneMonth,
        )
    }
}

/** Exercice au poids du corps (tractions de PreviewData, toutes à 0 kg) : reps max par séance. */
@PhonePreviews
@Composable
private fun ExerciseFicheBodyweightPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ExerciseFicheScreen(exerciseId = "pullup", onBack = {}, onEditWorkout = {}, insets = MockupInsets)
    }
}

/** Exercice jamais fait : état vide sous le titre. */
@PhonePreviews
@Composable
private fun ExerciseFicheNoSessionPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ExerciseFicheScreen(exerciseId = "dips", onBack = {}, onEditWorkout = {}, insets = MockupInsets)
    }
}

/** Onglet Progrès en entier (sans la barre d'onglets) : poids, exercices, calories et eau, pesées. */
@ScreenPreviews
@Composable
private fun ProgressScreenPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ProgressScreen(onOpenExercise = {}, onStartWorkout = {}, onOpenWorkout = {}, onOpenNutrition = {}, insets = PreviewTabInsets)
    }
}

/** Ce qu'un téléphone montre de l'onglet Progrès. */
@PhonePreviews
@Composable
private fun ProgressScreenPhonePreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ProgressScreen(onOpenExercise = {}, onStartWorkout = {}, onOpenWorkout = {}, onOpenNutrition = {}, insets = PreviewTabInsets)
    }
}

/** Progrès sans aucune donnée : surface sans poids, états vides. */
@PhonePreviews
@Composable
private fun ProgressScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) {
        ProgressScreen(onOpenExercise = {}, onStartWorkout = {}, onOpenWorkout = {}, onOpenNutrition = {}, insets = PreviewTabInsets)
    }
}

/** « Nouvelle pesée » : partie du dernier poids, au pavé ; puis après un poids refusé. */
@StudioComponentPreviews
@Composable
private fun WeighInFormPreview() = StudioComponentPreview {
    val dates = rememberDateFormats(PreviewData.zone)
    val last = DayValue(PreviewData.today.minusDays(4), 77.9)
    StateCaption("Prérempli avec le dernier poids")
    WeighInForm(PreviewData.today, last, dates, onSave = { _, _ -> }, bottomInset = 0.dp)
    StateCaption("Poids refusé")
    WeighInForm(PreviewData.today, null, dates, onSave = { _, _ -> }, bottomInset = 0.dp, initiallyInvalid = true)
}

