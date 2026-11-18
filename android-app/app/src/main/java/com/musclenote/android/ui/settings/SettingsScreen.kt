package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.backup.BackupReadResult
import com.musclenote.android.notifications.AppNotifications
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.ListGroup
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StatusMessage
import com.musclenote.android.ui.designsystem.templates.HubTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.tabScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.nutrition.SupplementSheetHost
import com.musclenote.android.ui.workout.ConfirmSheet

/** Types proposés au sélecteur de fichiers pour l'import (§ 2.12). */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

/** Formulaire ouvert au-dessus de Paramètres (§ 15.10.3). */
enum class SettingsSheet { Language, Theme, RestTimer, SupersetRest, WeighInReminder, AutoBackup, Health, CrashReport, Feedback }

/**
 * Paramètres (SPEC § 15.10.3, 🔄 D35) : hub de second niveau, ouvert par l'engrenage de l'Accueil.
 * Mes données ; « Préférences » (langue, thème, minuteur de repos) ; « Notifications »
 * (notifications coupées, rappel de pesée, rappels de chaque complément, § 15.13.3, 🔄 D38, et
 * nouvelle version disponible, § 15.14.5, 🔄 D39) ; « Données » (sauvegarde automatique § 15.2,
 * export JSON v2 et CSV, import avec confirmation § 5.4, Health Connect § 15.3 s'il est disponible) ;
 * « À propos » (version et vérification § 15.14.5, rapport de plantage § 10.9, avis § 14 si l'URL du
 * webhook a été fournie au build). Chaque réglage s'ouvre en formulaire
 * (bottom sheet) ou lance son action.
 */
