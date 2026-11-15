package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/*
 * Listes groupées (DESIGN.md § 9.1, v2) : les lignes d'une section vivent dans un groupe tonal,
 * un cran au-dessus du fond (`surfaceRaised`), rayon 20, sans bordure ; c'est le ton qui sépare.
 * Une [ListRow] posée dans un groupe prend son retrait de 16, son filet en retrait et l'état pressé
 * `surfaceGroupPressed`.
 */

/** Rayon d'un groupe : celui de la surface (`Studio.shapes.surface`). */
private val GroupRadius = 20.dp

/**
 * Place d'un élément dans un groupe de liste paresseuse : seuls le haut du premier et le bas du
 * dernier sont arrondis ; [isLast] dit si l'élément porte encore un filet sous lui.
 */
enum class GroupPosition(val roundTop: Boolean, val roundBottom: Boolean) {
    Single(roundTop = true, roundBottom = true),
    First(roundTop = true, roundBottom = false),
    Middle(roundTop = false, roundBottom = false),
    Last(roundTop = false, roundBottom = true),
    ;

    /** Dernier élément du groupe : pas de filet sous lui. */
    val isLast: Boolean get() = roundBottom

    companion object {
        /** Position de l'élément [index] dans un groupe de [size] éléments. */
        fun of(index: Int, size: Int): GroupPosition = when {
            size <= 1 -> Single
            index <= 0 -> First
            index >= size - 1 -> Last
            else -> Middle
        }
    }
}

/** Vrai à l'intérieur d'un groupe : [ListRow] y prend la disposition des listes groupées. */
val LocalInListGroup = staticCompositionLocalOf { false }

/**
 * Groupe de liste — `ListGroup` : colonne sur `surfaceRaised`, rayon 20, qui découpe ses lignes
 * (l'état pressé d'une ligne reste dans le groupe). Lignes ([ListRow]) ou contenu libre (rangée de
 * stats, avec son padding de 16).
 */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Studio.shapes.surface)
            .background(Studio.colors.surfaceRaised),
    ) {
        CompositionLocalProvider(LocalInListGroup provides true) { content() }
    }
}

/**
 * Un élément d'un groupe de liste paresseuse (Journal) : le morceau du groupe à sa [position],
 * arrondi en haut du premier et en bas du dernier, collé à ses voisins.
 */
@Composable
fun ListGroupSegment(position: GroupPosition, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (position.roundTop) GroupRadius else 0.dp,
        topEnd = if (position.roundTop) GroupRadius else 0.dp,
        bottomStart = if (position.roundBottom) GroupRadius else 0.dp,
        bottomEnd = if (position.roundBottom) GroupRadius else 0.dp,
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Studio.colors.surfaceRaised),
    ) {
        CompositionLocalProvider(LocalInListGroup provides true, content = content)
    }
}
