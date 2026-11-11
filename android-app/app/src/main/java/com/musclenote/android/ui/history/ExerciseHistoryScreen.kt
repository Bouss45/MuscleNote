package com.musclenote.android.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.workout.ExerciseSession
import com.musclenote.android.data.workout.WorkoutStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ChartPoint
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.LineChart
import com.musclenote.android.ui.components.MuscleGroupBadge
import com.musclenote.android.ui.components.NotFoundScreen
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Historique d'un exercice (SPEC § 2.8) : records, graphique chronologique de la charge maximale
 * (ou de la durée en cardio) et liste des séances, de la plus récente à la plus ancienne. Seules
 * les séances terminées et les séries validées comptent (🔄 D15) ; la séance en cours n'y est pas.
 */
@Composable
fun ExerciseHistoryScreen(exerciseId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    // Archivés compris : un exercice supprimé mais encore référencé garde son historique.
    val definition = remember(customExercises, exerciseId) { exerciseDefinitions(customExercises).firstOrNull { it.id == exerciseId } }
    if (definition == null) {
        NotFoundScreen(stringResource(R.string.exercise_history_not_found), onBack)
        return
    }

    val zone = container.time.zone()
    val history = remember(workouts, exerciseId, zone) { WorkoutStats.exerciseHistory(workouts, exerciseId, zone) }
    val dates = rememberDateFormats(zone)
    val isCardio = definition.isCardio
    val colors = MuscleNoteTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") { Header(definition, onBack) }

        if (history.isNotEmpty()) {
            item(key = "records") { Records(history, isCardio, Modifier.padding(bottom = 6.dp)) }
        }

        when {
            history.size >= 2 -> item(key = "chart") { ProgressChart(history, isCardio, dates, Modifier.padding(bottom = 6.dp)) }
            history.size == 1 -> item(key = "one") {
                MessageCard(R.drawable.ic_bar_chart, stringResource(R.string.exercise_history_need_more), Modifier.padding(bottom = 6.dp))
            }
        }

        item(key = "title") {
            Text(
                stringResource(R.string.exercise_history_history_title),
                color = colors.foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (history.isEmpty()) {
            item(key = "empty") { MessageCard(R.drawable.ic_schedule, stringResource(R.string.exercise_history_empty)) }
        }

        items(history, key = { it.workoutId }) { session -> SessionCard(session, isCardio, dates) }

        item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun Header(definition: ExerciseDefinition, onBack: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.common_back),
                tint = colors.foreground,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exerciseName(definition), color = colors.foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            MuscleGroupBadge(definition.muscleGroup, small = true)
        }
    }
}

/**
 * Cartes de record : « Record personnel » (poids max) et « 1RM estimé » en force ; « Séance la
 * plus longue » (durée totale max) en cardio, sans 1RM.
 */
@Composable
private fun Records(history: List<ExerciseSession>, isCardio: Boolean, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val record = history.maxOf { it.metric(isCardio) }
    val bestOneRepMax = if (isCardio) 0.0 else history.maxOf { it.bestOneRepMax }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RecordCard(
            icon = R.drawable.ic_emoji_events,
            color = colors.primary,
            label = stringResource(if (isCardio) R.string.exercise_history_longest else R.string.exercise_history_pr),
            value = "${formatNumber(record)} ${if (isCardio) "min" else "kg"}",
            modifier = Modifier.weight(1f),
        )
        if (bestOneRepMax > 0) {
            RecordCard(
                icon = R.drawable.ic_bolt,
                color = colors.info,
                label = stringResource(R.string.exercise_history_one_rm),
                value = "${formatNumber(bestOneRepMax)} kg",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecordCard(@DrawableRes icon: Int, color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.33f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column {
            Text(label, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 12.sp)
            // Deux cartes côte à côte : « 102,5 kg » doit tenir sur une ligne.
            BasicText(
                value,
                style = LocalTextStyle.current.merge(TextStyle(color = color, fontWeight = FontWeight.Bold)),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 24.sp),
            )
        }
    }
}

/** Charge maximale (ou durée) par séance, de la plus ancienne à gauche à la plus récente. */
@Composable
private fun ProgressChart(history: List<ExerciseSession>, isCardio: Boolean, dates: DateFormats, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    val points = remember(history, isCardio, dates) {
        history.asReversed().map { ChartPoint(dates.dayMonth(it.date), it.metric(isCardio)) }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
    ) {
        Text(
            stringResource(if (isCardio) R.string.exercise_history_duration_chart else R.string.exercise_history_max_load),
            color = colors.foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        LineChart(
            points = points,
            color = colors.primary,
            height = 180.dp,
            unit = if (isCardio) "min" else "kg",
            formatValue = { formatDecimal(it, locale, maxFractionDigits = 1) },
        )
    }
}

/** Carte d'information : icône et texte centrés (une seule séance, historique vide). */
@Composable
private fun MessageCard(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(24.dp))
        Text(text, color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Une séance de l'historique : date, poids de corps, puces des séries validées ; en force, max,
 * volume et 1RM de la séance.
 */
@Composable
private fun SessionCard(session: ExerciseSession, isCardio: Boolean, dates: DateFormats) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dates.weekdayDayMonth(session.date),
                color = colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.exercise_history_body_weight_suffix, formatNumber(session.bodyWeight)),
                color = colors.mutedForeground,
                fontSize = 12.sp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (set in session.sets) {
                Text(
                    if (isCardio) cardioSetText(set) else "${set.reps} × ${formatNumber(set.weight)} kg",
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.muted)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (!isCardio) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SessionStat(R.drawable.ic_trending_up, colors.primary, stringResource(R.string.exercise_history_max, formatNumber(session.maxWeight)))
                SessionStat(R.drawable.ic_layers, colors.mutedForeground, stringResource(R.string.exercise_history_volume, formatInteger(session.volume)))
                val oneRepMax = session.bestOneRepMax
                if (oneRepMax > 0) {
                    SessionStat(R.drawable.ic_bolt, colors.info, stringResource(R.string.exercise_history_row_one_rm, formatNumber(oneRepMax)))
                }
            }
        }
    }
}

/** Série de cardio : `20 min · 3,2 km · 9,5 km/h · 2 %`, éléments nuls autres que la durée omis. */
@Composable
private fun cardioSetText(set: WorkoutSet): String {
    val parts = mutableListOf("${formatNumber(set.duration ?: 0.0)} min")
    set.distance?.takeIf { it != 0.0 }?.let { parts += "${formatNumber(it)} km" }
    set.speed?.takeIf { it != 0.0 }?.let { parts += "${formatNumber(it)} km/h" }
    set.incline?.takeIf { it != 0.0 }?.let { parts += "${formatNumber(it)} %" }
    return parts.joinToString(" · ")
}

@Composable
private fun SessionStat(@DrawableRes icon: Int, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 12.sp)
    }
}
