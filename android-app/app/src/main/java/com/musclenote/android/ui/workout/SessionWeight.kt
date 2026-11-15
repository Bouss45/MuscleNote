package com.musclenote.android.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.ui.LocalAppContainer

/**
 * Poids affiché d'une séance (SPEC § 15.13.1, 🔄 D38) : le sien, sinon la dernière pesée connue au jour
 * de son début (pesées et séances terminées), sinon `null` — rien n'est affiché, jamais 0.
 */
@Composable
fun rememberSessionWeight(workout: Workout): Double? {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val weighIns by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    return remember(workout.bodyWeight, workout.date, workouts, weighIns, zone) {
        BodyWeightRules.sessionWeight(workout, workouts, weighIns, zone)
    }
}
