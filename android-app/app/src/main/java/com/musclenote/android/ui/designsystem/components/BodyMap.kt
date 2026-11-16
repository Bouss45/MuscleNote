package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.musclenote.android.R
import com.musclenote.android.data.model.BodyView
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.ui.designsystem.theme.Studio

/** Hauteurs de la carte du corps (DESIGN.md § 9.1, SPEC § 15.17.4). */
object BodyMapSize {
    /** Plus grande taille : la carte du filtre « Par muscle » quand la place ne manque pas. */
    val Large: Dp = 360.dp

    /** Section « Muscles travaillés » de la fiche exercice. */
    val Small: Dp = 150.dp

    /** Une vue, en tête de la sheet « Muscle ». */
    val SingleView: Dp = 140.dp

    /** Part de l'écran qu'une carte posée au-dessus d'une liste ne dépasse jamais, légendes comprises. */
    const val ABOVE_LIST_SCREEN_FRACTION = 0.4f

    /**
     * Hauteur de la carte fixe au-dessus d'une liste (filtre du sélecteur, SPEC § 15.17.6) : ce qui
     * laisse [reserve] sous elle dans [available], au plus [Large] et 40 % de [screenHeight] (légendes
     * « Face » / « Dos » comprises), jamais moins de [Small].
     */
    fun aboveList(available: Dp, screenHeight: Dp, reserve: Dp): Dp =
        minOf(Large, screenHeight * ABOVE_LIST_SCREEN_FRACTION - ViewLabelsHeight, available - reserve - ViewLabelsHeight)
            .coerceAtLeast(Small)
}

private val OutlineWidth = 1.dp
private val GapWidth = 1.5.dp

/** Anneau de sélection : un trait de 6 centré sur le bord (3 dehors), recouvert par l'écart de 2,5. */
private val RingStroke = 6.dp
private val RingGap = 2.5.dp

/** Distance au-delà de laquelle un toucher hors d'une forme ne choisit aucun muscle. */
private val TouchTolerance = 12.dp

private val ViewLabelGap = 6.dp

/** Place des légendes « Face » / « Dos » sous la carte (écart + une ligne de `small`). */
private val ViewLabelsHeight = 24.dp
private val SwatchSize = 10.dp
private val SwatchRadius = 3.dp
private val LegendGap = 10.dp
private val LegendLabelGap = 6.dp
private val LegendRowHeight = 48.dp
private val LegendPressBleed = 12.dp
private val LegendPressRadius = 12.dp

/**
 * Carte du corps — `BodyMap` (DESIGN.md § 9.1, SPEC § 15.17.4) : silhouettes de face et de dos (ou
 * une seule, [view]) et les 17 muscles, formes du SVG de la maquette. Un muscle de [primary] est plein
 * `textPrimary`, de [secondary] `bodySecondary`, sinon `bodyRest` ; un muscle de [selected] (filtre)
 * est plein, entouré d'un anneau. Les muscles sont séparés par un écart couleur de la surface dessous :
 * `surfaceRaised` si [onRaised] (groupe, sheet), sinon `background`. Les traits ne grandissent pas avec
 * la carte.
 *
 * La carte prend [height] (réduite si la largeur manque), la largeur suit. Avec [onMuscleClick], toucher
 * une forme (ou à 12 dp d'une forme) donne son muscle, et TalkBack lit un élément par muscle, dans
 * l'ordre de [Muscle] : case à cocher « Pecs » si [selectable] (filtre), sinon « Pecs, principal »
 * (bouton « Voir le muscle »). Sans action, la carte est décorative. [viewLabels] : « Face » et « Dos »
 * sous chaque moitié (grande carte du filtre).
 */
