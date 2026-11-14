package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.health.HealthRecords
import com.musclenote.android.data.repository.HealthHistoryResult
import com.musclenote.android.health.HealthConnectRecords
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio

/**
 * Réponse de l'écran d'autorisation plus rapide que ça : Health Connect ne l'a pas affiché (refusé
 * deux fois) — [Autoriser] ouvre alors ses réglages.
 */
private const val PERMISSION_DIALOG_SKIPPED_MS = 500L

/**
 * Health Connect disponible sur l'appareil (SPEC § 15.3, 🔄 D29), relu à chaque retour sur l'écran ;
 * les autorisations sont relues en même temps.
 */
@Composable
internal fun rememberHealthAvailable(): Boolean {
    val container = LocalAppContainer.current
    val health = container.health
    var available by remember { mutableStateOf(health.isAvailable()) }
    LifecycleResumeEffect(health) {
        available = health.isAvailable()
        if (available) container.launchMutation { health.refreshPermissions() }
        onPauseOrDispose { }
    }
    return available
}

/**
 * Ligne « Health Connect » (§ 15.10.3) : « Activé » / « Désactivé », et l'état en sous-titre (dernier
 * envoi, autorisation manquante) ; [onOpen] ouvre la sheet.
 */
@Composable
internal fun healthConnectRow(onOpen: () -> Unit): SettingsRow {
    val container = LocalAppContainer.current
    val state by container.health.state.collectAsStateWithLifecycle()
    val granted by container.health.granted.collectAsStateWithLifecycle()
    val lastSyncAt = state.lastSyncAt
    val subtitle = when {
        !state.enabled -> stringResource(R.string.health_subtitle_off)
        HealthRecordType.entries.any { it !in granted } -> stringResource(R.string.health_subtitle_missing)
        lastSyncAt != null -> stringResource(R.string.health_subtitle_last, rememberDateTime(lastSyncAt, appLocale(), container.time.zone()))
        else -> stringResource(R.string.health_never)
    }
    return SettingsRow(
        key = "health",
        title = stringResource(R.string.health_title),
        subtitle = subtitle,
        value = stringResource(if (state.enabled) R.string.settings_on_m else R.string.settings_off_m),
        onClick = onOpen,
    )
}

