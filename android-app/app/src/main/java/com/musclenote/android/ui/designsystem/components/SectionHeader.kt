package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 * en casse normale (`captionStrong`), sans chevron. TalkBack lit le titre dans sa casse d'origine.
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
) {
    if (grouped) {
        GroupLabel(title, modifier, subtitle, linkLabel, onLinkClick, trailingText, trailingTrend)
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
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = GroupLabelHeight)
            .padding(horizontal = Studio.spacing.screenGrouped),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Studio.spacing.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        when {
            linkLabel != null -> Row(
                Modifier
                    .heightIn(min = Studio.spacing.touch)
                    .clickable(enabled = onLinkClick != null, role = Role.Button) { onLinkClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(linkLabel, style = Studio.typography.captionStrong, color = Studio.colors.textSecondary)
            }
            trailingText != null -> Text(
                trailingText,
                style = Studio.typography.captionStrong,
                color = trailingTrend.color(),
            )
        }
    }
}
