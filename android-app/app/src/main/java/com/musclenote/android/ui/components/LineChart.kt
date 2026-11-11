package com.musclenote.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.ui.theme.InterFontFamily
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlin.math.max
import kotlin.math.roundToLong

/** Point d'un graphique : libellé de l'axe X (ex. `14/03`) et valeur. */
data class ChartPoint(val label: String, val value: Double)

// Marges du tracé (§ 2.13) : place des libellés Y à gauche, des libellés X en bas.
private val PADDING_LEFT = 44.dp
private val PADDING_RIGHT = 16.dp
private val PADDING_TOP = 16.dp
private val PADDING_BOTTOM = 36.dp

/** Nombre maximal de libellés X réguliers (en plus du premier et du dernier). */
private const val X_LABELS = 5

private enum class Anchor { Start, Center, End }

/**
 * Graphique linéaire maison (SPEC § 2.13, 🔄 D13), dessiné au `Canvas` : courbe lissée avec
 * remplissage en dégradé, 3 graduations Y, point maximal étiqueté, ligne d'objectif facultative.
 * Prend toute la largeur du parent ; [height] vaut 130, 180 ou 200 dp selon l'écran.
 *
 * [points] sont dans l'ordre chronologique (le plus ancien à gauche). [formatValue] formate les
 * graduations et l'étiquette du maximum, suivie de [unit] si elle n'est pas vide. Moins de
 * 2 points : texte d'attente centré à la place du graphique.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = MuscleNoteTheme.colors.primary,
    height: Dp = 180.dp,
    unit: String = "",
    formatValue: (Double) -> String = { it.roundToLong().toString() },
    goal: Double? = null,
) {
    val colors = MuscleNoteTheme.colors
    if (points.size < 2) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (points.isEmpty()) R.string.chart_no_data else R.string.chart_one_point),
                color = colors.mutedForeground,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val goalLabel = stringResource(R.string.chart_goal)
    val axisStyle = TextStyle(color = colors.mutedForeground, fontSize = 10.sp, fontFamily = InterFontFamily)
    val xStyle = axisStyle.copy(fontSize = 9.5.sp)
    val peakStyle = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily)
    val goalStyle = TextStyle(color = color.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFontFamily)
    val background = colors.background
    val gridColor = colors.border

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val left = PADDING_LEFT.toPx()
        val right = size.width - PADDING_RIGHT.toPx()
        val top = PADDING_TOP.toPx()
        val baseY = size.height - PADDING_BOTTOM.toPx()
        val innerW = right - left
        val innerH = baseY - top

        val values = points.map { it.value }
        // Échelle de min à max, ligne d'objectif comprise ; plage nulle → 1.
        val maxVal = max(values.max(), goal ?: Double.NEGATIVE_INFINITY)
        val minVal = minOf(values.min(), goal ?: Double.POSITIVE_INFINITY)
        val range = (maxVal - minVal).takeIf { it != 0.0 } ?: 1.0

        fun x(i: Int): Float = left + i.toFloat() / (points.size - 1) * innerW
        fun y(v: Double): Float = (baseY - (v - minVal) / range * innerH).toFloat()

        // Graduations : min, milieu, max — pointillés `border` et libellés à gauche.
        val gridDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (tick in listOf(minVal, (minVal + maxVal) / 2, maxVal)) {
            drawLine(gridColor, Offset(left, y(tick)), Offset(right, y(tick)), strokeWidth = 1.dp.toPx(), pathEffect = gridDash)
            drawLabel(measurer, formatValue(tick), axisStyle, left - 6.dp.toPx(), y(tick) + 4.dp.toPx(), Anchor.End)
        }

        if (goal != null) {
            drawLine(
                color.copy(alpha = 0.5f),
                Offset(left, y(goal)),
                Offset(right, y(goal)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            drawLabel(measurer, goalLabel, goalStyle, right, y(goal) - 4.dp.toPx(), Anchor.End)
        }

        // Courbe lissée : Bézier cubique, points de contrôle au milieu en x, à y0 puis y1.
        fun Path.curve() {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) {
                val cx = (x(i - 1) + x(i)) / 2
                cubicTo(cx, y(values[i - 1]), cx, y(values[i]), x(i), y(values[i]))
            }
        }
        val fill = Path().apply {
            curve()
            lineTo(x(values.lastIndex), baseY)
            lineTo(left, baseY)
            close()
        }
        // Dégradé sur la hauteur de la zone remplie (du point le plus haut à la base), 25 % → 2 %.
        val fillTop = values.minOf { y(it) }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.02f)), startY = fillTop, endY = baseY))
        drawPath(
            Path().apply { curve() },
            color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Points ; le maximum (premier atteint) est plein, plus grand, et étiqueté.
        val peak = values.indices.maxBy { values[it] }
        for (i in values.indices) {
            val center = Offset(x(i), y(values[i]))
            if (i == peak) {
                drawCircle(color, radius = 5.dp.toPx(), center = center)
            } else {
                drawCircle(background, radius = 3.5.dp.toPx(), center = center)
                drawCircle(color, radius = 3.5.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            }
        }
        val peakText = formatValue(values[peak]).let { if (unit.isEmpty()) it else "$it $unit" }
        drawLabel(measurer, peakText, peakStyle, x(peak), y(values[peak]) - 10.dp.toPx(), Anchor.Center)

        // Libellés X : le premier, le dernier et tous les max(1, n / 5) points.
        val step = max(1, points.size / X_LABELS)
        for (i in points.indices) {
            if (i == 0 || i == points.lastIndex || i % step == 0) {
                drawLabel(measurer, points[i].label, xStyle, x(i), size.height - 6.dp.toPx(), Anchor.Center)
            }
        }
    }
}

/**
 * Texte placé comme un `<text>` SVG : [x] est le point d'ancrage ([anchor]), [baseline] la ligne
 * de base. Décalé horizontalement si besoin pour rester dans le graphique.
 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    baseline: Float,
    anchor: Anchor,
) {
    val layout = measurer.measure(text, style, maxLines = 1)
    val width = layout.size.width.toFloat()
    val start = when (anchor) {
        Anchor.Start -> x
        Anchor.Center -> x - width / 2
        Anchor.End -> x - width
    }.coerceIn(0f, max(0f, size.width - width))
    drawText(layout, topLeft = Offset(start, baseline - layout.firstBaseline))
}
