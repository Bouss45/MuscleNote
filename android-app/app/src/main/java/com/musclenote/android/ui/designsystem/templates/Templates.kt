package com.musclenote.android.ui.designsystem.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.theme.Studio

/*
 * Les quatre gabarits d'écran (DESIGN.md § 10) : hub, fiche, en direct, formulaire. Tout écran est
 * l'un d'eux ; ils posent les marges, les insets et le rythme vertical, l'écran remplit les
 * emplacements avec des composants.
 */

/** Insets du système : barre d'état en haut, barre de navigation en bas. */
@Immutable
data class ScreenInsets(val top: Dp, val bottom: Dp)

/** Insets réels de la fenêtre. Les aperçus passent ceux de la maquette (40 en haut, 24 en bas). */
@Composable
fun systemScreenInsets(): ScreenInsets = ScreenInsets(
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
)

/** Écart entre la mini-barre et la barre d'onglets. */
private val MiniBarGap = 8.dp

/**
 * Gabarit **hub** (DESIGN.md § 10.1) : barre de titre, au plus une surface mise en avant, sections
 * espacées de 40 ; en bas, la mini-barre éventuelle et la barre d'onglets ([navigation] reçoit
 * l'inset du bas). Accueil, Journal, Progrès, Nutrition, Paramètres, Mes records.
 */
@Composable
fun HubTemplate(
    titleBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    highlight: (@Composable () -> Unit)? = null,
    miniBar: (@Composable () -> Unit)? = null,
    navigation: (@Composable (bottomInset: Dp) -> Unit)? = null,
    insets: ScreenInsets = systemScreenInsets(),
    sections: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Studio.spacing.screen, end = Studio.spacing.screen, top = insets.top),
        ) {
            titleBar()
            Spacer(Modifier.height(Studio.spacing.s))
            if (highlight != null) {
                highlight()
                Spacer(Modifier.height(Studio.spacing.section))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.section), content = sections)
            Spacer(Modifier.height(if (navigation == null) insets.bottom + Studio.spacing.section else Studio.spacing.section))
        }
        if (miniBar != null) {
            Column(Modifier.padding(horizontal = Studio.spacing.screen)) {
                miniBar()
                Spacer(Modifier.height(MiniBarGap))
            }
        }
        navigation?.invoke(insets.bottom)
    }
}

/**
 * Gabarit **fiche** (DESIGN.md § 10.2) : un sujet, un chiffre héros. Barre du haut, bloc titre,
 * segmenté, héros, courbe et période, stats, sections ; action principale optionnelle en bas.
 * Pas d'onglets. Fiche exercice, résumé de séance, poids, mensurations.
 */
@Composable
fun FicheTemplate(
    topBar: @Composable () -> Unit,
    titleBlock: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    segmented: (@Composable () -> Unit)? = null,
    curve: (@Composable () -> Unit)? = null,
    period: (@Composable () -> Unit)? = null,
    stats: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable (bottomInset: Dp) -> Unit)? = null,
    insets: ScreenInsets = systemScreenInsets(),
    sections: @Composable ColumnScope.() -> Unit = {},
) {
    val spacing = Studio.spacing
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.screen, end = spacing.screen, top = insets.top),
        ) {
            topBar()
            Spacer(Modifier.height(spacing.xxs))
            titleBlock()
            if (segmented != null) {
                Spacer(Modifier.height(spacing.screen))
                segmented()
            }
            Spacer(Modifier.height(spacing.hero))
            hero()
            if (curve != null) {
                Spacer(Modifier.height(spacing.screen))
                curve()
            }
            if (period != null) {
                Spacer(Modifier.height(spacing.xxs))
                period()
            }
            if (stats != null) {
                Spacer(Modifier.height(if (curve != null || period != null) spacing.l else spacing.hero))
                stats()
            }
            Spacer(Modifier.height(spacing.section))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.section), content = sections)
            Spacer(Modifier.height(if (bottomAction == null) insets.bottom + spacing.section else spacing.section))
        }
        bottomAction?.invoke(insets.bottom)
    }
}

/**
 * Gabarit **en direct** (DESIGN.md § 10.3) : ce qui se passe maintenant (la séance). En-tête
 * (réduire · titre · chrono), progression, blocs espacés de 24 ; en bas, fixe, le pavé numérique
 * ou une barre d'actions ([bottom] reçoit l'inset du bas). Pas d'onglets.
 */
@Composable
fun LiveTemplate(
    header: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    bottom: @Composable (bottomInset: Dp) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
    blocks: @Composable ColumnScope.() -> Unit,
) {
    val spacing = Studio.spacing
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.screen, end = spacing.screen, top = insets.top),
        ) {
            header()
            Spacer(Modifier.height(spacing.xs))
            progress()
            Spacer(Modifier.height(spacing.l))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.l), content = blocks)
            Spacer(Modifier.height(spacing.l))
        }
        bottom(insets.bottom)
    }
}

/** Écart entre les éléments d'un formulaire. */
private val FormGap = 12.dp
private val FormTitleGap = 4.dp

/**
 * Gabarit **formulaire** (DESIGN.md § 10.4) : toujours une bottom sheet. Titre et phrase d'aide,
 * champ héros, puces de saisie rapide, lignes d'information, puis le pavé (ou des options) dont la
 * touche principale porte l'action. À poser dans une `StudioBottomSheet`.
 */
@Composable
fun FormSheetTemplate(
    title: String,
    help: AnnotatedString?,
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    quickEntries: (@Composable RowScope.() -> Unit)? = null,
    info: (@Composable ColumnScope.() -> Unit)? = null,
    bottomInset: Dp = 0.dp,
    input: @Composable () -> Unit,
) {
    SheetSurface(modifier, spacing = FormGap, bottomPadding = Studio.spacing.xxs, bottomInset = bottomInset) {
        SheetTitle(title, help, gap = FormTitleGap, messageStyle = Studio.typography.body)
        field()
        if (quickEntries != null) Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs), content = quickEntries)
        if (info != null) Column(content = info)
        input()
    }
}
