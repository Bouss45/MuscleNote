package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val TrackHeight = 3.dp
private val TrackShape = RoundedCornerShape(2.dp)
private val SegmentGap = 3.dp

/**
 * Progression des séries — `SetProgress` (DESIGN.md § 9.3) : un segment par série prévue ;
 * faites en `textPrimary`, à faire en `surfacePressed`. Décoratif : le texte voisin donne le compte.
 */
@Composable
fun SetProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(TrackHeight).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        repeat(total.coerceAtLeast(1)) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (index < done) Studio.colors.textPrimary else Studio.colors.surfacePressed, TrackShape),
            )
        }
    }
}

/** Piste de repos : 3 de haut, `surfacePressed`, remplie en `live` selon [progress] (0 à 1). */
@Composable
fun LiveTrack(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .background(Studio.colors.surfacePressed, TrackShape)
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Studio.colors.live, TrackShape),
        )
    }
}

private val WeekBarsHeight = 28.dp
private val WeekBarGap = 6.dp
private val WeekBarShape = RoundedCornerShape(3.dp)

/** Une semaine des barres de l'Accueil : nombre de séances, semaine validée ou non. */
data class WeekBar(val count: Int, val validated: Boolean)

/**
 * Barres des semaines — `WeekBars` (DESIGN.md § 9.3) : 8 barres, hauteur proportionnelle (28
 * pour [maxCount]) ; validée en `textPrimary`, sinon `textTertiary` ; légende dessous.
 */
@Composable
fun WeekBars(
    weeks: List<WeekBar>,
    maxCount: Int,
    caption: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(WeekBarsHeight)
                .semantics { this.contentDescription = contentDescription },
            horizontalArrangement = Arrangement.spacedBy(WeekBarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            weeks.forEach { week ->
                val fraction = if (maxCount > 0) (week.count.toFloat() / maxCount).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .background(if (week.validated) Studio.colors.textPrimary else Studio.colors.textTertiary, WeekBarShape),
                )
            }
        }
        Text(caption, style = Studio.typography.small, color = Studio.colors.textSecondary)
    }
}
