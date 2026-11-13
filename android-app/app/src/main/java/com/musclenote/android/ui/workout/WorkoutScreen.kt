package com.musclenote.android.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.SessionInsights
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.data.workout.WorkoutNotificationRules
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.data.workout.WorkoutStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.BottomActionBar
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.LiveHeader
import com.musclenote.android.ui.designsystem.components.RestBanner
import com.musclenote.android.ui.designsystem.components.SetProgress
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.templates.LiveTemplate
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

/**
 * Séance en cours (SPEC § 15.7, 🔄 D32) au gabarit « en direct » : en-tête fixe (réduire, nom,
 * séries, chrono) et progression ; échauffement ; un bloc par exercice (colonne « Préc. »,
 * suggestion de charge, lignes de série, repos, « + Série »), réordonnable par appui long ; en bas,
 * le pavé numérique pendant une saisie, sinon « Terminer la séance ».
 *
 * [onBack] revient à l'Accueil (la séance reste en cours) ; [onFinished] aussi, une fois la séance
 * terminée ou abandonnée. [onOpenHistory] ouvre l'historique d'un exercice (§ 2.8).
 * [initialEntry] ouvre le pavé sur un champ dès l'affichage (aperçus).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenHistory: (exerciseId: String) -> Unit,
    onFinished: () -> Unit,
    initialEntry: EntryTarget? = null,
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val current by repository.currentWorkout.collectAsStateWithLifecycle()
    val history by repository.workouts.collectAsStateWithLifecycle()
    val restTimer by container.restTimer.state.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val locale = appLocale()
    val dates = rememberDateFormats(container.time.zone())

    val workout = current
    if (workout == null) {
        NoActiveWorkout(onBack)
        return
    }

    val definitions = remember(customExercises) { exerciseDefinitions(customExercises).associateBy { it.id } }
    // § 10.5 : un exercice dont la définition est introuvable est masqué (mais conservé).
    val visible = workout.exercises.filter { it.exerciseId in definitions }
    val visibleIds = visible.map { it.exerciseId }
    val references = remember(history, visibleIds) { visibleIds.associateWith { SessionInsights.reference(history, it) } }
    val restAnchor = SessionInsights.restAnchor(workout) { it in definitions }
    val restRunning = rememberRestRunning(restTimer, container.time)

    // Saisie au pavé (§ 15.7.4) ; fermée si sa série disparaît (supprimée, exercice retiré).
    val entry = rememberSaveable(saver = SetEntryState.Saver) {
        SetEntryState().also { state ->
            val set = initialEntry?.let { t -> visible.firstOrNull { it.exerciseId == t.exerciseId }?.sets?.firstOrNull { it.id == t.setId } }
            if (initialEntry != null && set != null) state.open(initialEntry, SetEntryRules.format(initialEntry.field.read(set), initialEntry.field, locale))
        }
    }
    val target = entry.target
    val targetExercise = target?.let { t -> visible.firstOrNull { it.exerciseId == t.exerciseId } }
    val targetSet = targetExercise?.sets?.firstOrNull { it.id == target.setId }
    LaunchedEffect(target, targetSet == null) { if (target != null && targetSet == null) entry.close() }
    BackHandler(enabled = targetSet != null) { entry.close() }

    // Bottom sheets.
    var warmupOpen by rememberSaveable { mutableStateOf(false) }
    var warmupDone by rememberSaveable { mutableIntStateOf(0) }
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var removeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSetOf by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var finishOpen by rememberSaveable { mutableStateOf(false) }

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

    fun isCardio(exerciseId: String) = definitions[exerciseId]?.isCardio == true
    val labels = visible.associate { it.exerciseId to exerciseName(definitions.getValue(it.exerciseId)) }
    fun label(exerciseId: String) = labels[exerciseId].orEmpty()
    fun text(field: SetEntryField, set: WorkoutSet) = SetEntryRules.format(field.read(set), field, locale)

    /** Ouvre le pavé sur [field] de [setId], neuf. */
    fun open(exerciseId: String, set: WorkoutSet, field: SetEntryField) =
        entry.open(EntryTarget(exerciseId, set.id, field), text(field, set))

    /** Non validée → validée : repos avec le nom affiché de l'exercice (§ 2.6, § 7.2). */
    fun completeAndRest(exerciseId: String, setId: String, label: String) =
        container.launchMutation { if (repository.completeSet(exerciseId, setId)) container.restTimer.start(label) }

    val restContent: @Composable () -> Unit = {
        restTimer?.let { rest ->
            LiveRestBanner(
                rest = rest,
                time = container.time,
                onMinus = { container.launchMutation { container.adjustRest(-WorkoutNotificationRules.ADJUST_SECONDS) } },
                onPlus = { container.launchMutation { container.adjustRest(WorkoutNotificationRules.ADJUST_SECONDS) } },
                onSkip = {
                    haptics.light()
                    container.launchMutation { container.skipRest() }
                },
            )
        }
    }

    LiveTemplate(
        header = { LiveSessionHeader(workout, completedSets, totalSets, container.time, onBack) },
        progress = if (totalSets > 0) {
            { SetProgress(done = completedSets, total = totalSets) }
        } else {
            null
        },
        bottom = { inset ->
            val exercise = targetExercise
            val set = targetSet
            if (target != null && exercise != null && set != null) {
                val cardio = isCardio(exercise.exerciseId)
                WorkoutKeypad(
                    set = set,
                    setNumber = exercise.sets.indexOf(set) + 1,
                    isCardio = cardio,
                    entry = entry,
                    field = target.field,
                    locale = locale,
                    bottomInset = inset,
                    onSelectField = { open(exercise.exerciseId, set, it) },
                    onDigit = { key ->
                        val next = SetEntryRules.type(entry.text, entry.fresh, key, target.field, locale) ?: return@WorkoutKeypad
                        entry.typed(next)
                        container.launchMutation { repository.updateSet(exercise.exerciseId, set.id) { SetEntryRules.apply(it, target.field, next) } }
                    },
                    onBackspace = {
                        val next = SetEntryRules.backspace(entry.text, entry.fresh)
                        entry.typed(next)
                        container.launchMutation { repository.updateSet(exercise.exerciseId, set.id) { SetEntryRules.apply(it, target.field, next) } }
                    },
                    onStep = { direction ->
                        val value = SetEntryRules.step(target.field.read(set), direction, target.field, locale) ?: return@WorkoutKeypad
                        entry.reset(SetEntryRules.format(value, target.field, locale))
                        container.launchMutation { repository.updateSet(exercise.exerciseId, set.id) { target.field.write(it, value) } }
                    },
                    onAction = {
                        val nextField = SetEntryRules.next(target.field, cardio)
                        when {
                            nextField != null -> open(exercise.exerciseId, set, nextField)
                            set.completed -> entry.close()
                            else -> {
                                haptics.medium()
                                completeAndRest(exercise.exerciseId, set.id, label(exercise.exerciseId))
                                val following = SetEntryRules.nextOpenSet(exercise.sets, set.id)
                                if (following != null) {
                                    open(exercise.exerciseId, following, SetEntryRules.fields(cardio).first())
                                } else {
                                    entry.close()
                                }
                            }
                        }
                    },
                    onDelete = {
                        deleteSetOf = exercise.exerciseId
                        deleteSetId = set.id
                    },
                    onHide = { entry.close() },
                )
            } else {
                BottomActionBar(
                    stringResource(R.string.workout_finish),
                    onClick = {
                        haptics.medium()
                        finishOpen = true
                    },
                    bottomInset = inset,
                )
            }
        },
        listState = listState,
    ) {
        item(key = "warmup") { WarmupRow(warmupDone) { warmupOpen = true } }

        if (restRunning && restAnchor == null) item(key = "rest") { restContent() }

        if (ordered.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ds_ic_fitness_center,
                    title = stringResource(R.string.workout_empty_title),
                    message = stringResource(R.string.workout_empty_exercises),
                    actionLabel = stringResource(R.string.workout_add_exercise),
                    onAction = onAddExercise,
                )
            }
        }

        items(ordered, key = { it.exerciseId }) { exercise ->
            val exerciseId = exercise.exerciseId
            val cardio = isCardio(exerciseId)
            val reference = references[exerciseId]
            val suggestion = SessionInsights.suggestion(reference, cardio)
            ReorderableItem(reorderState, key = exerciseId) { isDragging ->
                LiveExerciseBlock(
                    name = label(exerciseId),
                    exercise = exercise,
                    isCardio = cardio,
                    reference = reference,
                    referenceDate = reference?.workout?.let { parseInstantOrNull(it.date, container.time.zone()) }?.let { dates.weekdayDayMonth(it) },
                    suggestion = suggestion,
                    entry = entry,
                    locale = locale,
                    restAnchor = restAnchor.takeIf { restRunning },
                    rest = restContent,
                    onOpenDetails = { onOpenHistory(exerciseId) },
                    onMore = { optionsFor = exerciseId },
                    onApplySuggestion = {
                        if (reference != null && suggestion != null) {
                            haptics.light()
                            val applied = SessionInsights.applySuggestion(exercise.sets, reference, suggestion)
                            container.launchMutation {
                                repository.updateExerciseSets(exerciseId) { SessionInsights.applySuggestion(it, reference, suggestion) }
                            }
                            // Le champ ouvert montre la nouvelle valeur.
                            val t = entry.target
                            if (t != null && t.exerciseId == exerciseId) {
                                applied.firstOrNull { it.id == t.setId }?.let { entry.reset(text(t.field, it)) }
                            }
                        }
                    },
                    onFieldClick = { set, field -> open(exerciseId, set, field) },
                    onPreviousClick = { set, previous ->
                        haptics.light()
                        container.launchMutation { repository.updateSet(exerciseId, set.id) { SessionInsights.copyPrevious(it, previous) } }
                        val t = entry.target
                        if (t != null && t.setId == set.id) entry.reset(text(t.field, SessionInsights.copyPrevious(set, previous)))
                    },
                    onCheck = { set ->
                        haptics.medium()
                        container.launchMutation {
                            if (repository.toggleSetCompleted(exerciseId, set.id) == true) container.restTimer.start(label(exerciseId))
                        }
                    },
                    onAddSet = {
                        haptics.light()
                        container.launchMutation { repository.addSet(exerciseId) }
                    },
                    modifier = if (isDragging) Modifier.background(Studio.colors.surfaceRaised, Studio.shapes.surface) else Modifier,
                    headerModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = { haptics.light() },
                        onDragStopped = {
                            dragOrder?.let { order -> container.launchMutation { repository.reorderExercises(order) } }
                            dragOrder = null
                        },
                    ),
                )
            }
        }

        if (ordered.isNotEmpty()) {
            item(key = "add") {
                StudioButton(
                    stringResource(R.string.workout_add_exercise),
                    onAddExercise,
                    Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                    icon = R.drawable.ds_ic_add,
                )
            }
        }
    }

    if (warmupOpen) {
        StudioBottomSheet(onDismissRequest = { warmupOpen = false }) { inset ->
            WarmupSheetContent(
                done = warmupDone,
                onToggle = { index ->
                    haptics.light()
                    warmupDone = warmupDone xor (1 shl index)
                },
                onClose = { warmupOpen = false },
                bottomInset = inset,
            )
        }
    }

    optionsFor?.let { exerciseId ->
        val exercise = byId[exerciseId]
        if (exercise == null) {
            optionsFor = null
        } else {
            StudioBottomSheet(onDismissRequest = { optionsFor = null }) { inset ->
                ExerciseOptionsSheetContent(
                    name = label(exerciseId),
                    note = exercise.note.orEmpty(),
                    onNoteChange = { note -> container.launchMutation { repository.updateExerciseNote(exerciseId, note) } },
                    onRemove = {
                        optionsFor = null
                        removeFor = exerciseId
                    },
                    onClose = { optionsFor = null },
                    bottomInset = inset,
                )
            }
        }
    }

    removeFor?.let { exerciseId ->
        ConfirmSheet(
            title = stringResource(R.string.exercise_card_remove_confirm, label(exerciseId)),
            message = stringResource(R.string.workout_exercise_remove_message),
            confirmLabel = stringResource(R.string.exercise_card_remove),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.medium()
                removeFor = null
                container.launchMutation { repository.removeExerciseFromWorkout(exerciseId) }
            },
            onDismiss = { removeFor = null },
        )
    }

    val deleteExercise = deleteSetOf
    val deleteSet = deleteSetId
    if (deleteExercise != null && deleteSet != null) {
        val number = byId[deleteExercise]?.sets?.indexOfFirst { it.id == deleteSet }?.plus(1) ?: 0
        ConfirmSheet(
            title = stringResource(R.string.workout_delete_set_title, number),
            message = stringResource(R.string.workout_delete_set_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.medium()
                deleteSetOf = null
                deleteSetId = null
                entry.close()
                container.launchMutation { repository.removeSet(deleteExercise, deleteSet) }
            },
            onDismiss = {
                deleteSetOf = null
                deleteSetId = null
            },
        )
    }

    if (finishOpen) {
        val hasCompleted = WorkoutRules.hasCompletedSet(workout)
        val minutes = parseInstantOrNull(workout.date, container.time.zone())
            ?.let { ((container.time.now().toEpochMilli() - it.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0) } ?: 0
        ConfirmSheet(
            title = stringResource(if (hasCompleted) R.string.workout_finish_confirm else R.string.workout_abandon_title),
            message = if (hasCompleted) {
                pluralStringResource(R.plurals.workout_finish_message, completedSets, completedSets, WorkoutStats.formatDuration(minutes))
            } else {
                stringResource(R.string.workout_finish_empty)
            },
            confirmLabel = stringResource(if (hasCompleted) R.string.workout_finish_short else R.string.workout_abandon),
            dismissLabel = stringResource(R.string.workout_continue),
            onConfirm = {
                if (hasCompleted) haptics.success() else haptics.warning()
                finishOpen = false
                onFinished()
                // Termine (ou abandonne, sans série validée) après avoir quitté l'écran (§ 3.3) ; le
                // repos en cours s'arrête aussi (§ 7.2).
                container.launchMutation { container.endWorkout() }
            },
            onDismiss = { finishOpen = false },
        )
    }
}

