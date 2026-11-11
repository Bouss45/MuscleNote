package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.normalizeSearch
import com.musclenote.android.data.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Issue de la fin d'une séance (§ 3.3) ou de sa finalisation automatique (§ 3.4). */
sealed interface WorkoutEnd {
    /** La séance part dans l'historique. */
    data class Finished(val workout: Workout) : WorkoutEnd

    /** Aucune série validée : la séance est supprimée sans laisser d'historique. */
    data object Abandoned : WorkoutEnd
}

/** Suppression d'un exercice perso (§ 3.9). */
enum class CustomExerciseDeletion { Deleted, Archived }

/**
 * Règles métier des séances (SPEC § 3.1 – 3.4, § 3.9), en fonctions pures testables sur la JVM.
 * Les historiques passés en paramètre sont triés de la séance la plus récente à la plus
 * ancienne (invariant du dépôt).
 */
object WorkoutRules {
    /** Nouvelle série de force sans série précédente (§ 2.5). */
    const val DEFAULT_REPS = 10
    const val DEFAULT_WEIGHT = 20.0

    /** Nouvelle série de cardio sans série précédente : 30 min / 0 / 0 / 0 (§ 2.5). */
    const val DEFAULT_CARDIO_DURATION = 30.0

    /** Au-delà, une séance en cours est finalisée automatiquement (§ 3.4). */
    val MAX_SESSION: Duration = Duration.ofHours(24)

    /**
     * Clé de séance (🔄 D3), pour le pré-remplissage et le temps en salle : `preset:<id>` si la
     * séance a un préréglage, sinon `name:` + nom normalisé (sans casse ni accents, § 10.6).
     */
    fun sessionKey(preset: PresetId?, name: String): String =
        if (preset != null) "preset:${preset.id}" else "name:${normalizeSearch(name)}"

    fun sessionKey(workout: Workout): String = sessionKey(workout.preset, workout.name)

    /**
     * Séries reprises dans une nouvelle séance : nouvel id, non validées ; reps, poids et champs
     * cardio conservés (un champ cardio absent reste absent).
     */
    fun cloneSetsForReuse(sets: List<WorkoutSet>, newId: () -> String): List<WorkoutSet> =
        sets.map { it.copy(id = newId(), completed = false) }

    private fun cloneExercise(exercise: WorkoutExercise, newId: () -> String) = WorkoutExercise(
        exerciseId = exercise.exerciseId,
        note = exercise.note,
        sets = cloneSetsForReuse(exercise.sets, newId),
    )

    /**
     * Nouvelle séance (§ 3.1). [name] = libellé du préréglage dans la langue courante, ou nom
     * perso. Si une séance terminée a la même clé, la plus récente sert de plan : ses exercices
     * dans le même ordre, avec leur note et **toutes** leurs séries (validées ou non) clonées.
     */
    fun newWorkout(
        history: List<Workout>,
        bodyWeight: Double,
        preset: PresetId?,
        name: String,
        now: Instant,
        newId: () -> String,
    ): Workout {
        val key = sessionKey(preset, name)
        val template = history.firstOrNull { sessionKey(it) == key }
        return Workout(
            id = newId(),
            name = name.trim(),
            preset = preset,
            date = formatInstant(now),
            bodyWeight = bodyWeight,
            exercises = template?.exercises.orEmpty().map { cloneExercise(it, newId) },
        )
    }

    /**
     * Ajout d'un exercice à la séance en cours (§ 3.2) : sans effet s'il y est déjà ; sinon ajouté
     * en fin de liste avec la note et les séries clonées de la séance terminée la plus récente
     * qui le contient (même sans série), ou sans série.
     */
    fun addExercise(current: Workout, history: List<Workout>, exerciseId: String, newId: () -> String): Workout {
        if (current.exercises.any { it.exerciseId == exerciseId }) return current
        val previous = history.firstNotNullOfOrNull { workout -> workout.exercises.firstOrNull { it.exerciseId == exerciseId } }
        val added = previous?.let { cloneExercise(it, newId) } ?: WorkoutExercise(exerciseId, sets = emptyList())
        return current.copy(exercises = current.exercises + added)
    }

