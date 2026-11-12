package com.musclenote.android.data.workout

import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.timer.RestTimerRules
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.ZoneId
import java.util.Locale

/** Prochaine série à faire : [set], [number]-ième série (à partir de 1) de [exercise]. */
data class NextSet(val exercise: WorkoutExercise, val set: WorkoutSet, val number: Int)

/** Action d'une notification de séance (§ 15.5). */
enum class WorkoutAction { CompleteSet, RestMinus, RestPlus, SkipRest }

/**
 * Contenu de la notification « Séance en cours » (§ 15.5) : textes déjà traduits, chronomètre
 * système (depuis [chronometerBase] en ms, compte à rebours si [countDown]), texte court de la
 * pastille d'une Live Update ([chipText], `null` = le chronomètre), actions dans l'ordre.
 */
data class WorkoutNotificationContent(
    val title: String,
    val text: String,
    val chronometerBase: Long,
    val countDown: Boolean,
    val chipText: String?,
    val actions: List<WorkoutAction>,
)

/** Un exercice de la séance tel que la notification le montre. */
data class NotificationExercise(val name: String, val cardio: Boolean)

/**
 * Textes de la notification, dans la langue de l'app : formats Android (`%1$s`, `%1$d`) lus dans
 * les ressources, et [locale] pour les nombres (§ 3.14).
 */
data class WorkoutNotificationTexts(
    val locale: Locale,
    /** `%1$s` = nom de la séance. */
    val restTitle: String,
    /** `%1$d` = numéro de la série, `%2$s` = détail. */
    val setWithDetail: String,
    /** `%1$d` = numéro de la série. */
    val setOnly: String,
    /** `%1$d` = répétitions, `%2$s` = poids. */
    val strength: String,
    /** `%1$d` = répétitions (poids à 0). */
    val repsOnly: String,
    /** `%1$s` = minutes. */
    val minutes: String,
    /** `%1$s` = kilomètres. */
    val kilometers: String,
    /** `%1$s` = exercice et série. */
    val next: String,
    val allDone: String,
)

/**
 * Notification de séance avec actions (SPEC § 15.5, 🔄 D31), en fonctions pures testables sur la
 * JVM : prochaine série, texte et actions selon qu'un repos est en cours.
 */
object WorkoutNotificationRules {
    /** Séparateur entre l'exercice, la série et son détail. */
    const val SEPARATOR = " · "

    /** Pas des boutons « −30 s » / « +30 s ». */
    const val ADJUST_SECONDS = 30

    /**
     * Prochaine série (§ 15.5) : dans l'ordre de la séance, la première série non validée **après
     * la dernière série validée** ; s'il n'y en a pas, la première non validée depuis le début (une
     * série sautée plus haut). `null` quand tout est validé. Les exercices pour lesquels [isShown]
     * est faux (inconnus, donc absents de l'écran de séance) sont ignorés.
     */
    fun nextSet(workout: Workout, isShown: (String) -> Boolean = { true }): NextSet? {
        val positions = workout.exercises
            .filter { isShown(it.exerciseId) }
            .flatMap { exercise -> exercise.sets.mapIndexed { index, set -> NextSet(exercise, set, index + 1) } }
        val lastCompleted = positions.indexOfLast { it.set.completed }
        return positions.drop(lastCompleted + 1).firstOrNull { !it.set.completed }
            ?: positions.firstOrNull { !it.set.completed }
    }

    /**
     * Contenu de la notification de [workout] (§ 15.5) :
     * - hors repos : titre = nom de la séance, chronomètre de la séance, texte = exercice et
     *   prochaine série, action « Valider la série » ; tout validé → « Toutes les séries sont
     *   validées », aucune action ;
     * - repos en cours ([rest] pas encore fini à [nowMillis]) : titre « {séance} · Repos », compte à
     *   rebours jusqu'à la fin du repos, texte = « Prochaine : … », actions −30 s, +30 s, Passer
     *   (Android n'en montre que trois).
     */
    fun content(
        workout: Workout,
        workoutName: String,
        rest: RestTimerState?,
        nowMillis: Long,
        zone: ZoneId,
        exercises: (String) -> NotificationExercise?,
        texts: WorkoutNotificationTexts,
    ): WorkoutNotificationContent {
        val next = nextSet(workout) { exercises(it) != null }
        val nextLine = next?.let { set -> exercises(set.exercise.exerciseId)?.let { line(set, it, texts) } }
        val resting = rest?.takeUnless { RestTimerRules.isFinished(it, nowMillis) }
        return if (resting != null) {
            WorkoutNotificationContent(
                title = texts.restTitle.format(texts.locale, workoutName),
                text = nextLine?.let { texts.next.format(texts.locale, it) } ?: texts.allDone,
                chronometerBase = resting.endsAt,
                countDown = true,
                chipText = null,
                actions = listOf(WorkoutAction.RestMinus, WorkoutAction.RestPlus, WorkoutAction.SkipRest),
            )
        } else {
            WorkoutNotificationContent(
                title = workoutName,
                text = nextLine ?: texts.allDone,
                chronometerBase = parseInstantOrNull(workout.date, zone)?.toEpochMilli() ?: nowMillis,
                countDown = false,
                chipText = next?.let { texts.setOnly.format(texts.locale, it.number) },
                actions = if (next != null) listOf(WorkoutAction.CompleteSet) else emptyList(),
            )
        }
    }

    /** « Développé couché · Série 3 · 8 × 80 kg ». */
    fun line(next: NextSet, exercise: NotificationExercise, texts: WorkoutNotificationTexts): String {
        val detail = detail(next.set, exercise.cardio, texts)
        val set = if (detail == null) texts.setOnly.format(texts.locale, next.number)
        else texts.setWithDetail.format(texts.locale, next.number, detail)
        return exercise.name + SEPARATOR + set
    }

    /**
     * Détail d'une série : force → « 8 × 80 kg » (« 8 reps » à 0 kg) ; cardio → durée et distance
     * renseignées (« 30 min · 5 km »), `null` sans l'une ni l'autre.
     */
    private fun detail(set: WorkoutSet, cardio: Boolean, texts: WorkoutNotificationTexts): String? {
        if (!cardio) {
            return if (set.weight > 0) texts.strength.format(texts.locale, set.reps, number(set.weight, texts.locale))
            else texts.repsOnly.format(texts.locale, set.reps)
        }
        val parts = listOfNotNull(
            set.duration?.takeIf { it > 0 }?.let { texts.minutes.format(texts.locale, number(it, texts.locale)) },
            set.distance?.takeIf { it > 0 }?.let { texts.kilometers.format(texts.locale, number(it, texts.locale)) },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }

    private fun number(value: Double, locale: Locale): String = formatDecimal(value, locale)
}
