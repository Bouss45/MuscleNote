package com.musclenote.android.data.repository

import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.parseInstantOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Dépôt des séances : historique, exercices perso et séance en cours (SPEC § 4.2).
 *
 * Toute mutation passe par [mutex], part de la dernière valeur validée en mémoire, puis met à
 * jour l'état exposé et planifie l'écriture en un seul point. L'UI ne garde jamais sa propre
 * copie de ces données.
 */
class WorkoutRepository(
    private val workoutsDoc: JsonDocument<List<Workout>>,
    private val exercisesDoc: JsonDocument<List<Exercise>>,
    private val currentWorkoutDoc: JsonDocument<Workout>,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())

    /** Séances terminées, de la plus récente à la plus ancienne. */
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private val _customExercises = MutableStateFlow<List<Exercise>>(emptyList())

    /** Exercices perso, archivés compris (les intégrés viennent de [ExerciseCatalog]). */
    val customExercises: StateFlow<List<Exercise>> = _customExercises.asStateFlow()

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout.asStateFlow()

    /** (Re)lit les trois documents depuis le disque. */
    suspend fun load() {
        mutex.withLock {
            var workouts = sortedByDateDesc(workoutsDoc.read().orEmpty())
            // § 3.9 : un exercice stocké dont l'id est un id intégré est ignoré.
            val exercises = exercisesDoc.read().orEmpty().filterNot { ExerciseCatalog.isBuiltIn(it.id) }
            var current = currentWorkoutDoc.read()

            // § 3.4 : une séance en cours de plus de 24 h est finalisée (sans durée connue) si elle
            // a au moins une série validée, abandonnée sinon.
            if (current != null && isOlderThanMaxSession(current)) {
                if (current.exercises.any { e -> e.sets.any { it.completed } }) {
                    workouts = sortedByDateDesc(workouts + current.copy(durationMinutes = null))
                    workoutsDoc.save(workouts)
                }
                current = null
                currentWorkoutDoc.save(null)
            }

            _workouts.value = workouts
            _customExercises.value = exercises
            _currentWorkout.value = current
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

    private fun isOlderThanMaxSession(workout: Workout): Boolean {
        val start = parseInstantOrNull(workout.date, time.zone()) ?: return true
        return Duration.between(start, time.now()) > MAX_SESSION
    }

    private fun sortedByDateDesc(list: List<Workout>): List<Workout> =
        list.sortedByDescending { parseInstantOrNull(it.date, time.zone()) ?: Instant.MIN }

    companion object {
        val MAX_SESSION: Duration = Duration.ofHours(24)
    }
}
