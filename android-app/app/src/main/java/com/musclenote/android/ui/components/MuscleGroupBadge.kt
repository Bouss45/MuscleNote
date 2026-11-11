package com.musclenote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.ui.theme.MuscleGroupColors
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Couleur d'un groupe musculaire (§ 6.2), identique dans les deux thèmes : fonds, bordures, courbes. */
val MuscleGroup.color: Color
    get() = when (this) {
        MuscleGroup.Dos -> MuscleGroupColors.dos
        MuscleGroup.Pecs -> MuscleGroupColors.pecs
        MuscleGroup.Biceps -> MuscleGroupColors.biceps
        MuscleGroup.Triceps -> MuscleGroupColors.triceps
        MuscleGroup.Jambes -> MuscleGroupColors.jambes
        MuscleGroup.Epaules -> MuscleGroupColors.epaules
        MuscleGroup.Abdos -> MuscleGroupColors.abdos
        MuscleGroup.Cardio -> MuscleGroupColors.cardio
        MuscleGroup.AvantBras -> MuscleGroupColors.avantBras
    }

/**
 * Couleur du texte et des icônes d'un groupe : sa couleur en thème sombre, une nuance foncée en
 * thème clair (contraste ≥ 4,5:1, § 6.2). Fonds, bordures et courbes gardent [color].
 */
val MuscleGroup.textColor: Color
    @Composable @ReadOnlyComposable
    get() = if (MuscleNoteTheme.colors.isDark) {
        color
    } else {
        when (this) {
            MuscleGroup.Dos -> MuscleGroupColors.dosOnLight
            MuscleGroup.Pecs -> MuscleGroupColors.pecsOnLight
            MuscleGroup.Biceps -> MuscleGroupColors.bicepsOnLight
            MuscleGroup.Triceps -> MuscleGroupColors.tricepsOnLight
            MuscleGroup.Jambes -> MuscleGroupColors.jambesOnLight
            MuscleGroup.Epaules -> MuscleGroupColors.epaulesOnLight
            MuscleGroup.Abdos -> MuscleGroupColors.abdosOnLight
            MuscleGroup.Cardio -> MuscleGroupColors.cardioOnLight
            MuscleGroup.AvantBras -> MuscleGroupColors.avantBrasOnLight
        }
    }

/**
 * Badge de groupe (§ 6.2) : pilule au fond couleur à 13 %, bordure à 33 %, texte couleur
 * ([textColor]) ; 12 sp (padding 10 × 4), ou 10 sp en petit (7 × 2).
 */
@Composable
fun MuscleGroupBadge(group: MuscleGroup, modifier: Modifier = Modifier, small: Boolean = false) {
    val color = group.color
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.33f), shape)
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
    ) {
        Text(
            stringResource(group.labelRes),
            color = group.textColor,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
