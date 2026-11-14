package com.musclenote.android.ui.navigation

import android.net.Uri
import com.musclenote.android.data.model.PresetId

/**
 * Écrans hors onglets (SPEC § 15.8.1) : Paramètres (avec la barre d'onglets, l'Accueil
 * sélectionné) et les écrans plein écran, où la barre d'onglets est masquée.
 */
object Routes {
    const val SETTINGS = "settings"

    const val ARG_PRESET = "preset"
    const val ARG_NAME = "name"

    /** « Démarrer une séance » ; avec un type (préréglage ou nom), directement au poids de corps. */
    const val START_WORKOUT = "start-workout?$ARG_PRESET={$ARG_PRESET}&$ARG_NAME={$ARG_NAME}"
    const val WORKOUT = "workout"
    const val EXERCISE_PICKER = "exercise-picker"

    const val ARG_EXERCISE_ID = "exerciseId"
    const val EXERCISE_HISTORY = "exercise-history/{$ARG_EXERCISE_ID}"

    const val ARG_WORKOUT_ID = "workoutId"
    const val EDIT_WORKOUT = "edit-workout/{$ARG_WORKOUT_ID}"

    /** « Démarrer une séance » à l'étape 1, ou à l'étape du poids avec [preset] ou [name]. */
    fun startWorkout(preset: PresetId? = null, name: String? = null): String = buildString {
        append("start-workout")
        when {
            preset != null -> append("?$ARG_PRESET=${Uri.encode(preset.id)}")
            !name.isNullOrBlank() -> append("?$ARG_NAME=${Uri.encode(name)}")
        }
    }

    // Les ids sont des chaînes opaques (§ 3.15) : encodées pour tenir dans le chemin.
    fun exerciseHistory(exerciseId: String) = "exercise-history/${Uri.encode(exerciseId)}"

    fun editWorkout(workoutId: String) = "edit-workout/${Uri.encode(workoutId)}"
}