/** En-tête en direct : nom, « 78,4 kg · 2 / 16 séries », chrono `m:ss` rafraîchi chaque seconde. */
@Composable
private fun LiveSessionHeader(workout: Workout, completed: Int, total: Int, time: TimeSource, onCollapse: () -> Unit) {
    val start = remember(workout.date) { parseInstantOrNull(workout.date, time.zone())?.toEpochMilli() }
    val now = rememberSecondTicker(time)
    val elapsed = if (start == null) 0L else ((now - start) / 1_000L).coerceAtLeast(0L)
    LiveHeader(
        title = workoutDisplayName(workout),
        subtitle = if (workout.bodyWeight > 0) {
            pluralStringResource(R.plurals.workout_subtitle, total, formatNumber(workout.bodyWeight), completed, total)
        } else {
            pluralStringResource(R.plurals.workout_subtitle_no_weight, total, completed, total)
        },
        elapsed = clock(elapsed),
        onCollapse = onCollapse,
    )
}

/** Bandeau de repos (§ 15.7.1) : temps restant, total, temps écoulé, −30 s / +30 s / Passer. */
@Composable
private fun LiveRestBanner(rest: RestTimerState, time: TimeSource, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit) {
    val now = rememberSecondTicker(time)
    val remainingMs = (rest.endsAt - now).coerceAtLeast(0L)
    // Arrondi au-dessus : « 0:01 » jusqu'à la fin, jamais « 0:00 » pendant le repos.
    val remaining = (remainingMs + 999L) / 1_000L
    val total = rest.total.coerceAtLeast(1)
    RestBanner(
        label = stringResource(R.string.rest_timer_label),
        remaining = clock(remaining),
        total = stringResource(R.string.workout_rest_total, clock(rest.total.toLong())),
        progress = (1f - remainingMs / (total * 1_000f)).coerceIn(0f, 1f),
        minusLabel = stringResource(R.string.workout_notif_minus_30),
        plusLabel = stringResource(R.string.workout_notif_plus_30),
        skipLabel = stringResource(R.string.rest_timer_skip),
        onMinus = onMinus,
        onPlus = onPlus,
        onSkip = onSkip,
    )
}

