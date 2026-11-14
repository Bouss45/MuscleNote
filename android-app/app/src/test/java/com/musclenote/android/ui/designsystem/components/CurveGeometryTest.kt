package com.musclenote.android.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Placement des points de `TrendCurve` et point choisi sous le doigt (SPEC § 15.9.3). */
class CurveGeometryTest {

    @Test
    fun `abscisses proportionnelles aux dates, entre les marges`() {
        // Dates (jours) 0, 10, 40 sur une courbe de 100 de large, marges 4 et 6 : 90 utiles.
        val xs = CurveGeometry.positions(listOf(0f, 10f, 40f), width = 100f, start = 4f, end = 6f)
        assertEquals(listOf(4f, 26.5f, 94f), xs)
    }

    @Test
    fun `un seul point est posé à droite, des dates confondues à gauche`() {
        assertEquals(listOf(94f), CurveGeometry.positions(listOf(12f), width = 100f, start = 4f, end = 6f))
        assertEquals(listOf(4f, 4f), CurveGeometry.positions(listOf(12f, 12f), width = 100f, start = 4f, end = 6f))
        assertEquals(emptyList<Float>(), CurveGeometry.positions(emptyList(), width = 100f, start = 4f, end = 6f))
    }

    @Test
    fun `le point le plus proche du doigt`() {
        val xs = listOf(4f, 26.5f, 94f)
        assertEquals(0, CurveGeometry.nearestIndex(xs, 10f))
        assertEquals(1, CurveGeometry.nearestIndex(xs, 30f))
        assertEquals(1, CurveGeometry.nearestIndex(xs, 60f)) // 33,5 de 26,5 contre 34 de 94
        assertEquals(2, CurveGeometry.nearestIndex(xs, 61f))
    }

    @Test
    fun `doigt avant le premier point ou après le dernier`() {
        val xs = listOf(4f, 26.5f, 94f)
        assertEquals(0, CurveGeometry.nearestIndex(xs, -50f))
        assertEquals(2, CurveGeometry.nearestIndex(xs, 500f))
    }

    @Test
    fun `à égalité, le plus ancien, et aucun point sur une courbe vide`() {
        assertEquals(0, CurveGeometry.nearestIndex(listOf(10f, 20f), 15f))
        assertEquals(1, CurveGeometry.nearestIndex(listOf(4f, 10f, 10f, 30f), 10f))
        assertNull(CurveGeometry.nearestIndex(emptyList(), 15f))
    }

    @Test
    fun `choix du point depuis les dates, la largeur et la position du doigt`() {
        // 3 séances les jours 0, 3 et 30 : sur 300 de large, le doigt à 40 est plus près du jour 3.
        val xs = CurveGeometry.positions(listOf(0f, 3f, 30f), width = 300f, start = 0f, end = 0f)
        assertEquals(1, CurveGeometry.nearestIndex(xs, 40f))
        assertEquals(2, CurveGeometry.nearestIndex(xs, 200f))
    }
}
