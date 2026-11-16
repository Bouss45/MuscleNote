package com.musclenote.android.ui.designsystem.components

import com.musclenote.android.data.model.BodyView
import com.musclenote.android.data.model.Muscle
import kotlin.math.hypot

/** Commande d'un tracé de la carte, en unités du SVG (420 × 460). */
internal sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCommand
    data object Close : PathCommand
}

/** Rectangle en px de la carte dessinée (zone TalkBack d'un muscle). */
internal data class BodyMapBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Géométrie pure de la carte du corps (SPEC § 15.17.4) : lecture des tracés du SVG, proportions,
 * muscle sous le doigt et zone d'un muscle. Les coordonnées reçues et rendues sont en px de la carte
 * dessinée, dont la hauteur [MAP_HEIGHT] unités du SVG valent `height` px ; une vue seule
 * ([BodyView]) recadre sur sa moitié, la vue de dos partant de x = [BACK_ORIGIN].
 */
internal object BodyMapGeometry {
    const val MAP_WIDTH = 420f
    const val MAP_HEIGHT = 460f
    const val VIEW_WIDTH = 200f
    const val BACK_ORIGIN = 220f

    /** Segments par courbe de Bézier pour le toucher (un sous-tracé fait au plus ~60 unités). */
    private const val CURVE_STEPS = 16

    /** Les sous-tracés d'une vue sont tous d'un côté de cette abscisse. */
    private const val VIEW_SPLIT = 210f

    /** Largeur / hauteur de la carte : les deux vues, ou une seule. */
    fun aspectRatio(view: BodyView?): Float = (if (view == null) MAP_WIDTH else VIEW_WIDTH) / MAP_HEIGHT

    /** Abscisse du SVG du bord gauche de la carte. */
    fun originX(view: BodyView?): Float = if (view == BodyView.Back) BACK_ORIGIN else 0f

    /** Tracé absolu `M` / `L` / `C` / `Z` → commandes (le seul jeu qu'emploie le SVG de la maquette). */
    fun parse(d: String): List<PathCommand> {
        val tokens = Regex("[MLCZ]|-?\\d*\\.?\\d+").findAll(d).map { it.value }.toList()
        val commands = mutableListOf<PathCommand>()
        var i = 0
        fun next() = tokens[i++].toFloat()
        while (i < tokens.size) {
            when (val command = tokens[i++]) {
                "M" -> commands += PathCommand.MoveTo(next(), next())
                "L" -> commands += PathCommand.LineTo(next(), next())
                "C" -> commands += PathCommand.CubicTo(next(), next(), next(), next(), next(), next())
                "Z" -> commands += PathCommand.Close
                else -> error("Commande de tracé inattendue : $command")
            }
        }
        return commands
    }

    /** Commandes de chaque muscle, lues une fois. */
    val muscleCommands: Map<Muscle, List<PathCommand>> by lazy { BodyMapShapes.muscles.mapValues { parse(it.value) } }

    /** Commandes des deux silhouettes. */
    val silhouetteCommands: Map<BodyView, List<PathCommand>> by lazy { BodyMapShapes.silhouettes.mapValues { parse(it.value) } }

    /** Sous-tracé aplati en polygone (x, y alternés), avec sa vue. */
    private class Polygon(val points: FloatArray, val view: BodyView)

    private val polygons: Map<Muscle, List<Polygon>> by lazy { muscleCommands.mapValues { flatten(it.value) } }

