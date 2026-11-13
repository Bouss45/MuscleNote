package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val SectionHeaderHeight = 40.dp

/**
 * En-tête de section — `SectionHeader` (DESIGN.md § 9.1) : titre `titleSection` sur 40 de haut,
 * avec à droite un lien (« Tout voir › ») ou une mention (compteur, « Records battus · 2 »),
 * ou un sous-titre sous le titre.
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
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = SectionHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Studio.typography.titleSection, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Studio.typography.caption, color = Studio.colors.textSecondary)
            }
        }
        val linkStyle = Studio.typography.body.copy(fontWeight = FontWeight.SemiBold)
        when {
            linkLabel != null -> Row(
                Modifier
                    .height(SectionHeaderHeight)
                    .clickable(enabled = onLinkClick != null, role = Role.Button) { onLinkClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(linkLabel, style = linkStyle, color = Studio.colors.textSecondary, maxLines = 1)
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
                maxLines = 1,
            )
        }
    }
}