@Composable
fun BodyMap(
    height: Dp,
    modifier: Modifier = Modifier,
    primary: Collection<Muscle> = emptyList(),
    secondary: Collection<Muscle> = emptyList(),
    selected: Collection<Muscle> = emptyList(),
    view: BodyView? = null,
    onRaised: Boolean = false,
    selectable: Boolean = false,
    viewLabels: Boolean = false,
    onMuscleClick: ((Muscle) -> Unit)? = null,
) {
    val ratio = BodyMapGeometry.aspectRatio(view)
    BoxWithConstraints(modifier) {
        val mapHeight = if (constraints.hasBoundedWidth) min(height, maxWidth / ratio) else height
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ViewLabelGap)) {
        val colors = Studio.colors
        val gap = if (onRaised) colors.surfaceRaised else colors.background
        val fills = remember(primary, secondary, selected, colors) {
            Muscle.entries.associateWith { muscle ->
                when (muscle) {
                    in selected, in primary -> colors.textPrimary
                    in secondary -> colors.bodySecondary
                    else -> colors.bodyRest
                }
            }
        }
        val selectedSet = remember(selected) { selected.toSet() }
        val currentClick by rememberUpdatedState(onMuscleClick)
        val density = LocalDensity.current
        Box(
            Modifier
                .size(mapHeight * ratio, mapHeight)
                .clipToBounds()
                .drawWithCache {
                    val scale = size.height / BodyMapGeometry.MAP_HEIGHT
                    val origin = BodyMapGeometry.originX(view)
                    val silhouettes = BodyMapGeometry.silhouetteCommands.values.map { it.toPath(scale, origin) }
                    val muscles = BodyMapGeometry.muscleCommands.mapValues { it.value.toPath(scale, origin) }
                    val outline = Stroke(OutlineWidth.toPx(), join = StrokeJoin.Round)
                    val gapStroke = Stroke(GapWidth.toPx(), join = StrokeJoin.Round)
                    val ring = Stroke(RingStroke.toPx(), join = StrokeJoin.Round)
                    val ringGap = Stroke(RingGap.toPx(), join = StrokeJoin.Round)
                    onDrawBehind {
                        for (path in silhouettes) {
                            drawPath(path, colors.bodyBase)
                            drawPath(path, colors.bodyOutline, style = outline)
                        }
                        for ((muscle, path) in muscles) {
                            drawPath(path, fills.getValue(muscle))
                            drawPath(path, gap, style = gapStroke)
                        }
                        for ((muscle, path) in muscles) if (muscle in selectedSet) drawPath(path, colors.textPrimary, style = ring)
                        for ((muscle, path) in muscles) {
                            if (muscle !in selectedSet) continue
                            drawPath(path, colors.textPrimary)
                            drawPath(path, gap, style = ringGap)
                        }
                    }
                }
                .then(
                    if (onMuscleClick == null) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                            .semantics { isTraversalGroup = true }
                            .pointerInput(view) {
                                detectTapGestures { offset ->
                                    BodyMapGeometry.muscleAt(offset.x, offset.y, size.height.toFloat(), view, TouchTolerance.toPx())
                                        ?.let { currentClick?.invoke(it) }
                                }
                            }
                    },
                ),
        ) {
            if (onMuscleClick != null) {
                val heightPx = with(density) { mapHeight.toPx() }
                for (muscle in Muscle.entries) {
                    val bounds = BodyMapGeometry.bounds(muscle, heightPx, view) ?: continue
                    MuscleNode(muscle, bounds, primary, secondary, selectedSet, selectable) { currentClick?.invoke(muscle) }
                }
            }
        }
        if (viewLabels) ViewLabels(view, Modifier.width(mapHeight * ratio))
        }
    }
}

