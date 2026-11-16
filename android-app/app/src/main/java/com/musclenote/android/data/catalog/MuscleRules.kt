package com.musclenote.android.data.catalog

import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.util.normalizeSearch

/** Rôle d'un muscle dans un exercice (§ 15.17.2) : il fait le travail, ou il aide. */
enum class MuscleRole { Primary, Secondary }

/** Un exercice qui travaille un muscle, et comment (sheet « Muscle », § 15.17.7). */
data class MuscleUse(val definition: ExerciseDefinition, val role: MuscleRole)

/**
 * Muscles choisis pour un exercice perso (§ 15.17.3) : chaque liste dans l'ordre de [Muscle], sans
 * doublon, et un muscle dans une seule des deux.
 */
data class MuscleSelection(
    val primary: List<Muscle> = emptyList(),
    val secondary: List<Muscle> = emptyList(),
) {
    val isEmpty: Boolean get() = primary.isEmpty() && secondary.isEmpty()

    /** Ajoute [muscle] aux principaux (en le retirant des secondaires), ou l'en retire. */
    fun togglePrimary(muscle: Muscle): MuscleSelection =
        if (muscle in primary) copy(primary = primary - muscle) else of(primary + muscle, secondary - muscle)

    /** Ajoute [muscle] aux secondaires (en le retirant des principaux), ou l'en retire. */
    fun toggleSecondary(muscle: Muscle): MuscleSelection =
        if (muscle in secondary) copy(secondary = secondary - muscle) else of(primary - muscle, secondary + muscle)

    companion object {
        /** Remise en forme (§ 15.17.8) : ordre de la liste, doublons retirés, un muscle des deux reste principal. */
        fun of(primary: Collection<Muscle>, secondary: Collection<Muscle>): MuscleSelection {
            val p = primary.distinct().sortedBy { it.ordinal }
            return MuscleSelection(p, secondary.distinct().filterNot { it in p }.sortedBy { it.ordinal })
        }
    }
}

/** Filtre « Par muscle » et exercices d'un muscle (SPEC § 15.17.6, § 15.17.7, 🔄 D43). */
object MuscleRules {

    /** [exercise] avec ses muscles remis en forme (lecture d'un fichier, § 15.17.8). */
    fun normalized(exercise: Exercise): Exercise {
        val selection = MuscleSelection.of(exercise.primaryMuscles, exercise.secondaryMuscles)
        if (selection.primary == exercise.primaryMuscles && selection.secondary == exercise.secondaryMuscles) return exercise
        return exercise.copy(primaryMuscles = selection.primary, secondaryMuscles = selection.secondary)
    }

    /**
     * Exercices non archivés dont au moins un des [muscles] est principal ou secondaire et dont le
     * nom affiché ([nameOf]) contient [query] (sans casse ni accents, § 10.6). Aucun muscle → tous,
     * filtrés par le texte seul. Ordre : ceux où un muscle choisi est principal, puis les autres,
     * chacun dans l'ordre de [definitions].
     */
    fun filter(
        definitions: List<ExerciseDefinition>,
        muscles: Set<Muscle>,
        query: String,
        nameOf: (ExerciseDefinition) -> String,
    ): List<ExerciseDefinition> {
        val wanted = normalizeSearch(query)
        val matching = definitions.filter { !it.archived && (wanted.isEmpty() || normalizeSearch(nameOf(it)).contains(wanted)) }
        if (muscles.isEmpty()) return matching
        val primary = matching.filter { d -> d.primaryMuscles.any { it in muscles } }
        val secondary = matching.filter { d -> d !in primary && d.secondaryMuscles.any { it in muscles } }
        return primary + secondary
    }

    /** Exercices non archivés qui travaillent [muscle] : principaux puis secondaires, dans l'ordre de [definitions]. */
    fun usesOf(muscle: Muscle, definitions: List<ExerciseDefinition>): List<MuscleUse> {
        val active = definitions.filterNot { it.archived }
        return active.filter { muscle in it.primaryMuscles }.map { MuscleUse(it, MuscleRole.Primary) } +
            active.filter { muscle in it.secondaryMuscles }.map { MuscleUse(it, MuscleRole.Secondary) }
    }
}
