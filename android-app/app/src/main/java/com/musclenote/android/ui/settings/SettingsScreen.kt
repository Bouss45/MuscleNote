package com.musclenote.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musclenote.android.R
import com.musclenote.android.ui.components.ScreenHeader

/** Onglet Paramètres (SPEC § 2.12) — écran vide pour l'instant. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title))
    }
}