/** « Face » et « Dos » sous chaque moitié (décoratifs : TalkBack lit les muscles). */
@Composable
private fun ViewLabels(view: BodyView?, modifier: Modifier) {
    Row(modifier.clearAndSetSemantics {}) {
        val views = if (view == null) BodyView.entries else listOf(view)
        for (shown in views) {
            Text(
                stringResource(if (shown == BodyView.Front) R.string.body_map_front else R.string.body_map_back),
                Modifier.weight(1f),
                style = Studio.typography.small,
                color = Studio.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Élément TalkBack d'un muscle, posé sur sa forme (sans toucher : la carte le reçoit). */
@Composable
private fun MuscleNode(
    muscle: Muscle,
    bounds: BodyMapBounds,
    primary: Collection<Muscle>,
    secondary: Collection<Muscle>,
    selected: Set<Muscle>,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val name = stringResource(muscle.nameRes)
    val description = when {
        selectable -> name
        muscle in primary -> stringResource(R.string.muscle_state_primary, name)
        muscle in secondary -> stringResource(R.string.muscle_state_secondary, name)
        else -> stringResource(R.string.muscle_state_rest, name)
    }
    val actionLabel = stringResource(if (selectable) R.string.muscle_filter_action else R.string.muscle_open)
    Box(
        Modifier
            .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
            .size(with(density) { (bounds.right - bounds.left).toDp() }, with(density) { (bounds.bottom - bounds.top).toDp() })
            .semantics {
                contentDescription = description
                traversalIndex = muscle.ordinal.toFloat()
                if (selectable) {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(muscle in selected)
                } else {
                    role = Role.Button
                }
                onClick(label = actionLabel) {
                    onClick()
                    true
                }
            },
    )
}

/** Tracé de la carte à l'échelle : unités du SVG → px, la vue de dos ramenée à gauche. */
private fun List<PathCommand>.toPath(scale: Float, origin: Float): Path = Path().apply {
    fun x(value: Float) = (value - origin) * scale
    for (command in this@toPath) {
        when (command) {
            is PathCommand.MoveTo -> moveTo(x(command.x), command.y * scale)
            is PathCommand.LineTo -> lineTo(x(command.x), command.y * scale)
            is PathCommand.CubicTo -> cubicTo(
                x(command.x1), command.y1 * scale,
                x(command.x2), command.y2 * scale,
                x(command.x), command.y * scale,
            )
            PathCommand.Close -> close()
        }
    }
}

/**
 * Légende des muscles d'un exercice — `MuscleLegend` (DESIGN.md § 9.1, SPEC § 15.17.5), à côté de la
 * petite carte : étiquette « Principal » / « Principaux », une ligne par muscle (pastille pleine, nom en
 * 600), puis « Secondaire(s) » (pastille grise, nom en 500). Chaque ligne fait 48 au moins et ouvre le
 * muscle ([onMuscleClick]) ; pressée, fond `surfaceRaised` qui déborde de 12.
 */
@Composable
fun MuscleLegend(
    primary: List<Muscle>,
    secondary: List<Muscle>,
    onMuscleClick: (Muscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (primary.isNotEmpty()) {
            LegendLabel(pluralStringResource(R.plurals.exercise_muscles_primary, primary.size))
            for (muscle in primary) MuscleLegendRow(muscle, isPrimary = true, onClick = { onMuscleClick(muscle) })
        }
        if (secondary.isNotEmpty()) {
            if (primary.isNotEmpty()) Spacer(Modifier.height(LegendLabelGap))
            LegendLabel(pluralStringResource(R.plurals.exercise_muscles_secondary, secondary.size))
            for (muscle in secondary) MuscleLegendRow(muscle, isPrimary = false, onClick = { onMuscleClick(muscle) })
        }
    }
}

@Composable
private fun LegendLabel(text: String) {
    Text(
        text.uppercaseForDisplay(),
        Modifier
            .padding(bottom = LegendLabelGap)
            .semantics {
                heading()
                contentDescription = text
            },
        style = Studio.typography.label,
        color = Studio.colors.textSecondary,
    )
}

@Composable
private fun MuscleLegendRow(muscle: Muscle, isPrimary: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed = source.pressed()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LegendRowHeight)
            .pressedBackground(pressed, Studio.colors.surfaceRaised, LegendPressBleed, LegendPressRadius)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.muscle_open),
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LegendGap),
    ) {
        Box(
            Modifier
                .size(SwatchSize)
                .background(
                    if (isPrimary) Studio.colors.textPrimary else Studio.colors.bodySecondary,
                    RoundedCornerShape(SwatchRadius),
                ),
        )
        Text(
            stringResource(muscle.nameRes),
            Modifier.weight(1f),
            style = if (isPrimary) Studio.typography.bodyLineStrong else Studio.typography.bodyLine,
            color = Studio.colors.textPrimary,
        )
    }
}
