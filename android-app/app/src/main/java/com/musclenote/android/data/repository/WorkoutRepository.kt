package com.musclenote.android.data.repository

import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.catalog.MuscleRules
import com.musclenote.android.data.catalog.MuscleSelection
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.ExerciseType
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.SetKind
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.formatInstant
import com.musclenote.android.data.util.newId
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.ChainNext
import com.musclenote.android.data.workout.CustomExerciseDeletion
import com.musclenote.android.data.workout.HoldDuration
import com.musclenote.android.data.workout.SetTimerRules
import com.musclenote.android.data.workout.SupersetRules
import com.musclenote.android.data.workout.WorkoutEnd
import com.musclenote.android.data.workout.WorkoutNotificationRules
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
     * 24 h (§ 3.4) : renvoie alors son issue, pour que le repos en cours soit arrêté.
     */
    suspend fun load(): WorkoutEnd? {
        return mutex.withLock {
            // Une écriture différée de la séance en cours doit précéder sa relecture.
            currentWorkoutDoc.flush()
            // 🔄 D45 (§ 15.19.2) : une séance perso au nom d'un préréglage y est rattachée, en mémoire ; le
            // fichier reçoit le préréglage à sa prochaine écriture.
            _workouts.value = sortedByDateDesc(workoutsDoc.read().orEmpty().map(WorkoutRules::withPresetFromName))
            // § 3.9 : un exercice stocké dont l'id est un id intégré est ignoré.
            // § 15.17.8 : muscles remis en forme (ordre, doublons, un muscle des deux reste principal).
            _customExercises.value = exercisesDoc.read().orEmpty().filterNot { ExerciseCatalog.isBuiltIn(it.id) }.map(MuscleRules::normalized)
            // 🔄 D46 (§ 15.20.7) : liens de supersérie remis en forme (un exercice lié resté seul perd son lien).
            _currentWorkout.value = currentWorkoutDoc.read()?.let(WorkoutRules::withPresetFromName)?.let(::withNormalizedLinks)
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

    // ─── Séances terminées ────────────────────────────────────────────────────────

    /** Nouvelle date d'une séance terminée (modale de l'Accueil, § 2.2) ; l'historique est retrié. */
    suspend fun updateWorkoutDate(workoutId: String, date: Instant) = updateHistory { list ->
        sortedByDateDesc(list.map { if (it.id == workoutId) it.copy(date = formatInstant(date)) else it })
    }

    /** Suppression définitive d'une séance terminée (confirmée en ligne sur l'Accueil). */
    suspend fun deleteWorkout(workoutId: String) = updateHistory { list -> list.filterNot { it.id == workoutId } }

    /**
     * Enregistrement de l'éditeur (§ 2.9) : exercices, poids de corps (≤ 0 → ancien poids), nom et
     * préréglage. Appliqué à la dernière version de la séance ; sans effet si elle a disparu.
     */
    suspend fun patchWorkout(
        workoutId: String,
        exercises: List<WorkoutExercise>,
        bodyWeight: Double,
        name: String,
        preset: PresetId?,
    ) = updateHistory { list ->
        list.map { if (it.id == workoutId) WorkoutRules.patch(it, exercises, bodyWeight, name, preset, idGenerator) else it }
    }

    private suspend fun updateHistory(transform: (List<Workout>) -> List<Workout>) {
        mutex.withLock {
            val current = _workouts.value
            val next = transform(current)
            if (next != current) commitWorkouts(next)
        }
    }

    // ─── Séance en cours ────────────────────────────────────────────────────────────

    /** § 3.2 : sans effet si l'exercice est déjà dans la séance. */
    suspend fun addExerciseToWorkout(exerciseId: String) =
        updateCurrent { WorkoutRules.addExercise(it, _workouts.value, exerciseId, idGenerator) }

    /** Retrait d'un exercice : sa supersérie se referme sans lui (🔄 D46, § 15.20.1). */
    suspend fun removeExerciseFromWorkout(exerciseId: String) =
        updateCurrent { w -> withNormalizedLinks(w.copy(exercises = w.exercises.filterNot { it.exerciseId == exerciseId })) }

    /** Glisser-déposer (§ 2.4) : nouvel ordre des exercices ; les liens de supersérie suivent les voisins (§ 15.20.1). */
    suspend fun reorderExercises(exerciseIds: List<String>) = updateCurrent { withNormalizedLinks(WorkoutRules.reorder(it, exerciseIds)) }

    /** « Lier à l'exercice suivant » (🔄 D46, § 15.20.1) : exercices affichés seulement. */
    suspend fun linkToNext(exerciseId: String) =
        updateCurrent { w -> w.copy(exercises = SupersetRules.link(w.exercises, exerciseId, idGenerator, ::isShown)) }

    /** « Délier » (§ 15.20.1) : l'exercice sort de sa supersérie. */
    suspend fun unlink(exerciseId: String) =
        updateCurrent { w -> w.copy(exercises = SupersetRules.unlink(w.exercises, exerciseId, idGenerator, ::isShown)) }

    /** Note libre, enregistrée à chaque frappe ; une note vidée est retirée. */
    suspend fun updateExerciseNote(exerciseId: String, note: String) =
        updateCurrent { w -> w.mapExercise(exerciseId) { it.copy(note = note.ifEmpty { null }) } }

    /** « + Ajouter une série » (§ 2.5) : force, cardio ou au temps selon l'exercice (§ 15.18.1). */
    suspend fun addSet(exerciseId: String) = updateCurrent { w ->
        val kind = kindOf(exerciseId)
        w.mapExercise(exerciseId) { e -> e.copy(sets = e.sets + WorkoutRules.newSet(e.sets.lastOrNull(), kind, idGenerator())) }
    }

    /**
     * Chrono d'une série au temps arrêté (§ 15.18.4) : la série [setId] de la séance en cours prend
     * [seconds] comme durée et, si [validate], est validée. Renvoie l'id de son exercice si elle vient
     * de passer à validée (le repos démarre alors), sinon `null`.
     */
    suspend fun recordHold(setId: String, seconds: Int, validate: Boolean): String? {
        return mutex.withLock {
            val current = _currentWorkout.value ?: return null
            val located = SetTimerRules.locate(current, setId) ?: return null
            val completes = validate && !located.set.completed
            val exerciseId = located.exercise.exerciseId
            commitCurrent(
                current.mapSet(exerciseId, setId) {
                    it.copy(durationSeconds = HoldDuration.clamp(seconds), completed = it.completed || completes)
                },
            )
            exerciseId.takeIf { completes }
        }
    }

    /** Saisie dans une ligne de série, appliquée à chaque frappe (§ 2.6). */
    suspend fun updateSet(exerciseId: String, setId: String, transform: (WorkoutSet) -> WorkoutSet) =
        updateCurrent { it.mapSet(exerciseId, setId, transform) }

    /** Toutes les séries d'un exercice d'un coup (suggestion de charge, § 15.7.3). */
    suspend fun updateExerciseSets(exerciseId: String, transform: (List<WorkoutSet>) -> List<WorkoutSet>) =
        updateCurrent { w -> w.mapExercise(exerciseId) { e -> e.copy(sets = transform(e.sets)) } }

    /**
     * « Valider la série » du pavé (§ 15.7.4) : valide la série sans jamais la dévalider ; `true`
     * seulement si elle vient de passer à validée (le repos démarre alors).
     */
    suspend fun completeSet(exerciseId: String, setId: String): Boolean {
        return mutex.withLock {
            val current = _currentWorkout.value ?: return false
            val set = current.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets?.firstOrNull { it.id == setId }
            if (set == null || set.completed) return false
            commitCurrent(current.mapSet(exerciseId, setId) { it.copy(completed = true) })
            true
        }
    }

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

    /**
     * « Valider la série » de la notification de séance (§ 15.5) : valide la prochaine série
     * ([WorkoutNotificationRules.nextSet], exercices connus seulement, ordre d'enchaînement) et renvoie
     * l'id de son exercice, pour lancer le repos ; `null` sans séance en cours ou si tout est validé.
     */
    suspend fun completeNextSet(): String? = completeNext()?.first

    /** Comme [completeNextSet], avec l'id de la série validée : (exercice, série). */
    suspend fun completeNext(): Pair<String, String>? {
        return mutex.withLock {
            val current = _currentWorkout.value ?: return null
            val next = WorkoutNotificationRules.nextSet(current, ::isShown) ?: return null
            commitCurrent(current.mapSet(next.exercise.exerciseId, next.set.id) { it.copy(completed = true) })
            next.exercise.exerciseId to next.set.id
        }
    }

    /** Une série de la séance en cours reste à valider (exercices connus seulement, § 15.5). */
    fun hasNextSet(): Boolean = _currentWorkout.value?.let { w -> WorkoutNotificationRules.nextSet(w, ::isShown) } != null

    /**
     * Après la validation de la série [setId] de [exerciseId] (§ 15.20.3) : transition dans une supersérie
     * ou non, et la série suivante, parmi les exercices affichés.
     */
    fun afterCompleting(exerciseId: String, setId: String): ChainNext? {
        val current = _currentWorkout.value ?: return null
        return SupersetRules.afterCompleting(current.exercises.filter { isShown(it.exerciseId) }, exerciseId, setId)
    }

    private suspend fun updateCurrent(transform: (Workout) -> Workout) {
        mutex.withLock {
            val current = _currentWorkout.value ?: return
            val next = transform(current)
            if (next != current) commitCurrent(next)
        }
    }

    // ─── Exercices perso (§ 3.9) ──────────────────────────────────────────────────────

    /**
     * Création, en fin de liste, avec ses muscles facultatifs (§ 15.17.3). Nom vide → rien (`null`).
     * Pas de contrôle de doublon.
     */
    suspend fun addCustomExercise(
        name: String,
        muscleGroup: MuscleGroup,
        muscles: MuscleSelection = MuscleSelection(),
        type: ExerciseType = ExerciseType.Reps,
    ): Exercise? {
        return mutex.withLock {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return null
            val exercise = MuscleRules.normalized(
                Exercise(
                    id = idGenerator(),
                    name = trimmed,
                    muscleGroup = muscleGroup,
                    primaryMuscles = muscles.primary,
                    secondaryMuscles = muscles.secondary,
                    type = type,
                ),
            )
            commitExercises(_customExercises.value + exercise)
            exercise
        }
    }

    /**
     * Modification du nom, du groupe et, s'ils ne sont pas `null`, des muscles (§ 15.17.3) et du type
     * (§ 15.18.1) ; les exercices intégrés ne sont pas modifiables. Les séances ne changent pas.
     */
    suspend fun updateCustomExercise(
        id: String,
        name: String,
        muscleGroup: MuscleGroup,
        muscles: MuscleSelection? = null,
        type: ExerciseType? = null,
    ): Boolean {
        return mutex.withLock {
            val trimmed = name.trim()
            val list = _customExercises.value
            if (trimmed.isEmpty() || ExerciseCatalog.isBuiltIn(id) || list.none { it.id == id }) return false
            commitExercises(
                list.map { exercise ->
                    if (exercise.id != id) return@map exercise
                    val renamed = exercise.copy(name = trimmed, muscleGroup = muscleGroup, type = type ?: exercise.type)
                    if (muscles == null) renamed else MuscleRules.normalized(renamed.copy(primaryMuscles = muscles.primary, secondaryMuscles = muscles.secondary))
                },
            )
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

    /** Exercice affiché dans la séance (§ 10.5) : sa définition est connue. */
    private fun isShown(exerciseId: String): Boolean = muscleGroupOf(exerciseId) != null

    private fun withNormalizedLinks(workout: Workout): Workout {
        val exercises = SupersetRules.normalized(workout.exercises, idGenerator, ::isShown)
        return if (exercises === workout.exercises) workout else workout.copy(exercises = exercises)
    }

    private fun muscleGroupOf(exerciseId: String): MuscleGroup? =
        ExerciseCatalog.find(exerciseId)?.muscleGroup
            ?: _customExercises.value.firstOrNull { it.id == exerciseId }?.muscleGroup

    /** Champs des séries de l'exercice (§ 15.18.1) ; un exercice inconnu est traité en force. */
    private fun kindOf(exerciseId: String): SetKind {
        ExerciseCatalog.find(exerciseId)?.let { return SetKind.of(it.muscleGroup, it.type) }
        val custom = _customExercises.value.firstOrNull { it.id == exerciseId } ?: return SetKind.Strength
        return SetKind.of(custom.muscleGroup, custom.type)
    }

    private fun sortedByDateDesc(list: List<Workout>): List<Workout> =
        list.sortedByDescending { parseInstantOrNull(it.date, time.zone()) ?: Instant.MIN }

    companion object {
        /** Regroupement des écritures de la séance en cours (§ 4.2). */
        const val CURRENT_WRITE_DELAY_MS = 300L
    }
}
