package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    fun sortedNewestFirst(entries: List<BodyWeightEntry>, zone: ZoneId): List<BodyWeightEntry> =
        entries.sortedByDescending { parseInstantOrNull(it.date, zone) ?: Instant.MIN }
}
