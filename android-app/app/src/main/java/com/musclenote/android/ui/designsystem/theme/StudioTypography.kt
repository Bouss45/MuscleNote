package com.musclenote.android.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.musclenote.android.R

/*
 * Typographie « Studio épuré » (DESIGN.md § 3) : Archivo, police variable embarquée
 * (`res/font/archivo.ttf`, SIL OFL 1.1), en deux largeurs. Les polices téléchargeables ne donnent
 * pas l'axe de largeur, d'où l'embarquement.
 */

private const val WIDTH_NORMAL = 100f
private const val WIDTH_CONDENSED = 75f

private fun archivo(weight: Int, width: Float) = Font(
    resId = R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
    ),
)

/** Archivo en largeur normale : tous les textes, et les chiffres de moins de 24 sp. */
val ArchivoFamily = FontFamily(
    archivo(400, WIDTH_NORMAL),
    archivo(500, WIDTH_NORMAL),
    archivo(600, WIDTH_NORMAL),
    archivo(700, WIDTH_NORMAL),
    archivo(800, WIDTH_NORMAL),
)

/** Archivo condensée (largeur 75) : les chiffres de 24 sp et plus. */
val ArchivoCondensedFamily = FontFamily(
    archivo(600, WIDTH_CONDENSED),
    archivo(700, WIDTH_CONDENSED),
)

/** Chiffres tabulaires et alignés, partout : rien ne bouge quand une valeur change. */
private const val NUMBER_FEATURES = "'tnum', 'lnum'"

private val BaseStyle = TextStyle(
    fontFamily = ArchivoFamily,
    fontFeatureSettings = NUMBER_FEATURES,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private fun text(size: Int, weight: Int, lineHeight: TextUnit = TextUnit.Unspecified, letterSpacing: TextUnit = TextUnit.Unspecified) =
    BaseStyle.copy(
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )

private fun number(size: Int, weight: Int, lineHeight: TextUnit = 1.em, letterSpacing: TextUnit = TextUnit.Unspecified) =
    text(size, weight, lineHeight, letterSpacing).copy(fontFamily = ArchivoCondensedFamily)

/** Échelle typographique (DESIGN.md § 3). Les graisses d'état sont appliquées par les composants. */
@Immutable
data class StudioTypography(
    /** 96 · 700 · condensé — le chiffre héros, un par écran. */
    val numberHero: TextStyle = number(96, 700, lineHeight = 0.85.em, letterSpacing = (-0.01).em),
    /** 56 · 700 · condensé — repos, champ héros de formulaire. */
    val numberLarge: TextStyle = number(56, 700, lineHeight = 0.85.em),
    /** 32 · 700 · condensé — stats, valeurs de série, chrono d'en-tête. */
    val numberMedium: TextStyle = number(32, 700),
    /** 26 · 600 · condensé — touches chiffrées du pavé. */
    val numberKey: TextStyle = number(26, 600),
    /** 32 · 700 — titre de hub et de résumé. */
    val titleScreen: TextStyle = text(32, 700, letterSpacing = (-0.01).em),
    /** 28 · 700 — titre de fiche. */
    val titleFiche: TextStyle = text(28, 700, lineHeight = 1.1.em, letterSpacing = (-0.01).em),
    /** 22 · 700 — titre de bloc exercice. */
    val titleBlock: TextStyle = text(22, 700),
    /** 20 · 700 — en-tête de section, titre de bottom sheet. */
    val titleSection: TextStyle = text(20, 700),
    /** 20 · 600 — unité après un chiffre héros ou grand. */
    val unit: TextStyle = text(20, 600),
    /** 18 · 700 — titre d'en-tête en direct, d'état vide. */
    val titleItem: TextStyle = text(18, 700),
    /** 16 · 700 — bouton 56 dp, bouton texte. */
    val action: TextStyle = text(16, 700),
    /** 16 · 500 — libellé de ligne. */
    val bodyLine: TextStyle = text(16, 500),
    /** 16 · 600 — valeur de ligne. */
    val bodyLineStrong: TextStyle = text(16, 600),
    /** 15 · 700 — bouton 48 dp, mini-barre. */
    val actionSmall: TextStyle = text(15, 700),
    /** 15 · 600 — variation sous un héros, date de l'Accueil, sélecteur de période. */
    val emphasis: TextStyle = text(15, 600),
    /** 14 · 400 — puces, segmenté, lien, phrase d'aide d'un formulaire, ligne de texte courte. */
    val body: TextStyle = text(14, 400),
    /** 14 · 400 · interligne 1,45 — paragraphe : texte de sheet de confirmation, d'état vide. */
    val paragraph: TextStyle = text(14, 400, lineHeight = 1.45.em),
    /** 13 · 400 — légendes. */
    val caption: TextStyle = text(13, 400),
    /** 12 · 700 · capitales +12 % — « EN COURS », « REPOS » (le composant met en capitales). */
    val label: TextStyle = text(12, 700, letterSpacing = 0.12.em),
    /** 12 · 400 — onglets, axes de courbe, petites légendes. */
    val small: TextStyle = text(12, 400),
    /** 11 · 700 · capitales +12 % — en-têtes de colonnes des séries. */
    val columnHeader: TextStyle = text(11, 700, letterSpacing = 0.12.em),
)
