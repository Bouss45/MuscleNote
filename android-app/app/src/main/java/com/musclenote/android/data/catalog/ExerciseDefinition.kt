package com.musclenote.android.data.catalog

import androidx.annotation.StringRes
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.MuscleGroup

/**
 * Exercice intégré ou perso tel que les écrans le présentent. Le nom affiché d'un exercice
 * intégré vient de [nameRes] (langue courante, § 6.1), celui d'un exercice perso de [customName].
 */
data class ExerciseDefinition(
    val id: String,
    val muscleGroup: MuscleGroup,
    @param:StringRes val nameRes: Int?,
    val customName: String?,
    val archived: Boolean,
) {
    /** « Perso ou intégré » se décide par l'appartenance au catalogue (§ 3.9). */
    val isCustom: Boolean get() = nameRes == null

    /** Un exercice est « cardio » ssi son groupe est `cardio` (§ 4.1). */
    val isCardio: Boolean get() = muscleGroup == MuscleGroup.Cardio
}

/** Catalogue (§ 6.1) puis exercices perso par ordre de création : l'ordre d'affichage (§ 2.7). */
fun exerciseDefinitions(custom: List<Exercise>): List<ExerciseDefinition> =
    ExerciseCatalog.all.map { ExerciseDefinition(it.id, it.muscleGroup, it.nameRes, customName = null, archived = false) } +
        custom.map { ExerciseDefinition(it.id, it.muscleGroup, nameRes = null, customName = it.name, archived = it.archived) }
