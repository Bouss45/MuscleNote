package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/** Variante de bouton (DESIGN.md § 9.2). */
enum class ButtonVariant { Primary, Secondary, Text }

/** Taille de bouton : grand 56 (texte : 48), moyen 48, petit 44. */
enum class ButtonSize(val height: Dp) { Large(56.dp), Medium(48.dp), Small(44.dp) }

/** Ton du contenu d'un bouton texte ou icône : normal, ou discret (`textSecondary`). */
enum class ButtonTone { Default, Subtle }

private const val PrimaryPressedAlpha = 0.8f

/**
 * Marge intérieure d'un bouton plein : 4 (le texte est centré, le bouton prend la largeur de sa
 * colonne, parfois étroite) ; 12 pour un bouton texte ou petit ; [WideButtonPadding] (20) pour un
 * bouton à la largeur de son texte (état vide).
 */
private val FilledButtonPadding = 4.dp
private val TextButtonPadding = 12.dp
val WideButtonPadding = 20.dp
private val ButtonIconGap = 8.dp
private val ButtonIconSize = 22.dp

/**
 * Bouton — `StudioButton` (DESIGN.md § 9.2), rayon 12. Principal en inverse (le jaune reste au
 * direct), secondaire sur `surfaceControl`, texte transparent ; états pressé et désactivé.
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    tone: ButtonTone = ButtonTone.Default,
    horizontalPadding: Dp = if (variant == ButtonVariant.Text || size == ButtonSize.Small) TextButtonPadding else FilledButtonPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Studio.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val target = when {
        !enabled -> if (variant == ButtonVariant.Text) Color.Transparent else colors.surfaceControl
        variant == ButtonVariant.Primary -> colors.inverse
        variant == ButtonVariant.Secondary -> if (pressed) colors.surfacePressed else colors.surfaceControl
        else -> if (pressed) colors.surfaceRaised else colors.surfaceRaised.copy(alpha = 0f)
    }
    val background by animateColorAsState(target, Studio.motion.fast.spec(), label = "button")
    val alpha by animateFloatAsState(
        if (enabled && pressed && variant == ButtonVariant.Primary) PrimaryPressedAlpha else 1f,
        Studio.motion.fast.spec(),
        label = "buttonAlpha",
    )
    val content = when {
        !enabled -> colors.textTertiary
        variant == ButtonVariant.Primary -> colors.onInverse
        tone == ButtonTone.Subtle -> colors.textSecondary
        else -> colors.textPrimary
    }
    val height = if (variant == ButtonVariant.Text && size == ButtonSize.Large) ButtonSize.Medium.height else size.height
    val style = if (size == ButtonSize.Large) Studio.typography.action else Studio.typography.actionSmall
    Row(
        modifier
            .height(height)
            .alpha(alpha)
            .background(background, Studio.shapes.control)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ButtonIconGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) StudioIcon(icon, contentDescription = null, size = ButtonIconSize, tint = content)
        Text(text, style = style, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Bouton icône — `StudioIconButton` (DESIGN.md § 9.2) : rond, 48 (44 en petit), icône 24 ;
 * pressé sur `surfaceControl`.
 */
@Composable
fun StudioIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Default,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Studio.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val background by animateColorAsState(
        if (enabled && pressed) colors.surfaceControl else colors.surfaceControl.copy(alpha = 0f),
        Studio.motion.fast.spec(),
        label = "iconButton",
    )
    val tint = when {
        !enabled -> colors.textTertiary
        tone == ButtonTone.Subtle -> colors.textSecondary
        else -> colors.textPrimary
    }
    Box(
        modifier
            .size(if (size == ButtonSize.Small) ButtonSize.Small.height else ButtonSize.Medium.height)
            .background(background, Studio.shapes.full)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(icon, contentDescription = contentDescription, tint = tint)
    }
}
