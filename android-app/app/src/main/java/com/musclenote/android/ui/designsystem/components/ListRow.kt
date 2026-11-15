package com.musclenote.android.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val RowGap = 14.dp
private val RowHeightWithTags = 72.dp
private val LeadingSize = 40.dp
private val PressBleed = 12.dp
private val PressRadius = 12.dp

/** Valeur → bouton plein d'une ligne (🔄 D42, maquette Nutrition : 12). */
private val TrailingActionGap = 12.dp

/**
 * Le bouton plein (rond de 40 centré dans une cible de 48) déborde de 8 dans le retrait de 16 : son rond
 * s'arrête à 12 du bord du groupe, comme sur la maquette.
 */
private val TrailingActionBleed = 8.dp

/** Dans un groupe : ligne d'une seule ligne de texte (Paramètres), retrait avant un chevron. */
private val GroupRowSingleLineHeight = 56.dp
private val GroupChevronEndPadding = 12.dp

/** Fin d'une ligne de liste (DESIGN.md § 9.1). */
sealed interface ListRowTrailing {
    /**
     * Valeur `bodyLineStrong`, avec sous elle une variation optionnelle (`caption` 600).
     * [valueTrend] colore la valeur elle-même (stock bas en `down`).
     */
    @Immutable
    data class Value(
        val value: String,
        val delta: String? = null,
        val trend: Trend = Trend.Neutral,
        val valueTrend: Trend? = null,
    ) : ListRowTrailing

    /**
     * Mini-courbe ([MiniTrendCurve], absente sans point) puis valeur et variation optionnelle, comme
     * [Value] : un exercice de Progrès (dernière séance, variation depuis la première).
     */
    @Immutable
    data class CurveValue(
        val points: List<CurvePoint>,
        val value: String,
        val delta: String? = null,
        val trend: Trend = Trend.Neutral,
    ) : ListRowTrailing

    /** Variation seule, en `emphasis` (« +2 kg », « identique »). */
    @Immutable
    data class DeltaOnly(val delta: String, val trend: Trend) : ListRowTrailing

    /** Chevron : la ligne ouvre un autre écran. */
    data object Chevron : ListRowTrailing

    /**
     * Valeur ([valueTrend] la colore : `up` une fois l'objectif atteint) puis bouton icône plein
     * (🔄 D42) : le bouton a sa propre action [onAction], lue [actionDescription] par TalkBack ; le reste
     * de la ligne garde son `onClick` (eau et protéines de Nutrition : + → saisie, ligne → détail).
     */
    @Immutable
    data class ValueAction(
        val value: String,
        @param:DrawableRes val icon: Int,
        val actionDescription: String,
        val onAction: () -> Unit,
        val valueTrend: Trend? = null,
    ) : ListRowTrailing

    /**
     * Case à cocher (celle de la ligne de série). Sans [onToggle], toute la ligne la bascule
     * (échauffement de la séance en cours, lignes à cocher d'une sheet) ; sans `onClick` de la ligne
     * non plus, elle est lue cochée (ou non) et inactive (exercice déjà dans la séance). Avec [onToggle], la case
     * seule (cible 48) la bascule, lue [toggleLabel] par TalkBack, et le reste de la ligne garde son
     * `onClick` (complément : la case = « pris aujourd'hui », la ligne ouvre ses rappels). [value]
     * (🔄 D42) : une valeur avant la case (jours de stock d'un complément), colorée par [valueTrend]
     * (`down` pour un stock bas) et lue [valueDescription] par TalkBack.
     */
    @Immutable
    data class Check(
        val checked: Boolean,
        val onToggle: (() -> Unit)? = null,
        val toggleLabel: String? = null,
        val value: String? = null,
        val valueTrend: Trend? = null,
        val valueDescription: String? = null,
    ) : ListRowTrailing
}