    /**
     * Série ajoutée par « + Ajouter une série » (§ 2.5) : force → reps et poids de [last], ou
     * 10 × 20 kg ; cardio → reps et poids à 0, durée / distance / vitesse / inclinaison de [last]
     * champ par champ, ou 30 min / 0 / 0 / 0. Non validée en séance ; l'éditeur de séance terminée
     * passe [completed] = `true` (§ 2.9).
     */
    fun newSet(last: WorkoutSet?, isCardio: Boolean, id: String, completed: Boolean = false): WorkoutSet =
        if (isCardio) {
            WorkoutSet(
                id = id,
                reps = 0,
                weight = 0.0,
                completed = completed,
                duration = last?.duration ?: DEFAULT_CARDIO_DURATION,
                distance = last?.distance ?: 0.0,
                speed = last?.speed ?: 0.0,
                incline = last?.incline ?: 0.0,
            )
        } else {
            WorkoutSet(
                id = id,
                reps = last?.reps ?: DEFAULT_REPS,
                weight = last?.weight ?: DEFAULT_WEIGHT,
                completed = completed,
            )
        }

    fun hasCompletedSet(workout: Workout): Boolean = workout.exercises.any { e -> e.sets.any { it.completed } }

    /**
     * Fin de séance (§ 3.3, 🔄 D15) : sans série validée → abandon ; sinon la séance part dans
     * l'historique **avec toutes ses séries** et `durationMinutes = round((now − date) / 1 min)`.
     */
    fun end(current: Workout, now: Instant, zone: ZoneId): WorkoutEnd {
        if (!hasCompletedSet(current)) return WorkoutEnd.Abandoned
        val start = parseInstantOrNull(current.date, zone)
        // roundToInt arrondit les demis vers +∞, comme Math.round (§ 3.5) ; une horloge reculée
        // ne donne pas de durée négative.
        val minutes = start?.let { ((now.toEpochMilli() - it.toEpochMilli()) / 60_000.0).roundToInt().coerceAtLeast(0) }
        return WorkoutEnd.Finished(current.copy(durationMinutes = minutes))
    }

    /** Séance en cours démarrée il y a plus de 24 h (ou à la date illisible) (§ 3.4). */
    fun isStale(current: Workout, now: Instant, zone: ZoneId): Boolean {
        val start = parseInstantOrNull(current.date, zone) ?: return true
        return Duration.between(start, now) > MAX_SESSION
    }

    /**
     * Finalisation automatique (§ 3.4) : avec une série validée, la séance part dans l'historique
     * **sans** `durationMinutes` (durée réelle inconnue) ; sinon elle est abandonnée.
     */
    fun finalizeStale(current: Workout): WorkoutEnd =
        if (hasCompletedSet(current)) WorkoutEnd.Finished(current.copy(durationMinutes = null)) else WorkoutEnd.Abandoned

    /**
     * Enregistrement de l'éditeur de séance terminée (§ 2.9) : exercices, nom et préréglage
     * remplacés ; un poids de corps ≤ 0 (champ vide ou illisible) garde l'ancien. L'id, la date et
     * la durée ne changent pas.
     */
    fun patch(workout: Workout, exercises: List<WorkoutExercise>, bodyWeight: Double, name: String, preset: PresetId?): Workout =
        workout.copy(
            exercises = exercises,
            bodyWeight = if (bodyWeight > 0) bodyWeight else workout.bodyWeight,
            name = name.trim(),
            preset = preset,
        )

    /**
     * Un exercice est référencé s'il apparaît dans la séance en cours ou dans au moins une séance
     * terminée, même sans série (§ 3.9) : sa suppression ne fait alors que l'archiver.
     */
    fun isReferenced(exerciseId: String, current: Workout?, history: List<Workout>): Boolean {
        fun Workout.uses() = exercises.any { it.exerciseId == exerciseId }
        return current?.uses() == true || history.any { it.uses() }
    }

    /**
     * Nouvel ordre des exercices (glisser-déposer, § 2.4) : ceux de [exerciseIds] dans cet ordre,
     * puis ceux qui n'y figurent pas (par ex. masqués car leur définition est introuvable).
     */
    fun reorder(workout: Workout, exerciseIds: List<String>): Workout {
        val byId = workout.exercises.associateBy { it.exerciseId }
        val listed = exerciseIds.distinct().mapNotNull { byId[it] }
        val listedIds = listed.mapTo(HashSet()) { it.exerciseId }
        return workout.copy(exercises = listed + workout.exercises.filterNot { it.exerciseId in listedIds })
    }
}

/** Applique [transform] à l'exercice [exerciseId] de la séance. */
internal fun Workout.mapExercise(exerciseId: String, transform: (WorkoutExercise) -> WorkoutExercise): Workout =
    copy(exercises = exercises.map { if (it.exerciseId == exerciseId) transform(it) else it })

/** Applique [transform] à la série [setId] de l'exercice [exerciseId]. */
internal fun Workout.mapSet(exerciseId: String, setId: String, transform: (WorkoutSet) -> WorkoutSet): Workout =
    mapExercise(exerciseId) { e -> e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) }
