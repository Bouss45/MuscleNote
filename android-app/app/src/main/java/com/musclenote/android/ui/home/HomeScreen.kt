package com.musclenote.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Onglet Accueil (SPEC § 2.2) — pour l'instant, seulement la carte provisoire de vérification. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.tabs_home))
        LoadedDataCard(Modifier.padding(20.dp))
    }
}

/**
 * Provisoire (étape 3) : ce que les dépôts ont chargé, pour vérifier un import. À retirer
 * quand l'Accueil sera construit.
 */
@Composable
private fun LoadedDataCard(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val exercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val bodyWeights by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val supplements by container.nutrition.supplements.collectAsStateWithLifecycle()
    val archived = exercises.count { it.archived }

    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.debug_data_title),
            color = colors.mutedForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        CountRow(stringResource(R.string.debug_data_sessions), workouts.size)
        CountRow(
            label = stringResource(R.string.debug_data_custom_exercises),
            value = exercises.size - archived,
            detail = if (archived > 0) stringResource(R.string.debug_data_archived, archived) else null,
        )
        CountRow(stringResource(R.string.debug_data_weigh_ins), bodyWeights.size)
        CountRow(stringResource(R.string.debug_data_supplements), supplements.size)
    }
}

@Composable
private fun CountRow(label: String, value: Int, detail: String? = null) {
    val colors = MuscleNoteTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.foreground, fontSize = 15.sp)
            if (detail != null) Text(detail, color = colors.mutedForeground, fontSize = 12.sp)
        }
        Text(value.toString(), color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
