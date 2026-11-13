package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val HubTitleHeight = 56.dp
private val BarHeight = 48.dp

/** Barre de titre d'un hub — `ScreenTitleBar` (DESIGN.md § 9.3) : 56, `titleScreen`, action à droite. */
@Composable
fun ScreenTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().height(HubTitleHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = Studio.typography.titleScreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (action != null) Row(Modifier.bleed(end = IconButtonBleed)) { action() }
    }
}

/** Barre de l'Accueil — `DateTitleBar` : date `emphasis` en texte secondaire, Paramètres à droite. */
@Composable
fun DateTitleBar(
    date: String,
    settingsDescription: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(BarHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(date, Modifier.weight(1f), style = Studio.typography.emphasis, color = Studio.colors.textSecondary, maxLines = 1)
        StudioIconButton(
            R.drawable.ds_ic_settings,
            contentDescription = settingsDescription,
            onClick = onSettings,
            modifier = Modifier.bleed(end = IconButtonBleed),
        )
    }
}

/**
 * Barre du haut d'une fiche — `FicheTopBar` : bouton à gauche (retour ou fermer) et actions à
 * droite (options, note), icônes alignées sur la marge.
 */
@Composable
fun FicheTopBar(
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes navigationIcon: Int = R.drawable.ds_ic_arrow_back,
    navigationDescription: String = stringResource(R.string.common_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().bleed(start = IconButtonBleed, end = IconButtonBleed).height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(navigationIcon, navigationDescription, onNavigate)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * Bloc titre d'une fiche : surtitre optionnel (légende au-dessus, résumé de séance), titre
 * (`titleFiche`, ou [titleStyle]), sous-titre.
 */
@Composable
fun FicheTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    overline: String? = null,
    titleStyle: TextStyle = Studio.typography.titleFiche,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
        if (overline != null) Text(overline, style = Studio.typography.caption, color = Studio.colors.textSecondary)
        Text(title, Modifier.semantics { heading() }, style = titleStyle)
        if (subtitle != null) Text(subtitle, style = Studio.typography.caption, color = Studio.colors.textSecondary)
    }
}

/**
 * En-tête en direct — `LiveHeader` : « Réduire », nom et détail de la séance, chrono en `liveText`.
 */
@Composable
fun LiveHeader(
    title: String,
    subtitle: String,
    elapsed: String,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    collapseDescription: String = stringResource(R.string.ds_collapse_session),
) {
    Row(
        modifier.fillMaxWidth().bleed(start = IconButtonBleed).height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xxs),
    ) {
        StudioIconButton(R.drawable.ds_ic_keyboard_arrow_down, collapseDescription, onCollapse)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Studio.typography.titleItem, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Studio.typography.caption, color = Studio.colors.textSecondary, maxLines = 1)
        }
        RollingText(elapsed, style = Studio.typography.numberMedium, color = Studio.colors.liveText)
    }
}

/**
 * En-tête d'un bloc exercice — `ExerciseBlockHeader` (DESIGN.md § 9.3) : titre `titleBlock`,
 * boutons fiche et options, puis la rangée de puces (précédent, suggestion).
 */
@Composable
fun ExerciseBlockHeader(
    title: String,
    onOpenDetails: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    detailsDescription: String = stringResource(R.string.ds_exercise_details),
    moreDescription: String = stringResource(R.string.ds_more_options),
    chips: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
        Row(Modifier.fillMaxWidth().bleed(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = Studio.typography.titleBlock,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StudioIconButton(R.drawable.ds_ic_monitoring, detailsDescription, onOpenDetails, size = ButtonSize.Small)
            StudioIconButton(R.drawable.ds_ic_more_vert, moreDescription, onMore, size = ButtonSize.Small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs), content = chips)
    }
}
