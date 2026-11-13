package com.musclenote.android.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Jetons de couleur du design system « Studio épuré » (DESIGN.md § 2). Noir, blanc et gris pour
 * tout ; trois couleurs qui ont un sens : [live] = en direct, [up] = progression, [down] = baisse.
 */
@Immutable
data class StudioColors(
    val isDark: Boolean,
    /** Fond de tous les écrans, de la fenêtre et du splash. */
    val background: Color,
    /** La seule surface mise en avant d'un écran, bottom sheet, mini-barre, état pressé léger. */
    val surfaceRaised: Color,
    /** Touches, segmenté, puces, bouton secondaire, pastille d'icône, filets sur une surface. */
    val surfaceControl: Color,
    /** État pressé, option sélectionnée, touches d'action, pistes de progression, poignée. */
    val surfacePressed: Color,
    /** Filets de liste (1 dp) posés sur le fond. */
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Pré-rempli, désactivé : chiffres de 24 sp et plus seulement (contraste 4:1). */
    val textTertiary: Color,
    /** Bouton principal, case validée, filtre sélectionné. */
    val inverse: Color,
    val onInverse: Color,
    /** Aplat « en direct » : point en cours, barre de repos. */
    val live: Color,
    /** Texte « en direct » : chrono, repos, curseur de saisie. */
    val liveText: Color,
    /** Fond du champ en saisie, de la puce en direct. */
    val liveDim: Color,
    val onLive: Color,
    val up: Color,
    val upDim: Color,
    val down: Color,
    val downDim: Color,
    /** Voile sous une bottom sheet. */
    val scrim: Color,
)

val StudioDarkColors = StudioColors(
    isDark = true,
    background = Color(0xFF0F1012),
    surfaceRaised = Color(0xFF1C1D21),
    surfaceControl = Color(0xFF2A2C31),
    surfacePressed = Color(0xFF36383E),
    separator = Color(0xFF222428),
    textPrimary = Color(0xFFF3F3F1),
    textSecondary = Color(0xFFA2A5AB),
    textTertiary = Color(0xFF6F737A),
    inverse = Color(0xFFF3F3F1),
    onInverse = Color(0xFF0F1012),
    live = Color(0xFFFFC93D),
    liveText = Color(0xFFFFC93D),
    liveDim = Color(0xFF332C14),
    onLive = Color(0xFF17140A),
    up = Color(0xFF3DD68C),
    upDim = Color(0xFF12301F),
    down = Color(0xFFFF6B5E),
    downDim = Color(0xFF3A1714),
    scrim = Color(0x99000000),
)

val StudioLightColors = StudioColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F2F0),
    surfaceControl = Color(0xFFE7E7E4),
    surfacePressed = Color(0xFFD9D9D5),
    separator = Color(0xFFEBEBE8),
    textPrimary = Color(0xFF121315),
    textSecondary = Color(0xFF5C5F65),
    textTertiary = Color(0xFF85888E),
    inverse = Color(0xFF121315),
    onInverse = Color(0xFFFFFFFF),
    live = Color(0xFFFFC93D),
    liveText = Color(0xFF7A5900),
    liveDim = Color(0xFFFFF0C2),
    onLive = Color(0xFF17140A),
    up = Color(0xFF0B7A43),
    upDim = Color(0xFFE3F5EA),
    down = Color(0xFFC62D22),
    downDim = Color(0xFFFCE6E3),
    scrim = Color(0x66121315),
)