    private fun flatten(commands: List<PathCommand>): List<Polygon> {
        val result = mutableListOf<Polygon>()
        var points = mutableListOf<Float>()
        var x = 0f
        var y = 0f
        fun close() {
            if (points.size >= 6) {
                val xs = points.filterIndexed { index, _ -> index % 2 == 0 }
                result += Polygon(points.toFloatArray(), if (xs.max() < VIEW_SPLIT) BodyView.Front else BodyView.Back)
            }
            points = mutableListOf()
        }
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    close()
                    x = command.x
                    y = command.y
                    points += listOf(x, y)
                }
                is PathCommand.LineTo -> {
                    x = command.x
                    y = command.y
                    points += listOf(x, y)
                }
                is PathCommand.CubicTo -> {
                    for (step in 1..CURVE_STEPS) {
                        val t = step.toFloat() / CURVE_STEPS
                        val u = 1 - t
                        val a = u * u * u
                        val b = 3 * u * u * t
                        val c = 3 * u * t * t
                        val e = t * t * t
                        points += a * x + b * command.x1 + c * command.x2 + e * command.x
                        points += a * y + b * command.y1 + c * command.y2 + e * command.y
                    }
                    x = command.x
                    y = command.y
                }
                PathCommand.Close -> close()
            }
        }
        close()
        return result
    }

    /**
     * Muscle sous le point ([x], [y]) d'une carte de [height] px montrant [view] (les deux si `null`) :
     * celui dont la forme contient le point (le dernier dessiné l'emporte), sinon le plus proche à
     * [tolerance] px au plus, sinon `null` (hors de la carte, silhouette, vide).
     */
    fun muscleAt(x: Float, y: Float, height: Float, view: BodyView?, tolerance: Float): Muscle? {
        if (height <= 0f) return null
        val scale = height / MAP_HEIGHT
        if (x < 0f || y < 0f || x > height * aspectRatio(view) || y > height) return null
        val sx = x / scale + originX(view)
        val sy = y / scale
        val visible = Muscle.entries.associateWith { muscle -> polygons.getValue(muscle).filter { view == null || it.view == view } }
        Muscle.entries.reversed().firstOrNull { muscle -> visible.getValue(muscle).any { contains(it.points, sx, sy) } }
            ?.let { return it }
        val limit = tolerance / scale
        return Muscle.entries
            .map { muscle -> muscle to (visible.getValue(muscle).minOfOrNull { distance(it.points, sx, sy) } ?: Float.MAX_VALUE) }
            .filter { it.second <= limit }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Zone de [muscle] en px d'une carte de [height] px : sa forme dans [view], ou, carte des deux
     * vues (`null`), dans la vue du muscle ([Muscle.view]). `null` s'il n'est pas dans la vue.
     */
    fun bounds(muscle: Muscle, height: Float, view: BodyView?): BodyMapBounds? {
        val shown = polygons.getValue(muscle).filter { it.view == (view ?: muscle.view) }
        if (shown.isEmpty()) return null
        val scale = height / MAP_HEIGHT
        val origin = originX(view)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (polygon in shown) {
            val p = polygon.points
            for (k in p.indices step 2) {
                left = minOf(left, p[k])
                right = maxOf(right, p[k])
                top = minOf(top, p[k + 1])
                bottom = maxOf(bottom, p[k + 1])
            }
        }
        return BodyMapBounds((left - origin) * scale, top * scale, (right - origin) * scale, bottom * scale)
    }

    /** Point dans le polygone (règle pair-impair ; les formes du SVG sont simples). */
    private fun contains(p: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = p.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = p[2 * i]
            val yi = p[2 * i + 1]
            val xj = p[2 * j]
            val yj = p[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Distance du point au bord du polygone. */
    private fun distance(p: FloatArray, x: Float, y: Float): Float {
        val n = p.size / 2
        var best = Float.MAX_VALUE
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x1 = p[2 * i]
            val y1 = p[2 * i + 1]
            val dx = p[2 * j] - x1
            val dy = p[2 * j + 1] - y1
            val length = dx * dx + dy * dy
            val t = if (length == 0f) 0f else (((x - x1) * dx + (y - y1) * dy) / length).coerceIn(0f, 1f)
            best = minOf(best, hypot(x - (x1 + t * dx), y - (y1 + t * dy)))
        }
        return best
    }
}
