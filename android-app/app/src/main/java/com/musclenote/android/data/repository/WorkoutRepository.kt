package com.musclenote.android.data.repository

import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.newId
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.CustomExerciseDeletion
import com.musclenote.android.data.workout.WorkoutEnd
import com.musclenote.android.data.workout.WorkoutRules
import com.musclenote.android.data.workout.mapExercise
import com.musclenote.android.data.workout.mapSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Dépôt des séances : historique, exercices perso et séance en cours (SPEC § 3.1 – 3.4, § 3.9,
 * § 4.2).
 *
 * Toute mutation passe par [mutex], part de la dernière valeur validée en mémoire, puis met à
 * jour l'état exposé et planifie l'écriture en un seul point (`commit…`). L'UI ne garde jamais
 * sa propre copie de ces données ; elle lance les mutations par `AppContainer.launchMutation`.
 * La séance en cours, modifiée à chaque frappe, est écrite au plus tard [CURRENT_WRITE_DELAY_MS]
 * après le dernier changement, et tout de suite à la mise en arrière-plan ([flushPendingWrites]).
 */
class WorkoutRepository(
    private val workoutsDoc: JsonDocument<List<Workout>>,
    private val exercisesDoc: JsonDocument<List<Exercise>>,
    private val currentWorkoutDoc: JsonDocument<Workout>,
    private val time: TimeSource,
    private val idGenerator: () -> String = { newId() },
) {
    private val mutex = Mutex()

    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())

    /** Séances terminées, de la plus récente à la plus ancienne. */
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private val _customExercises = MutableStateFlow<List<Exercise>>(emptyList())

    /** Exercices perso par ordre de création, archivés compris (les intégrés viennent de [ExerciseCatalog]). */
    val customExercises: StateFlow<List<Exercise>> = _customExercises.asStateFlow()

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout.asStateFlow()

    // ─── Chargement, import ─────────────────────────────────────────────────────

    /**
     * (Re)lit les trois documents depuis le disque, puis finalise une séance en cours de plus de
     * 24 h (§ 3.4).
     */
    suspend fun load() {
        mutex.withLock {
            // Une écriture différée de la séance en cours doit précéder sa relecture.
            currentWorkoutDoc.flush()
            _workouts.value = sortedByDateDesc(workoutsDoc.read().orEmpty())
            // § 3.9 : un exercice stocké dont l'id est un id intégré est ignoré.
            _customExercises.value = exercisesDoc.read().orEmpty().filterNot { ExerciseCatalog.isBuiltIn(it.id) }
            _currentWorkout.value = currentWorkoutDoc.read()
            finalizeStaleLocked()
        }
    }

    /**
     * Import (§ 5.4) : remplace les documents présents dans la sauvegarde (`null` = absent du
     * fichier, non touché) et attend leur écriture. La séance en cours n'est pas touchée.
     */
    suspend fun replaceFromBackup(workouts: List<Workout>?, exercises: List<Exercise>?) {
        mutex.withLock {
            workouts?.let { workoutsDoc.saveNow(it) }
            exercises?.let { exercisesDoc.saveNow(it) }
        }
    }

    /** Écrit tout de suite la séance en cours si une écriture différée attend (§ 4.2). */
    suspend fun flushPendingWrites() = currentWorkoutDoc.flush()

    // ─── Début et fin de séance ───────────────────────────────────────────────────

    /**
     * Démarre une séance (§ 3.1) : [preset] (ou `null`) et [name] = libellé du préréglage dans la
     * langue courante, ou nom perso. Pré-remplie depuis la dernière séance de même clé.
     */
    suspend fun startWorkout(bodyWeight: Double, preset: PresetId?, name: String): Workout = mutex.withLock {
        val workout = WorkoutRules.newWorkout(_workouts.value, bodyWeight, preset, name, time.now(), idGenerator)
        commitCurrent(workout)
        workout
    }

    /**
     * Termine la séance en cours (§ 3.3) : historique avec sa durée, ou abandon sans série
     * validée. `null` s'il n'y avait pas de séance en cours.
     */
    suspend fun endWorkout(): WorkoutEnd? {
        return mutex.withLock {
            val current = _currentWorkout.value ?: return null
            val end = WorkoutRules.end(current, time.now(), time.zone())
            if (end is WorkoutEnd.Finished) commitWorkouts(sortedByDateDesc(listOf(end.workout) + _workouts.value))
            commitCurrent(null)
            end
        }
    }

    /**
     * Contrôle des 24 h (§ 3.4), au chargement et à chaque retour de l'app au premier plan.
     * Renvoie l'issue si une séance a été finalisée ou abandonnée.
     */
    suspend fun finalizeStaleSession(): WorkoutEnd? = mutex.withLock { finalizeStaleLocked() }

    private fun finalizeStaleLocked(): WorkoutEnd? {
        val current = _currentWorkout.value ?: return null
        if (!WorkoutRules.isStale(current, time.now(), time.zone())) return null
        val end = WorkoutRules.finalizeStale(current)
        if (end is WorkoutEnd.Finished) commitWorkouts(sortedByDateDesc(listOf(end.workout) + _workouts.value))
        commitCurrent(null)
        return end
    }

    // ─── Séance en cours ────────────────────────────────────────────────────────────

    /** § 3.2 : sans effet si l'exercice est déjà dans la séance. */
    suspend fun addExerciseToWorkout(exerciseId: String) =
        updateCurrent { WorkoutRules.addExercise(it, _workouts.value, exerciseId, idGenerator) }

    suspend fun removeExerciseFromWorkout(exerciseId: String) =
        updateCurrent { w -> w.copy(exercises = w.exercises.filterNot { it.exerciseId == exerciseId }) }

    /** Glisser-déposer (§ 2.4) : nouvel ordre des exercices. */
    suspend fun reorderExercises(exerciseIds: List<String>) = updateCurrent { WorkoutRules.reorder(it, exerciseIds) }

    /** Note libre, enregistrée à chaque frappe ; une note vidée est retirée. */
    suspend fun updateExerciseNote(exerciseId: String, note: String) =
        updateCurrent { w -> w.mapExercise(exerciseId) { it.copy(note = note.ifEmpty { null }) } }

    /** « + Ajouter une série » (§ 2.5) : force ou cardio selon le groupe de l'exercice. */
    suspend fun addSet(exerciseId: String) = updateCurrent { w ->
        val cardio = muscleGroupOf(exerciseId) == MuscleGroup.Cardio
        w.mapExercise(exerciseId) { e -> e.copy(sets = e.sets + WorkoutRules.newSet(e.sets.lastOrNull(), cardio, idGenerator())) }
    }

    /** Saisie dans une ligne de série, appliquée à chaque frappe (§ 2.6). */
    suspend fun updateSet(exerciseId: String, setId: String, transform: (WorkoutSet) -> WorkoutSet) =
        updateCurrent { it.mapSet(exerciseId, setId, transform) }

    suspend fun removeSet(exerciseId: String, setId: String) =
        updateCurrent { w -> w.mapExercise(exerciseId) { e -> e.copy(sets = e.sets.filterNot { it.id == setId }) } }

    /** Bascule la validation d'une série ; renvoie le nouvel état (`null` si série introuvable). */
    suspend fun toggleSetCompleted(exerciseId: String, setId: String): Boolean? {
        return mutex.withLock {
            val current = _currentWorkout.value ?: return null
            val set = current.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets?.firstOrNull { it.id == setId }
                ?: return null
            val completed = !set.completed
            commitCurrent(current.mapSet(exerciseId, setId) { it.copy(completed = completed) })
            completed
        }
    }

    private suspend fun updateCurrent(transform: (Workout) -> Workout) {
        mutex.withLock {
            val current = _currentWorkout.value ?: return
            val next = transform(current)
            if (next != current) commitCurrent(next)
        }
    }

    // ─── Exercices perso (§ 3.9) ──────────────────────────────────────────────────────

    /** Création, en fin de liste. Nom vide → rien (`null`). Pas de contrôle de doublon. */
    suspend fun addCustomExercise(name: String, muscleGroup: MuscleGroup): Exercise? {
        return mutex.withLock {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return null
            val exercise = Exercise(id = idGenerator(), name = trimmed, muscleGroup = muscleGroup)
            commitExercises(_customExercises.value + exercise)
            exercise
        }
    }

    /** Modification du nom et du groupe ; les exercices intégrés ne sont pas modifiables. */
    suspend fun updateCustomExercise(id: String, name: String, muscleGroup: MuscleGroup): Boolean {
        return mutex.withLock {
            val trimmed = name.trim()
            val list = _customExercises.value
            if (trimmed.isEmpty() || ExerciseCatalog.isBuiltIn(id) || list.none { it.id == id }) return false
            commitExercises(list.map { if (it.id == id) it.copy(name = trimmed, muscleGroup = muscleGroup) else it })
            true
        }
    }

    /**
     * Suppression : un exercice référencé par la séance en cours ou par une séance terminée est
     * seulement archivé ; sinon il est supprimé. `null` si introuvable ou intégré.
     */
    suspend fun deleteCustomExercise(id: String): CustomExerciseDeletion? {
        return mutex.withLock {
            val list = _customExercises.value
            if (ExerciseCatalog.isBuiltIn(id) || list.none { it.id == id }) return null
            if (WorkoutRules.isReferenced(id, _currentWorkout.value, _workouts.value)) {
                commitExercises(list.map { if (it.id == id) it.copy(archived = true) else it })
                CustomExerciseDeletion.Archived
            } else {
                commitExercises(list.filterNot { it.id == id })
                CustomExerciseDeletion.Deleted
            }
        }
    }

    // ─── Écriture ───────────────────────────────────────────────────────────────────

    private fun commitWorkouts(next: List<Workout>) {
        _workouts.value = next
        workoutsDoc.save(next)
    }

    private fun commitExercises(next: List<Exercise>) {
        _customExercises.value = next
        exercisesDoc.save(next)
    }

    private fun commitCurrent(next: Workout?) {
        _currentWorkout.value = next
        // Fin de séance : effacement immédiat, qui rend caduque une écriture différée en attente.
        if (next == null) currentWorkoutDoc.save(null) else currentWorkoutDoc.saveDebounced(next, CURRENT_WRITE_DELAY_MS)
    }

    private fun muscleGroupOf(exerciseId: String): MuscleGroup? =
        ExerciseCatalog.find(exerciseId)?.muscleGroup
            ?: _customExercises.value.firstOrNull { it.id == exerciseId }?.muscleGroup

    private fun sortedByDateDesc(list: List<Workout>): List<Workout> =
        list.sortedByDescending { parseInstantOrNull(it.date, time.zone()) ?: Instant.MIN }

    companion object {
        /** Regroupement des écritures de la séance en cours (§ 4.2). */
        const val CURRENT_WRITE_DELAY_MS = 300L
    }
}