/** Sans séance en cours (après la fin, ou lien périmé) : état vide et retour. */
@Composable
private fun NoActiveWorkout(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Studio.colors.background), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = R.drawable.ds_ic_fitness_center,
            title = stringResource(R.string.workout_no_active),
            message = stringResource(R.string.workout_no_active_message),
            actionLabel = stringResource(R.string.common_back),
            onAction = onBack,
        )
    }
}

/** Heure courante en ms, rafraîchie à chaque seconde pleine. */
@Composable
private fun rememberSecondTicker(time: TimeSource): Long {
    val now by produceState(time.now().toEpochMilli()) {
        while (true) {
            value = time.now().toEpochMilli()
            delay(1_000 - value % 1_000)
        }
    }
    return now
}

/** Le repos est en cours : vrai jusqu'à `endsAt`, sans relire l'heure chaque seconde. */
@Composable
private fun rememberRestRunning(rest: RestTimerState?, time: TimeSource): Boolean {
    val running by produceState(rest != null && time.now().toEpochMilli() < rest.endsAt, rest) {
        if (rest == null) {
            value = false
            return@produceState
        }
        val remaining = rest.endsAt - time.now().toEpochMilli()
        value = remaining > 0
        if (remaining > 0) {
            delay(remaining)
            value = false
        }
    }
    return running
}

/** Durée `m:ss`, minutes non bornées (« 75:03 »). */
private fun clock(seconds: Long): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
