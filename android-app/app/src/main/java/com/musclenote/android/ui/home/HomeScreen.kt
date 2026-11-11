package com.musclenote.android.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.Workout
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Onglet Accueil (SPEC § 2.2) — pour l'instant, le bouton « Nouvelle séance » ou la carte de la
 * séance en cours (point 1), puis la carte provisoire de vérification.
 */
@Composable
fun HomeScreen(onStartWorkout: () -> Unit, onOpenWorkout: () -> Unit, modifier: Modifier = Modifier) {
    val currentWorkout by LocalAppContainer.current.workouts.currentWorkout.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.tabs_home))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val active = currentWorkout
            if (active != null) {
                ActiveWorkoutCard(active, onClick = onOpenWorkout)
            } else {
                NewWorkoutButton(
                    onClick = {
                        haptics.medium()
                        onStartWorkout()
                    },
                )
            }
            LoadedDataCard()
        }
    }
}

/** Gros bouton `primary` « Nouvelle séance » (§ 2.2, point 1). */
@Composable
private fun NewWorkoutButton(onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.home_new_session), color = colors.primaryForeground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Carte de la séance en cours : bordure et fond `primary`, nom affiché, exercices et poids. */
@Composable
private fun ActiveWorkoutCard(workout: Workout, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primary.copy(alpha = 0.13f))
            .border(1.5.dp, colors.primary, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
        Column(Modifier.weight(1f)) {
            Text(workoutDisplayName(workout), color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.home_active_subtitle,
                    pluralStringResource(R.plurals.common_exercises_count, workout.exercises.size, workout.exercises.size),
                    formatNumber(workout.bodyWeight),
                ),
                color = colors.mutedForeground,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
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
