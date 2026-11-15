package com.musclenote.android.ui.designsystem.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.components.GroupPosition
import com.musclenote.android.ui.designsystem.components.ListGroup
import com.musclenote.android.ui.designsystem.components.ListGroupSegment
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.designsystem.theme.StudioSpacing

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

/**
 * Insets d'un écran d'onglet posé au-dessus de la barre d'onglets de l'app (SPEC § 15.8.1) : la
 * barre absorbe l'inset du bas, l'écran ne garde que celui du haut.
 */
@Composable
fun tabScreenInsets(): ScreenInsets = ScreenInsets(top = systemScreenInsets().top, bottom = 0.dp)

/** Écart entre la mini-barre et la barre d'onglets. */
private val MiniBarGap = 8.dp

/**
 * Rythme d'un hub : marges, écart après le titre, écart entre sections. Les listes groupées (v2,
 * DESIGN.md § 10.1) ont des marges de 16, le titre aligné sur le texte des lignes (retrait de 16),
 * 8 entre le titre et le groupe du héros (32 sans héros) et 32 entre les groupes.
 */
private class HubRhythm(grouped: Boolean, hasHighlight: Boolean, spacing: StudioSpacing) {
    val margin: Dp = if (grouped) spacing.screenGrouped else spacing.screen
    val titleInset: Dp = if (grouped) spacing.screenGrouped else 0.dp
    val afterTitle: Dp = when {
        !grouped -> spacing.s
        hasHighlight -> spacing.xs
        else -> spacing.group
    }
    val sectionGap: Dp = if (grouped) spacing.group else spacing.section
}

/**
 * Gabarit **hub** (DESIGN.md § 10.1) : barre de titre, au plus une surface mise en avant, sections
 * espacées de 40 ; en bas, la mini-barre éventuelle et la barre d'onglets ([navigation] reçoit
 * l'inset du bas). Accueil, Journal, Progrès, Nutrition, Paramètres, Mes records. [grouped] : hub en
 * listes groupées (v2 : Journal, Paramètres, Nutrition), les sections étant des groupes titrés
 * ([com.musclenote.android.ui.designsystem.components.SectionHeader] `grouped` puis [ListGroup]).
 */
@Composable
fun HubTemplate(
    titleBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    highlight: (@Composable () -> Unit)? = null,
    miniBar: (@Composable () -> Unit)? = null,
    navigation: (@Composable (bottomInset: Dp) -> Unit)? = null,
    insets: ScreenInsets = systemScreenInsets(),
    grouped: Boolean = false,
    sections: @Composable ColumnScope.() -> Unit,
) {
    val rhythm = HubRhythm(grouped, highlight != null, Studio.spacing)
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = rhythm.margin, end = rhythm.margin, top = insets.top),
        ) {
            Box(Modifier.fillMaxWidth().padding(start = rhythm.titleInset)) { titleBar() }
            Spacer(Modifier.height(rhythm.afterTitle))
            if (highlight != null) {
                highlight()
                Spacer(Modifier.height(rhythm.sectionGap))
            }
            Column(verticalArrangement = Arrangement.spacedBy(rhythm.sectionGap), content = sections)
            Spacer(Modifier.height(if (navigation == null) insets.bottom + rhythm.sectionGap else rhythm.sectionGap))
        }
        if (miniBar != null) {
            Column(Modifier.padding(horizontal = rhythm.margin)) {
                miniBar()
                Spacer(Modifier.height(MiniBarGap))
            }
        }
        navigation?.invoke(insets.bottom)
    }
}

/**
 * Sections d'un [HubLazyTemplate] : chaque [section] ajoute 40 avant elle (sauf la première), son
 * en-tête puis ses lignes, chacune en élément paresseux. Dans un hub groupé (v2), [group] et
 * [groupBlock] posent des groupes titrés, espacés de 32.
 */
class HubSections internal constructor(private val list: LazyListScope, private val sectionGap: Dp) {
    private var first = true

    /**
     * Un groupe (v2) : étiquette [label] (`SectionHeader` groupé, 8 au-dessus du groupe), puis une
     * ligne par élément de [items], chacune un segment du groupe ; [row] reçoit sa position (le
     * dernier n'a pas de filet).
     */
    fun <T> group(
        key: Any,
        label: (@Composable () -> Unit)?,
        items: List<T>,
        itemKey: (T) -> Any,
        contentType: Any? = null,
        row: @Composable (item: T, position: GroupPosition) -> Unit,
    ) {
        gap(key)
        if (label != null) {
            list.item(key = hubKey(key, "label"), contentType = "hub-group-label") {
                label()
                Spacer(Modifier.height(GroupLabelGap))
            }
        }
        list.itemsIndexed(items, key = { _, item -> itemKey(item) }, contentType = { _, _ -> contentType }) { index, item ->
            val position = GroupPosition.of(index, items.size)
            ListGroupSegment(position) { row(item, position) }
        }
    }

    /** Un groupe d'un seul bloc (rangée de stats, avec son padding), sous son étiquette [label]. */
    fun groupBlock(key: Any, label: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
        gap(key)
        list.item(key = hubKey(key, "group-block"), contentType = "hub-group-block") {
            Column {
                if (label != null) {
                    label()
                    Spacer(Modifier.height(GroupLabelGap))
                }
                ListGroup { content() }
            }
        }
    }

    /** Une section : en-tête ([header], suivi de 4), puis [rows] (`items`, `item`). */
    fun section(key: Any, header: @Composable () -> Unit, rows: LazyListScope.() -> Unit) {
        gap(key)
        list.item(key = hubKey(key, "header"), contentType = "hub-header") {
            header()
            Spacer(Modifier.height(SectionHeaderGap))
        }
        list.rows()
    }

