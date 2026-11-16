package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.MuscleRules
import com.musclenote.android.data.model.Muscle
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

/**
 * Filtre « Par muscle » (§ 15.17.6) : au moins un des [muscles] principal ou secondaire, **et** le
 * texte dans le nom affiché ; principaux d'abord ([MuscleRules.filter]).
 */
@Composable
fun filterExercisesByMuscles(definitions: List<ExerciseDefinition>, query: String, muscles: Set<Muscle>): List<ExerciseDefinition> {
    val resources = LocalResources.current
    return remember(definitions, query, muscles, resources) {
        MuscleRules.filter(definitions, muscles, query) { definition ->
            definition.nameRes?.let { resources.getString(it) } ?: definition.customName.orEmpty()
        }
    }
}

/**
 * Muscles d'un exercice en sous-titre (§ 15.17.6) : principaux, puis après « · » les secondaires, en
 * minuscules après le premier nom (« Pecs · épaules avant, triceps ») ; `null` sans muscle.
 */
@Composable
fun musclesSubtitle(definition: ExerciseDefinition): String? {
    if (!definition.hasMuscles) return null
    val locale = appLocale()
    val primary = definition.primaryMuscles.map { stringResource(it.nameRes) }
    val secondary = definition.secondaryMuscles.map { stringResource(it.nameRes) }
    val names = (primary + secondary).mapIndexed { index, name -> if (index == 0) name else name.replaceFirstChar { it.lowercase(locale) } }
    val first = names.take(primary.size).joinToString(", ")
    val rest = names.drop(primary.size).joinToString(", ")
    return listOf(first, rest).filter { it.isNotEmpty() }.joinToString(" · ")
}
