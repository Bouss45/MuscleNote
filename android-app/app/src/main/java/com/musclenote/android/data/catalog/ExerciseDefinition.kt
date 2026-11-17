package com.musclenote.android.data.catalog

import androidx.annotation.StringRes
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.ExerciseType
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.SetKind

/**
 * Exercice intégré ou perso tel que les écrans le présentent. Le nom affiché d'un exercice
 * intégré vient de [nameRes] (langue courante, § 6.1), celui d'un exercice perso de [customName].
 * Ses muscles (§ 15.17, 🔄 D43) : ceux du catalogue, ou ceux choisis pour un exercice perso. Son type
 * (§ 15.18.1, 🔄 D44) : embarqué, ou choisi pour un exercice perso.
 */
data class ExerciseDefinition(
    val id: String,
    val muscleGroup: MuscleGroup,
    @param:StringRes val nameRes: Int?,
    val customName: String?,
    val archived: Boolean,
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    val type: ExerciseType = ExerciseType.Reps,
) {
    /** « Perso ou intégré » se décide par l'appartenance au catalogue (§ 3.9). */
    val isCustom: Boolean get() = nameRes == null

    /** Un exercice est « cardio » ssi son groupe est `cardio` (§ 4.1). */
    val isCardio: Boolean get() = muscleGroup == MuscleGroup.Cardio

    /** Champs de ses séries : cardio (groupe), sinon au temps ou force selon le type (§ 15.18.1). */
    val kind: SetKind get() = SetKind.of(muscleGroup, type)

    val isTimed: Boolean get() = kind == SetKind.Timed

    /** Un exercice perso peut n'avoir aucun muscle (§ 15.17.3). */
    val hasMuscles: Boolean get() = primaryMuscles.isNotEmpty() || secondaryMuscles.isNotEmpty()

    /** Rôle de [muscle] pour cet exercice, `null` s'il ne le travaille pas. */
    fun roleOf(muscle: Muscle): MuscleRole? = when (muscle) {
        in primaryMuscles -> MuscleRole.Primary
        in secondaryMuscles -> MuscleRole.Secondary
        else -> null
    }
}

/** Catalogue (§ 6.1) puis exercices perso par ordre de création : l'ordre d'affichage (§ 2.7). */
fun exerciseDefinitions(custom: List<Exercise>): List<ExerciseDefinition> =
    ExerciseCatalog.all.map {
        ExerciseDefinition(it.id, it.muscleGroup, it.nameRes, customName = null, archived = false, it.primaryMuscles, it.secondaryMuscles, it.type)
    } + custom.map {
        ExerciseDefinition(it.id, it.muscleGroup, nameRes = null, customName = it.name, archived = it.archived, it.primaryMuscles, it.secondaryMuscles, it.type)
    }
