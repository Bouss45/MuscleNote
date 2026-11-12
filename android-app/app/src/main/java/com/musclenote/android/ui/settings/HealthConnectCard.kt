package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.health.HealthRecordType
import com.musclenote.android.data.repository.HealthHistoryResult
import com.musclenote.android.health.HealthConnectRecords
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.nutrition.AppSwitch
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Réponse de l'écran d'autorisation plus rapide que ça : Health Connect ne l'a pas affiché (refusé
 * deux fois) — [Autoriser] ouvre alors ses réglages.
 */
private const val PERMISSION_DIALOG_SKIPPED_MS = 500L

/**
 * Section « SANTÉ » de Paramètres (SPEC § 15.3, 🔄 D29), affichée seulement si Health Connect est
 * disponible (relu à chaque retour sur l'écran) : titre de section et carte Health Connect.
 */
@Composable
fun HealthConnectSection() {
    val container = LocalAppContainer.current
    val health = container.health
    var available by remember { mutableStateOf(health.isAvailable()) }
    LifecycleResumeEffect(health) {
        available = health.isAvailable()
        if (available) container.launchMutation { health.refreshPermissions() }
        onPauseOrDispose { }
    }
    if (!available) return
    SectionTitle(stringResource(R.string.settings_section_health))
    HealthConnectCard()
}

/**
 * Carte « Health Connect » : interrupteur (demande d'autorisation), autorisations manquantes et
 * [Autoriser], dernier envoi ou échec, [Envoyer l'historique] et son résultat.
 */
@Composable
private fun HealthConnectCard() {
    val container = LocalAppContainer.current
    val health = container.health
    val state by health.state.collectAsStateWithLifecycle()
    val granted by health.granted.collectAsStateWithLifecycle()
    val syncing by health.syncing.collectAsStateWithLifecycle()
    val historyResult by health.historyResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    /** La dernière demande venait de [Autoriser] (et non de l'interrupteur), lancée à cet instant. */
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
            CardIcon(R.drawable.ic_monitor_heart, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.health_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.health_desc), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
            AppSwitch(
                checked = state.enabled,
                onCheckedChange = { on ->
                    haptics.light()
                    denied = false
                    if (on) requestPermissions(fromButton = false) else container.launchMutation { health.disable() }
                },
            )
        }
        if (!state.enabled && denied) {
            Text(stringResource(R.string.health_denied), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (state.enabled) {
            val missingSessions = HealthRecordType.Session !in granted
            val missingWeight = HealthRecordType.Weight !in granted
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (missingSessions) {
                    Text(stringResource(R.string.health_missing_sessions), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (missingWeight) {
                    Text(stringResource(R.string.health_missing_weight), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
                }
                val lastSyncAt = state.lastSyncAt
                Text(
                    if (lastSyncAt == null) stringResource(R.string.health_never)
                    else stringResource(R.string.health_last, rememberDateTime(lastSyncAt, appLocale(), container.time.zone())),
                    color = colors.mutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                state.lastError?.let {
                    Text(stringResource(R.string.health_failed, it), color = colors.destructive, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (missingSessions || missingWeight) {
                    CardButton(
                        text = stringResource(R.string.health_allow),
                        accent = colors.destructive,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.light()
                            requestPermissions(fromButton = true)
                        },
                    )
                }
                CardButton(
                    text = stringResource(R.string.health_send_history),
                    filled = true,
                    loading = syncing,
                    enabled = !syncing,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.light()
                        container.launchMutation {
                            when (health.sendHistory()) {
                                is HealthHistoryResult.Sent -> haptics.success()
                                is HealthHistoryResult.Failed -> haptics.warning()
                            }
                        }
                    },
                )
            }
        }
    }
    if (state.enabled) {
        when (val result = historyResult) {
            is HealthHistoryResult.Sent -> StatusBox(
                success = true,
                message = stringResource(
                    R.string.health_history_sent,
                    pluralStringResource(R.plurals.health_sessions, result.sessions, result.sessions),
                    pluralStringResource(R.plurals.health_weights, result.weights, result.weights),
                ),
            )
            is HealthHistoryResult.Failed -> StatusBox(success = false, message = stringResource(R.string.health_failed, result.detail))
            null -> Unit
        }
    }
}

/** Réglages de Health Connect pour les données de MuscleNote (autorisations, suppression). */
private fun openHealthConnectSettings(context: Context) {
    try {
        context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
    } catch (e: ActivityNotFoundException) {
        Log.w("HealthConnect", "Réglages de Health Connect introuvables", e)
    }
}
