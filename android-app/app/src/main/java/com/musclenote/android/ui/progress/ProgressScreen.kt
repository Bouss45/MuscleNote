package com.musclenote.android.ui.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musclenote.android.R
import com.musclenote.android.ui.components.ScreenHeader

/** Onglet Progrès (SPEC § 2.10) — écran vide pour l'instant. */
@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.progress_title))
    }
}
