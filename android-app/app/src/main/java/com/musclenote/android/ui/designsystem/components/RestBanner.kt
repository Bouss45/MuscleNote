package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val BannerTop = 12.dp
private val BannerBottom = 14.dp
private val BannerGap = 10.dp
private val FinishedTop = 8.dp
private val FinishedBottom = 12.dp

/**
 * Bandeau de repos en cours — `RestBanner` (DESIGN.md § 9.2) : entre deux séries, sans fond.
 * Temps restant en `liveText`, durée totale, piste remplie selon [progress] (temps écoulé, 0 à 1),
 * « −30 s » · « +30 s » · « Passer ».
 */
@Composable
fun RestBanner(
    label: String,
    remaining: String,
    total: String,
    progress: Float,
    minusLabel: String,
    plusLabel: String,
    skipLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .bottomDivider(Studio.colors.separator)
            .padding(top = BannerTop, bottom = BannerBottom),
        verticalArrangement = Arrangement.spacedBy(BannerGap),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                Text(label.uppercaseForDisplay(), style = Studio.typography.label, color = Studio.colors.liveText)
                RollingText(remaining, style = Studio.typography.numberLarge, color = Studio.colors.liveText)
            }
            Text(
                total,
                modifier = Modifier.padding(bottom = 2.dp),
                style = Studio.typography.body,
                color = Studio.colors.textSecondary,
            )
        }
        LiveTrack(progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            StudioButton(minusLabel, onMinus, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Medium)
            StudioButton(plusLabel, onPlus, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Medium)
            StudioButton(skipLabel, onSkip, Modifier.weight(1f), ButtonVariant.Text, ButtonSize.Medium)
        }
    }
}

/** Bandeau de repos terminé : « Repos terminé », détail, « Relancer » discret. */
@Composable
fun RestFinishedBanner(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .bottomDivider(Studio.colors.separator)
            .padding(top = FinishedTop, bottom = FinishedBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Studio.typography.action)
            Text(detail, style = Studio.typography.caption, color = Studio.colors.textSecondary)
        }
        StudioButton(actionLabel, onAction, variant = ButtonVariant.Text, size = ButtonSize.Medium, tone = ButtonTone.Subtle)
    }
}
