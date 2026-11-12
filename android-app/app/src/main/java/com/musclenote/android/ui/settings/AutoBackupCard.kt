package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.AutoBackupFailure
import com.musclenote.android.data.repository.AutoBackupResult
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.nutrition.AppSwitch
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Carte « Sauvegarde automatique » (SPEC § 15.2, 🔄 D28) : interrupteur, dossier choisi dans le
 * sélecteur du système, dernière sauvegarde, échec (dossier inaccessible ou autre erreur) et
 * [Sauvegarder maintenant].
 */
@Composable
fun AutoBackupCard() {
    val container = LocalAppContainer.current
    val autoBackup = container.autoBackup
    val state by autoBackup.state.collectAsStateWithLifecycle()
    val running by autoBackup.running.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    fun onResult(result: AutoBackupResult?) {
        when (result) {
            is AutoBackupResult.Saved -> haptics.success()
            is AutoBackupResult.Failed -> haptics.warning()
            null -> Unit
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) container.launchMutation { onResult(autoBackup.chooseFolder(uri.toString())) }
    }

    fun pickFolder() {
        try {
            folderLauncher.launch(state.folderUri?.let(Uri::parse))
        } catch (e: ActivityNotFoundException) {
            Log.w("AutoBackup", "Aucun sélecteur de dossier", e)
        }
    }

    val locale = appLocale()
    val zone = container.time.zone()
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
            CardIcon(R.drawable.ic_folder, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.settings_auto_backup_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_auto_backup_desc), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
            AppSwitch(
                checked = state.enabled,
                onCheckedChange = { on ->
                    if (running) return@AppSwitch
                    haptics.light()
                    when {
                        !on -> container.launchMutation { autoBackup.setEnabled(false) }
                        state.folderUri == null -> pickFolder()
                        else -> container.launchMutation { onResult(autoBackup.setEnabled(true)) }
                    }
                },
            )
        }
        if (state.enabled) {
            val folderLost = state.failure == AutoBackupFailure.Folder
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.folderName?.let {
                    Text(stringResource(R.string.settings_auto_backup_folder, it), color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                val last = state.lastSuccessAt
                Text(
                    if (last == null) stringResource(R.string.settings_auto_backup_never)
                    else stringResource(R.string.settings_auto_backup_last, rememberDateTime(last, locale, zone)),
                    color = colors.mutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                val failedAt = state.lastFailureAt
                when {
                    folderLost -> Text(stringResource(R.string.settings_auto_backup_folder_lost), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
                    state.failure == AutoBackupFailure.Write && failedAt != null -> Text(
                        stringResource(R.string.settings_auto_backup_failed, rememberDateTime(failedAt, locale, zone), state.failureDetail.orEmpty()),
                        color = colors.destructive,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardButton(
                    text = stringResource(if (folderLost) R.string.settings_auto_backup_choose_folder else R.string.settings_auto_backup_change),
                    accent = if (folderLost) colors.destructive else null,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.light()
                        pickFolder()
                    },
                )
                CardButton(
                    text = stringResource(R.string.settings_auto_backup_now),
                    filled = true,
                    loading = running,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.light()
                        container.launchMutation { onResult(autoBackup.backupNow()) }
                    },
                )
            }
        }
    }
}

/** Date et heure courtes dans la locale de l'app (§ 3.14). */
@Composable
internal fun rememberDateTime(epochMillis: Long, locale: Locale, zone: ZoneId): String = remember(epochMillis, locale, zone) {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/**
 * Bouton d'une carte de Paramètres : contour (`border`, ou [accent]) ou plein (`primary`), comme
 * ceux de la carte des mises à jour ; indicateur à la place du texte pendant [loading].
 */
@Composable
internal fun CardButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    loading: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val content = when {
        filled -> colors.primaryForeground
        accent != null -> accent
        else -> colors.foreground
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (filled) Modifier.background(colors.primary) else Modifier.border(1.dp, accent?.copy(alpha = 0.5f) ?: colors.border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Text(text, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
