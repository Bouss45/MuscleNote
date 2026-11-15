package com.musclenote.android.ui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

private val LocalStudioColors = staticCompositionLocalOf { StudioDarkColors }
private val LocalStudioTypography = staticCompositionLocalOf { StudioTypography() }
private val LocalStudioSpacing = staticCompositionLocalOf { StudioSpacing() }
private val LocalStudioShapes = staticCompositionLocalOf { StudioShapes() }
private val LocalStudioMotion = staticCompositionLocalOf { StudioMotion() }

/**
 * Thème du design system « Studio épuré » (DESIGN.md). [darkTheme] vient du choix Système /
 * Clair / Sombre de Paramètres. Fournit aussi un `MaterialTheme` aux couleurs et à la police du
 * design system, pour les composants Material encore utilisés à l'intérieur.
 */
@Composable
fun StudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) StudioDarkColors else StudioLightColors
    CompositionLocalProvider(
        LocalStudioColors provides colors,
        LocalStudioTypography provides StudioTypography(),
        LocalStudioSpacing provides StudioSpacing(),
        LocalStudioShapes provides StudioShapes(),
        LocalStudioMotion provides StudioMotion(),
    ) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = StudioMaterialTypography) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}

/** Accès aux jetons : `Studio.colors.live`, `Studio.typography.numberHero`… */
object Studio {
    val colors: StudioColors
        @Composable @ReadOnlyComposable
        get() = LocalStudioColors.current

    val typography: StudioTypography
        @Composable @ReadOnlyComposable
        get() = LocalStudioTypography.current

    val spacing: StudioSpacing
        @Composable @ReadOnlyComposable
        get() = LocalStudioSpacing.current

    val shapes: StudioShapes
        @Composable @ReadOnlyComposable
        get() = LocalStudioShapes.current

    val motion: StudioMotion
        @Composable @ReadOnlyComposable
        get() = LocalStudioMotion.current
}

/** Rôles Material 3 tirés des jetons, pour les composants Material (feuilles, champs…). */
private fun StudioColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = inverse,
        onPrimary = onInverse,
        primaryContainer = surfaceControl,
        onPrimaryContainer = textPrimary,
        inversePrimary = inverse,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = surfacePressed,
        onSecondaryContainer = textPrimary,
        tertiary = live,
        onTertiary = onLive,
        tertiaryContainer = liveDim,
        onTertiaryContainer = liveText,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceControl,
        onSurfaceVariant = textSecondary,
        surfaceTint = background,
        inverseSurface = inverse,
        inverseOnSurface = onInverse,
        error = down,
        onError = background,
        errorContainer = downDim,
        onErrorContainer = down,
        outline = textTertiary,
        outlineVariant = separator,
        scrim = scrim,
        surfaceBright = surfaceRaised,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceRaised,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceControl,
    )
}

private val StudioMaterialTypography: Typography = Typography().run {
    fun TextStyle.archivo() = copy(fontFamily = ArchivoFamily)
    Typography(
        displayLarge = displayLarge.archivo(),
        displayMedium = displayMedium.archivo(),
        displaySmall = displaySmall.archivo(),
        headlineLarge = headlineLarge.archivo(),
        headlineMedium = headlineMedium.archivo(),
        headlineSmall = headlineSmall.archivo(),
        titleLarge = titleLarge.archivo(),
        titleMedium = titleMedium.archivo(),
        titleSmall = titleSmall.archivo(),
        bodyLarge = bodyLarge.archivo(),
        bodyMedium = bodyMedium.archivo(),
        bodySmall = bodySmall.archivo(),
        labelLarge = labelLarge.archivo(),
        labelMedium = labelMedium.archivo(),
        labelSmall = labelSmall.archivo(),
    )
}
