package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

/*
 * Les deux planches de composants de la maquette (T2-Composants-1 / -2), recomposées avec les
 * composants du design system, à la même taille (1400 × 1320) : base de la comparaison côte à
 * côte avec l'export Claude Design. L'habillage des planches (titres, notes) est propre aux
 * aperçus.
 */

/** Titres des planches : Archivo 800, largeur 110 (habillage de la maquette, pas un jeton). */
private val BoardTitleFamily = FontFamily(
    Font(R.font.archivo, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(110f))),
)

@Composable
private fun Board(
    kicker: String,
    title: String,
    intro: String,
    gap: Dp,
    columnGap: Dp,
    columns: List<@Composable ColumnScope.() -> Unit>,
) = StudioPreviewTheme {
    Column(Modifier.fillMaxWidth().padding(56.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kicker.uppercase(), style = Studio.typography.label, color = Studio.colors.textSecondary)
                Text(
                    title,
                    style = TextStyle(fontFamily = BoardTitleFamily, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
                    color = Studio.colors.textPrimary,
                )
            }
            Text(
                intro,
                Modifier.widthIn(max = 520.dp),
                style = Studio.typography.emphasis.copy(fontWeight = FontWeight.Normal, lineHeight = 1.5.em),
                color = Studio.colors.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            columns.forEach { column ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(columnGap), content = column)
            }
        }
    }
}

/** Un composant sur la planche : son nom, une note à droite, puis ses états. */
@Composable
private fun BoardItem(name: String, note: String, gap: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(name, Modifier.weight(1f), style = Studio.typography.action)
            Text(note, style = Studio.typography.small, color = Studio.colors.textSecondary)
        }
        content()
    }
}

@StudioBoardPreviews
@Composable
private fun ComponentsBoard1Preview() = Board(
    kicker = "Composants 1",
    title = "Données et listes",
    intro = "Chaque composant n'utilise que des tokens. Le thème se change dans les réglages de la planche : la version claire, à droite, est ce même fichier.",
    gap = 40.dp,
    columnGap = 40.dp,
    columns = listOf(
        {
            BoardItem("Chiffre héros", "un seul par écran") { HeroNumberSamples() }
            BoardItem("En-tête de section", "titre.section") { SectionHeaderSamples() }
            BoardItem("Rangée de stats", "2 à 4 valeurs, sans fond") { StatRowSample() }
        },
        {
            BoardItem("Ligne de liste", "64 dp · filet entre les lignes", gap = 10.dp) { ListRowSamples() }
            BoardItem("Puce", "32 dp · rayon 12") {
                ChipSamples()
                Text(
                    "Neutre · hausse · baisse · en direct · record · filtre sélectionné / non sélectionné",
                    style = Studio.typography.small,
                    color = Studio.colors.textSecondary,
                )
            }
        },
        {
            BoardItem("Courbe", "1,5 dp · sans grille ni remplissage") { CurveSamples() }
            BoardItem("Sélecteur de période", "texte seul", gap = 10.dp) { PeriodSelectorSample() }
            BoardItem("Contrôle segmenté", "2 à 4 options", gap = 10.dp) { SegmentedSamples() }
            BoardItem("Assemblage : une section", "en-tête + lignes, posé sur le fond", gap = 10.dp) { SectionAssemblySample() }
        },
    ),
)

@StudioBoardPreviews
@Composable
private fun ComponentsBoard2Preview() = Board(
    kicker = "Composants 2",
    title = "Actions et saisie",
    intro = "Le bouton principal est en inverse (blanc sur sombre, noir sur clair) : le jaune reste réservé à ce qui se passe en direct.",
    gap = 28.dp,
    columnGap = 32.dp,
    columns = listOf(
        {
            BoardItem("Boutons", "56 dp · rayon 12", gap = 12.dp) { ButtonSamples() }
            BoardItem("Surface mise en avant", "une seule par écran · rayon 20", gap = 12.dp) { HighlightSurfaceSamples() }
        },
        {
            BoardItem("Ligne de série", "56 dp · case 48", gap = 8.dp) { SetRowSamples() }
            BoardItem("Bandeau de repos", "entre deux séries, sans fond", gap = 10.dp) { RestBannerSamples() }
            BoardItem("État vide", "une phrase, une action", gap = 12.dp) { EmptyStateSample() }
        },
        {
            BoardItem("Bottom sheet", "surface.avant · voile", gap = 12.dp) { BottomSheetSample() }
            BoardItem("Pavé numérique", "dans une bottom sheet", gap = 12.dp) {
                KeypadSample()
                Text(
                    "Touches de pas configurables : ±2,5 kg, ±1 rep, ±50 g… La touche principale reprend l'action de l'écran.",
                    style = Studio.typography.small,
                    color = Studio.colors.textSecondary,
                )
            }
            BoardItem("Mini-barre + onglets", "séance réduite", gap = 12.dp) { MiniBarAndNavSample() }
        },
    ),
)
