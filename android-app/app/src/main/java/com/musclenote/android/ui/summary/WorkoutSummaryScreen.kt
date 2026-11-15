package com.musclenote.android.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.progress.ExerciseMetric
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.SummaryExercise
import com.musclenote.android.data.workout.VolumeComparison
import com.musclenote.android.data.workout.WorkoutSummary
import com.musclenote.android.data.workout.WorkoutSummaryRules
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.exerciseName
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatSignedDelta
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.BottomActionBar
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.HeroNumber
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioIconButton
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.templates.FicheLazyTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.templates.systemScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.exercise.bestSetLine
import com.musclenote.android.ui.exercise.metricUnit
import com.musclenote.android.ui.journal.SessionActionSheets
import com.musclenote.android.ui.journal.compactDurationText
import com.musclenote.android.ui.journal.rememberSessionActions
import java.time.LocalDate
import java.time.ZoneId

/**
 * Résumé d'une séance terminée (SPEC § 15.12), gabarit fiche : volume total en chiffre héros et sa
 * variation par rapport à la dernière séance du même programme, durée et séries, puis chaque
 * exercice avec sa meilleure série et « Record » s'il en a battu un.
 *
 * - [finished] (ouvert par « Terminer la séance ») : aucun bouton en haut, « Terminer » seul en bas
 *   ([onDone] → Accueil).
 * - sinon (Journal, « Dernière séance » de l'Accueil) : ← ([onBack]) et ⋮ → les actions de la séance
 *   (modifier → [onEditWorkout], date, suppression) ; une séance supprimée ferme le résumé.
 */
@Composable
fun WorkoutSummaryScreen(
    workoutId: String,
    finished: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onEditWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
) {
    val container = LocalAppContainer.current
    val repository = container.workouts
    val workouts by repository.workouts.collectAsStateWithLifecycle()
    val currentWorkout by repository.currentWorkout.collectAsStateWithLifecycle()
    val customExercises by repository.customExercises.collectAsStateWithLifecycle()
    // Archivés compris : un exercice supprimé mais encore référencé garde son nom (§ 3.9).
    val definitions = remember(customExercises) { exerciseDefinitions(customExercises) }
    val summary = remember(workouts, workoutId, definitions) { WorkoutSummaryRules.summary(workouts, workoutId, definitions) }
    val zone = container.time.zone()
    val today = rememberToday()
    val actions = rememberSessionActions()

    // Une séance affichée puis supprimée (⋮ → « Supprimer la séance ») ferme son résumé.
    var shown by rememberSaveable(workoutId) { mutableStateOf(false) }
    LaunchedEffect(summary != null) {
        if (summary != null) shown = true else if (shown) onBack()
    }

    when {
        summary != null -> WorkoutSummaryContent(
            summary = summary,
            finished = finished,
            zone = zone,
            today = today,
            onDone = onDone,
            onBack = onBack,
            onOptions = { actions.actionsFor = workoutId },
            modifier = modifier,
            insets = insets,
        )
        // « Terminer la séance » ouvre le résumé juste avant d'écrire la séance dans l'historique.
        currentWorkout?.id == workoutId -> Box(modifier.fillMaxSize().background(Studio.colors.background))
        else -> FicheLazyTemplate(modifier = modifier, topBar = { FicheTopBar(onBack) }, titleBlock = {}, insets = insets) {
            block("not-found") {
                EmptyState(
                    icon = R.drawable.ds_ic_fitness_center,
                    title = stringResource(R.string.edit_workout_not_found),
                    message = null,
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                )
            }
        }
    }
    if (!finished) SessionActionSheets(actions, workouts, onEditWorkout)
}

