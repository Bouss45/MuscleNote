package com.musclenote.android.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.progress.ExerciseProgress
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.ui.components.ChartPoint
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.LineChart
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.color
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.theme.FixedColors
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.ZoneId

/**
 * Onglet d'un groupe musculaire (§ 2.10, calculs § 3.10, séries validées seulement) : séances,
 * charge (ou durée) max et volume (ou distance) max ; top 3 des exercices les plus utilisés avec
 * record, tendance et mini-graphique ; liste de tous les exercices utilisés. Chaque exercice ouvre
 * son historique ([onOpenHistory]).
 */
@Composable
fun MuscleGroupTab(
    group: MuscleGroup,
    workouts: List<Workout>,
    definitions: List<ExerciseDefinition>,
    zone: ZoneId,
    dates: DateFormats,
    onOpenHistory: (exerciseId: String) -> Unit,
) {
    val progress = remember(workouts, definitions, group, zone) { ProgressStats.muscleGroupProgress(workouts, definitions, group, zone) }
    val isCardio = group == MuscleGroup.Cardio
    val unit = if (isCardio) "min" else "kg"
    val color = group.color

    if (progress.isEmpty) {
        val name = stringResource(group.labelRes).lowercase(appLocale())
        ProgressEmptyState(
            title = stringResource(R.string.progress_muscle_empty, name),
            hint = stringResource(R.string.progress_muscle_empty_hint, name),
            icon = R.drawable.ic_bar_chart,
        )
        return
    }

    StatsRow(
        listOf(
            StatItem(progress.sessions.toString(), stringResource(R.string.progress_muscle_sessions), color),
            StatItem(
                "${formatNumber(progress.maxMetric)} $unit",
                stringResource(if (isCardio) R.string.progress_muscle_max_duration else R.string.progress_muscle_max_load),
                color,
            ),
            StatItem(
                // Volume en tonnes au dixième (§ 3.8), distance en km au dixième.
                if (isCardio) "${formatOneDecimal(progress.maxVolume)} km" else "${formatOneDecimal(progress.maxVolume / 1000)} t",
                stringResource(if (isCardio) R.string.progress_muscle_max_distance else R.string.progress_muscle_max_volume),
                color,
            ),
        ),
    )

    TopExercisesCard(progress.top3, isCardio, unit, dates, onOpenHistory)
    AllExercisesCard(progress.exercises, unit, color, onOpenHistory)
}

/** « Exercices les plus utilisés » : rang coloré, séances, record, tendance, mini-graphique. */
@Composable
private fun TopExercisesCard(
    top3: List<ExerciseProgress>,
    isCardio: Boolean,
    unit: String,
    dates: DateFormats,
    onOpenHistory: (String) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    ProgressCard(padding = 0.dp) {
        CardTitle(
            stringResource(R.string.progress_muscle_top_exercises),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            subtitle = stringResource(if (isCardio) R.string.progress_muscle_top_exercises_sub_cardio else R.string.progress_muscle_top_exercises_sub),
        )
        top3.forEachIndexed { index, exercise ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
            val rankColor = FixedColors.progressTop3[index % FixedColors.progressTop3.size]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHistory(exercise.definition.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rankColor.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#${index + 1}", color = rankColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ExerciseLabel(exercise, Modifier.weight(1f))
                if (exercise.record > 0) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(rankColor.copy(alpha = 0.13f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_emoji_events), contentDescription = null, tint = rankColor, modifier = Modifier.size(11.dp))
                        Text("${formatNumber(exercise.record)} $unit", color = rankColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val trend = exercise.trend
                if (trend != null && trend != 0.0) Trend(trend, unit)
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
            }
            if (exercise.points.size >= 2) {
                LineChart(
                    points = remember(exercise, dates) { exercise.points.map { ChartPoint(dates.dayMonth(it.date), it.value) } },
                    color = rankColor,
                    height = 130.dp,
                    unit = unit,
                    formatValue = { formatDecimal(it, locale, maxFractionDigits = 1) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            } else {
                Text(
                    stringResource(R.string.progress_muscle_need_two),
                    color = colors.mutedForeground,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** Tendance (dernier − premier point) : flèche et écart, vert en hausse, rouge en baisse. */
@Composable
private fun Trend(trend: Double, unit: String) {
    val colors = MuscleNoteTheme.colors
    val tint = if (trend > 0) colors.success else colors.destructive
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            painterResource(if (trend > 0) R.drawable.ic_trending_up else R.drawable.ic_trending_down),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text("${if (trend > 0) "+" else ""}${formatOneDecimal(trend)} $unit", color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** « Tous les exercices » du groupe utilisés au moins une fois : séances et record. */
@Composable
private fun AllExercisesCard(exercises: List<ExerciseProgress>, unit: String, color: Color, onOpenHistory: (String) -> Unit) {
    val colors = MuscleNoteTheme.colors
    ProgressCard {
        CardTitle(stringResource(R.string.progress_muscle_all_exercises), Modifier.padding(bottom = 6.dp))
        exercises.forEachIndexed { index, exercise ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHistory(exercise.definition.id) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExerciseLabel(exercise, Modifier.weight(1f))
                if (exercise.record > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(painterResource(R.drawable.ic_emoji_events), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                        Text("${formatNumber(exercise.record)} $unit", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Nom affiché de l'exercice et « N séances ». */
@Composable
private fun ExerciseLabel(exercise: ExerciseProgress, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Column(modifier) {
        Text(exerciseName(exercise.definition), color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            pluralStringResource(R.plurals.common_sessions_count, exercise.sessionCount, exercise.sessionCount),
            color = colors.mutedForeground,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
