package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.downloadUpdate
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberNotificationPermissionRequest
import com.musclenote.android.update.InstallSource

/**
 * Ligne « Nouvelle version disponible » de Paramètres › Notifications (SPEC § 15.14.5, 🔄 D39) :
 * ligne à cocher, coupée par défaut si Obtainium a installé l'app ; activer demande la permission
 * des notifications. Elle commande aussi la vérification quotidienne. `null` dans la preview.
 */
@Composable
internal fun updateNotifyRow(): SettingsRow? {
    val container = LocalAppContainer.current
    val updates = container.updates
    if (!updates.notificationsAvailable) return null
    val state by updates.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val enabled = updates.notifyEnabled(state)
    return SettingsRow(
        key = "update-notify",
        title = stringResource(R.string.update_notify_title),
        subtitle = stringResource(if (updates.installedByObtainium) R.string.update_notify_subtitle_obtainium else R.string.update_notify_subtitle),
        checked = enabled,
        onClick = {
            haptics.light()
            // § 7.1 : demandée à l'activation ; un refus n'empêche pas la vérification.
            if (!enabled) requestNotificationPermission()
            container.launchMutation { updates.setNotifyEnabled(!enabled) }
        },
    )
}

/**
 * Lignes de la version de Paramètres › À propos (SPEC § 15.14.5, 🔄 D39) : « Version 1.0.42 · à
 * jour » ou « · 1.0.43 disponible » avec la date de la dernière vérification ; « Vérifier
 * maintenant », Obtainium ou non, notification coupée ou non ; une version plus récente ajoute
 * « Télécharger la … » (APK dans le navigateur) ou, installée par Obtainium, « Ouvrir Obtainium ».
 */
@Composable
internal fun updateRows(): List<SettingsRow> {
    val container = LocalAppContainer.current
    val updates = container.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val checking by updates.checking.collectAsStateWithLifecycle()
    val failed by updates.lastCheckFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    val installed = updates.currentVersion
    val available = updates.available(state)
    val checkedAt = rememberDateTime(state.checkedAt, appLocale(), container.time.zone())
    val title = when {
        available != null -> stringResource(R.string.settings_version_available, installed, available.version)
        state.checkedAt != 0L -> stringResource(R.string.settings_version_up_to_date, installed)
        else -> stringResource(R.string.settings_version, installed)
    }
    val subtitle = when {
        failed -> stringResource(R.string.settings_update_failed)
        state.checkedAt == 0L -> stringResource(R.string.settings_version_never)
        else -> stringResource(R.string.settings_version_checked, checkedAt)
    }
    return buildList {
        add(SettingsRow(key = "version", title = title, subtitle = subtitle))
        add(
            SettingsRow(
                key = "check-now",
                title = stringResource(R.string.settings_update_check_now),
                subtitle = if (checking) stringResource(R.string.settings_update_subtitle_checking) else null,
                onClick = if (checking) null else ({
                    haptics.light()
                    container.launchMutation { updates.checkNow() }
                }),
            ),
        )
        if (available != null) {
            add(
                if (updates.installedByObtainium) {
                    SettingsRow(
                        key = "open-obtainium",
                        title = stringResource(R.string.settings_update_open_obtainium),
                        chevron = true,
                        onClick = { viewUpdate(context, available) },
                    )
                } else {
                    SettingsRow(
                        key = "download",
                        title = stringResource(R.string.settings_update_download, available.version),
                        chevron = true,
                        onClick = { downloadUpdate(context, available) },
                    )
                },
            )
        }
    }
}

/** « Ouvrir Obtainium » : la destination de « Voir » de la notification (§ 15.14.4). */
private fun viewUpdate(context: Context, release: LatestRelease) {
    try {
        context.startActivity(InstallSource.viewUpdateIntent(context, release))
    } catch (e: ActivityNotFoundException) {
        Log.w("UpdateRows", "Ni Obtainium ni navigateur", e)
    }
}
