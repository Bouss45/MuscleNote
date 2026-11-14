package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

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
private val GoalStroke = 1.dp
private val GoalDash = 4.dp
private val GoalLabelGap = 4.dp

/** Hauteur d'une courbe de fiche ; [CompactCurveHeight] pour une courbe dans une surface. */
val CurveHeight = 172.dp

/** Hauteur d'une courbe dans un écran chargé (section d'un hub). */
val CurveHeightDense = 150.dp
val CompactCurveHeight = 48.dp

/**
 * Courbe — `TrendCurve` (DESIGN.md § 9.1) : trait 1,5 `textPrimary`, ni grille ni remplissage,
 * point final, première et dernière date sous l'axe. Au toucher (glisser), [onSelect] reçoit le
 * point le plus proche ([CurveGeometry.nearestIndex] ; null au relâchement) : l'écran le montre
 * dans son chiffre héros et passe [selectedIndex] / [selectedLabel] ; la suite de la courbe passe en
 * `textTertiary`. La courbe garde le geste (elle ne fait pas défiler l'écran).
 *
 * [goal] : ligne d'objectif pointillée (`textTertiary`), marquée [goalLabel] à droite, comprise dans
 * l'échelle. [emptyText] : texte centré quand il n'y a aucun point. [contentDescription] est lu par
 * TalkBack (période, départ, arrivée, variation).
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
    goal: Float? = null,
    goalLabel: String? = null,
    emptyText: String? = null,
) {
    val colors = Studio.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = Studio.typography.small.copy(color = colors.textSecondary)
    val selectedStyle = Studio.typography.small.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
    val emptyStyle = Studio.typography.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
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
                            val xs = CurveGeometry.positions(points.map { it.x }, size.width.toFloat(), StartInset.toPx(), EndInset.toPx())
                            select.value?.invoke(CurveGeometry.nearestIndex(xs, down.position.x))
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                select.value?.invoke(CurveGeometry.nearestIndex(xs, change.position.x))
                                change.consume()
                            }
                            select.value?.invoke(null)
                        }
                    }
                },
            ),
    ) {
        if (points.isEmpty()) {
            if (emptyText != null) {
                val layout = measurer.measure(emptyText, emptyStyle, constraints = Constraints(maxWidth = size.width.toInt()))
                drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2))
            }
            return@Canvas
        }
        val plot = plot(points, StartInset.toPx(), EndInset.toPx(), PlotTop.toPx(), size.height - PlotBottomInset.toPx(), goal)
        val offsets = plot.offsets
        plot.goalY?.let { y ->
            drawLine(
                colors.textTertiary,
                Offset(StartInset.toPx(), y),
                Offset(size.width - EndInset.toPx(), y),
                strokeWidth = GoalStroke.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(GoalDash.toPx(), GoalDash.toPx())),
            )
            goalLabel?.let { drawLabel(measurer.measure(it, axisStyle), size.width - EndInset.toPx(), y - GoalLabelGap.toPx(), Anchor.End) }
        }
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
        val offsets = plot(points, CompactInset.toPx(), CompactInset.toPx(), CompactPlotInset.toPx(), size.height - CompactPlotInset.toPx()).offsets
        drawPolyline(offsets, color)
        drawCircle(color, CompactDotRadius.toPx(), offsets.last())
    }
}

/** Taille d'une mini-courbe de ligne de liste. */
val MiniCurveWidth = 56.dp
val MiniCurveHeight = 24.dp
private val MiniInset = 3.dp
private val MiniDotRadius = 2.5.dp

/**
 * Mini-courbe (fin d'une ligne de liste, `ListRowTrailing.CurveValue`) : 56 × 24, trait 1,5, point
 * final, ni dates ni toucher. Décorative : la valeur et la variation de la ligne disent la même chose.
 */
@Composable
fun MiniTrendCurve(points: List<CurvePoint>, modifier: Modifier = Modifier) {
    val color = Studio.colors.textPrimary
    Canvas(modifier.size(MiniCurveWidth, MiniCurveHeight).clearAndSetSemantics {}) {
        if (points.isEmpty()) return@Canvas
        val offsets = plot(points, MiniInset.toPx(), MiniInset.toPx(), MiniInset.toPx(), size.height - MiniInset.toPx()).offsets
        drawPolyline(offsets, color)
        drawCircle(color, MiniDotRadius.toPx(), offsets.last())
    }
}

private enum class Anchor { Start, Center, End }

private fun DrawScope.drawLabel(layout: TextLayoutResult, x: Float, baseline: Float, anchor: Anchor) {
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

/** Points placés (et ordonnée de l'objectif), l'échelle allant du minimum au maximum, objectif compris. */
private class Plot(val offsets: List<Offset>, val goalY: Float?)

private fun DrawScope.plot(points: List<CurvePoint>, start: Float, end: Float, top: Float, bottom: Float, goal: Float? = null): Plot {
    val xs = CurveGeometry.positions(points.map { it.x }, size.width, start, end)
    val values = if (goal != null) points.map { it.y } + goal else points.map { it.y }
    val minY = values.min()
    val spanY = values.max() - minY
    fun y(value: Float) = if (spanY > 0f) bottom - (value - minY) / spanY * (bottom - top) else (top + bottom) / 2
    return Plot(points.mapIndexed { i, p -> Offset(xs[i], y(p.y)) }, goal?.let(::y))
}
