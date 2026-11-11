package com.musclenote.android.ui.workout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.InlineConfirm
import com.musclenote.android.ui.components.NotFoundScreen
import com.musclenote.android.ui.components.dashedBorder
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

/**
 * Séance en cours (SPEC § 2.4) : en-tête avec chronomètre et progression, carte d'échauffement,
 * cartes d'exercice réordonnables par appui long, puis « Terminer la séance » avec confirmation.
 *
 * [onBack] revient à l'Accueil (la séance reste en cours) ; [onFinished] aussi, une fois la
 * séance terminée ou abandonnée. [onOpenHistory] ouvre l'historique d'un exercice (§ 2.8).
 */
@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenHistory: (exerciseId: String) -> Unit,
    onFinished: () -> Unit,
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val current by repository.currentWorkout.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val colors = MuscleNoteTheme.colors

    val workout = current
    if (workout == null) {
        NotFoundScreen(stringResource(R.string.workout_no_active), onBack)
        return
    }

    val definitions = remember(customExercises) { exerciseDefinitions(customExercises).associateBy { it.id } }
    // § 10.5 : un exercice dont la définition est introuvable est masqué (mais conservé).
    val visible = workout.exercises.filter { it.exerciseId in definitions }
    val visibleIds = visible.map { it.exerciseId }

    // Pendant un glisser-déposer, l'ordre vit ici ; il part au dépôt au relâchement (§ 2.4).
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val byId = visible.associateBy { it.exerciseId }
    val ordered = (dragOrder ?: visibleIds).mapNotNull { byId[it] }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val order = dragOrder ?: visibleIds
        val fromIndex = order.indexOfFirst { it == from.key }
        val toIndex = order.indexOfFirst { it == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            dragOrder = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.tick()
        }
    }

    val totalSets = workout.exercises.sumOf { it.sets.size }
    val completedSets = workout.exercises.sumOf { e -> e.sets.count { it.completed } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        WorkoutHeader(workout, completedSets, totalSets, container.time, onBack)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item(key = "warmup") { WarmupCard() }

            if (ordered.isEmpty()) item(key = "empty") { EmptyExercises() }

            items(ordered, key = { it.exerciseId }) { workoutExercise ->
                val exerciseId = workoutExercise.exerciseId
                ReorderableItem(reorderState, key = exerciseId) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 10.dp else 0.dp, label = "elevation")
                    val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "scale")
                    ExerciseCard(
                        definition = definitions.getValue(exerciseId),
                        workoutExercise = workoutExercise,
                        onToggleSet = { setId -> container.launchMutation { repository.toggleSetCompleted(exerciseId, setId) } },
                        onUpdateSet = { setId, transform -> container.launchMutation { repository.updateSet(exerciseId, setId, transform) } },
                        onRemoveSet = { setId -> container.launchMutation { repository.removeSet(exerciseId, setId) } },
                        onAddSet = { container.launchMutation { repository.addSet(exerciseId) } },
                        onNoteChange = { note -> container.launchMutation { repository.updateExerciseNote(exerciseId, note) } },
                        onRemove = { container.launchMutation { repository.removeExerciseFromWorkout(exerciseId) } },
                        onOpenHistory = { onOpenHistory(exerciseId) },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .shadow(elevation, RoundedCornerShape(14.dp))
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.light() },
                                onDragStopped = {
                                    dragOrder?.let { order -> container.launchMutation { repository.reorderExercises(order) } }
                                    dragOrder = null
                                },
                            ),
                    )
                }
            }

            item(key = "add") { AddExerciseButton(onAddExercise) }
        }

        FinishFooter(
            hasCompletedSet = WorkoutRules.hasCompletedSet(workout),
            onConfirm = { completed ->
                if (completed) haptics.success() else haptics.warning()
                onFinished()
                // Termine (ou abandonne, sans série validée) après avoir quitté l'écran (§ 3.3) ; le
                // repos en cours s'arrête aussi (§ 7.2).
                container.launchMutation { container.endWorkout() }
            },
        )
    }
}

@Composable
private fun WorkoutHeader(workout: Workout, completedSets: Int, totalSets: Int, time: TimeSource, onBack: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.foreground,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 12.dp),
                ) {
                    Text(stringResource(R.string.workout_in_progress), color = colors.mutedForeground, fontSize = 12.sp)
                    Text(
                        workoutDisplayName(workout),
                        color = colors.foreground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text("${formatNumber(workout.bodyWeight)} kg", color = colors.mutedForeground, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                }
                Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                ElapsedTime(workout.date, time, Modifier.padding(start = 5.dp))
            }
            if (totalSets > 0) {
                Row(
                    Modifier.padding(start = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.muted),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(completedSets.toFloat() / totalSets)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary),
                        )
                    }
                    Text(
                        stringResource(R.string.workout_sets_progress, completedSets, totalSets),
                        color = colors.mutedForeground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

/** Chronomètre `m:ss` (minutes non bornées) depuis le début de la séance, chaque seconde. */
@Composable
private fun ElapsedTime(startDate: String, time: TimeSource, modifier: Modifier = Modifier) {
    val start = remember(startDate) { parseInstantOrNull(startDate, time.zone())?.toEpochMilli() }
    val now by produceState(time.now().toEpochMilli()) {
        while (true) {
            value = time.now().toEpochMilli()
            delay(1_000 - value % 1_000)
        }
    }
    val elapsedSeconds: Long = if (start == null) 0L else ((now - start) / 1_000L).coerceAtLeast(0L)
    Text(
        String.format(Locale.ROOT, "%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
        color = MuscleNoteTheme.colors.primary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun EmptyExercises() {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_fitness_center), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.workout_empty_exercises), color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** « ⊕ Ajouter un exercice » en pointillés (séance en cours, éditeur de séance). */
@Composable
internal fun AddExerciseButton(onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .dashedBorder(1.5.dp, colors.border, 14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_add_circle), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.workout_add_exercise), color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Pied de page (§ 2.4) : « Terminer la séance », puis confirmation en ligne. Sans série validée,
 * la confirmation prévient que la séance ne sera pas enregistrée (🔄 D15).
 */
@Composable
private fun FinishFooter(hasCompletedSet: Boolean, onConfirm: (hasCompletedSet: Boolean) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        ) {
            if (confirming) {
                InlineConfirm(
                    message = stringResource(if (hasCompletedSet) R.string.workout_finish_confirm else R.string.workout_finish_empty),
                    confirmLabel = stringResource(if (hasCompletedSet) R.string.workout_finish_short else R.string.workout_abandon),
                    confirmIcon = if (hasCompletedSet) R.drawable.ic_check else R.drawable.ic_delete,
                    onCancel = { confirming = false },
                    onConfirm = { onConfirm(hasCompletedSet) },
                    large = true,
                )
            } else {
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.5.dp, colors.destructive, shape)
                        .clickable {
                            haptics.medium()
                            confirming = true
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check_box), contentDescription = null, tint = colors.destructive, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.workout_finish), color = colors.destructive, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