/**
 * Ligne de liste — `ListRow` (DESIGN.md § 9.1) : 64 de haut (72 avec des mini-puces), filet en
 * bas ; pastille d'icône optionnelle, titre et sous-titre ou mini-puces, fin au choix. Pressée :
 * fond `surfaceRaised` qui déborde de 12. [onLongClick] (optionnel, libellé d'accessibilité
 * [onLongClickLabel]) : appui long, pour les actions secondaires d'une ligne (séance du Journal).
 *
 * Dans un groupe ([ListGroup], v2) : retrait de 16 (12 avant un chevron), filet en retrait sous le
 * texte, 56 de haut sans sous-titre ; pressée, toute la ligne prend `surfaceGroupPressed`.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    tags: List<String> = emptyList(),
    trailing: ListRowTrailing? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClickLabel: String? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed = source.pressed()
    val inGroup = LocalInListGroup.current
    val minHeight = when {
        tags.isNotEmpty() -> RowHeightWithTags
        inGroup && subtitle == null -> GroupRowSingleLineHeight
        else -> Studio.spacing.row
    }
    val groupInset = Studio.spacing.screenGrouped
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(
                if (inGroup) {
                    // Le groupe découpe : la ligne pressée se remplit, le filet reste en retrait.
                    Modifier
                        .then(if (pressed) Modifier.background(Studio.colors.surfaceGroupPressed) else Modifier)
                        .bottomDivider(
                            Studio.colors.separator,
                            show = showDivider,
                            startInset = if (leadingIcon != null) groupInset + LeadingSize + RowGap else groupInset,
                        )
                } else {
                    Modifier
                        .pressedBackground(pressed, Studio.colors.surfaceRaised, PressBleed, PressRadius)
                        .bottomDivider(Studio.colors.separator, show = showDivider && !pressed)
                },
            )
            .then(
                when {
                    // Case sans action (exercice déjà dans la séance) : lue cochée et inactive.
                    onClick == null && trailing is ListRowTrailing.Check && trailing.onToggle == null -> Modifier.toggleable(
                        value = trailing.checked,
                        enabled = false,
                        role = Role.Checkbox,
                        onValueChange = {},
                    )
                    onClick == null -> Modifier
                    // Ligne à cocher avec un appui long (exercice perso du sélecteur : modifier, supprimer).
                    trailing is ListRowTrailing.Check && trailing.onToggle == null && onLongClick != null -> Modifier
                        .semantics { toggleableState = ToggleableState(trailing.checked) }
                        .combinedClickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Checkbox,
                            onClickLabel = onClickLabel,
                            onLongClickLabel = onLongClickLabel,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    trailing is ListRowTrailing.Check && trailing.onToggle == null -> Modifier.toggleable(
                        value = trailing.checked,
                        interactionSource = source,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                    onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                    else -> Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                },
            )
            .then(
                if (inGroup) {
                    Modifier.padding(
                        start = groupInset,
                        end = if (trailing == ListRowTrailing.Chevron) GroupChevronEndPadding else groupInset,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = Studio.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.size(LeadingSize).background(Studio.colors.surfaceControl, Studio.shapes.full),
                contentAlignment = Alignment.Center,
            ) {
                StudioIcon(leadingIcon, contentDescription = null, size = IconSize.Row, tint = Studio.colors.textPrimary)
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (tags.isEmpty()) 3.dp else Studio.spacing.xs),
        ) {
            Text(title, style = Studio.typography.bodyLine)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = Studio.typography.caption,
                    color = Studio.colors.textSecondary,
                )
            }
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                    tags.forEach { MiniTag(it) }
                }
            }
        }
        when (trailing) {
            is ListRowTrailing.Value -> TrailingValue(trailing.value, trailing.delta, trailing.trend, trailing.valueTrend)
            is ListRowTrailing.CurveValue -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Studio.spacing.s)) {
                if (trailing.points.isNotEmpty()) MiniTrendCurve(trailing.points)
                TrailingValue(trailing.value, trailing.delta, trailing.trend, valueTrend = null)
            }
            is ListRowTrailing.DeltaOnly -> Delta(trailing.delta, trailing.trend, size = DeltaSize.Line)
            ListRowTrailing.Chevron -> StudioIcon(
                R.drawable.ds_ic_chevron_right,
                contentDescription = null,
                tint = Studio.colors.textSecondary,
            )
            is ListRowTrailing.ValueAction -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TrailingActionGap)) {
                TrailingValue(trailing.value, delta = null, trend = Trend.Neutral, valueTrend = trailing.valueTrend)
                StudioIconButton(
                    trailing.icon,
                    contentDescription = trailing.actionDescription,
                    onClick = trailing.onAction,
                    filled = true,
                    modifier = Modifier.bleed(end = TrailingActionBleed),
                )
            }
            is ListRowTrailing.Check -> {
                if (trailing.value != null) {
                    val description = trailing.valueDescription
                    Box(if (description != null) Modifier.semantics { contentDescription = description } else Modifier) {
                        TrailingValue(trailing.value, delta = null, trend = Trend.Neutral, valueTrend = trailing.valueTrend)
                    }
                }
                val onToggle = trailing.onToggle
                if (onToggle == null) {
                    CheckBoxVisual(trailing.checked)
                } else {
                    val label = trailing.toggleLabel
                    CheckBoxVisual(
                        trailing.checked,
                        Modifier
                            .toggleable(
                                value = trailing.checked,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Checkbox,
                                onValueChange = { onToggle() },
                            )
                            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
                    )
                }
            }
            null -> Unit
        }
    }
}

/** Valeur de fin de ligne (`bodyLineStrong`) et, dessous, sa variation (`caption` 600). */
@Composable
private fun TrailingValue(value: String, delta: String?, trend: Trend, valueTrend: Trend?) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = Studio.typography.bodyLineStrong, color = valueTrend?.color() ?: Studio.colors.textPrimary, maxLines = 1)
        if (delta != null) Delta(delta, trend, size = DeltaSize.Row)
    }
}

/** Mini-puce d'une ligne (« 80 × 8 ») : `caption` sur `surfaceControl`, rayon 8. */
@Composable
fun MiniTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Studio.colors.surfaceControl, RoundedCornerShape(8.dp))
            .padding(horizontal = Studio.spacing.xs, vertical = 3.dp),
        style = Studio.typography.caption,
        maxLines = 1,
    )
}
