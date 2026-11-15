package com.musclenote.android.ui.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.designsystem.theme.StudioTheme

/*
 * Aperçus du design system « Studio épuré » (DESIGN.md). Source set `debug` uniquement, comme les
 * aperçus des écrans (`ui/preview/`). Chaque aperçu existe en sombre et en clair.
 */

/** Largeur d'une colonne des planches de composants de la maquette (1400 − 4 × 56) / 3. */
const val BOARD_COLUMN_DP = 392

/** Un composant, sur la largeur d'une colonne de planche (+ 16 de marge de chaque côté). */
@Preview(name = "Sombre", group = "Design system", locale = "fr", widthDp = BOARD_COLUMN_DP + 32, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clair", group = "Design system", locale = "fr", widthDp = BOARD_COLUMN_DP + 32, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class StudioComponentPreviews

/** Un écran de la maquette : 412 × 915 dp. */
@Preview(name = "Sombre", group = "Gabarits", locale = "fr", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clair", group = "Gabarits", locale = "fr", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class StudioScreenPreviews

/** Une planche de composants de la maquette : 1400 × 1320 dp. */
@Preview(name = "Sombre", group = "Planches", locale = "fr", widthDp = 1400, heightDp = 1320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clair", group = "Planches", locale = "fr", widthDp = 1400, heightDp = 1320, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class StudioBoardPreviews

/** Planche des jetons : 1400 × 1200 dp, comme dans la maquette. */
@Preview(name = "Sombre", group = "Planches", locale = "fr", widthDp = 1400, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clair", group = "Planches", locale = "fr", widthDp = 1400, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class StudioTokenBoardPreviews

/** Thème du design system selon le mode de l'aperçu, sur le fond. */
@Composable
fun StudioPreviewTheme(content: @Composable () -> Unit) {
    StudioTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.fillMaxSize().background(Studio.colors.background)) { content() }
    }
}

/** Un composant : fond, marge de 16, ses états empilés et légendés comme sur la planche. */
@Composable
fun StudioComponentPreview(content: @Composable ColumnScope.() -> Unit) {
    StudioTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier.background(Studio.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Légende d'un état, comme sur les planches (12, texte secondaire). */
@Composable
fun StateCaption(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        style = Studio.typography.small,
        color = Studio.colors.textSecondary,
    )
}
