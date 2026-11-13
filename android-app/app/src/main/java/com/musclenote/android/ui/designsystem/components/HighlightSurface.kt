package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val SurfaceGap = 14.dp

/**
 * Surface mise en avant — `HighlightSurface` (DESIGN.md § 9.2) : une seule par écran,
 * `surfaceRaised`, rayon 20, padding 20, éléments espacés de [spacing] (14).
 */
@Composable
fun HighlightSurface(
    modifier: Modifier = Modifier,
    spacing: Dp = SurfaceGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Studio.colors.surfaceRaised, Studio.shapes.surface)
            .padding(Studio.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Surface « En direct » — `LiveSessionCard` : la séance en cours sur l'Accueil. Chrono en
 * `liveText`, repos en cours, progression des séries, « Reprendre ».
 */
@Composable
fun LiveSessionCard(
    liveLabel: String,
    sessionName: String,
    elapsed: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    restLabel: String? = null,
    restTime: String? = null,
    restDetail: String? = null,
    setsDone: Int = 0,
    setsTotal: Int = 0,
    progressStart: String? = null,
    progressEnd: String? = null,
) {
    HighlightSurface(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LiveLabel(liveLabel, Modifier.weight(1f))
            Text(sessionName, style = Studio.typography.emphasis.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            RollingText(elapsed, style = Studio.typography.numberHero, color = Studio.colors.liveText)
            if (restTime != null) {
                val secondary = SpanStyle(color = Studio.colors.textSecondary)
                Text(
                    annotated(
                        "${restLabel.orEmpty()} " to secondary,
                        restTime to SpanStyle(color = Studio.colors.liveText, fontWeight = FontWeight.Bold),
                        (restDetail?.let { " · $it" } ?: "") to secondary,
                    ),
                    style = Studio.typography.body,
                    maxLines = 1,
                )
            }
        }
        if (setsTotal > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
                SetProgress(setsDone, setsTotal)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(progressStart.orEmpty(), style = Studio.typography.caption, color = Studio.colors.textSecondary)
                    Text(progressEnd.orEmpty(), style = Studio.typography.caption, color = Studio.colors.textSecondary)
                }
            }
        }
        StudioButton(actionLabel, onAction, Modifier.fillMaxWidth())
    }
}

/**
 * Surface « Prochaine séance » — `NextSessionCard` : type suggéré, puces des types, « Démarrer ».
 */
@Composable
fun NextSessionCard(
    caption: String,
    title: String,
    detail: String,
    types: List<String>,
    selectedType: Int,
    onSelectType: (Int) -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HighlightSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            Text(caption, style = Studio.typography.caption, color = Studio.colors.textSecondary)
            Text(title, style = Studio.typography.titleScreen.copy(lineHeight = Studio.typography.numberMedium.lineHeight), maxLines = 1)
            Text(detail, style = Studio.typography.caption, color = Studio.colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEachIndexed { index, type ->
                FilterChip(type, selected = index == selectedType, onClick = { onSelectType(index) })
            }
        }
        StudioButton(actionLabel, onAction, Modifier.fillMaxWidth())
    }
}

private val MetricHeaderHeight = 32.dp
private val MetricGap = 6.dp
private val MetricCurveGap = 10.dp

/**
 * Surface « Mesure » — `MetricSurface` (hub Progrès) : légende et bouton d'ajout, chiffre héros,
 * variation, courbe compacte.
 */
@Composable
fun MetricSurface(
    label: String,
    value: String,
    unit: String,
    points: List<CurvePoint>,
    curveDescription: String,
    addDescription: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    delta: String? = null,
    trend: Trend = Trend.Neutral,
    deltaContext: String? = null,
) {
    HighlightSurface(modifier, spacing = MetricGap) {
        Row(Modifier.fillMaxWidth().height(MetricHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = Studio.typography.caption, color = Studio.colors.textSecondary, maxLines = 1)
            StudioIconButton(
                R.drawable.ds_ic_add,
                contentDescription = addDescription,
                onClick = onAdd,
                size = ButtonSize.Small,
                modifier = Modifier.bleed(end = 10.dp),
            )
        }
        HeroNumber(value, unit = unit, delta = delta, trend = trend, deltaContext = deltaContext)
        Spacer(Modifier.height(MetricCurveGap - MetricGap))
        CompactTrendCurve(points, curveDescription)
    }
}
