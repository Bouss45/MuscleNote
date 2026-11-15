package com.musclenote.android.ui.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Espacements, base 4 (DESIGN.md § 4). */
@Immutable
data class StudioSpacing(
    /** 4 — titre de section → première ligne, période sous la courbe. */
    val xxs: Dp = 4.dp,
    /** 8 — dans un groupe. */
    val xs: Dp = 8.dp,
    /** 12 — titre de section → contenu, barre de titre → surface. */
    val s: Dp = 12.dp,
    /** 16 — padding vertical de ligne, colonnes d'une rangée de stats. */
    val m: Dp = 16.dp,
    /** 20 — marge d'écran, padding de surface et de sheet. */
    val screen: Dp = 20.dp,
    /** 24 — bloc exercice suivant, stats sous la période. */
    val l: Dp = 24.dp,
    /** 32 — autour du chiffre héros. */
    val hero: Dp = 32.dp,
    /** 40 — entre sections. */
    val section: Dp = 40.dp,
    /** 48 — cible tactile minimale. */
    val touch: Dp = 48.dp,
    /** 64 — hauteur de ligne de liste. */
    val row: Dp = 64.dp,
)

/** Rayons (DESIGN.md § 5). */
@Immutable
data class StudioShapes(
    /** 12 — boutons, puces, touches, segmenté, champ, case de série. */
    val control: Shape = RoundedCornerShape(12.dp),
    /** 20 — surface mise en avant. */
    val surface: Shape = RoundedCornerShape(20.dp),
    /** 20 en haut seulement — bottom sheet. */
    val sheet: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    /** Cercle — points, pastilles, boutons icône. */
    val full: Shape = CircleShape,
)

/** Mouvements (DESIGN.md § 7). Aucune animation en boucle. */
@Immutable
data class StudioMotion(
    /** Pressions, changements d'état : sans rebond, raideur 1500. */
    val fast: SpringSpec = SpringSpec(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
    /** Bottom sheet, transitions d'écran : sans rebond, raideur 400. */
    val standard: SpringSpec = SpringSpec(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
    /** Série validée, record : rebond léger 0,75, raideur 1500. */
    val expressive: SpringSpec = SpringSpec(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
    /** Un chiffre qui change défile verticalement, en 250 ms. */
    val numberChangeMillis: Int = 250,
)

/** Paramètres d'un ressort, convertis en spécification d'animation pour n'importe quel type. */
@Immutable
data class SpringSpec(val dampingRatio: Float, val stiffness: Float) {
    fun <T> spec(): FiniteAnimationSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}
