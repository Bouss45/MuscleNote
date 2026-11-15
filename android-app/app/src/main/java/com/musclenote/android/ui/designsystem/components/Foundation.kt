package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

/* Briques communes aux composants du design system (DESIGN.md § 9). */

/** Tailles d'icône (DESIGN.md § 8). */
internal object IconSize {
    val Default = 24.dp
    val Row = 22.dp
    val Link = 20.dp
    val Chip = 18.dp
    val Empty = 28.dp
}

/** Écart entre un bouton icône aligné sur un bord et la marge : son icône tombe sur la marge. */
internal val IconButtonBleed = 12.dp

/** Icône Material Symbols Rounded (`R.drawable.ds_ic_…`), teinte du contenu par défaut. */
@Composable
fun StudioIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.Default,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Sens d'une variation : la couleur suit l'objectif, pas le signe (DESIGN.md § 1). */
enum class Trend { Up, Down, Neutral }

@Composable
internal fun Trend.color(): Color = when (this) {
    Trend.Up -> Studio.colors.up
    Trend.Down -> Studio.colors.down
    Trend.Neutral -> Studio.colors.textSecondary
}

/** Taille d'une variation : sous un chiffre héros, sous une valeur de ligne, ou seule en fin de ligne. */
enum class DeltaSize { Hero, Row, Line }

/**
 * Variation — `Delta` (DESIGN.md § 9.3) : texte signé (« +19,0 kg », « −12 reps », « Record »,
 * « identique ») coloré selon [trend], suivi de son contexte optionnel en texte secondaire.
 */
@Composable
fun Delta(
    value: String,
    trend: Trend,
    modifier: Modifier = Modifier,
    context: String? = null,
    size: DeltaSize = DeltaSize.Hero,
) {
    val style = when (size) {
        DeltaSize.Hero, DeltaSize.Line -> Studio.typography.emphasis
        DeltaSize.Row -> Studio.typography.captionStrong
    }
    val secondary = Studio.colors.textSecondary
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = trend.color())) { append(value) }
        if (context != null) {
            withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Normal)) { append(" $context") }
        }
    }
    Text(text, modifier = modifier, style = style)
}

/** Texte en capitales dans la langue de l'app (étiquettes « EN COURS », « REPOS »…). */
@Composable
internal fun String.uppercaseForDisplay(): String = uppercase(appLocale())

/** Point « en direct » — `LiveDot` : 8, aplat `live`. */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(Studio.colors.live, CircleShape))
}

/** Étiquette « en direct » — `LiveLabel` : point + 8 + étiquette en capitales `liveText`. */
@Composable
fun LiveLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        LiveDot()
        Text(text.uppercaseForDisplay(), style = Studio.typography.label, color = Studio.colors.liveText)
    }
}

