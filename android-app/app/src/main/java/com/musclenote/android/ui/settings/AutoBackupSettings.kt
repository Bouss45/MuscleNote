package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.AutoBackupFailure
import com.musclenote.android.data.repository.AutoBackupResult
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StatusMessage
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Date et heure courtes dans la locale de l'app (§ 3.14). */
@Composable
internal fun rememberDateTime(epochMillis: Long, locale: Locale, zone: ZoneId): String = remember(epochMillis, locale, zone) {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/**
 * Ligne « Sauvegarde automatique » (SPEC § 15.10.3, 🔄 D28) : « Activée » / « Désactivée », et l'état
 * en sous-titre (dernière sauvegarde, échec daté, dossier inaccessible) ; [onOpen] ouvre la sheet.
 */
@Composable
internal fun autoBackupRow(onOpen: () -> Unit): SettingsRow {
    val container = LocalAppContainer.current
    val state by container.autoBackup.state.collectAsStateWithLifecycle()
    val locale = appLocale()
    val zone = container.time.zone()
    val last = state.lastSuccessAt
    val failedAt = state.lastFailureAt
    val subtitle = when {
        !state.enabled -> stringResource(R.string.settings_auto_backup_off_subtitle)
        state.failure == AutoBackupFailure.Folder -> stringResource(R.string.settings_auto_backup_folder_lost_short)
        state.failure == AutoBackupFailure.Write && failedAt != null ->
            stringResource(R.string.settings_auto_backup_failed_short, rememberDateTime(failedAt, locale, zone))
        last != null -> stringResource(R.string.settings_auto_backup_last_short, rememberDateTime(last, locale, zone))
        else -> stringResource(R.string.settings_auto_backup_never)
    }
    return SettingsRow(
        key = "auto-backup",
        title = stringResource(R.string.settings_auto_backup_title),
        subtitle = subtitle,
        value = stringResource(if (state.enabled) R.string.settings_on_f else R.string.settings_off_f),
        onClick = onOpen,
    )
}

/**
 * Sheet « Sauvegarde automatique » (§ 15.2, § 15.10.3) : « Sauvegarde quotidienne » (cochée sans
 * dossier → sélecteur du système), dossier, dernière sauvegarde, échec, [Sauvegarder maintenant].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupSheet(onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> AutoBackupSheetContent(bottomInset = inset) }
}

@Composable
internal fun AutoBackupSheetContent(bottomInset: Dp = 0.dp) {
    val container = LocalAppContainer.current
    val autoBackup = container.autoBackup
    val state by autoBackup.state.collectAsStateWithLifecycle()
    val running by autoBackup.running.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

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
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(
            stringResource(R.string.settings_auto_backup_title),
            AnnotatedString(stringResource(R.string.settings_auto_backup_desc)),
            messageStyle = Studio.typography.body,
        )
        Column(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.settings_auto_backup_toggle),
                trailing = ListRowTrailing.Check(state.enabled),
                showDivider = false,
                onClick = {
                    if (running) return@ListRow
                    haptics.light()
                    when {
                        state.enabled -> container.launchMutation { autoBackup.setEnabled(false) }
                        state.folderUri == null -> pickFolder()
                        else -> container.launchMutation { onResult(autoBackup.setEnabled(true)) }
                    }
                },
            )
            if (state.enabled) {
                InfoRow(
                    label = stringResource(R.string.settings_auto_backup_folder_label),
                    value = state.folderName ?: stringResource(R.string.settings_auto_backup_choose_folder),
                    onClick = if (running) null else ({
                        haptics.light()
                        pickFolder()
                    }),
                )
                val last = state.lastSuccessAt
                InfoRow(
                    label = stringResource(R.string.settings_auto_backup_last_label),
                    value = if (last == null) stringResource(R.string.settings_auto_backup_none) else rememberDateTime(last, locale, zone),
                    showDivider = false,
                )
            }
        }
        if (state.enabled) {
            val failedAt = state.lastFailureAt
            when {
                state.failure == AutoBackupFailure.Folder -> StatusMessage(stringResource(R.string.settings_auto_backup_folder_lost))
                state.failure == AutoBackupFailure.Write && failedAt != null -> StatusMessage(
                    stringResource(R.string.settings_auto_backup_failed, rememberDateTime(failedAt, locale, zone), state.failureDetail.orEmpty()),
                )
            }
            StudioButton(
                stringResource(if (running) R.string.settings_auto_backup_running else R.string.settings_auto_backup_now),
                onClick = {
                    haptics.light()
                    container.launchMutation { onResult(autoBackup.backupNow()) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
            )
        }
    }
}