@Composable
fun SettingsScreen(
    backupViewModel: BackupViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = tabScreenInsets(),
    initialSheet: SettingsSheet? = null,
) {
    val state by backupViewModel.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current

    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    // Complément ouvert depuis « Rappels » : sa sheet de Nutrition (§ 15.13.3).
    var supplementId by rememberSaveable { mutableStateOf<String?>(null) }
    val crashReport = rememberLatestCrashReport()
    val healthAvailable = rememberHealthAvailable()
    var notificationsEnabled by remember { mutableStateOf(AppNotifications.areEnabled(context)) }
    // Revérifié à chaque retour sur l'écran (réglages du système, § 2.12).
    LifecycleResumeEffect(context) {
        notificationsEnabled = AppNotifications.areEnabled(context)
        onPauseOrDispose { }
    }

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

    HubTemplate(
        modifier = modifier,
        titleBar = {
            Column(Modifier.fillMaxWidth()) {
                FicheTopBar(onNavigate = onBack)
                Spacer(Modifier.height(Studio.spacing.xxs))
                FicheTitle(stringResource(R.string.settings_title), titleStyle = Studio.typography.titleScreen)
            }
        },
        insets = insets,
        // 🔄 D40 : listes groupées (DESIGN.md § 10.1), 32 sous le titre comme dans la maquette.
        grouped = true,
    ) {
        // Mes données : rangée de stats dans un groupe, sans étiquette.
        ListGroup {
            StatRow(
                modifier = Modifier.padding(Studio.spacing.m),
                stats = listOf(
                    Stat(formatInteger(workouts.size.toDouble()), stringResource(R.string.settings_stat_sessions)),
                    Stat(formatInteger(customExercises.count { !it.archived }.toDouble()), stringResource(R.string.settings_stat_custom_exercises)),
                ),
            )
        }

        SettingsSection(
            stringResource(R.string.settings_section_preferences),
            buildList {
                add(SettingsRow("language", stringResource(R.string.settings_language_title), value = languageLabel(currentAppLanguage())) { sheet = SettingsSheet.Language })
                add(SettingsRow("theme", stringResource(R.string.settings_theme_title), value = themeLabel(preferences.theme)) { sheet = SettingsSheet.Theme })
                add(SettingsRow("rest", stringResource(R.string.settings_rest_timer_title), value = restDurationLabel(preferences.restSeconds)) { sheet = SettingsSheet.RestTimer })
                // 🔄 D46 : repos de transition d'une supersérie, juste après le minuteur (§ 15.20.4).
                add(
                    SettingsRow("superset-rest", stringResource(R.string.settings_superset_rest_title), value = restDurationLabel(preferences.supersetRestSeconds)) {
                        sheet = SettingsSheet.SupersetRest
                    },
                )
            },
        )

        // 🔄 D39 : « Rappels » devient « Notifications » (§ 15.14.5).
        SettingsSection(
            stringResource(R.string.settings_section_notifications),
            buildList {
                if (!notificationsEnabled) {
                    add(
                        SettingsRow(
                            "notifications",
                            stringResource(R.string.settings_notifications_off_title),
                            subtitle = stringResource(R.string.settings_notifications_off_subtitle),
                            chevron = true,
                        ) { openNotificationSettings(context) },
                    )
                }
                add(weighInReminderRow { sheet = SettingsSheet.WeighInReminder })
                addAll(supplementReminderRows { supplementId = it })
                updateNotifyRow()?.let(::add)
            },
        )

        val busy = state.operation != null
        val inProgress = stringResource(R.string.settings_in_progress)
        SettingsSection(
            stringResource(R.string.settings_section_data),
            buildList {
                add(autoBackupRow { sheet = SettingsSheet.AutoBackup })
                add(
                    SettingsRow(
                        "export",
                        stringResource(R.string.settings_export_title),
                        subtitle = if (state.operation == BackupOperation.Export) inProgress else stringResource(R.string.settings_export_short),
                        onClick = backupViewModel::export.takeUnless { busy },
                    ),
                )
                add(
                    SettingsRow(
                        "csv",
                        stringResource(R.string.settings_export_csv_title),
                        subtitle = if (state.operation == BackupOperation.CsvExport) inProgress else stringResource(R.string.settings_export_csv_short),
                        onClick = backupViewModel::exportCsv.takeUnless { busy },
                    ),
                )
                add(
                    SettingsRow(
                        "import",
                        stringResource(R.string.settings_import_title),
                        subtitle = if (state.operation == BackupOperation.Import) inProgress else stringResource(R.string.settings_import_short),
                        onClick = if (busy) null else ({
                            backupViewModel.onImportRequested()
                            try {
                                pickLauncher.launch(IMPORT_MIME_TYPES)
                            } catch (e: ActivityNotFoundException) {
                                backupViewModel.onFileReadUnavailable(e.localizedMessage ?: e.javaClass.simpleName)
                            }
                        }),
                    ),
                )
                if (healthAvailable) add(healthConnectRow { sheet = SettingsSheet.Health })
            },
        ) {
            state.status?.let { status ->
                StatusMessage(status.message())
            }
            Text(stringResource(R.string.settings_info), style = Studio.typography.caption, color = Studio.colors.textSecondary)
        }

        SettingsSection(
            stringResource(R.string.settings_section_about),
            buildList {
                addAll(updateRows())
                crashReport.value?.let { report ->
                    add(
                        SettingsRow(
                            "crash",
                            stringResource(R.string.settings_crash_title),
                            subtitle = stringResource(R.string.settings_crash_desc, crashDate(report)),
                        ) { sheet = SettingsSheet.CrashReport },
                    )
                }
                if (container.feedback.enabled) {
                    add(
                        SettingsRow(
                            "feedback",
                            stringResource(R.string.feedback_row_title),
                            subtitle = stringResource(R.string.feedback_row_subtitle),
                        ) { sheet = SettingsSheet.Feedback },
                    )
                }
            },
        )
    }

    val dismiss = { sheet = null }
    when (sheet) {
        SettingsSheet.Language -> LanguageSheet(onDismiss = dismiss)
        SettingsSheet.Theme -> ThemeSheet(
            selected = preferences.theme,
            onSelect = { theme ->
                haptics.light()
                sheet = null
                container.launchMutation { container.preferences.setTheme(theme) }
            },
            onDismiss = dismiss,
        )
        SettingsSheet.RestTimer -> RestTimerSheet(
            selected = preferences.restSeconds,
            onSelect = { seconds ->
                haptics.light()
                sheet = null
                container.launchMutation { container.preferences.setRestSeconds(seconds) }
            },
            onDismiss = dismiss,
        )
        SettingsSheet.SupersetRest -> SupersetRestSheet(
            selected = preferences.supersetRestSeconds,
            onSelect = { seconds ->
                haptics.light()
                sheet = null
                container.launchMutation { container.preferences.setSupersetRestSeconds(seconds) }
            },
            onDismiss = dismiss,
        )
        SettingsSheet.WeighInReminder -> WeighInReminderSheet(onDismiss = dismiss)
        SettingsSheet.AutoBackup -> AutoBackupSheet(onDismiss = dismiss)
        SettingsSheet.Health -> if (healthAvailable) HealthConnectSheet(onDismiss = dismiss)
        SettingsSheet.CrashReport -> crashReport.value?.let { report ->
            CrashReportSheet(report, onDeleted = { crashReport.value = null; sheet = null }, onDismiss = dismiss)
        }
        SettingsSheet.Feedback -> if (container.feedback.enabled) FeedbackSheet(onDismiss = dismiss)
        null -> Unit
    }
    SupplementSheetHost(supplementId, onClose = { supplementId = null })

    if (state.pendingImport != null) {
        ConfirmSheet(
            title = stringResource(R.string.settings_import_confirm_title),
            message = stringResource(R.string.settings_import_confirm_desc, workouts.size),
            confirmLabel = stringResource(R.string.common_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = backupViewModel::confirmImport,
            onDismiss = backupViewModel::cancelImport,
        )
    }
}

/**
 * Une ligne de Paramètres : titre, sous-titre (état), valeur à droite, chevron ou case à cocher
 * ([checked], toute la ligne la bascule : « Nouvelle version disponible ») ; [onClick] nul = ligne
 * inactive (opération en cours) ou d'information (version installée).
 */
internal data class SettingsRow(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
    val chevron: Boolean = false,
    val checked: Boolean? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * Une section de la liste groupée (🔄 D40) : étiquette, groupe de lignes (pas de filet sous la
 * dernière), puis [footer] sous le groupe, aligné sur le texte des lignes et espacé de 8.
 */
@Composable
internal fun SettingsSection(title: String, rows: List<SettingsRow>, footer: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
        SectionHeader(title, grouped = true)
        ListGroup {
            rows.forEachIndexed { index, row ->
                key(row.key) {
                    ListRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        trailing = when {
                            row.checked != null -> ListRowTrailing.Check(row.checked)
                            row.value != null -> ListRowTrailing.Value(row.value)
                            row.chevron -> ListRowTrailing.Chevron
                            else -> null
                        },
                        showDivider = index < rows.lastIndex,
                        onClick = row.onClick,
                    )
                }
            }
        }
        if (footer != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Studio.spacing.screenGrouped),
                verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                content = footer,
            )
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
