package com.musclenote.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Annonce « Mise à jour disponible » de l'Accueil (§ 2.2, § 3.17, 🔄 D25) : version disponible et
 * version installée, [Télécharger] (APK dans le navigateur) et ✕ (masquée jusqu'à la version
 * suivante).
 */
@Composable
fun UpdateBanner(
    release: LatestRelease,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.primary.copy(alpha = 0.33f), shape)
            .padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_update_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.home_update_text, release.version, installedVersion),
                color = colors.mutedForeground,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary)
                    .clickable(onClick = onDownload)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.update_download), color = colors.primaryForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_cancel), tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}
