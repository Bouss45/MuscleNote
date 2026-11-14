package com.musclenote.android.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.ui.nutrition.CaloriesCard
import com.musclenote.android.ui.nutrition.SupplementCard
import com.musclenote.android.ui.nutrition.WaterCard
import com.musclenote.android.ui.settings.MyDataCard
import com.musclenote.android.ui.settings.StatusBox
import com.musclenote.android.ui.settings.ThemeCard
import com.musclenote.android.ui.theme.MuscleNoteTheme
import com.musclenote.android.ui.workout.SetRow

/* Composants des écrans (séance, nutrition, progrès, accueil, paramètres), avec PreviewData. */

private val definitions: Map<String, ExerciseDefinition> =
    exerciseDefinitions(PreviewData.customExercises).associateBy { it.id }

// ─── Séance ──────────────────────────────────────────────────────────────────────

/** Lignes de série de l'éditeur de séance terminée (§ 2.6) : validée, à faire, cardio. */
@ComponentPreviews
@Composable
private fun SetRowPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetRow(WorkoutSet("a", reps = 8, weight = 80.0, completed = true), index = 0, isCardio = false, onToggle = {}, onUpdate = {}, onRemove = {})
        SetRow(WorkoutSet("b", reps = 8, weight = 82.5, completed = false), index = 1, isCardio = false, onToggle = {}, onUpdate = {}, onRemove = {})
        SetRow(
            WorkoutSet("c", reps = 0, weight = 0.0, completed = false, duration = 20.0, distance = 3.2, speed = 9.5, incline = 2.0),
            index = 0,
            isCardio = true,
            onToggle = {},
            onUpdate = {},
            onRemove = {},
        )
    }
}

// ─── Nutrition ───────────────────────────────────────────────────────────────────

/** Suivi du jour : calories sous l'objectif, eau à 60 %. */
@ComponentPreviews
@Composable
private fun DailyTrackingCardsPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CaloriesCard(kcal = 1850, goal = 2600, onAdd = {}, onSet = {}, onReset = {}, onGoalChange = {})
        WaterCard(ml = 1500, goalMl = 2500, onAdd = {}, onReset = {}, onGoalChange = {})
    }
}

/** Compléments : pris (replié), rappels dépliés, confirmation de suppression. */
@ComponentPreviews
@Composable
private fun SupplementCardPreview() = ComponentPreview {
    val (creatine, whey, vitaminD) = PreviewData.supplements
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SupplementCard(creatine, taken = true, expanded = false, deleting = false, {}, {}, {}, {}, {}, {}, {}, {}, {})
        SupplementCard(vitaminD, taken = false, expanded = true, deleting = false, {}, {}, {}, {}, {}, {}, {}, {}, {})
        SupplementCard(whey, taken = false, expanded = false, deleting = true, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

// ─── Paramètres ───────────────────────────────────────────────────────

/** Cartes de Paramètres et encarts de résultat. */
@ComponentPreviews
@Composable
private fun SettingsCardsPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MyDataCard(sessions = PreviewData.workouts.size, customExercises = 1)
        ThemeCard(selected = ThemeMode.System, onSelect = {})
        StatusBox(success = true, message = stringResource(R.string.settings_import_success))
        StatusBox(success = false, message = stringResource(R.string.settings_auto_backup_folder_lost))
    }
}
