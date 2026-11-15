package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val SurfaceGap = 14.dp
private val ChipGap = 6.dp

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
        // Le nom, au plus la moitié de la ligne, passe à la ligne s'il le faut (texte à 200 %).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LiveLabel(liveLabel, Modifier.weight(1f))
            Text(
                sessionName,
                Modifier.weight(1f, fill = false).padding(start = Studio.spacing.s),
                style = Studio.typography.emphasis.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
            )
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
 * Surface « Prochaine séance » — `NextSessionCard` : type suggéré, puces des types (sur plusieurs
 * lignes s'il le faut ; aucune rangée sans type), « Démarrer ». [selectedType] : -1 si aucune.
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
            Text(title, style = Studio.typography.titleScreen.copy(lineHeight = Studio.typography.numberMedium.lineHeight))
            Text(detail, style = Studio.typography.caption, color = Studio.colors.textSecondary)
        }
        if (types.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipGap), verticalArrangement = Arrangement.spacedBy(ChipGap)) {
                types.forEachIndexed { index, type ->
                    FilterChip(type, selected = index == selectedType, onClick = { onSelectType(index) })
                }
            }
        }
        StudioButton(actionLabel, onAction, Modifier.fillMaxWidth())
    }
}

private val MetricHeaderHeight = 32.dp
private val MetricGap = 6.dp
private val MetricCurveGap = 10.dp

/**
 * Bouton icône facultatif de l'en-tête d'une surface (DESIGN.md § 9.2) : [icon], son libellé TalkBack
 * [description], [onClick].
 */
data class SurfaceAction(@param:DrawableRes val icon: Int, val description: String, val onClick: () -> Unit)

/**
 * Surface « Mesure » — `MetricSurface` (hub Progrès, poids de corps) : légende et bouton d'ajout,
 * chiffre héros, variation, courbe compacte, puis une légende à gauche et à droite sous la courbe
 * ([footerStart] « 42 jours », [footerEnd] « 68 – 78,4 kg »). Sans valeur ([value] nul), la phrase
 * [emptyMessage] remplace le chiffre et la courbe. [secondaryAction] : un second bouton icône, avant
 * l'ajout (le rappel de pesée, SPEC § 15.13.3).
 */
@Composable
fun MetricSurface(
    label: String,
    value: String?,
    unit: String,
    points: List<CurvePoint>,
    curveDescription: String,
    addDescription: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    delta: String? = null,
    trend: Trend = Trend.Neutral,
    deltaContext: String? = null,
    footerStart: String? = null,
    footerEnd: String? = null,
    emptyMessage: String? = null,
    secondaryAction: SurfaceAction? = null,
) {
    HighlightSurface(modifier, spacing = MetricGap) {
        Row(Modifier.fillMaxWidth().heightIn(min = MetricHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = Studio.typography.caption, color = Studio.colors.textSecondary)
            if (secondaryAction != null) {
                StudioIconButton(
                    secondaryAction.icon,
                    contentDescription = secondaryAction.description,
                    onClick = secondaryAction.onClick,
                    size = ButtonSize.Small,
                )
            }
            StudioIconButton(
                R.drawable.ds_ic_add,
                contentDescription = addDescription,
                onClick = onAdd,
                size = ButtonSize.Small,
                modifier = Modifier.bleed(end = IconButtonBleed),
            )
        }
        if (value == null) {
            if (emptyMessage != null) {
                Text(emptyMessage, style = Studio.typography.paragraph, color = Studio.colors.textSecondary)
            }
            return@HighlightSurface
        }
        HeroNumber(value, unit = unit, delta = delta, trend = trend, deltaContext = deltaContext)
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(MetricCurveGap - MetricGap))
            CompactTrendCurve(points, curveDescription)
        }
        if (footerStart != null || footerEnd != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(footerStart.orEmpty(), Modifier.weight(1f, fill = false), style = Studio.typography.caption, color = Studio.colors.textSecondary)
                Text(footerEnd.orEmpty(), Modifier.weight(1f, fill = false), style = Studio.typography.caption, color = Studio.colors.textSecondary, textAlign = TextAlign.End)
            }
        }
    }
}


/**
 * Surface « Aujourd'hui » — `DailySurface` (hub Nutrition, SPEC § 15.10.1) : légende, chiffre héros
 * et unité (kcal du jour), état par rapport à l'objectif ([status], coloré par [statusTrend], suivi
 * de [statusContext]), piste de progression, rangée de stats (eau, compléments pris), puis deux
 * boutons secondaires moyens de largeurs égales (icône `add`).
 */
@Composable
fun DailySurface(
    label: String,
    value: String,
    unit: String,
    progress: Float,
    stats: List<Stat>,
    firstAction: String,
    onFirstAction: () -> Unit,
    secondAction: String,
    onSecondAction: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusTrend: Trend = Trend.Neutral,
    statusContext: String? = null,
) {
    HighlightSurface(modifier) {
        HeroNumber(value, label = label, unit = unit, delta = status, trend = statusTrend, deltaContext = statusContext)
        ProgressTrack(progress)
        if (stats.isNotEmpty()) StatRow(stats)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            StudioButton(
                firstAction,
                onFirstAction,
                Modifier.weight(1f),
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Medium,
                icon = R.drawable.ds_ic_add,
            )
            StudioButton(
                secondAction,
                onSecondAction,
                Modifier.weight(1f),
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Medium,
                icon = R.drawable.ds_ic_add,
            )
        }
    }
}
