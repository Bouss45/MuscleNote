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
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.ui.theme.MuscleNoteTheme
import com.musclenote.android.ui.workout.SetRow

/* Composants des écrans (éditeur de séance), avec PreviewData. */

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
