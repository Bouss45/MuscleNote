package com.musclenote.android.ui.navigation

import android.net.Uri

/** Écrans plein écran, hors onglets (SPEC § 2.1) : la barre d'onglets y est masquée. */
object Routes {
    const val START_WORKOUT = "start-workout"
    const val WORKOUT = "workout"
    const val EXERCISE_PICKER = "exercise-picker"

    const val ARG_EXERCISE_ID = "exerciseId"
    const val EXERCISE_HISTORY = "exercise-history/{$ARG_EXERCISE_ID}"

    /** Les ids sont des chaînes opaques (§ 3.15) : encodées pour tenir dans le chemin. */
    fun exerciseHistory(exerciseId: String) = "exercise-history/${Uri.encode(exerciseId)}"
}