/** Contenu du résumé pour [summary] : séparé du dépôt pour les aperçus. */
@Composable
internal fun WorkoutSummaryContent(
    summary: WorkoutSummary,
    finished: Boolean,
    zone: ZoneId,
    today: LocalDate,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    insets: ScreenInsets = systemScreenInsets(),
) {
    val dates = rememberDateFormats(zone)
    val workout = summary.workout
    val start = remember(workout.date, zone) { parseInstantOrNull(workout.date, zone) }
    FicheLazyTemplate(
        modifier = modifier,
        topBar = {
            if (finished) {
                // Un seul bouton, « Terminer », en bas : la barre garde sa hauteur, vide.
                Spacer(Modifier.height(Studio.spacing.touch))
            } else {
                FicheTopBar(onBack) {
                    StudioIconButton(R.drawable.ds_ic_more_vert, stringResource(R.string.session_long_press), onOptions)
                }
            }
        },
        titleBlock = {
            FicheTitle(
                title = workoutDisplayName(workout),
                overline = start?.let { stringResource(R.string.summary_finished_on, dates.sessionDate(it, today), dates.time(it)) }
                    ?: stringResource(R.string.summary_finished),
                titleStyle = Studio.typography.titleScreen,
            )
        },
        hero = { VolumeHero(summary, dates, today, zone) },
        stats = {
            val minutes = workout.durationMinutes?.takeIf { it > 0 }
            StatRow(
                buildList {
                    if (minutes != null) add(Stat(compactDurationText(minutes), stringResource(R.string.summary_stat_duration)))
                    add(
                        Stat(
                            formatInteger(summary.completedSets.toDouble()),
                            pluralStringResource(R.plurals.summary_stat_sets, summary.completedSets),
                        ),
                    )
                },
            )
        },
        bottomAction = if (finished) {
            { inset -> BottomActionBar(stringResource(R.string.summary_done), onDone, bottomInset = inset) }
        } else {
            null
        },
        insets = insets,
    ) {
        if (summary.exercises.isNotEmpty()) {
            section(
                key = "exercises",
                header = {
                    val records = summary.recordCount
                    SectionHeader(
                        stringResource(R.string.progress_exercises),
                        trailingText = if (records > 0) {
                            pluralStringResource(R.plurals.summary_records, records, records)
                        } else {
                            formatInteger(summary.exercises.size.toDouble())
                        },
                        trailingTrend = if (records > 0) Trend.Up else Trend.Neutral,
                    )
                },
            ) {
                itemsIndexed(summary.exercises, key = { _, exercise -> exercise.definition.id }, contentType = { _, _ -> "exercise" }) { index, exercise ->
                    SummaryExerciseRow(exercise, showDivider = index < summary.exercises.lastIndex)
                }
            }
        }
    }
}

/**
 * Chiffre héros : « Volume total », le volume au kg et, s'il y a une séance précédente du même
 * programme, la variation (« +280 kg » verte, « −150 kg » rouge, « identique » grise) et « vs Push du
 * jeu. 5 nov. ».
 */
@Composable
private fun VolumeHero(summary: WorkoutSummary, dates: DateFormats, today: LocalDate, zone: ZoneId) {
    val comparison = summary.comparison
    HeroNumber(
        value = formatInteger(summary.volumeKg.toDouble()),
        label = stringResource(R.string.summary_volume),
        unit = metricUnit(ExerciseMetric.Load),
        delta = comparison?.let { volumeDelta(it) },
        trend = when {
            comparison == null || comparison.variationKg == 0L -> Trend.Neutral
            comparison.variationKg > 0 -> Trend.Up
            else -> Trend.Down
        },
        deltaContext = comparison?.let { previousContext(it, dates, today, zone) },
    )
}

@Composable
private fun volumeDelta(comparison: VolumeComparison): String =
    if (comparison.variationKg == 0L) {
        stringResource(R.string.delta_same)
    } else {
        stringResource(R.string.common_kg_value, formatSignedDelta(comparison.variationKg.toDouble(), integer = true))
    }

/** « vs Push du jeu. 5 nov. » ; le nom seul si la date de cette séance est illisible. */
@Composable
private fun previousContext(comparison: VolumeComparison, dates: DateFormats, today: LocalDate, zone: ZoneId): String {
    val name = workoutDisplayName(comparison.previous)
    val start = parseInstantOrNull(comparison.previous.date, zone) ?: return name
    return stringResource(R.string.summary_delta_context, name, dates.sessionDate(start, today))
}

/** Un exercice : nom, « Meilleure série 82,5 × 8 · 1RM ~104 kg », « Record » s'il en a battu un. */
@Composable
private fun SummaryExerciseRow(exercise: SummaryExercise, showDivider: Boolean) {
    ListRow(
        title = exerciseName(exercise.definition),
        subtitle = bestSetLine(exercise.bestSet, exercise.bestOneRepMax, exercise.definition.isCardio),
        trailing = if (exercise.brokeRecord) {
            ListRowTrailing.DeltaOnly(stringResource(R.string.exercise_fiche_record), Trend.Up)
        } else {
            null
        },
        showDivider = showDivider,
    )
}
