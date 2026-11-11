package com.musclenote.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Jetons de couleur de l'app (SPEC § 9.1). Les noms reprennent ceux de l'app Expo ; Material 3
 * reçoit une correspondance dans [Theme.kt], mais les écrans lisent ces jetons via
 * `MuscleNoteTheme.colors`.
 *
 * Transparences : utiliser `couleur.copy(alpha = …)` (l'alpha Compose est au début de
 * `0xAARRGGBB`, contrairement au suffixe `#RRGGBBAA` de l'app Expo).
 */
@Immutable
data class MuscleNoteColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val calories: Color,
    val water: Color,
    val progressTrack: Color,
    val isDark: Boolean,
)

/** Palette sombre : celle de l'app Expo. */
val DarkColors = MuscleNoteColors(
    background = Color(0xFF080508),
    foreground = Color(0xFFF8F8F8),
    card = Color(0xFF150A10),
    cardForeground = Color(0xFFF8F8F8),
    primary = Color(0xFF9B1C1C),
    primaryForeground = Color(0xFFFFFFFF),
    secondary = Color(0xFF1E0E18),
    secondaryForeground = Color(0xFFD0D0D0),
    muted = Color(0xFF120A10),
    mutedForeground = Color(0xFF8A8A8A),
    accent = Color(0xFFE11D48),
    destructive = Color(0xFFEF4444),
    border = Color(0xFF2D1520),
    input = Color(0xFF1A0A14),
    success = Color(0xFF22C55E),
    warning = Color(0xFFEAB308),
    info = Color(0xFF38BDF8),
    calories = Color(0xFFE85A0A),
    water = Color(0xFF3B82F6),
    progressTrack = Color.White.copy(alpha = 0.09f),
    isDark = true,
)

/** Palette claire (🔄 D10). */
val LightColors = MuscleNoteColors(
    background = Color(0xFFFAF7F8),
    foreground = Color(0xFF1C1014),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF1C1014),
    primary = Color(0xFF9B1C1C),
    primaryForeground = Color(0xFFFFFFFF),
    secondary = Color(0xFFF3E9ED),
    secondaryForeground = Color(0xFF3D2B32),
    muted = Color(0xFFF2EBEE),
    mutedForeground = Color(0xFF6E6266),
    accent = Color(0xFFE11D48),
    destructive = Color(0xFFDC2626),
    border = Color(0xFFE6D7DD),
    input = Color(0xFFF7F1F3),
    success = Color(0xFF16A34A),
    warning = Color(0xFFCA8A04),
    info = Color(0xFF0369A1),
    calories = Color(0xFFC2410C),
    water = Color(0xFF2563EB),
    progressTrack = Color.Black.copy(alpha = 0.08f),
    isDark = false,
)

/** Couleurs fixes, identiques dans les deux thèmes (§ 9.1). */
object FixedColors {
    val csvExport = Color(0xFF217346)
    val dataImport = Color(0xFF3B82F6)
    val restTimerAmber = Color(0xFFF59E0B)
    val progressTop3 = listOf(Color(0xFF16A34A), Color(0xFF3B82F6), Color(0xFFA855F7))
    val modalScrim = Color.Black.copy(alpha = 0.6f)
}

/** Couleurs des groupes musculaires, par identifiant (§ 6.2), identiques dans les deux thèmes. */
object MuscleGroupColors {
    val dos = Color(0xFF3B82F6)
    val pecs = Color(0xFFF97316)
    val biceps = Color(0xFFA855F7)
    val triceps = Color(0xFF8B5CF6)
    val jambes = Color(0xFF22C55E)
    val epaules = Color(0xFFF59E0B)
    val abdos = Color(0xFFEF4444)
    val cardio = Color(0xFFEC4899)
    val avantBras = Color(0xFF14B8A6)
}
