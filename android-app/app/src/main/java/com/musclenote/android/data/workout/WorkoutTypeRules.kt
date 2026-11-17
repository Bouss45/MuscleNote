package com.musclenote.android.data.workout

import com.musclenote.android.data.model.RemovedWorkoutType
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.normalizeWorkoutName
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * « Mes types » (SPEC § 15.19.3 – 15.19.5, 🔄 D45) : les noms personnalisés déjà utilisés, calculés à
 * partir des séances terminées ; seule la liste des types retirés est stockée (dans les préférences).
 * Les historiques passés en paramètre sont triés de la séance la plus récente à la plus ancienne
 * (invariant du dépôt).
 */
object WorkoutTypeRules {
    /** Puces de « Mes types » au plus. */
    const val MAX_MY_TYPES = 8

    /** Deux noms désignent le même type : même nom normalisé, non vide (§ 15.19.2). */
    fun isSameType(a: String, b: String): Boolean {
        val key = normalizeWorkoutName(a)
        return key.isNotEmpty() && key == normalizeWorkoutName(b)
    }

    /**
     * Noms de « Mes types », du plus récent au plus ancien, [limit] au plus : un par nom normalisé, à la
     * forme de sa séance la plus récente. Écartés : séances avec préréglage (enregistré ou d'après le
     * nom), noms vides, types retirés que nulle séance commencée après le retrait n'a réutilisés.
     */
    fun myTypes(
        history: List<Workout>,
        removed: List<RemovedWorkoutType>,
        zone: ZoneId,
        limit: Int = MAX_MY_TYPES,
    ): List<String> {
        val removedAt = removedByKey(removed)
        val seen = HashSet<String>()
        val types = ArrayList<String>()
        for (workout in history) {
            if (types.size >= limit) break
            if (WorkoutRules.presetOf(workout) != null) continue
            val key = normalizeWorkoutName(workout.name)
            if (key.isEmpty() || !seen.add(key)) continue
            // La séance la plus récente de ce nom décide : commencée après le retrait, le type revient.
            val removal = removedAt[key]
            if (removal != null) {
                val start = parseInstantOrNull(workout.date, zone)
                if (start == null || !start.isAfter(removal)) continue
            }
            types += workout.name.trim()
        }
        return types
    }

    /**
     * Retire [name] de « Mes types » à [now] (§ 15.19.4) : une entrée du même nom normalisé est remplacée,
     * la nouvelle va en fin de liste. Nom vide : rien ne change.
     */
    fun remove(removed: List<RemovedWorkoutType>, name: String, now: Instant): List<RemovedWorkoutType> {
        val trimmed = name.trim()
        if (normalizeWorkoutName(trimmed).isEmpty()) return removed
        return removed.filterNot { isSameType(it.name, trimmed) } + RemovedWorkoutType(trimmed, formatInstant(now))
    }

    /**
     * Liste relue (fichier ou import, § 15.19.5) : entrées sans nom ou à l'instant illisible écartées ;
     * pour un même nom normalisé, l'entrée au retrait le plus récent, à la place de la première.
     */
    fun normalized(removed: List<RemovedWorkoutType>): List<RemovedWorkoutType> {
        val kept = LinkedHashMap<String, Pair<RemovedWorkoutType, Instant>>()
        for (entry in removed) {
            val key = normalizeWorkoutName(entry.name)
            if (key.isEmpty()) continue
            val at = parseInstantOrNull(entry.removedAt, ZoneOffset.UTC) ?: continue
            val previous = kept[key]
            if (previous == null || at.isAfter(previous.second)) kept[key] = entry to at
        }
        return kept.values.map { it.first }
    }

    /** Nom normalisé → instant du retrait le plus récent. */
    private fun removedByKey(removed: List<RemovedWorkoutType>): Map<String, Instant> {
        val byKey = HashMap<String, Instant>()
        for (entry in removed) {
            val key = normalizeWorkoutName(entry.name)
            val at = parseInstantOrNull(entry.removedAt, ZoneOffset.UTC) ?: continue
            if (key.isNotEmpty() && byKey[key]?.isAfter(at) != true) byKey[key] = at
        }
        return byKey
    }
}
