package com.musclenote.android.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Courbes de la fiche exercice et de Progrès (SPEC § 15.9.3). */
class ProgressCurvesTest {

    /** Un point de test : jour local, valeur, et un nom pour reconnaître les égalités. */
    private data class P(val day: LocalDate, val value: Double, val name: String = "")

    private fun p(day: String, value: Double, name: String = "") = P(LocalDate.parse(day), value, name)

    private val today = LocalDate.parse("2026-11-14")

    private fun inPeriod(points: List<P>, period: ChartPeriod, on: LocalDate = today) =
        ProgressCurves.inPeriod(points, period, on) { it.day }

    private fun summarize(points: List<P>) = ProgressCurves.summarize(points) { it.value }

    private fun downsample(points: List<P>, max: Int) =
        ProgressCurves.downsample(points, max, x = { it.day.toEpochDay().toDouble() }, value = { it.value })

    // ─── Série d'une période ─────────────────────────────────────────────────────────

    private val history = listOf(
        p("2025-11-13", 70.0),
        p("2025-11-14", 72.5),
        p("2026-05-14", 80.0),
        p("2026-08-13", 82.5),
        p("2026-08-14", 85.0),
        p("2026-10-13", 87.5),
        p("2026-10-14", 90.0),
        p("2026-11-14", 92.5),
    )

    @Test
    fun `une période part du même jour, 1, 3, 6 ou 12 mois plus tôt, ce jour compris`() {
        assertEquals(listOf("2026-10-14", "2026-11-14"), inPeriod(history, ChartPeriod.OneMonth).map { it.day.toString() })
        assertEquals(4, inPeriod(history, ChartPeriod.ThreeMonths).size) // depuis le 14 août
        assertEquals(6, inPeriod(history, ChartPeriod.SixMonths).size) // depuis le 14 mai
        assertEquals(7, inPeriod(history, ChartPeriod.OneYear).size) // depuis le 14 nov. 2025
        assertEquals(history, inPeriod(history, ChartPeriod.All))
    }

    @Test
    fun `premier jour d'une période, fin de mois ramenée au dernier jour du mois`() {
        assertEquals(LocalDate.parse("2026-10-14"), ChartPeriod.OneMonth.startDay(today))
        assertEquals(LocalDate.parse("2025-11-14"), ChartPeriod.OneYear.startDay(today))
        assertEquals(LocalDate.parse("2026-02-28"), ChartPeriod.OneMonth.startDay(LocalDate.parse("2026-03-31")))
        assertNull(ChartPeriod.All.startDay(today))
    }

    @Test
    fun `une période sans point donne une série vide, l'ordre chronologique est gardé`() {
        val old = listOf(p("2026-01-05", 60.0), p("2026-02-05", 65.0))
        assertTrue(inPeriod(old, ChartPeriod.OneMonth).isEmpty())
        assertTrue(inPeriod(emptyList(), ChartPeriod.All).isEmpty())
        // Une séance datée après aujourd'hui (horloge changée) reste dans la période.
        assertEquals(listOf(92.5, 95.0), inPeriod(history + p("2026-11-20", 95.0), ChartPeriod.OneMonth).map { it.value }.drop(1))
    }

    // ─── Résumé et variation ─────────────────────────────────────────────────────────

    @Test
    fun `période vide, aucun résumé`() {
        assertNull(summarize(emptyList()))
    }

    @Test
    fun `un seul point, pas de variation`() {
        val only = p("2026-11-05", 95.0)
        val summary = summarize(listOf(only))!!
        assertSame(only, summary.first)
        assertSame(only, summary.last)
        assertSame(only, summary.record)
        assertNull(summary.variation)
    }

    @Test
    fun `la variation est la dernière séance moins le premier point, au dixième`() {
        val rising = summarize(listOf(p("2026-08-14", 92.5), p("2026-09-01", 102.5), p("2026-11-05", 100.0)))!!
        assertEquals(7.5, rising.variation!!, 0.0)
        assertEquals(100.0, rising.last.value, 0.0)
        assertEquals(102.5, rising.record.value, 0.0)
        assertEquals(Direction.Up, ProgressCurves.direction(rising.variation!!))

        val falling = summarize(listOf(p("2026-08-14", 100.0), p("2026-09-01", 104.0), p("2026-11-05", 97.5)))!!
        assertEquals(-2.5, falling.variation!!, 0.0)
        assertEquals(Direction.Down, ProgressCurves.direction(falling.variation!!))

        assertEquals(0.3, summarize(listOf(p("2026-08-14", 80.0), p("2026-11-05", 80.25)))!!.variation!!, 0.0)
    }

