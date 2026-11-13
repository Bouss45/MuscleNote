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
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.home.UpdateBanner
import com.musclenote.android.ui.nutrition.CaloriesCard
import com.musclenote.android.ui.nutrition.SupplementCard
import com.musclenote.android.ui.nutrition.WaterCard
import com.musclenote.android.ui.progress.BodyWeightTab
import com.musclenote.android.ui.progress.DailyKind
import com.musclenote.android.ui.progress.DailyTab
import com.musclenote.android.ui.progress.MuscleGroupTab
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

// ─── Progrès (corps des onglets, sous les puces d'onglet) ─────────────────────────

/** Corps d'un onglet de Progrès : marges et espacement de l'écran. */
@Composable
private fun ProgressTabBody(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MuscleNoteTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@ScreenPreviews
@Composable
private fun ProgressCaloriesTabPreview() = ScreenPreview {
    ProgressTabBody {
        DailyTab(DailyKind.Calories, PreviewData.tracker, PreviewData.today, rememberDateFormats(PreviewData.zone))
    }
}

@ScreenPreviews
@Composable
private fun ProgressWaterTabPreview() = ScreenPreview {
    ProgressTabBody {
        DailyTab(DailyKind.Water, PreviewData.tracker, PreviewData.today, rememberDateFormats(PreviewData.zone))
    }
}

@ScreenPreviews
@Composable
private fun ProgressMuscleGroupTabPreview() = ScreenPreview {
    ProgressTabBody {
        MuscleGroupTab(
            group = MuscleGroup.Pecs,
            workouts = PreviewData.workouts,
            definitions = definitions.values.toList(),
            zone = PreviewData.zone,
            dates = rememberDateFormats(PreviewData.zone),
            onOpenHistory = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun ProgressBodyWeightTabPreview() = ScreenPreview {
    ProgressTabBody {
        BodyWeightTab(
            workouts = PreviewData.workouts,
            bodyWeights = PreviewData.bodyWeights,
            zone = PreviewData.zone,
            dates = rememberDateFormats(PreviewData.zone),
            onAddWeighIn = {},
            onRemoveWeighIn = {},
        )
    }
}

// ─── Accueil et Paramètres ───────────────────────────────────────────────────────

/** Annonce d'une mise à jour (app installée sans Obtainium). */
@ComponentPreviews
@Composable
private fun UpdateBannerPreview() = ComponentPreview {
    UpdateBanner(
        release = LatestRelease("1.0.6", "https://github.com/Bouss45/MuscleNote/releases/tag/v1.0.6", null),
        installedVersion = "1.0.5",
        onDownload = {},
        onDismiss = {},
    )
}

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
