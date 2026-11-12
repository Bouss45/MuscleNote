package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Poids de corps d'un jour local (§ 3.10) : [weight] kg, relevé à [time] (début de la séance, ou
 * midi local pour une pesée) ; [weighIn] = vient d'une pesée manuelle.
 */
data class DailyWeight(val day: LocalDate, val weight: Double, val time: Instant, val weighIn: Boolean)

/** Pesées manuelles (SPEC § 3.11), en fonctions pures testables sur la JVM. */
object BodyWeightRules {
    const val MAX_WEIGHT = 500.0

    /** Poids accepté : `0 < poids ≤ 500` kg. */
    fun isValidWeight(weight: Double): Boolean = weight > 0 && weight <= MAX_WEIGHT

    /**
     * Instant enregistré pour le jour [day] : **midi local** (§ 10.2), robuste à un changement de
     * fuseau de ± 11 h.
     */
    fun noonOf(day: LocalDate, zone: ZoneId): Instant = day.atTime(LocalTime.NOON).atZone(zone).toInstant()

    /**
     * Ajoute la pesée du jour [day] : elle **remplace** toute pesée du même jour local. Liste
     * triée de la plus récente à la plus ancienne.
     */
    fun add(entries: List<BodyWeightEntry>, day: LocalDate, weight: Double, zone: ZoneId): List<BodyWeightEntry> {
        val kept = entries.filterNot { entry -> parseInstantOrNull(entry.date, zone)?.atZone(zone)?.toLocalDate() == day }
        return sortedNewestFirst(kept + BodyWeightEntry(formatInstant(noonOf(day, zone)), weight), zone)
    }

    /** Suppression par égalité exacte de `date` (§ 3.11). */
    fun remove(entries: List<BodyWeightEntry>, date: String): List<BodyWeightEntry> = entries.filterNot { it.date == date }

    /**
     * Un poids par **jour local** (§ 3.10), la règle de l'écran Progrès et de Health Connect
     * (§ 15.3) : pour chaque jour, la séance la plus récente avec un poids non nul ; puis chaque
     * pesée manuelle remplace la valeur de son jour (la plus récente si deux pesées tombent le même
     * jour). Tri par jour croissant ; une date illisible est ignorée.
     */
    fun dailyWeights(workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): List<DailyWeight> {
        val byDay = HashMap<LocalDate, DailyWeight>()
        for (workout in workouts) {
            if (workout.bodyWeight == 0.0) continue
            val time = parseInstantOrNull(workout.date, zone) ?: continue
            val day = time.atZone(zone).toLocalDate()
            val current = byDay[day]
            // À instant égal, la première de la liste (triée de la plus récente à la plus ancienne) gagne.
            if (current == null || time > current.time) byDay[day] = DailyWeight(day, workout.bodyWeight, time, weighIn = false)
        }
        weighIns
            .mapNotNull { entry -> parseInstantOrNull(entry.date, zone)?.let { it to entry } }
            .sortedBy { it.first }
            .forEach { (time, entry) ->
                val day = time.atZone(zone).toLocalDate()
                byDay[day] = DailyWeight(day, entry.weight, time, weighIn = true)
            }
        return byDay.values.sortedBy { it.day }
    }

    fun sortedNewestFirst(entries: List<BodyWeightEntry>, zone: ZoneId): List<BodyWeightEntry> =
        entries.sortedByDescending { parseInstantOrNull(it.date, zone) ?: Instant.MIN }
}
