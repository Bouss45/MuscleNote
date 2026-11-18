package com.musclenote.android.data.workout

import com.musclenote.android.data.model.WorkoutSet

/**
 * Numéro affiché d'une série (SPEC § 15.21.2, 🔄 D47) : « chauffe [rank] » pour une série de chauffe,
 * « série [rank] » pour une série de travail, chacune comptée parmi les séries de son genre dans
 * l'exercice (à partir de 1).
 */
data class SetNumber(val warmup: Boolean, val rank: Int)

/** Numérotation des séries d'un exercice : les séries de travail sans les chauffes (C, 1, 2, 3). */
object SetNumbering {

    /** Le numéro de chaque série de [sets], dans leur ordre ; marquer ne déplace rien. */
    fun of(sets: List<WorkoutSet>): List<SetNumber> {
        var warmups = 0
        var work = 0
        return sets.map { set -> if (set.warmup) SetNumber(true, ++warmups) else SetNumber(false, ++work) }
    }

    /** Le numéro de la série [setId] de [sets] ; `null` si elle n'y est pas. */
    fun of(sets: List<WorkoutSet>, setId: String): SetNumber? {
        val index = sets.indexOfFirst { it.id == setId }
        return if (index < 0) null else of(sets)[index]
    }
}
