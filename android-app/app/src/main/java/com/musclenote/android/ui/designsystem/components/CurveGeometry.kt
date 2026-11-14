package com.musclenote.android.ui.designsystem.components

import kotlin.math.abs

/**
 * Géométrie de `TrendCurve` (SPEC § 15.9.3), sans Compose pour être testée sur la JVM : abscisses
 * des points et point choisi sous le doigt.
 */
object CurveGeometry {
    /**
     * Abscisse de chaque point sur une courbe de [width], entre les marges [start] et [end],
     * proportionnelle à sa date [xs] (croissantes). Un seul point est posé à droite ; des dates
     * toutes égales, à gauche.
     */
    fun positions(xs: List<Float>, width: Float, start: Float, end: Float): List<Float> {
        if (xs.size == 1) return listOf(width - end)
        if (xs.isEmpty()) return emptyList()
        val minX = xs.first()
        val span = (xs.last() - minX).takeIf { it > 0f } ?: 1f
        return xs.map { start + (it - minX) / span * (width - start - end) }
    }

    /**
     * Index du point dont l'abscisse ([positions], croissantes) est la plus proche de [x] — le plus
     * ancien à égalité ; avant le premier point, le premier ; après le dernier, le dernier ; `null`
     * sans point.
     */
    fun nearestIndex(positions: List<Float>, x: Float): Int? {
        if (positions.isEmpty()) return null
        var best = 0
        for (index in 1 until positions.size) {
            if (abs(positions[index] - x) < abs(positions[best] - x)) best = index
        }
        return best
    }
}