/**
 * Chiffre qui défile verticalement quand il change (`Studio.motion.numberChangeMillis`), sans
 * animation en boucle ; [animate] faux : il change sans défiler (valeur qui suit le doigt sur une
 * courbe). Expose sa ligne de base pour s'aligner sur une unité.
 *
 * L'interligne du style est appliqué comme en CSS : une boîte de la hauteur de l'interligne,
 * glyphes centrés, quitte à déborder (interligne 0,85 des grands chiffres). Compose, lui, ne
 * descend pas sous la hauteur de la police sur la première et la dernière ligne.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        // Plus large que sa place (texte à 200 %, colonne étroite) : le chiffre rétrécit, jusqu'à 60 %.
        // Remesuré à chaque pas : au-delà de 100 %, Android agrandit les grands textes moins que les
        // petits, une réduction en sp ne réduit donc pas la largeur d'autant.
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val fitted = remember(text, style, maxWidth, bounded) {
            var size = style.fontSize
            val min = style.fontSize * FIT_TEXT_MIN_SCALE
            var current = style
            if (bounded) {
                repeat(FIT_STEPS) {
                    val width = measurer.measure(text, current.copy(lineHeight = TextUnit.Unspecified)).size.width
                    if (width <= maxWidth || size.value <= min.value) return@remember current
                    val next = size * (maxWidth.toFloat() / width).coerceAtMost(FIT_STEP_MAX)
                    size = if (next.value < min.value) min else next
                    current = style.copy(fontSize = size)
                }
            }
            current
        }
        RollingGlyphs(text, fitted, color, animate, measurer)
    }
}

@Composable
private fun RollingGlyphs(text: String, style: TextStyle, color: Color, animate: Boolean, measurer: TextMeasurer) {
    val density = LocalDensity.current
    val lineBox = with(density) {
        when {
            style.lineHeight.isSp -> style.lineHeight.toPx()
            style.lineHeight.isEm -> style.lineHeight.value * style.fontSize.toPx()
            else -> null
        }
    }
    val glyphStyle = style.copy(lineHeight = TextUnit.Unspecified)
    val baseline = remember(text, glyphStyle) { measurer.measure(text, glyphStyle).firstBaseline }
    val millis = Studio.motion.numberChangeMillis
    Layout(
        content = {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    if (animate) {
                        (slideInVertically(tween(millis)) { it } + fadeIn(tween(millis))) togetherWith
                            (slideOutVertically(tween(millis)) { -it } + fadeOut(tween(millis))) using
                            SizeTransform(clip = false)
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false)
                    }
                },
                label = "RollingText",
            ) { value ->
                Text(value, style = glyphStyle, color = color, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = lineBox?.roundToInt() ?: placeable.height
        val offset = (height - placeable.height) / 2
        val line = baseline.roundToInt() + offset
        layout(placeable.width, height, mapOf(FirstBaseline to line, LastBaseline to line)) {
            placeable.place(0, offset)
        }
    }
}

/** Élargit l'élément de [start] / [end] au-delà de ses contraintes (bouton icône sur un bord). */
internal fun Modifier.bleed(start: Dp = 0.dp, end: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val extra = (start + end).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-start.roundToPx(), 0) }
}

/** Filet de 1 en bas de l'élément. */
internal fun Modifier.bottomDivider(color: Color, show: Boolean = true, startInset: Dp = 0.dp): Modifier =
    if (!show) this else drawBehind {
        val stroke = 1.dp.toPx()
        // Filet en retrait au début (ligne d'un groupe) : il commence sous le texte, jusqu'au bord.
        val inset = startInset.toPx().coerceIn(0f, size.width)
        val left = if (layoutDirection == LayoutDirection.Ltr) inset else 0f
        drawRect(color, topLeft = Offset(left, size.height - stroke), size = Size(size.width - inset, stroke))
    }

/** Fond d'état pressé qui déborde de [bleed] de chaque côté (ligne de liste pressée). */
internal fun Modifier.pressedBackground(pressed: Boolean, color: Color, bleed: Dp, radius: Dp): Modifier =
    if (!pressed) this else drawBehind {
        val b = bleed.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(-b, 0f),
            size = Size(size.width + 2 * b, size.height),
            cornerRadius = CornerRadius(radius.toPx()),
        )
    }

/** État pressé d'un élément, lu sur sa source d'interactions. */
@Composable
internal fun MutableInteractionSource.pressed(): Boolean {
    val pressed by collectIsPressedAsState()
    return pressed
}

/**
 * Source d'interactions toujours pressée : sert aux aperçus pour montrer l'état « Pressé » des
 * planches de la maquette.
 */
@Immutable
class AlwaysPressedInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction> = flowOf(PressInteraction.Press(Offset.Zero))
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction): Boolean = true
}

/** Texte annoté : [parts] concaténés, chacun avec son style. */
internal fun annotated(vararg parts: Pair<String, SpanStyle>): AnnotatedString = buildAnnotatedString {
    parts.forEach { (text, style) -> withStyle(style) { append(text) } }
}

/** Plus petite taille d'un [FitText] ou d'un [RollingText], en part de sa taille normale. */
private const val FIT_TEXT_MIN_SCALE = 0.6f

/** Ajustement d'un [RollingText] : au plus 8 mesures, chacune réduisant d'au moins 3 %. */
private const val FIT_STEPS = 8
private const val FIT_STEP_MAX = 0.97f

/**
 * Un mot qui doit tenir sur une ligne dans une colonne étroite (libellé d'onglet, en-tête de
 * colonne) : avec un grand texte du système (200 %), la police rétrécit jusqu'à 60 % de sa taille
 * plutôt que de couper le mot.
 */
@Composable
internal fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * FIT_TEXT_MIN_SCALE, maxFontSize = style.fontSize),
    )
}