/**
 * Sheet « Health Connect » (§ 15.3, § 15.10.3) : « Envoyer à Health Connect » (cocher demande
 * l'autorisation), autorisations manquantes et [Autoriser], dernier envoi, écrits, dernière erreur,
 * [Envoyer l'historique] et son résultat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectSheet(onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> HealthConnectSheetContent(bottomInset = inset) }
}

@Composable
internal fun HealthConnectSheetContent(bottomInset: Dp = 0.dp) {
    val container = LocalAppContainer.current
    val health = container.health
    val state by health.state.collectAsStateWithLifecycle()
    val granted by health.granted.collectAsStateWithLifecycle()
    val syncing by health.syncing.collectAsStateWithLifecycle()
    val historyResult by health.historyResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    /** La dernière demande venait de [Autoriser] (et non de la case), lancée à cet instant. */
    var fromAllowButton by remember { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { permissions ->
        val types = HealthConnectRecords.typesOf(permissions)
        val skipped = SystemClock.elapsedRealtime() - requestedAt < PERMISSION_DIALOG_SKIPPED_MS
        denied = types.isEmpty() && !fromAllowButton
        if (fromAllowButton && skipped && types.size < HealthRecordType.entries.size) openHealthConnectSettings(context)
        container.launchMutation { health.onPermissionsResult(types) }
    }

    fun requestPermissions(fromButton: Boolean) {
        fromAllowButton = fromButton
        requestedAt = SystemClock.elapsedRealtime()
        try {
            permissionLauncher.launch(HealthConnectRecords.PERMISSIONS)
        } catch (e: ActivityNotFoundException) {
            Log.w("HealthConnect", "Écran d'autorisation introuvable", e)
            openHealthConnectSettings(context)
        }
    }

    val locale = appLocale()
    val zone = container.time.zone()
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(stringResource(R.string.health_title), AnnotatedString(stringResource(R.string.health_desc)), messageStyle = Studio.typography.body)
        Column(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.health_toggle),
                trailing = ListRowTrailing.Check(state.enabled),
                showDivider = false,
                onClick = {
                    haptics.light()
                    denied = false
                    if (state.enabled) container.launchMutation { health.disable() } else requestPermissions(fromButton = false)
                },
            )
            if (state.enabled) {
                val lastSyncAt = state.lastSyncAt
                InfoRow(
                    label = stringResource(R.string.health_last_label),
                    value = if (lastSyncAt == null) "—" else rememberDateTime(lastSyncAt, locale, zone),
                )
                val written = remember(state.sent) { HealthRecords.countByType(state.sent.keys) }
                val sessions = written[HealthRecordType.Session] ?: 0
                val weights = written[HealthRecordType.Weight] ?: 0
                InfoRow(
                    label = stringResource(R.string.health_written_label),
                    value = pluralStringResource(R.plurals.health_sessions, sessions, sessions) + " · " +
                        pluralStringResource(R.plurals.health_weights, weights, weights),
                    showDivider = false,
                )
            }
        }
        if (!state.enabled && denied) StatusText(stringResource(R.string.health_denied))
        if (state.enabled) {
            val missingSessions = HealthRecordType.Session !in granted
            val missingWeight = HealthRecordType.Weight !in granted
            if (missingSessions || missingWeight) {
                Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                    if (missingSessions) StatusText(stringResource(R.string.health_missing_sessions))
                    if (missingWeight) StatusText(stringResource(R.string.health_missing_weight))
                }
                StudioButton(
                    stringResource(R.string.health_allow),
                    onClick = {
                        haptics.light()
                        requestPermissions(fromButton = true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                )
            }
            state.lastError?.let { error ->
                val detail = healthErrorText(state.lastErrorType, error)
                val at = state.lastErrorAt
                StatusText(if (at == null) detail else stringResource(R.string.health_failed, rememberDateTime(at, locale, zone), detail))
            }
            StudioButton(
                stringResource(if (syncing) R.string.health_sending else R.string.health_send_history),
                onClick = {
                    haptics.light()
                    container.launchMutation {
                        when (val result = health.sendHistory()) {
                            is HealthHistoryResult.Done -> if (result.failures.isEmpty()) haptics.success() else haptics.warning()
                            is HealthHistoryResult.Failed -> haptics.warning()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !syncing,
            )
            when (val result = historyResult) {
                is HealthHistoryResult.Done -> {
                    // Seulement ce que Health Connect a confirmé, puis chaque échec et type non autorisé.
                    val lines = buildList {
                        add(
                            stringResource(
                                R.string.health_history_sent,
                                pluralStringResource(R.plurals.health_sessions, result.sessions, result.sessions),
                                pluralStringResource(R.plurals.health_weights, result.weights, result.weights),
                            ),
                        )
                        result.failures.forEach { (type, error) -> add(healthErrorText(type, error)) }
                        if (HealthRecordType.Session in result.notGranted) add(stringResource(R.string.health_missing_sessions))
                        if (HealthRecordType.Weight in result.notGranted) add(stringResource(R.string.health_missing_weight))
                    }
                    StatusText(lines.joinToString("\n"))
                }
                is HealthHistoryResult.Failed -> StatusText(stringResource(R.string.health_unavailable, result.detail))
                null -> Unit
            }
        }
    }
}

/** Erreur d'un type (« Séances : échec (…). ») ou de Health Connect entier (« Envoi impossible (…). »). */
@Composable
private fun healthErrorText(type: HealthRecordType?, error: String): String = when (type) {
    HealthRecordType.Session -> stringResource(R.string.health_type_failed_sessions, error)
    HealthRecordType.Weight -> stringResource(R.string.health_type_failed_weight, error)
    null -> stringResource(R.string.health_unavailable, error)
}

/** Réglages de Health Connect pour les données de MuscleNote (autorisations, suppression). */
private fun openHealthConnectSettings(context: Context) {
    try {
        context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
    } catch (e: ActivityNotFoundException) {
        Log.w("HealthConnect", "Réglages de Health Connect introuvables", e)
    }
}
