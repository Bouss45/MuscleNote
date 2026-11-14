package com.musclenote.android.data.progress

import java.time.LocalDate

/**
 * Période d'une courbe (SPEC § 15.9.1, point 5) : 1, 3, 6 ou 12 mois calendaires avant aujourd'hui,
 * ou tout l'historique.
 */
enum class ChartPeriod(private val months: Long?) {
    OneMonth(1),
    ThreeMonths(3),
    SixMonths(6),
    OneYear(12),
    All(null);

    /**
     * Premier jour de la période, compris : aujourd'hui moins [months] mois (le 31 mars − 1 mois
     * donne le 28 ou 29 février) ; `null` pour tout l'historique.
     */
    fun startDay(today: LocalDate): LocalDate? = months?.let(today::minusMonths)
}

/** Sens d'une variation : hausse, baisse, ou égale au dixième. */
enum class Direction { Up, Down, Same }

/**
 * Résumé d'une série chronologique non vide (§ 15.9.3) : premier point (départ), dernier point
 * (valeur au repos du chiffre héros), record (valeur max, le plus ancien à égalité) et variation
 * dernier − départ au dixième, `null` avec un seul point.
 */
data class CurveSummary<T>(val first: T, val last: T, val record: T, val variation: Double?)

/**
 * Courbes de la fiche exercice et de Progrès (SPEC § 15.9.3), en fonctions pures testables sur la
 * JVM. Les séries sont dans l'ordre chronologique ; leurs points sont lus par des sélecteurs (jour,
 * abscisse, valeur), pour servir aux séances comme aux journaux quotidiens.
 */
object ProgressCurves {
    /** Points dessinés au plus par une courbe de fiche, de surface ou de hub. */
    const val CURVE_MAX_POINTS = 120

    /** Points dessinés au plus par la mini-courbe d'une ligne. */
    const val MINI_CURVE_MAX_POINTS = 24

    /** Points de [points] dont le jour local ([day]) est dans [period] ; l'ordre est gardé. */
    fun <T> inPeriod(points: List<T>, period: ChartPeriod, today: LocalDate, day: (T) -> LocalDate): List<T> {
        val start = period.startDay(today) ?: return points
        return points.filter { !day(it).isBefore(start) }
    }

    /** Résumé de [points] (chronologiques) ; `null` sans point. */
    fun <T> summarize(points: List<T>, value: (T) -> Double): CurveSummary<T>? {
        if (points.isEmpty()) return null
        val first = points.first()
        val last = points.last()
        // maxBy garde le premier des maximums : le plus ancien.
        val record = points.maxBy(value)
        return CurveSummary(
            first = first,
            last = last,
            record = record,
            variation = if (points.size < 2) null else variation(from = value(first), to = value(last)),
        )
    }

    /** Variation [to] − [from], au dixième (demi vers +∞, § 3.5) ; jamais −0,0. */
    fun variation(from: Double, to: Double): Double = ProgressStats.roundTenth(to - from)

    /** Sens d'une variation déjà arrondie par [variation]. */
    fun direction(variation: Double): Direction = when {
        variation > 0.0 -> Direction.Up
        variation < 0.0 -> Direction.Down
        else -> Direction.Same
    }

    /**
     * Réduit [points] (chronologiques) à [maxPoints] au plus (≥ 4) : le premier et le dernier sont
     * gardés ; l'intervalle des abscisses ([x]) est coupé en `(maxPoints − 2) / 2` tranches égales,
     * et chaque tranche garde son point le plus bas et son point le plus haut ([value], le plus
     * ancien à égalité), dans l'ordre chronologique. La forme de la courbe, ses creux et son record
     * restent. Une série assez courte est rendue telle quelle.
     */
    fun <T> downsample(points: List<T>, maxPoints: Int, x: (T) -> Double, value: (T) -> Double): List<T> {
        require(maxPoints >= 4) { "maxPoints < 4" }
        if (points.size <= maxPoints) return points
        val buckets = (maxPoints - 2) / 2
        val minX = x(points.first())
        val span = x(points.last()) - minX
        val lowest = arrayOfNulls<Int>(buckets)
        val highest = arrayOfNulls<Int>(buckets)
        for (index in 1 until points.lastIndex) {
            val position = if (span > 0.0) (x(points[index]) - minX) / span else 0.0
            val bucket = (position * buckets).toInt().coerceIn(0, buckets - 1)
            val v = value(points[index])
            // Comparaisons strictes : à égalité, le premier point rencontré (le plus ancien) reste.
            if (lowest[bucket]?.let { v < value(points[it]) } != false) lowest[bucket] = index
            if (highest[bucket]?.let { v > value(points[it]) } != false) highest[bucket] = index
        }
        val kept = sortedSetOf(0, points.lastIndex)
        lowest.forEach { it?.let(kept::add) }
        highest.forEach { it?.let(kept::add) }
        return kept.map { points[it] }
    }
}
