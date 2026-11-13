package com.musclenote.android.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.NotificationExercise
import com.musclenote.android.data.workout.WorkoutAction
import com.musclenote.android.data.workout.WorkoutNotificationContent
import com.musclenote.android.data.workout.WorkoutNotificationRules
import com.musclenote.android.data.workout.WorkoutNotificationTexts
import com.musclenote.android.data.workout.WorkoutNotifications
import com.musclenote.android.ui.components.appFormattingLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Notification « Séance en cours » (SPEC § 15.5, 🔄 D31) : nom de la séance, chronomètre système
 * (durée de la séance, ou compte à rebours du repos), exercice et prochaine série, actions
 * « Valider la série » ou « −30 s », « +30 s », « Passer ». En cours, silencieuse, sur l'écran
 * verrouillé ; proposée en Live Update (Android 16 et plus : `requestPromotedOngoing`, accordé ou
 * non par le système et l'utilisateur), sinon notification en cours classique. Android ne relaie
 * pas une notification en cours vers une montre : c'est l'alerte de fin de repos qui y arrive
 * ([RestTimerNotifier]).
 */
class WorkoutNotifier(context: Context) : WorkoutNotifications {
    private val context = context.applicationContext

    override fun show(workout: Workout, rest: RestTimerState?, custom: List<Exercise>) {
        val localized = context.withAppLocale()
        val locale = localized.resources.configuration.appFormattingLocale()
        val zone = ZoneId.systemDefault()
        val definitions = exerciseDefinitions(custom).associateBy { it.id }
        val content = WorkoutNotificationRules.content(
            workout = workout,
            workoutName = workoutName(localized, workout, zone),
            rest = rest,
            nowMillis = System.currentTimeMillis(),
            zone = zone,
            exercises = { id ->
                definitions[id]?.let { NotificationExercise(it.nameRes?.let(localized::getString) ?: it.customName.orEmpty(), it.isCardio) }
            },
            texts = WorkoutNotificationTexts(
                locale = locale,
                restTitle = localized.getString(R.string.workout_notif_rest_title),
                setWithDetail = localized.getString(R.string.workout_notif_set),
                setOnly = localized.getString(R.string.workout_notif_set_only),
                strength = localized.getString(R.string.workout_notif_strength),
                repsOnly = localized.getString(R.string.workout_notif_reps),
                minutes = localized.getString(R.string.workout_notif_minutes),
                kilometers = localized.getString(R.string.workout_notif_km),
                next = localized.getString(R.string.workout_notif_next),
                allDone = localized.getString(R.string.workout_notif_all_done),
            ),
        )
        // La notification de repos (jusqu'à la v1.0.6) n'existe plus : retirée si elle est encore affichée.
        AppNotifications.cancel(context, AppNotifications.ID_REST_TIMER_ONGOING)
        AppNotifications.post(context, AppNotifications.ID_WORKOUT_ONGOING, build(localized, content))
    }

    override fun cancel() {
        AppNotifications.cancel(context, AppNotifications.ID_WORKOUT_ONGOING)
        AppNotifications.cancel(context, AppNotifications.ID_REST_TIMER_ONGOING)
    }

    override fun exerciseName(exerciseId: String, custom: List<Exercise>): String {
        val localized = context.withAppLocale()
        return ExerciseCatalog.find(exerciseId)?.let { localized.getString(it.nameRes) }
            ?: custom.firstOrNull { it.id == exerciseId }?.name
            ?: ""
    }

    private fun build(localized: Context, content: WorkoutNotificationContent) =
        AppNotifications.builder(context, AppChannel.WorkoutOngoing)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setWhen(content.chronometerBase)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(content.countDown)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (content.countDown) NotificationCompat.CATEGORY_STOPWATCH else NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(RestTimerNotifier.openWorkout(context))
            .setRequestPromotedOngoing(true)
            .apply {
                content.chipText?.let { setShortCriticalText(it) }
                for (action in content.actions) {
                    val (icon, label, receiverAction) = when (action) {
                        WorkoutAction.CompleteSet -> Triple(R.drawable.ic_check, R.string.workout_notif_complete_set, RestTimerReceiver.ACTION_COMPLETE_SET)
                        WorkoutAction.RestMinus -> Triple(R.drawable.ic_replay_30, R.string.workout_notif_minus_30, RestTimerReceiver.ACTION_REST_MINUS)
                        WorkoutAction.RestPlus -> Triple(R.drawable.ic_forward_30, R.string.workout_notif_plus_30, RestTimerReceiver.ACTION_REST_PLUS)
                        WorkoutAction.SkipRest -> Triple(R.drawable.ic_skip_next, R.string.rest_timer_skip, RestTimerReceiver.ACTION_SKIP)
                    }
                    addAction(icon, localized.getString(label), RestTimerReceiver.action(context, receiverAction))
                }
            }
            .build()

    /** Nom affiché de la séance (§ 3.1) : préréglage traduit, sinon nom saisi, sinon date. */
    private fun workoutName(localized: Context, workout: Workout, zone: ZoneId): String {
        workout.preset?.let { return localized.getString(it.labelRes) }
        if (workout.name.isNotBlank()) return workout.name
        val start = parseInstantOrNull(workout.date, zone) ?: return ""
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(localized.resources.configuration.appFormattingLocale())
        return start.atZone(zone).format(format)
    }
}
