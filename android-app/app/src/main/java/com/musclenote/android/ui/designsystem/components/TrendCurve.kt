package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlin.math.abs

/** Un point de courbe : abscisse (ex. jour) et valeur, dans n'importe quelle unité. */
@Immutable
data class CurvePoint(val x: Float, val y: Float)

private val StrokeWidth = 1.5.dp
private val PlotTop = 22.dp
private val PlotBottomInset = 36.dp
private val GuideBottomInset = 20.dp
private val LabelBaselineInset = 4.dp
private val StartInset = 4.dp
private val EndInset = 6.dp
private val EndDotRadius = 4.dp
private val SelectedDotRadius = 5.dp

/** Hauteur d'une courbe de fiche ; [CompactCurveHeight] pour une courbe dans une surface. */
val CurveHeight = 172.dp
val CompactCurveHeight = 48.dp

/**
 * Courbe — `TrendCurve` (DESIGN.md § 9.1) : trait 1,5 `textPrimary`, ni grille ni remplissage,
 * point final, première et dernière date sous l'axe. Au toucher (glisser), [onSelect] reçoit le
 * point le plus proche (null au relâchement) : l'écran le montre dans son chiffre héros et passe
 * [selectedIndex] / [selectedLabel] ; la suite de la courbe passe en `textTertiary`.
 */
@Composable
fun TrendCurve(
    points: List<CurvePoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
    height: Dp = CurveHeight,
    selectedIndex: Int? = null,
    selectedLabel: String? = null,
    onSelect: ((Int?) -> Unit)? = null,
) {
    val colors = Studio.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = Studio.typography.small.copy(color = colors.textSecondary)
    val selectedStyle = Studio.typography.small.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
    val select = rememberUpdatedState(onSelect)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onSelect == null || points.size < 2) {
                    Modifier
                } else {
                    Modifier.pointerInput(points) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val xs = plotXs(points, size.width.toFloat(), StartInset.toPx(), EndInset.toPx())
                            select.value?.invoke(nearest(xs, down.position.x))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                select.value?.invoke(nearest(xs, change.position.x))
                                change.consume()
                            }
                            select.value?.invoke(null)
                        }
                    }
                },
            ),
    ) {
        if (points.isEmpty()) return@Canvas
        val offsets = plotOffsets(points, StartInset.toPx(), EndInset.toPx(), PlotTop.toPx(), size.height - PlotBottomInset.toPx())
        val selected = selectedIndex?.takeIf { it in offsets.indices }
        if (selected == null) {
            drawPolyline(offsets, colors.textPrimary)
            drawCircle(colors.textPrimary, EndDotRadius.toPx(), offsets.last())
            val baseline = size.height - LabelBaselineInset.toPx()
            startLabel?.let { drawLabel(measurer.measure(it, axisStyle), StartInset.toPx(), baseline, Anchor.Start) }
            endLabel?.let { drawLabel(measurer.measure(it, axisStyle), size.width - StartInset.toPx(), baseline, Anchor.End) }
        } else {
            drawPolyline(offsets.subList(0, selected + 1), colors.textPrimary)
            drawPolyline(offsets.subList(selected, offsets.size), colors.textTertiary)
            val point = offsets[selected]
            drawLine(
                colors.textSecondary,
                Offset(point.x, 0f),
                Offset(point.x, size.height - GuideBottomInset.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(colors.textPrimary, SelectedDotRadius.toPx(), point)
            selectedLabel?.let {
                drawLabel(measurer.measure(it, selectedStyle), point.x, size.height - LabelBaselineInset.toPx(), Anchor.Center)
            }
        }
    }
}

private val CompactInset = 2.dp
private val CompactPlotInset = 6.dp
private val CompactDotRadius = 3.5.dp

/** Courbe compacte (dans une surface mise en avant) : 48 de haut, sans dates, point final. */
@Composable
fun CompactTrendCurve(
    points: List<CurvePoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val color = Studio.colors.textPrimary
    Canvas(
        modifier
            .fillMaxWidth()
            .height(CompactCurveHeight)
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (points.isEmpty()) return@Canvas
        val offsets = plotOffsets(
            points,
            CompactInset.toPx(),
            CompactInset.toPx(),
            CompactPlotInset.toPx(),
            size.height - CompactPlotInset.toPx(),
        )
        drawPolyline(offsets, color)
        drawCircle(color, CompactDotRadius.toPx(), offsets.last())
    }
}

private enum class Anchor { Start, Center, End }

private fun DrawScope.drawLabel(layout: androidx.compose.ui.text.TextLayoutResult, x: Float, baseline: Float, anchor: Anchor) {
    val width = layout.size.width.toFloat()
    val left = when (anchor) {
        Anchor.Start -> x
        Anchor.End -> x - width
        Anchor.Center -> (x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    }
    drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))
}

private fun DrawScope.drawPolyline(offsets: List<Offset>, color: Color) {
    if (offsets.size < 2) return
    val path = Path().apply {
        moveTo(offsets.first().x, offsets.first().y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(StrokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun plotXs(points: List<CurvePoint>, width: Float, start: Float, end: Float): List<Float> {
    val minX = points.minOf { it.x }
    val spanX = (points.maxOf { it.x } - minX).takeIf { it > 0f } ?: 1f
    return points.map { start + (it.x - minX) / spanX * (width - start - end) }
}

private fun DrawScope.plotOffsets(points: List<CurvePoint>, start: Float, end: Float, top: Float, bottom: Float): List<Offset> {
    val xs = plotXs(points, size.width, start, end)
    val minY = points.minOf { it.y }
    val spanY = points.maxOf { it.y } - minY
    return points.mapIndexed { i, p ->
        val y = if (spanY > 0f) bottom - (p.y - minY) / spanY * (bottom - top) else (top + bottom) / 2
        Offset(if (points.size == 1) size.width - end else xs[i], y)
    }
}

private fun nearest(xs: List<Float>, x: Float): Int = xs.indices.minBy { abs(xs[it] - x) }