    @Test
    fun `valeurs égales, variation nulle et stable, jamais plus ou moins zéro`() {
        val equal = summarize(listOf(p("2026-08-14", 80.0), p("2026-09-14", 85.0), p("2026-11-05", 80.0)))!!
        assertEquals(0.0, equal.variation!!, 0.0)
        assertEquals(Direction.Same, ProgressCurves.direction(equal.variation!!))
        // Écarts arrondis à zéro au dixième : ni « +0,0 » ni « −0,0 ».
        for (delta in listOf(0.04, -0.04, -0.05)) {
            val v = ProgressCurves.variation(from = 80.0, to = 80.0 + delta)
            assertEquals(Direction.Same, ProgressCurves.direction(v))
            assertEquals("0.0", v.toString())
        }
    }

    @Test
    fun `le record est le point le plus haut, le plus ancien à égalité`() {
        val summary = summarize(listOf(p("2026-08-14", 90.0), p("2026-09-01", 100.0, "a"), p("2026-10-01", 100.0, "b"), p("2026-11-05", 95.0)))!!
        assertEquals("a", summary.record.name)
    }

    @Test
    fun `variation d'un point touché, par rapport au premier point`() {
        assertEquals(-5.0, ProgressCurves.variation(from = 92.5, to = 87.5), 0.0)
        assertEquals(0.0, ProgressCurves.variation(from = 92.5, to = 92.5), 0.0)
    }

    // ─── Réduction du nombre de points ──────────────────────────────────────────────

    @Test
    fun `peu de points, la série est gardée telle quelle`() {
        val points = history
        assertSame(points, downsample(points, max = 120))
        assertSame(points, downsample(points, max = points.size))
    }

    @Test
    fun `beaucoup de points, au plus max, premier, dernier, plus haut et plus bas gardés`() {
        val start = LocalDate.parse("2023-01-02")
        // 4 ans, 3 séances par semaine : une vague, un creux profond et un record isolés.
        val points = (0 until 624).map { i ->
            val value = when (i) {
                300 -> 40.0 // blessure
                500 -> 140.0 // record
                else -> 80.0 + 10 * kotlin.math.sin(i / 20.0)
            }
            P(start.plusDays(i * 7L / 3), value, "#$i")
        }
        val reduced = downsample(points, max = 120)
        assertTrue(reduced.size <= 120)
        assertTrue(reduced.size > 60)
        assertEquals(points.first(), reduced.first())
        assertEquals(points.last(), reduced.last())
        assertTrue(reduced.any { it.name == "#300" })
        assertTrue(reduced.any { it.name == "#500" })
        // Ordre chronologique conservé, et uniquement des points de la série.
        assertEquals(reduced.sortedBy { it.day }, reduced)
        assertTrue(points.containsAll(reduced))
    }

    @Test
    fun `réduction d'une mini-courbe à 24 points`() {
        val start = LocalDate.parse("2026-01-01")
        val points = (0 until 50).map { P(start.plusDays(it.toLong()), (it % 7).toDouble()) }
        val reduced = downsample(points, max = 24)
        assertTrue(reduced.size <= 24)
        assertEquals(points.first(), reduced.first())
        assertEquals(points.last(), reduced.last())
    }

    @Test
    fun `le record le plus ancien survit à la réduction quand il est égalé`() {
        val start = LocalDate.parse("2026-01-01")
        val points = (0 until 200).map { i -> P(start.plusDays(i.toLong()), if (i == 50 || i == 51) 120.0 else 80.0, "#$i") }
        val reduced = downsample(points, max = 24)
        assertEquals(summarize(points)!!.record, reduced.maxWith(compareBy<P> { it.value }.thenByDescending { it.day }))
    }

    @Test
    fun `points tous le même jour, réduits sans erreur`() {
        val day = LocalDate.parse("2026-11-14")
        val points = (0 until 30).map { P(day, it.toDouble(), "#$it") }
        val reduced = downsample(points, max = 10)
        assertTrue(reduced.size <= 10)
        assertEquals(points.first(), reduced.first())
        assertEquals(points.last(), reduced.last())
        assertTrue(reduced.any { it.value == 28.0 }) // le plus haut des points intérieurs
    }
}
