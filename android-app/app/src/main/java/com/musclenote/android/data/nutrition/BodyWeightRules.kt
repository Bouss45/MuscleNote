package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Poids de corps d'un jour local (§ 3.10) : [weight] kg, relevé à [time] (début de la séance, ou
 * midi local pour une pesée) ; [weighIn] = vient d'une pesée manuelle.
 */
data class DailyWeight(val day: LocalDate, val weight: Double, val time: Instant, val weighIn: Boolean)

/** Étape « Poids de corps » de « Démarrer une séance » (§ 15.13.2, 🔄 D38). */
sealed interface StartWeightPrompt {
    /** Le poids du jour est connu ([weight]) : pas de champ, la séance démarre sans poids. */
    data class WeighedToday(val weight: Double) : StartWeightPrompt

    /**
     * Champ vide ; aide = la dernière pesée connue [lastWeight], d'il y a [daysAgo] jours locaux
     * (les deux `null` sans aucune pesée).
     */
    data class Ask(val lastWeight: Double?, val daysAgo: Long?) : StartWeightPrompt {
        /** Valeur d'où partent les pas − / + : la saisie, ou la dernière pesée quand le champ est vide. */
        fun stepBase(text: String): Double = if (text.isBlank()) lastWeight ?: 0.0 else parseDecimal(text)
    }
}

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
            // 🔄 D38 : 0 = non saisi ; un poids nul ou négatif n'est jamais une valeur (§ 15.13.1).
            if (workout.bodyWeight <= 0) continue
            val time = parseInstantOrNull(workout.date, zone) ?: continue
            val day = time.atZone(zone).toLocalDate()
            val current = byDay[day]
            // À instant égal, la première de la liste (triée de la plus récente à la plus ancienne) gagne.
            if (current == null || time > current.time) byDay[day] = DailyWeight(day, workout.bodyWeight, time, weighIn = false)
        }
        weighIns
            .filter { it.weight > 0 }
            .mapNotNull { entry -> parseInstantOrNull(entry.date, zone)?.let { it to entry } }
            .sortedBy { it.first }
            .forEach { (time, entry) ->
                val day = time.atZone(zone).toLocalDate()
                byDay[day] = DailyWeight(day, entry.weight, time, weighIn = true)
            }
        return byDay.values.sortedBy { it.day }
    }

    /**
     * Poids du jour [day] (§ 15.13.1), ou `null` : pas pesé ce jour-là. [workouts] = séances
     * terminées et, s'il y en a une, la séance en cours (une séance de son jour). Health Connect n'est
     * pas lu (🔄 D29) : seules les données de l'app comptent.
     */
    fun weightOn(day: LocalDate, workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): DailyWeight? =
        dailyWeights(workouts, weighIns, zone).lastOrNull { it.day == day }

    /** Dernière pesée connue au jour [upTo] : le poids du jour le plus récent ≤ [upTo] (§ 15.13.1). */
    fun latestKnown(upTo: LocalDate, workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): DailyWeight? =
        dailyWeights(workouts, weighIns, zone).lastOrNull { it.day <= upTo }

    /**
     * Poids d'une séance pour l'affichage (§ 15.13.1) : son `bodyWeight` s'il est > 0, sinon la
     * dernière pesée connue au jour local de son début ([workouts] : les autres séances), sinon `null`
     * — rien n'est affiché, jamais 0.
     */
    fun sessionWeight(workout: Workout, workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): Double? {
        if (workout.bodyWeight > 0) return workout.bodyWeight
        val day = parseInstantOrNull(workout.date, zone)?.atZone(zone)?.toLocalDate() ?: return null
        return latestKnown(day, workouts, weighIns, zone)?.weight
    }

    /**
     * Étape du poids au démarrage d'une séance, le jour [today] (§ 15.13.2) : pas de champ si le poids
     * du jour est connu, sinon un champ vide avec la dernière pesée connue et son ancienneté.
     */
    fun startPrompt(today: LocalDate, workouts: List<Workout>, weighIns: List<BodyWeightEntry>, zone: ZoneId): StartWeightPrompt {
        val latest = latestKnown(today, workouts, weighIns, zone)
        return when {
            latest == null -> StartWeightPrompt.Ask(null, null)
            latest.day == today -> StartWeightPrompt.WeighedToday(latest.weight)
            else -> StartWeightPrompt.Ask(latest.weight, ChronoUnit.DAYS.between(latest.day, today))
        }
    }

    fun sortedNewestFirst(entries: List<BodyWeightEntry>, zone: ZoneId): List<BodyWeightEntry> =
        entries.sortedByDescending { parseInstantOrNull(it.date, zone) ?: Instant.MIN }
}
