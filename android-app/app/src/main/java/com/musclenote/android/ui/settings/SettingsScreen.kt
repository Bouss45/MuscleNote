package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.BackupReadResult
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.theme.FixedColors
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Types proposés au sélecteur de fichiers pour l'import (§ 2.12). */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

/**
 * Onglet Paramètres (SPEC § 2.12). Pour l'instant, seule la section « SAUVEGARDE » :
 * export JSON v2, import avec confirmation (§ 5.4) et encart de statut.
 */
@Composable
fun SettingsScreen(backupViewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val state by backupViewModel.state.collectAsStateWithLifecycle()
    val workouts by LocalAppContainer.current.workouts.workouts.collectAsStateWithLifecycle()
    val colors = MuscleNoteTheme.colors

    val chooserTitle by rememberUpdatedState(stringResource(R.string.settings_share_json))
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        backupViewModel.onShareSheetClosed()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupViewModel.onFileReceived(uri)
    }
    LaunchedEffect(backupViewModel) {
        backupViewModel.shareRequests.collect { uri ->
            try {
                shareLauncher.launch(shareIntent(uri, chooserTitle))
            } catch (e: ActivityNotFoundException) {
                backupViewModel.onShareSheetUnavailable(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_backup))

            val busy = state.operation != null
            ActionCard(
                icon = R.drawable.ic_upload,
                tint = colors.primary,
                title = stringResource(R.string.settings_export_title),
                description = stringResource(R.string.settings_export_desc),
                loading = state.operation == BackupOperation.Export,
                enabled = !busy,
                onClick = backupViewModel::export,
            )
            ActionCard(
                icon = R.drawable.ic_download,
                tint = FixedColors.dataImport,
                title = stringResource(R.string.settings_import_title),
                description = stringResource(R.string.settings_import_desc),
                loading = state.operation == BackupOperation.Import,
                enabled = !busy,
                onClick = {
                    backupViewModel.onImportRequested()
                    try {
                        pickLauncher.launch(IMPORT_MIME_TYPES)
                    } catch (e: ActivityNotFoundException) {
                        backupViewModel.onFileReadUnavailable(e.localizedMessage ?: e.javaClass.simpleName)
                    }
                },
            )

            if (state.pendingImport != null) {
                ImportConfirmation(
                    workoutCount = workouts.size,
                    onCancel = backupViewModel::cancelImport,
                    onConfirm = backupViewModel::confirmImport,
                )
            }
            state.status?.let { StatusMessage(it) }
        }
    }
}

private fun shareIntent(uri: Uri, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Carte-action : icône ronde, titre, description, chevron (§ 2.12, point 3). */
@Composable
private fun ActionCard(
    @DrawableRes icon: Int,
    tint: Color,
    title: String,
    description: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** « Écraser les données actuelles ? » [Annuler] [✓ Confirmer] (§ 5.4, point 2). */
@Composable
private fun ImportConfirmation(workoutCount: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val accent = FixedColors.dataImport
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.27f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Column {
            Text(
                stringResource(R.string.settings_import_confirm_title),
                color = colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                stringResource(R.string.settings_import_confirm_desc, workoutCount),
                color = colors.mutedForeground,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val buttonShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .border(1.dp, colors.border, buttonShape)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .background(accent)
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.common_confirm), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Encart de succès ou d'erreur sous les actions (§ 2.12, point 4). */
@Composable
private fun StatusMessage(status: BackupStatus) {
    val colors = MuscleNoteTheme.colors
    val success = status is BackupStatus.ExportShared || status is BackupStatus.ImportSucceeded
    val tint = if (success) colors.primary else colors.destructive
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.27f), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(if (success) R.drawable.ic_check_circle else R.drawable.ic_error),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = status.message(),
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackupStatus.message(): String = when (this) {
    BackupStatus.ExportShared -> stringResource(R.string.settings_export_success_share)
    is BackupStatus.ExportFailed -> stringResource(R.string.settings_export_error, detail)
    BackupStatus.ImportSucceeded -> stringResource(R.string.settings_import_success)
    is BackupStatus.ReadFailed -> stringResource(R.string.settings_import_read_error, detail)
    is BackupStatus.ImportFailed -> stringResource(R.string.settings_import_error, detail)
    is BackupStatus.ImportRejected -> stringResource(
        R.string.settings_import_error,
        when (val r = reason) {
            BackupReadResult.InvalidFormat -> stringResource(R.string.settings_import_invalid_format)
            BackupReadResult.NoData -> stringResource(R.string.settings_import_no_muscle_note)
            BackupReadResult.NewerVersion -> stringResource(R.string.settings_import_newer_version)
            is BackupReadResult.InvalidKey -> stringResource(R.string.settings_import_invalid_key, r.key)
        },
    )
}
