package com.musclenote.android.ui.navigation

import android.net.Uri

/**
 * Écrans hors onglets (SPEC § 15.8.1) : Paramètres (avec la barre d'onglets, l'Accueil
 * sélectionné) et les écrans plein écran, où la barre d'onglets est masquée. « Démarrer une
 * séance » et le sélecteur d'exercices sont des bottom sheets, sans route (§ 15.11).
 */
object Routes {
    const val SETTINGS = "settings"

    const val WORKOUT = "workout"

    const val ARG_EXERCISE_ID = "exerciseId"
    const val EXERCISE_HISTORY = "exercise-history/{$ARG_EXERCISE_ID}"

    const val ARG_WORKOUT_ID = "workoutId"
    const val EDIT_WORKOUT = "edit-workout/{$ARG_WORKOUT_ID}"

    // Les ids sont des chaînes opaques (§ 3.15) : encodées pour tenir dans le chemin.
    fun exerciseHistory(exerciseId: String) = "exercise-history/${Uri.encode(exerciseId)}"

    fun editWorkout(workoutId: String) = "edit-workout/${Uri.encode(workoutId)}"
}
