package com.musclenote.android.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.update.UpdateRules
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.GymTime
import com.musclenote.android.data.workout.Streak
import com.musclenote.android.data.workout.WeekStatus
import com.musclenote.android.data.workout.WorkoutStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.downloadUpdate
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.theme.MuscleNoteTheme
import java.time.ZoneId

/**
 * Onglet Accueil (SPEC § 2.2) : sur l'image de fond floutée, le bouton « Nouvelle séance » (ou la
 * séance en cours), l'annonce d'une mise à jour (§ 3.17), la carte de transfert depuis l'ancienne
 * app, la série hebdomadaire, les statistiques, le temps en salle, puis toutes les séances
 * terminées avec édition, modale de date et suppression en deux temps ; état vide sans aucune
 * séance.
 */
@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onOpenWorkout: () -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val currentWorkout by repository.currentWorkout.collectAsStateWithLifecycle()
    val localPreferences by container.preferences.localPreferences.collectAsStateWithLifecycle()
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = MuscleNoteTheme.colors

    val time = container.time
    val zone = time.zone()
    // Relu à chaque reprise : la semaine courante de la série change chaque lundi.
    val today = rememberToday()

    val streak = remember(workouts, today, zone) { WorkoutStats.streak(workouts, today, zone) }
    val gymTime = remember(workouts) { WorkoutStats.gymTime(workouts) }
    val totalVolume = remember(workouts) { workouts.sumOf { WorkoutStats.volume(it) } }
    val dates = rememberDateFormats(zone)

    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var dateEditId by rememberSaveable { mutableStateOf<String?>(null) }

    // 🔄 D25 : nouvelle Release pour une app installée sans Obtainium, tant qu'elle n'est pas masquée.
    val installedVersion = container.updates.currentVersion
    val update = if (container.updateChecksEnabled) {
        UpdateRules.available(updateState, installedVersion)?.takeIf { it.version != updateState.dismissedVersion }
    } else {
        null
    }

    Box(modifier.fillMaxSize()) {
        HomeBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item(key = "top") { Spacer(Modifier.statusBarsPadding().height(10.dp)) }

            item(key = "main") {
                val active = currentWorkout
                if (active != null) {
                    ActiveWorkoutCard(active, onClick = onOpenWorkout, modifier = Modifier.padding(bottom = 20.dp))
                } else {
                    NewWorkoutButton(
                        onClick = {
                            haptics.medium()
                            onStartWorkout()
                        },
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            }

            if (update != null) {
                item(key = "update") {
                    UpdateBanner(
                        release = update,
                        installedVersion = installedVersion,
                        onDownload = { downloadUpdate(context, update) },
                        onDismiss = {
                            haptics.light()
                            container.launchMutation { container.updates.dismiss(update.version) }
                        },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            // 🔄 D4/D5 : tant que l'app n'a aucune donnée, explique le transfert depuis l'app Expo.
            if (workouts.isEmpty() && currentWorkout == null && !localPreferences.migrateCardDismissed) {
                item(key = "migrate") {
                    MigrateCard(
                        onDismiss = {
                            haptics.light()
                            container.launchMutation { container.preferences.dismissMigrateCard() }
                        },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            if (workouts.isNotEmpty()) {
                item(key = "streak") { StreakCard(streak, Modifier.padding(bottom = 16.dp)) }
                item(key = "stats") {
                    StatsRow(
                        sessions = workouts.size,
                        totalVolume = totalVolume,
                        lastBodyWeight = workouts.first().bodyWeight,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (gymTime.sessionCount > 0) {
                    item(key = "gym-time") { GymTimeCard(gymTime, Modifier.padding(bottom = 18.dp)) }
                }
                item(key = "recent-title") {
                    Text(
                        stringResource(R.string.home_recent_sessions),
                        color = colors.foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                    )
                }
                items(workouts, key = { it.id }) { workout ->
                    WorkoutCard(
                        workout = workout,
                        dates = dates,
                        zone = zone,
                        deleting = deletingId == workout.id,
                        onEdit = { onEditWorkout(workout.id) },
                        onEditDate = {
                            haptics.light()
                            deletingId = null
                            dateEditId = workout.id
                        },
                        onDelete = {
                            deletingId = if (deletingId == workout.id) null else workout.id
                        },
                        onCancelDelete = { deletingId = null },
                        onConfirmDelete = {
                            haptics.warning()
                            deletingId = null
                            val id = workout.id
                            container.launchMutation { repository.deleteWorkout(id) }
                        },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            } else if (currentWorkout == null) {
                item(key = "empty") { EmptyState() }
            }
        }
    }

    val editing = dateEditId?.let { id -> workouts.firstOrNull { it.id == id } }
    if (editing != null) {
        WorkoutDateDialog(
            workout = editing,
            zone = zone,
            onDismiss = { dateEditId = null },
            onSave = { instant ->
                dateEditId = null
                val id = editing.id
                container.launchMutation { repository.updateWorkoutDate(id, instant) }
            },
        )
    }
}

/**
 * Image de fond plein écran, recadrée, floutée (Android 12+ ; sans flou en dessous) et estompée :
 * opacité 0,35 en thème sombre, 0,12 en thème clair.
 */
@Composable
private fun HomeBackground() {
    Image(
        painter = painterResource(R.drawable.home_bg),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = if (MuscleNoteTheme.colors.isDark) 0.35f else 0.12f,
        modifier = Modifier
            .fillMaxSize()
            .blur(6.dp),
    )
}

/** Gros bouton `primary` « Nouvelle séance » (§ 2.2, point 1). */
@Composable
private fun NewWorkoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Row(
        modifier = modifier
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
private fun ActiveWorkoutCard(workout: Workout, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
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

/** « Tu viens de l'ancienne MuscleNote ? » + mode d'emploi du transfert (§ 5.5), masquable. */
@Composable
private fun MigrateCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.info.copy(alpha = 0.33f), shape)
            .padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.info.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.info, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_migrate_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.home_migrate_text), color = colors.mutedForeground, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.common_cancel), tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Carte « Série en cours » (§ 2.2, point 3 ; calcul § 3.6) : compteur et meilleure série,
 * mini-calendrier des 8 dernières semaines, légende et phrase d'aide. En `primary` tant que la
 * série est vivante.
 */
@Composable
private fun StreakCard(streak: Streak, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val active = streak.current > 0 && streak.isAlive
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.primary.copy(alpha = 0.08f) else colors.card)
            .border(1.5.dp, if (active) colors.primary else colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.primary.copy(alpha = 0.15f) else colors.muted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = if (active) colors.primary else colors.mutedForeground, modifier = Modifier.size(18.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(stringResource(R.string.home_streak_current), color = colors.mutedForeground, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        streak.current.toString(),
                        color = if (active) colors.primary else colors.foreground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        pluralStringResource(R.plurals.home_streak_week_unit, streak.current),
                        color = colors.mutedForeground,
                        fontSize = 14.sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.home_streak_best), color = colors.mutedForeground, fontSize = 10.sp)
                Text(stringResource(R.string.home_streak_best_value, streak.best), color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (streak.lastWeeks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_streak_last8weeks), color = colors.mutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (week in streak.lastWeeks) {
                        val (background, content) = weekColors(week.status)
                        val cellShape = RoundedCornerShape(8.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(cellShape)
                                .background(background)
                                .then(if (week.isCurrent) Modifier.border(1.5.dp, colors.primary, cellShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(week.count.toString(), color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendItem(weekColors(WeekStatus.Validated).first, stringResource(R.string.home_streak_legend_validated))
                    LegendItem(weekColors(WeekStatus.Partial).first, stringResource(R.string.home_streak_legend_partial))
                    LegendItem(weekColors(WeekStatus.None).first, stringResource(R.string.home_streak_legend_none))
                }
            }
        }

        Text(
            stringResource(
                when {
                    streak.current == 0 -> R.string.home_streak_hint_start
                    streak.isAlive -> R.string.home_streak_hint_alive
                    else -> R.string.home_streak_hint_over
                },
            ),
            color = colors.mutedForeground,
            fontSize = 11.sp,
        )
    }
}

/** Fond et texte d'une case du mini-calendrier ; partielle en ambre `warning` (🔄 D15). */
@Composable
private fun weekColors(status: WeekStatus): Pair<Color, Color> {
    val colors = MuscleNoteTheme.colors
    return when (status) {
        WeekStatus.Validated -> colors.primary to colors.primaryForeground
        WeekStatus.Partial -> colors.warning.copy(alpha = 0.2f) to colors.foreground
        WeekStatus.None -> colors.muted to colors.mutedForeground
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, color = MuscleNoteTheme.colors.mutedForeground, fontSize = 10.sp)
    }
}

/** Séances, volume total (tonnes arrondies, § 3.8) et poids de corps de la dernière séance. */
@Composable
private fun StatsRow(sessions: Int, totalVolume: Double, lastBodyWeight: Double, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(formatInteger(sessions.toDouble()), stringResource(R.string.home_stat_sessions), Modifier.weight(1f))
        StatCard("${formatInteger(totalVolume / 1000)}t", stringResource(R.string.home_stat_total_volume), Modifier.weight(1f))
        StatCard("${formatNumber(lastBodyWeight)} kg", stringResource(R.string.home_stat_last_weight), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = colors.mutedForeground, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

/** Temps total en salle et durée moyenne par séance (§ 3.7). */
@Composable
private fun GymTimeCard(gymTime: GymTime, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(WorkoutStats.formatDuration(gymTime.totalMinutes), color = colors.foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.home_gym_time, WorkoutStats.formatDuration(gymTime.averageMinutes)),
                color = colors.mutedForeground,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Carte d'une séance terminée : nom affiché, date · heure · durée, poids de corps, éditer / date
 * / supprimer, et statistiques des séries validées (🔄 D15) — remplacées par la confirmation
 * pendant une suppression.
 */
@Composable
private fun WorkoutCard(
    workout: Workout,
    dates: DateFormats,
    zone: ZoneId,
    deleting: Boolean,
    onEdit: () -> Unit,
    onEditDate: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val start = remember(workout.date, zone) { parseInstantOrNull(workout.date, zone) }
    val duration = workout.durationMinutes?.takeIf { it != 0 }?.let { stringResource(R.string.home_minutes, it) }
    val subtitle = listOfNotNull(start?.let(dates::weekdayDayMonth), start?.let(dates::time), duration).joinToString(" · ")

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(workoutDisplayName(workout), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = colors.mutedForeground, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatNumber(workout.bodyWeight)} kg",
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                SmallIconButton(R.drawable.ic_edit, colors.mutedForeground, stringResource(R.string.edit_workout_header_label), onEdit)
                SmallIconButton(R.drawable.ic_calendar_today, colors.mutedForeground, stringResource(R.string.home_edit_date), onEditDate)
                SmallIconButton(R.drawable.ic_delete, colors.destructive, stringResource(R.string.common_delete), onDelete)
            }
        }

        if (deleting) {
            InlineConfirm(
                message = stringResource(R.string.home_delete_confirm),
                confirmLabel = stringResource(R.string.common_delete),
                onCancel = onCancelDelete,
                onConfirm = onConfirmDelete,
            )
        } else {
            val exercises = WorkoutStats.exercisesWithCompletedSets(workout)
            val sets = WorkoutStats.completedSetCount(workout)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkoutStat(R.drawable.ic_layers, pluralStringResource(R.plurals.common_exercises_count, exercises, exercises))
                WorkoutStat(R.drawable.ic_repeat, pluralStringResource(R.plurals.common_sets_count, sets, sets))
                WorkoutStat(R.drawable.ic_trending_up, stringResource(R.string.home_volume_short, formatInteger(WorkoutStats.volume(workout))))
            }
        }
    }
}

/** Bouton icône 28 × 28 bordé (rayon 8). */
@Composable
private fun SmallIconButton(@DrawableRes icon: Int, tint: Color, contentDescription: String, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(28.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun WorkoutStat(@DrawableRes icon: Int, text: String) {
    val colors = MuscleNoteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(12.dp))
        Text(text, color = colors.mutedForeground, fontSize = 12.sp)
    }
}

/** « Prêt à s'entrainer ? » : aucune séance terminée ni en cours. */
@Composable
private fun EmptyState() {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_fitness_center), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.home_empty_title), color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.home_empty_text),
            color = colors.mutedForeground,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}
