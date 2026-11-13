package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val ChipHeight = 32.dp
private val ChipPaddingWithIcon = 10.dp
private val ChipPadding = 12.dp

/** Ton d'une puce informative (DESIGN.md § 9.1). */
enum class ChipTone { Neutral, Up, Down, Live, Record }

/**
 * Puce informative — `StudioChip` (DESIGN.md § 9.1) : 32 de haut, rayon 12. Neutre (icône
 * optionnelle en texte secondaire), hausse, baisse, en direct, record. Avec [onClick], elle porte
 * une action (suggestion de charge à appliquer) ; [onClickLabel] la décrit.
 */
@Composable
fun StudioChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = Studio.colors
    val (background, content) = when (tone) {
        ChipTone.Neutral -> colors.surfaceControl to colors.textPrimary
        ChipTone.Up, ChipTone.Record -> colors.upDim to colors.up
        ChipTone.Down -> colors.downDim to colors.down
        ChipTone.Live -> colors.liveDim to colors.liveText
    }
    val toneIcon = when (tone) {
        ChipTone.Neutral -> icon
        ChipTone.Up -> R.drawable.ds_ic_arrow_upward
        ChipTone.Down -> R.drawable.ds_ic_arrow_downward
        ChipTone.Record -> R.drawable.ds_ic_emoji_events
        ChipTone.Live -> null
    }
    val hasLeading = toneIcon != null || tone == ChipTone.Live
    val gap = if (tone == ChipTone.Neutral || tone == ChipTone.Live) 6.dp else Studio.spacing.xxs
    val action = if (onClick != null) {
        Modifier.clip(Studio.shapes.control).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    ChipBox(background, if (hasLeading) ChipPaddingWithIcon else ChipPadding, gap, modifier.then(action)) {
        if (tone == ChipTone.Live) LiveDot()
        if (toneIcon != null) {
            StudioIcon(
                toneIcon,
                contentDescription = null,
                size = IconSize.Chip,
                tint = if (tone == ChipTone.Neutral) colors.textSecondary else content,
            )
        }
        Text(
            text,
            style = Studio.typography.body.copy(fontWeight = if (tone == ChipTone.Neutral) FontWeight.Normal else FontWeight.Bold),
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * Puce de filtre ou de saisie rapide — `FilterChip` (DESIGN.md § 9.1) : sélectionnée en inverse
 * (700), sinon `surfaceControl` (400).
 */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    ChipBox(
        background = if (selected) colors.inverse else colors.surfaceControl,
        horizontalPadding = ChipPadding,
        gap = 0.dp,
        modifier = modifier
            .clip(Studio.shapes.control)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text,
            style = Studio.typography.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) colors.onInverse else colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChipBox(
    background: Color,
    horizontalPadding: Dp,
    gap: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .height(ChipHeight)
            .background(background, Studio.shapes.control)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) { content() }
}
