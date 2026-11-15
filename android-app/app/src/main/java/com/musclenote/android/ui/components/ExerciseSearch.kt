package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.util.normalizeSearch

/**
 * Filtre des sélecteurs d'exercices (§ 2.7) : groupe **et** texte, le texte étant cherché dans
 * le nom **affiché** (langue courante pour les intégrés), sans casse ni accents (§ 10.6).
 */
@Composable
fun filterExercises(definitions: List<ExerciseDefinition>, query: String, group: MuscleGroup?): List<ExerciseDefinition> {
    val resources = LocalResources.current
    return remember(definitions, query, group, resources) {
        val wanted = normalizeSearch(query)
        definitions.filter { definition ->
            val name = definition.nameRes?.let { resources.getString(it) } ?: definition.customName.orEmpty()
            (group == null || definition.muscleGroup == group) && (wanted.isEmpty() || normalizeSearch(name).contains(wanted))
        }
    }
}
