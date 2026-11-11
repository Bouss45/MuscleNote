package com.musclenote.android.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.update.UpdateRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.downloadUpdate
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Carte « Mises à jour » (§ 2.12, § 3.17, 🔄 D25) : version installée et état de la vérification.
 * Installée par Obtainium → « gérées par Obtainium », rien d'autre. Sinon [Vérifier] (tout de
 * suite) et, si une Release plus récente existe, [Télécharger] (APK dans le navigateur).
 */
@Composable
fun UpdateCard() {
    val container = LocalAppContainer.current
    val updates = container.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val checking by updates.checking.collectAsStateWithLifecycle()
    val failed by updates.lastCheckFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    val enabled = container.updateChecksEnabled
    val installed = updates.currentVersion
    val available = if (enabled) UpdateRules.available(state, installed) else null
    val locale = appLocale()
    val zone = container.time.zone()
    val checkedAt = remember(state.checkedAt, locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(state.checkedAt).atZone(zone))
    }
    val description = when {
        !enabled -> stringResource(R.string.settings_update_managed, installed)
        available != null -> stringResource(R.string.settings_update_available, installed, available.version)
        state.checkedAt == 0L -> stringResource(R.string.settings_update_never, installed)
        else -> stringResource(R.string.settings_update_up_to_date, installed, checkedAt)
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardIcon(R.drawable.ic_download, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.settings_update_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = if (available != null) colors.primary else colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
                if (enabled && failed && !checking) {
                    Text(stringResource(R.string.settings_update_failed), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val buttonShape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .border(1.dp, colors.border, buttonShape)
                        .clickable(enabled = !checking) {
                            haptics.light()
                            container.launchMutation { updates.checkNow() }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checking) {
                        CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.settings_update_check), color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (available != null) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(buttonShape)
                            .background(colors.primary)
                            .clickable { downloadUpdate(context, available) }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.update_download), color = colors.primaryForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
