package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val EmptyIconBox = 64.dp
private val EmptyGap = 10.dp
private val EmptyMessageWidth = 300.dp

/**
 * État vide — `EmptyState` (DESIGN.md § 9.2) : pastille et icône, titre, une phrase, une action
 * (bouton secondaire moyen).
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(start = Studio.spacing.s, end = Studio.spacing.s, top = Studio.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmptyGap),
    ) {
        Box(
            Modifier.size(EmptyIconBox).background(Studio.colors.surfaceControl, Studio.shapes.full),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(icon, contentDescription = null, size = IconSize.Empty, tint = Studio.colors.textSecondary)
        }
        Text(title, style = Studio.typography.titleItem, textAlign = TextAlign.Center)
        Text(
            message,
            modifier = Modifier.widthIn(max = EmptyMessageWidth),
            style = Studio.typography.paragraph,
            color = Studio.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        StudioButton(
            actionLabel,
            onAction,
            Modifier.padding(top = Studio.spacing.xxs),
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Medium,
            horizontalPadding = WideButtonPadding,
        )
    }
}
