package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/** Un onglet : libellé, icône contour et icône pleine (sélectionné). */
@Immutable
data class NavItem(
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
)

private val NavTop = 10.dp
private val NavItemsHeight = 53.dp
private val NavIconGap = 4.dp

/**
 * Barre d'onglets — `StudioNavigationBar` (DESIGN.md § 9.2) : filet en haut, 4 colonnes égales,
 * icône 24 + libellé `small`. Sélectionné : icône pleine, `textPrimary`, 700. Pas d'indicateur.
 * [bottomInset] : inset de la barre de navigation du système.
 */
@Composable
fun StudioNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Studio.colors.background)
            .topDivider()
            .padding(top = NavTop, bottom = bottomInset)
            .height(NavItemsHeight)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val color = if (selected) Studio.colors.textPrimary else Studio.colors.textSecondary
            Column(
                Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NavIconGap),
            ) {
                StudioIcon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = color)
                Text(
                    item.label,
                    style = Studio.typography.small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Modifier.topDivider(): Modifier {
    val color = Studio.colors.separator
    return this.then(
        Modifier.background(color).padding(top = 1.dp).background(Studio.colors.background),
    )
}

private val MiniBarHeight = 56.dp
private val MiniBarStart = 16.dp
private val MiniBarEnd = 8.dp
private val MiniBarGap = 12.dp

/**
 * Mini-barre de séance — `MiniSessionBar` (DESIGN.md § 9.2) : la séance réduite, au-dessus des
 * onglets. Point en direct, « Push · repos 1:48 » (le temps en `liveText`), « Reprendre ».
 */
@Composable
fun MiniSessionBar(
    sessionName: String,
    status: String,
    time: String,
    actionLabel: String,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(MiniBarHeight)
            .background(Studio.colors.surfaceRaised, Studio.shapes.control)
            .clickable(role = Role.Button, onClick = onResume)
            .padding(start = MiniBarStart, end = MiniBarEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MiniBarGap),
    ) {
        LiveDot()
        Text(
            annotated(
                sessionName to SpanStyle(color = Studio.colors.textPrimary),
                " · $status " to SpanStyle(color = Studio.colors.textSecondary, fontWeight = FontWeight.Medium),
                time to SpanStyle(color = Studio.colors.liveText),
            ),
            modifier = Modifier.weight(1f),
            style = Studio.typography.actionSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StudioButton(actionLabel, onResume, variant = ButtonVariant.Text, size = ButtonSize.Small)
    }
}
