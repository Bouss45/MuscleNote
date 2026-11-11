package com.musclenote.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalMuscleNoteColors = staticCompositionLocalOf { DarkColors }

/**
 * Thème de l'app : palettes sombre et claire (§ 9.1, pas de couleurs dynamiques), Inter
 * (§ 9.2) et rayons (§ 9.3). Le choix Système / Clair / Sombre (§ 2.12) arrivera avec les
 * préférences ; en attendant, le thème suit le système.
 */
@Composable
fun MuscleNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalMuscleNoteColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = MuscleNoteTypography,
            shapes = MuscleNoteShapes,
            content = content,
        )
    }
}

object MuscleNoteTheme {
    /** Jetons de couleur de l'app (§ 9.1), à préférer à `MaterialTheme.colorScheme`. */
    val colors: MuscleNoteColors
        @Composable @ReadOnlyComposable
        get() = LocalMuscleNoteColors.current
}

/** Correspondance avec les rôles Material 3, pour que ses composants prennent la palette. */
private fun MuscleNoteColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = primaryForeground,
        primaryContainer = primary,
        onPrimaryContainer = primaryForeground,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = secondaryForeground,
        secondaryContainer = secondary,
        onSecondaryContainer = secondaryForeground,
        tertiary = accent,
        onTertiary = Color.White,
        tertiaryContainer = accent,
        onTertiaryContainer = Color.White,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceTint = primary,
        inverseSurface = foreground,
        inverseOnSurface = background,
        error = destructive,
        onError = Color.White,
        errorContainer = destructive,
        onErrorContainer = Color.White,
        outline = border,
        outlineVariant = border,
        scrim = FixedColors.modalScrim,
        surfaceBright = card,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
    )
}
