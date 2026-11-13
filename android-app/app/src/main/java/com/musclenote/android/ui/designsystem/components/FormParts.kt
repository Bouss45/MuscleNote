package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val HeroFieldHeight = 76.dp
private val HeroFieldPadding = 16.dp
private val HeroFieldGap = 10.dp
private val HeroCaretWidth = 2.dp
private val HeroCaretHeight = 40.dp

/**
 * Champ héros de formulaire — `FormHeroField` (DESIGN.md § 9.3) : 76 de haut, fond `liveDim` en
 * saisie, valeur `numberLarge`, curseur `liveText`, unité en texte secondaire.
 */
@Composable
fun FormHeroField(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    editing: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(HeroFieldHeight)
            .background(if (editing) Studio.colors.liveDim else Studio.colors.surfaceControl, Studio.shapes.control)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = HeroFieldPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeroFieldGap),
    ) {
        Text(value, style = Studio.typography.numberLarge.copy(lineHeight = Studio.typography.numberMedium.lineHeight), maxLines = 1)
        if (editing) Box(Modifier.size(HeroCaretWidth, HeroCaretHeight).background(Studio.colors.liveText))
        Text(unit, style = Studio.typography.unit, color = Studio.colors.textSecondary, maxLines = 1)
    }
}

private val InfoRowHeight = 52.dp

/**
 * Ligne d'information — `InfoRow` (DESIGN.md § 9.3) : 52 de haut, libellé et valeur, chevron si
 * elle s'ouvre ; filet `surfaceControl` (posée sur une sheet).
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(InfoRowHeight)
            .bottomDivider(Studio.colors.surfaceControl, showDivider)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = Studio.typography.bodyLine, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = Studio.typography.bodyLineStrong,
                color = if (valueColor == Color.Unspecified) Studio.colors.textPrimary else valueColor,
                maxLines = 1,
            )
            if (onClick != null) {
                StudioIcon(R.drawable.ds_ic_chevron_right, contentDescription = null, size = IconSize.Row, tint = Studio.colors.textSecondary)
            }
        }
    }
}

private val ActionBarTop = 12.dp

/**
 * Barre d'action — `BottomActionBar` (DESIGN.md § 9.3) : sur le fond, un bouton principal pleine
 * largeur ; [bottomInset] = inset de navigation, + 4.
 */
@Composable
fun BottomActionBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    bottomInset: Dp = 0.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Studio.colors.background)
            .padding(
                start = Studio.spacing.screen,
                end = Studio.spacing.screen,
                top = ActionBarTop,
                bottom = Studio.spacing.xxs + bottomInset,
            ),
    ) {
        StudioButton(label, onClick, Modifier.fillMaxWidth(), icon = icon, enabled = enabled)
    }
}
