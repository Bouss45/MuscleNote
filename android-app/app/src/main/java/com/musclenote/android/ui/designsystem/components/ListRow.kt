package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val RowGap = 14.dp
private val RowHeightWithTags = 72.dp
private val LeadingSize = 40.dp
private val PressBleed = 12.dp
private val PressRadius = 12.dp

/** Fin d'une ligne de liste (DESIGN.md § 9.1). */
sealed interface ListRowTrailing {
    /**
     * Valeur `bodyLineStrong`, avec sous elle une variation optionnelle (`caption` 600).
     * [valueTrend] colore la valeur elle-même (stock bas en `down`).
     */
    @Immutable
    data class Value(
        val value: String,
        val delta: String? = null,
        val trend: Trend = Trend.Neutral,
        val valueTrend: Trend? = null,
    ) : ListRowTrailing

    /** Variation seule, en `emphasis` (« +2 kg », « identique »). */
    @Immutable
    data class DeltaOnly(val delta: String, val trend: Trend) : ListRowTrailing

    /** Chevron : la ligne ouvre un autre écran. */
    data object Chevron : ListRowTrailing
}

/**
 * Ligne de liste — `ListRow` (DESIGN.md § 9.1) : 64 de haut (72 avec des mini-puces), filet en
 * bas ; pastille d'icône optionnelle, titre et sous-titre ou mini-puces, fin au choix. Pressée :
 * fond `surfaceRaised` qui déborde de 12.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    tags: List<String> = emptyList(),
    trailing: ListRowTrailing? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val minHeight = if (tags.isEmpty()) Studio.spacing.row else RowHeightWithTags
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .pressedBackground(pressed, Studio.colors.surfaceRaised, PressBleed, PressRadius)
            .bottomDivider(Studio.colors.separator, show = showDivider && !pressed)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = Studio.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.size(LeadingSize).background(Studio.colors.surfaceControl, Studio.shapes.full),
                contentAlignment = Alignment.Center,
            ) {
                StudioIcon(leadingIcon, contentDescription = null, size = IconSize.Row, tint = Studio.colors.textPrimary)
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (tags.isEmpty()) 3.dp else Studio.spacing.xs),
        ) {
            Text(title, style = Studio.typography.bodyLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = Studio.typography.caption,
                    color = Studio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                    tags.forEach { MiniTag(it) }
                }
            }
        }
        when (trailing) {
            is ListRowTrailing.Value -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    trailing.value,
                    style = Studio.typography.bodyLineStrong,
                    color = trailing.valueTrend?.color() ?: Studio.colors.textPrimary,
                    maxLines = 1,
                )
                if (trailing.delta != null) Delta(trailing.delta, trailing.trend, size = DeltaSize.Row)
            }
            is ListRowTrailing.DeltaOnly -> Delta(trailing.delta, trailing.trend, size = DeltaSize.Line)
            ListRowTrailing.Chevron -> StudioIcon(
                R.drawable.ds_ic_chevron_right,
                contentDescription = null,
                tint = Studio.colors.textSecondary,
            )
            null -> Unit
        }
    }
}

/** Mini-puce d'une ligne (« 80 × 8 ») : `caption` sur `surfaceControl`, rayon 8. */
@Composable
fun MiniTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Studio.colors.surfaceControl, RoundedCornerShape(8.dp))
            .padding(horizontal = Studio.spacing.xs, vertical = 3.dp),
        style = Studio.typography.caption,
        maxLines = 1,
    )
}
