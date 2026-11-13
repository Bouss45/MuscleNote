package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/** Une valeur d'une rangée de stats : chiffre déjà formaté et sa légende. */
@Immutable
data class Stat(val value: String, val label: String)

/**
 * Rangée de stats — `StatRow` (DESIGN.md § 9.1) : 2 à 4 valeurs sans fond, colonnes égales
 * espacées de 16 ; chiffre `numberMedium`, légende `caption`.
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Studio.spacing.m)) {
        stats.forEach { stat ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RollingText(stat.value, style = Studio.typography.numberMedium, color = Studio.colors.textPrimary)
                Text(stat.label, style = Studio.typography.caption, color = Studio.colors.textSecondary, maxLines = 1)
            }
        }
    }
}
