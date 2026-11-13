package com.musclenote.android.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.theme.MuscleNoteTheme

/*
 * Aperçus Compose (@Preview) des écrans et des composants : base de comparaison avant / après
 * d'un changement de design. Source set `debug` uniquement : rien n'entre dans l'APK release et
 * aucun fichier de l'app n'est modifié. Rendus en PNG par `compose-preview` (voir CLAUDE.md) ou
 * dans Android Studio.
 */

/** Composant seul, en français, thèmes clair et sombre. */
@Preview(name = "Clair", group = "Composants", locale = "fr", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Sombre", group = "Composants", locale = "fr", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ComponentPreviews

/**
 * Écran entier sur un téléphone (largeur 411 dp), en français, thèmes clair et sombre. Hauteur
 * de [SCREEN_HEIGHT_DP] : les écrans qui défilent sont rendus en entier, pas seulement le haut.
 */
@Preview(name = "Clair", group = "Écrans", locale = "fr", widthDp = 411, heightDp = SCREEN_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Sombre", group = "Écrans", locale = "fr", widthDp = 411, heightDp = SCREEN_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ScreenPreviews

/** Hauteur des aperçus d'écran, assez grande pour les écrans qui défilent. */
const val SCREEN_HEIGHT_DP = 1800

/** Écran très long (Paramètres), rendu en entier. */
@Preview(name = "Clair", group = "Écrans", locale = "fr", widthDp = 411, heightDp = TALL_SCREEN_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Sombre", group = "Écrans", locale = "fr", widthDp = 411, heightDp = TALL_SCREEN_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class TallScreenPreviews

const val TALL_SCREEN_HEIGHT_DP = 2400

/**
 * Ce que montre l'écran d'un téléphone (411 × 891 dp) : écrans sans défilement, barre de
 * navigation, état vide.
 */
@Preview(name = "Clair", group = "Écrans", locale = "fr", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Sombre", group = "Écrans", locale = "fr", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PhonePreviews

/** Thème de l'app selon le mode de l'aperçu (clair ou sombre), sur le fond de l'app. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    MuscleNoteTheme(darkTheme = isSystemInDarkTheme()) {
        content()
    }
}

/** Composant seul : thème de l'aperçu, fond de l'app, marge de 16 dp. */
@Composable
fun ComponentPreview(content: @Composable () -> Unit) {
    PreviewTheme {
        Box(Modifier.background(MuscleNoteTheme.colors.background).padding(16.dp)) {
            content()
        }
    }
}
