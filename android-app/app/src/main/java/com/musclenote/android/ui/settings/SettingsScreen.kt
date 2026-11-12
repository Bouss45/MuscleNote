package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.BackupReadResult
import com.musclenote.android.data.timer.RestTimerRules
import com.musclenote.android.notifications.AppNotifications
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.FixedColors
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Types proposés au sélecteur de fichiers pour l'import (§ 2.12). */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

/**
 * Onglet Paramètres (SPEC § 2.12) : « Mes données » ; « PRÉFÉRENCES » (langue, thème, minuteur de
 * repos, avertissement « Notifications désactivées ») ; « SAUVEGARDE » (export JSON v2, import avec
 * confirmation § 5.4) ; encart de statut ; encart d'info ; « APPLICATION » (mises à jour § 3.17,
 * rapport de plantage s'il y en a un § 10.9) ; « TON AVIS » (§ 14), seulement si l'URL du webhook
 * a été fournie au build.
 */
@Composable
fun SettingsScreen(backupViewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val state by backupViewModel.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    val jsonChooserTitle by rememberUpdatedState(stringResource(R.string.settings_share_json))
    val csvChooserTitle by rememberUpdatedState(stringResource(R.string.settings_share_csv))
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        backupViewModel.onShareSheetClosed()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupViewModel.onFileReceived(uri)
    }
    LaunchedEffect(backupViewModel) {
        backupViewModel.shareRequests.collect { request ->
            try {
                shareLauncher.launch(
                    when (request) {
                        is ShareRequest.Json -> shareIntent(request.uri, jsonChooserTitle)
                        is ShareRequest.Csv -> csvShareIntent(request.uris, csvChooserTitle)
                    },
                )
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
            MyDataCard(sessions = workouts.size, customExercises = customExercises.count { !it.archived })

            SectionTitle(stringResource(R.string.settings_section_preferences))
            LanguageCard()
            ThemeCard(
                selected = preferences.theme,
                onSelect = { theme ->
                    haptics.light()
                    container.launchMutation { container.preferences.setTheme(theme) }
                },
            )
            RestTimerCard(
                selected = preferences.restSeconds,
                onSelect = { seconds ->
                    haptics.light()
                    container.launchMutation { container.preferences.setRestSeconds(seconds) }
                },
            )
            NotificationsDisabledCard()

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
                icon = R.drawable.ic_table_view,
                tint = FixedColors.csvExport,
                title = stringResource(R.string.settings_export_csv_title),
                description = stringResource(R.string.settings_export_csv_desc),
                loading = state.operation == BackupOperation.CsvExport,
                enabled = !busy,
                onClick = backupViewModel::exportCsv,
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
            SettingsInfoBox()

            SectionTitle(stringResource(R.string.settings_section_app))
            UpdateCard()
            CrashReportCard()

            if (container.feedback.enabled) {
                SectionTitle(stringResource(R.string.settings_section_feedback))
                FeedbackCard()
            }
        }
    }
}

/** Fichiers CSV partagés ensemble (`ACTION_SEND_MULTIPLE`, `text/csv`, § 8). */
private fun csvShareIntent(uris: List<Uri>, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/csv"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title)
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

/** Icône ronde (carré arrondi 44 dp, fond couleur à 13 %) des cartes de Paramètres. */
@Composable
internal fun CardIcon(@DrawableRes icon: Int, tint: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Minuteur de repos (§ 2.12) : préréglages de 1 à 5 min ; la durée choisie vaut pour les repos
 * démarrés ensuite (§ 7.2).
 */
@Composable
private fun RestTimerCard(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MuscleNoteTheme.colors
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
            CardIcon(R.drawable.ic_schedule, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.settings_rest_timer_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_rest_timer_desc), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (seconds in RestTimerRules.PRESETS) {
                val active = seconds == selected
                val chipShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .clip(chipShape)
                        .background(if (active) colors.primary else colors.muted)
                        .border(1.dp, if (active) colors.primary else colors.border, chipShape)
                        .clickable { onSelect(seconds) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        restDurationLabel(seconds),
                        color = if (active) colors.primaryForeground else colors.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** `45 s`, `1 min`, `1 min 30` (§ 2.12). */
@Composable
private fun restDurationLabel(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.settings_rest_timer_seconds, rest)
        rest == 0 -> stringResource(R.string.settings_rest_timer_minutes, minutes)
        else -> stringResource(R.string.settings_rest_timer_minutes_seconds, minutes, rest)
    }
}

/**
 * « Notifications désactivées — minuteur et rappels silencieux » + « Ouvrir les réglages »
 * (🔄 D6), seulement si la permission est refusée ou les notifications de l'app coupées ; revérifié
 * à chaque retour sur l'écran.
 */
@Composable
private fun NotificationsDisabledCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppNotifications.areEnabled(context)) }
    LifecycleResumeEffect(context) {
        enabled = AppNotifications.areEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) return

    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.destructive.copy(alpha = 0.08f))
            .border(1.dp, colors.destructive.copy(alpha = 0.27f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardIcon(R.drawable.ic_notifications_off, colors.destructive)
            Text(
                stringResource(R.string.settings_notifications_denied),
                color = colors.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f),
            )
        }
        val buttonShape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(buttonShape)
                .border(1.dp, colors.destructive.copy(alpha = 0.5f), buttonShape)
                .clickable { openNotificationSettings(context) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.settings_notifications_open), color = colors.destructive, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Réglages de notification de l'app ; à défaut, sa fiche dans les réglages système. */
private fun openNotificationSettings(context: Context) {
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(notificationSettings)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
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
    val success = status is BackupStatus.ExportShared || status is BackupStatus.CsvExported || status is BackupStatus.ImportSucceeded
    StatusBox(success = success, message = status.message())
}

/** Encart de succès (`primary`) ou d'erreur (`destructive`) : sauvegarde (§ 2.12), avis (§ 14.4). */
@Composable
internal fun StatusBox(success: Boolean, message: String) {
    val colors = MuscleNoteTheme.colors
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
            text = message,
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
    BackupStatus.CsvExported -> stringResource(R.string.settings_export_success_csv)
    BackupStatus.NothingToExport -> stringResource(R.string.settings_export_no_data)
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
