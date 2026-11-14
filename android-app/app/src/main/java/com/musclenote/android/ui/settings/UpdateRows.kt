package com.musclenote.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.update.UpdateRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.downloadUpdate
import com.musclenote.android.ui.components.rememberHaptics

/**
 * Lignes « Mises à jour » (SPEC § 15.10.3, § 3.17, 🔄 D25) : version installée à droite, état en
 * sous-titre. Installée par Obtainium → « Gérées par Obtainium », ligne inactive. Sinon toucher
 * vérifie tout de suite ; une Release plus récente ajoute « Télécharger la … » (APK dans le
 * navigateur).
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

    val enabled = container.updateChecksEnabled
    val installed = updates.currentVersion
    val available = if (enabled) UpdateRules.available(state, installed) else null
    val checkedAt = rememberDateTime(state.checkedAt, appLocale(), container.time.zone())
    val subtitle = when {
        !enabled -> stringResource(R.string.settings_update_subtitle_managed)
        checking -> stringResource(R.string.settings_update_subtitle_checking)
        available != null -> stringResource(R.string.settings_update_subtitle_available, available.version)
        failed -> stringResource(R.string.settings_update_failed)
        state.checkedAt == 0L -> stringResource(R.string.settings_update_subtitle_never)
        else -> stringResource(R.string.settings_update_subtitle_up_to_date, checkedAt)
    }
    return buildList {
        add(
            SettingsRow(
                key = "updates",
                title = stringResource(R.string.settings_update_title),
                subtitle = subtitle,
                value = installed,
                onClick = if (enabled && !checking) ({
                    haptics.light()
                    container.launchMutation { updates.checkNow() }
                }) else null,
            ),
        )
        if (available != null) {
            add(
                SettingsRow(
                    key = "download",
                    title = stringResource(R.string.settings_update_download, available.version),
                    chevron = true,
                    onClick = { downloadUpdate(context, available) },
                ),
            )
        }
    }
}
