package com.musclenote.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Rayons utilisés par l'app (SPEC § 9.3) ; 12 dp est le rayon de référence. */
object Radius {
    val xs = 8.dp // petits boutons, champs de série, icônes 28×28
    val sm = 10.dp // lignes de série, recherche, rappels
    val md = 12.dp // cartes de stat, cartes de séance
    val lg = 14.dp // bouton principal, cartes d'exercice, échauffement
    val xl = 16.dp // carte de série hebdo
    val pill = 20.dp // puces, badges, modales
}

val MuscleNoteShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.xl),
    extraLarge = RoundedCornerShape(Radius.pill),
)