    /** Un bloc sans en-tête (rangée de stats, état vide), espacé comme une section. */
    fun block(key: Any, content: @Composable () -> Unit) {
        gap(key)
        list.item(key = hubKey(key, "block"), contentType = "hub-block") { content() }
    }

    private fun gap(key: Any) {
        if (!first) list.item(key = hubKey(key, "gap"), contentType = "hub-gap") { Spacer(Modifier.height(sectionGap)) }
        first = false
    }
}

/**
 * Clé d'un élément posé par le gabarit (en-tête, écart), distincte des clés des lignes. Une chaîne :
 * une clé de liste paresseuse doit pouvoir être rangée dans un `Bundle`.
 */
private fun hubKey(section: Any, part: String): String = "hub:$section:$part"

/** Écart entre un en-tête de section et sa première ligne. */
private val SectionHeaderGap = 4.dp

/** Écart entre l'étiquette d'un groupe et le groupe (v2). */
private val GroupLabelGap = 8.dp

/**
 * Gabarit **hub** en liste paresseuse (DESIGN.md § 10.1) : même disposition que [HubTemplate],
 * pour un hub dont les lignes peuvent se compter par centaines (Journal). Les sections viennent de
 * [HubSections.section], ou des groupes [HubSections.group] si [grouped] ; [listState] garde le
 * défilement.
 */
@Composable
fun HubLazyTemplate(
    titleBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    highlight: (@Composable () -> Unit)? = null,
    miniBar: (@Composable () -> Unit)? = null,
    navigation: (@Composable (bottomInset: Dp) -> Unit)? = null,
    insets: ScreenInsets = systemScreenInsets(),
    listState: LazyListState = rememberLazyListState(),
    grouped: Boolean = false,
    sections: HubSections.() -> Unit,
) {
    val rhythm = HubRhythm(grouped, highlight != null, Studio.spacing)
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = rhythm.margin,
                end = rhythm.margin,
                top = insets.top,
                bottom = if (navigation == null) insets.bottom + rhythm.sectionGap else rhythm.sectionGap,
            ),
        ) {
            item(key = "hub-title", contentType = "hub-title") {
                Box(Modifier.fillMaxWidth().padding(start = rhythm.titleInset)) { titleBar() }
                Spacer(Modifier.height(rhythm.afterTitle))
            }
            if (highlight != null) {
                item(key = "hub-highlight", contentType = "hub-highlight") {
                    highlight()
                    Spacer(Modifier.height(rhythm.sectionGap))
                }
            }
            HubSections(this, rhythm.sectionGap).sections()
        }
        if (miniBar != null) {
            Column(Modifier.padding(horizontal = rhythm.margin)) {
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
 * Gabarit **fiche** en liste paresseuse (DESIGN.md § 10.2) : même disposition que [FicheTemplate],
 * pour une fiche dont les lignes peuvent se compter par centaines (fiche exercice). Le haut — barre,
 * titre, héros, courbe, période, stats — est un élément ; les sections viennent de
 * [HubSections.section], la première 40 sous ce haut. Sans [hero] (fiche sans donnée), les sections
 * suivent le titre. [bottomAction] : fixe en bas (barre d'action, ou pavé numérique d'une saisie),
 * il reçoit l'inset du bas.
 */
@Composable
fun FicheLazyTemplate(
    topBar: @Composable () -> Unit,
    titleBlock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hero: (@Composable () -> Unit)? = null,
    curve: (@Composable () -> Unit)? = null,
    period: (@Composable () -> Unit)? = null,
    stats: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable (bottomInset: Dp) -> Unit)? = null,
    insets: ScreenInsets = systemScreenInsets(),
    listState: LazyListState = rememberLazyListState(),
    sections: HubSections.() -> Unit = {},
) {
    val spacing = Studio.spacing
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = insets.top,
                bottom = if (bottomAction == null) insets.bottom + spacing.section else spacing.section,
            ),
        ) {
            item(key = "fiche-top", contentType = "fiche-top") {
                Column {
                    topBar()
                    Spacer(Modifier.height(spacing.xxs))
                    titleBlock()
                    if (hero != null) {
                        Spacer(Modifier.height(spacing.hero))
                        hero()
                    }
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
                    Spacer(Modifier.height(if (hero != null) spacing.section else spacing.hero))
                }
            }
            HubSections(this, spacing.section).sections()
        }
        bottomAction?.invoke(insets.bottom)
    }
}

/**
 * Gabarit **en direct** (DESIGN.md § 10.3) : ce qui se passe maintenant (la séance). En haut, fixes,
 * l'en-tête (réduire · titre · chrono) et la progression (optionnelle) ; puis la liste paresseuse
 * des blocs, espacés de 24 ([listState] : glisser-déposer, défilement vers le champ en saisie) ; en
 * bas, fixe, le pavé numérique ou une barre d'actions ([bottom] reçoit l'inset du bas). Pas
 * d'onglets.
 */
@Composable
fun LiveTemplate(
    header: @Composable () -> Unit,
    progress: (@Composable () -> Unit)?,
    bottom: @Composable (bottomInset: Dp) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
    listState: LazyListState = rememberLazyListState(),
    blocks: LazyListScope.() -> Unit,
) {
    val spacing = Studio.spacing
    Column(modifier.fillMaxSize().background(Studio.colors.background)) {
        Column(Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, top = insets.top)) {
            header()
            if (progress != null) {
                Spacer(Modifier.height(spacing.xs))
                progress()
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.l, bottom = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
            content = blocks,
        )
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
