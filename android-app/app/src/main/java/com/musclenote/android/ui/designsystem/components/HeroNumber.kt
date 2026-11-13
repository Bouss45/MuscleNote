package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/** Écart entre la légende, le chiffre et la variation d'un chiffre héros. */
private val HeroGap = 6.dp

/**
 * Chiffre héros — `HeroNumber` (DESIGN.md § 9.1). Un seul par écran : légende, valeur
 * (`numberHero`) et unité, variation signée. Au toucher d'une courbe, l'écran passe la date et la
 * valeur du point.
 */
@Composable
fun HeroNumber(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    unit: String? = null,
    delta: String? = null,
    trend: Trend = Trend.Neutral,
    deltaContext: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HeroGap)) {
        if (label != null) {
            Text(label, style = Studio.typography.caption, color = Studio.colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HeroGap)) {
            RollingText(
                value,
                style = Studio.typography.numberHero,
                color = Studio.colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    unit,
                    modifier = Modifier.alignByBaseline(),
                    style = Studio.typography.unit,
                    color = Studio.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (delta != null) {
            Delta(delta, trend, context = deltaContext)
        }
    }
}

/**
 * Chiffre héros en direct (DESIGN.md § 9.1, état « En direct ») : étiquette en direct, valeur en
 * `liveText` (chrono de la séance), sans unité ni variation.
 */
@Composable
fun LiveHeroNumber(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HeroGap)) {
        LiveLabel(label)
        RollingText(value, style = Studio.typography.numberHero, color = Studio.colors.liveText)
    }
}
