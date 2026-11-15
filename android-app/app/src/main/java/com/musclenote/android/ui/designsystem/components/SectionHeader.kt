package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val SectionHeaderHeight = 40.dp

/** Étiquette d'un groupe (v2) : 32 de haut, 8 au-dessus de son groupe. */
private val GroupLabelHeight = 32.dp

/**
 * En-tête de section — `SectionHeader` (DESIGN.md § 9.1) : titre `titleSection` sur 40 de haut,
 * avec à droite un lien (« Tout voir › ») ou une mention (compteur, « Records battus · 2 »),
 * ou un sous-titre sous le titre.
 *
 * [grouped] (listes groupées, v2) : étiquette en capitales (`label`, texte secondaire) sur 32 de
 * haut, en retrait de 16 pour s'aligner sur le texte des lignes du groupe ; lien ou mention à droite
 * en casse normale (`captionStrong`), sans chevron. TalkBack lit le titre dans sa casse d'origine,
 * et la mention avec [trailingDescription] quand elle est abrégée (« 2/3 » → « 2 séances sur 3 »).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    linkLabel: String? = null,
    onLinkClick: (() -> Unit)? = null,
    trailingText: String? = null,
    trailingTrend: Trend = Trend.Neutral,
    grouped: Boolean = false,
    trailingDescription: String? = null,
) {
    if (grouped) {
        GroupLabel(title, modifier, subtitle, linkLabel, onLinkClick, trailingText, trailingTrend, trailingDescription)
        return
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = SectionHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Studio.typography.titleSection)
            if (subtitle != null) {
                Text(subtitle, style = Studio.typography.caption, color = Studio.colors.textSecondary)
            }
        }
        val linkStyle = Studio.typography.body.copy(fontWeight = FontWeight.SemiBold)
        when {
            linkLabel != null -> Row(
                Modifier
                    .heightIn(min = SectionHeaderHeight)
                    .clickable(enabled = onLinkClick != null, role = Role.Button) { onLinkClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(linkLabel, style = linkStyle, color = Studio.colors.textSecondary)
                StudioIcon(
                    R.drawable.ds_ic_chevron_right,
                    contentDescription = null,
                    size = IconSize.Link,
                    tint = Studio.colors.textSecondary,
                )
            }
            trailingText != null -> Text(
                trailingText,
                style = linkStyle,
                color = trailingTrend.color(),
            )
        }
    }
}

/** Étiquette d'un groupe (v2) : capitales +12 %, texte secondaire, retrait de 16. */
@Composable
private fun GroupLabel(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    linkLabel: String?,
    onLinkClick: (() -> Unit)?,
    trailingText: String?,
    trailingTrend: Trend,
    trailingDescription: String?,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = GroupLabelHeight)
        .padding(horizontal = Studio.spacing.screenGrouped)
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                // Capitales françaises accentuées (« PRÉFÉRENCES ») ; TalkBack lit « Préférences ».
                title.uppercase(),
                Modifier.semantics {
                    heading()
                    contentDescription = title
                },
                style = Studio.typography.label,
                color = Studio.colors.textSecondary,
            )
            if (subtitle != null) {
                Text(subtitle, style = Studio.typography.caption, color = Studio.colors.textSecondary)
            }
        }
    }
    if (linkLabel == null && trailingText != null) {
        // Mention à droite (« 1 sur 3 pris », « 3/3 · validée ») : c'est elle qui passe à la ligne en
        // texte agrandi, l'étiquette garde ses mots entiers (« COMPLÉMENTS » n'est jamais coupé).
        LabelWithTrailing(rowModifier, gap = Studio.spacing.s, label = label) {
            Text(
                trailingText,
                if (trailingDescription != null) Modifier.semantics { contentDescription = trailingDescription } else Modifier,
                style = Studio.typography.captionStrong,
                color = trailingTrend.color(),
                textAlign = TextAlign.End,
            )
        }
        return
    }
    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Studio.spacing.s),
    ) {
        Box(Modifier.weight(1f)) { label() }
        if (linkLabel != null) {
            Row(
                Modifier
                    .heightIn(min = Studio.spacing.touch)
                    .clickable(enabled = onLinkClick != null, role = Role.Button) { onLinkClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(linkLabel, style = Studio.typography.captionStrong, color = Studio.colors.textSecondary)
            }
        }
    }
}

/**
 * Étiquette et mention sur une ligne, centrées verticalement : l'étiquette prend sa largeur sur une
 * ligne tant qu'il reste à la mention la place de son mot le plus long (au moins la moitié de la
 * ligne), la mention prend le reste, alignée à droite, et passe à la ligne s'il le faut.
 */
@Composable
private fun LabelWithTrailing(modifier: Modifier, gap: Dp, label: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    Layout(contents = listOf(label, trailing), modifier = modifier) { (labelMeasurables, trailingMeasurables), constraints ->
        val labelMeasurable = labelMeasurables.first()
        val trailingMeasurable = trailingMeasurables.first()
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val trailingMin = trailingMeasurable.minIntrinsicWidth(Constraints.Infinity)
        val labelWidth = minOf(labelMeasurable.maxIntrinsicWidth(Constraints.Infinity), maxOf(width - gapPx - trailingMin, width / 2))
        val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = labelWidth.coerceAtLeast(0)))
        val trailingPlaceable = trailingMeasurable.measure(Constraints(maxWidth = (width - labelPlaceable.width - gapPx).coerceAtLeast(0)))
        val height = maxOf(labelPlaceable.height, trailingPlaceable.height, constraints.minHeight)
        layout(width, height) {
            labelPlaceable.place(0, (height - labelPlaceable.height) / 2)
            trailingPlaceable.place(width - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}
