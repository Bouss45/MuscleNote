package com.musclenote.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val PeriodItemHeight = 44.dp
private val PeriodItemMinWidth = 48.dp
private val UnderlineThickness = 2.dp
private val UnderlineOffset = 6.dp

/**
 * Sélecteur de période — `PeriodSelector` (DESIGN.md § 9.1) : texte seul, options réparties sur
 * la largeur (1M · 3M · 6M · 1A · Tout) ; la sélection passe en 800, `textPrimary`, soulignée.
 */
@Composable
fun PeriodSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val underline = Studio.colors.textPrimary
            val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
            Box(
                Modifier
                    .height(PeriodItemHeight)
                    .widthIn(min = PeriodItemMinWidth)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    modifier = if (!selected) {
                        Modifier
                    } else {
                        Modifier.drawBehind {
                            val result = layout.value ?: return@drawBehind
                            val y = result.firstBaseline + UnderlineOffset.toPx()
                            drawRect(underline, Offset(0f, y), Size(size.width, UnderlineThickness.toPx()))
                        }
                    },
                    style = Studio.typography.emphasis.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (selected) Studio.colors.textPrimary else Studio.colors.textSecondary,
                    maxLines = 1,
                    onTextLayout = { layout.value = it },
                )
            }
        }
    }
}

private val SegmentInset = 4.dp
private val SegmentHeight = 40.dp
private val CompactSegmentHeight = 36.dp
private val CompactSegmentPadding = 14.dp
private val SegmentShape = RoundedCornerShape(9.dp)

/**
 * Contrôle segmenté — `SegmentedControl` (DESIGN.md § 9.1) : 2 à 4 options sur `surfaceControl`,
 * l'option choisie sur `surfacePressed`. [compact] : options à la largeur du contenu (en-tête du
 * pavé numérique).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .background(Studio.colors.surfaceControl, Studio.shapes.control)
            .padding(SegmentInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentInset),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                if (selected) Studio.colors.surfacePressed else Studio.colors.surfaceControl.copy(alpha = 0f),
                Studio.motion.fast.spec(),
                label = "segment",
            )
            Box(
                Modifier
                    .then(if (compact) Modifier.padding(horizontal = 0.dp) else Modifier.weight(1f))
                    .height(if (compact) CompactSegmentHeight else SegmentHeight)
                    .background(background, SegmentShape)
                    .clip(SegmentShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .then(if (compact) Modifier.padding(horizontal = CompactSegmentPadding) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Studio.typography.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (selected) Studio.colors.textPrimary else Studio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
