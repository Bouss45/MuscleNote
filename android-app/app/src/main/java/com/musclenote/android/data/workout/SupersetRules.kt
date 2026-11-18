package com.musclenote.android.data.workout

import com.musclenote.android.data.model.WorkoutExercise

/**
 * Après la validation d'une série (SPEC § 15.20.3) : [transition] = un exercice placé après le sien dans
 * la supersérie a encore cette série de travail à faire (repos de transition, ou aucun repos) ; sinon fin
 * du tour, repos normal. [next] = la série non validée suivante de la supersérie (ou de l'exercice, hors
 * supersérie) dans l'ordre d'enchaînement : celle que le pavé ouvre, dont l'exercice nomme le repos.
 */
data class ChainNext(val transition: Boolean, val next: NextSet?)

/**
 * Superséries (SPEC § 15.20, 🔄 D46), en fonctions pures testables sur la JVM. Une supersérie est une
 * suite d'exercices **voisins** au même `supersetId` (deux au moins ; trois ou plus : un circuit). Les
 * listes reçues sont dans l'ordre de la séance ; [isShown] écarte les exercices masqués (inconnus,
 * § 10.5), qui ne comptent pas comme voisins et ne sont jamais touchés.
 */
object SupersetRules {

    /** Les superséries de [exercises], dans l'ordre, chacune avec ses exercices dans l'ordre. */
    fun groups(exercises: List<WorkoutExercise>): List<List<WorkoutExercise>> = runs(exercises).filter { it.size > 1 }

    /** La supersérie de [exerciseId] ; `null` s'il n'est lié à aucun exercice. */
    fun groupOf(exercises: List<WorkoutExercise>, exerciseId: String): List<WorkoutExercise>? =
        groups(exercises).firstOrNull { group -> group.any { it.exerciseId == exerciseId } }

    /** [exerciseId] est lié à l'exercice qui le suit. */
    fun isLinkedToNext(exercises: List<WorkoutExercise>, exerciseId: String): Boolean {
        val index = exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0 || index == exercises.lastIndex) return false
        val id = exercises[index].supersetId
        return id != null && exercises[index + 1].supersetId == id
    }

    /** « Lier à l'exercice suivant » est proposé (§ 15.20.1) : un exercice le suit, et ils ne sont pas liés. */
    fun canLinkToNext(exercises: List<WorkoutExercise>, exerciseId: String): Boolean {
        val index = exercises.indexOfFirst { it.exerciseId == exerciseId }
        return index in 0 until exercises.lastIndex && !isLinkedToNext(exercises, exerciseId)
    }

    /**
     * « Lier à l'exercice suivant » (§ 15.20.1) : [exerciseId] et le suivant entrent dans la même
     * supersérie ; si l'un des deux est déjà dans une supersérie, l'autre (et toute la sienne) la rejoint.
     * Renvoie [exercises] lui-même si rien ne change.
     */
    fun link(
        exercises: List<WorkoutExercise>,
        exerciseId: String,
        newId: () -> String,
        isShown: (String) -> Boolean = { true },
    ): List<WorkoutExercise> {
        val shown = exercises.filter { isShown(it.exerciseId) }
        val index = shown.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0 || index == shown.lastIndex || isLinkedToNext(shown, exerciseId)) return exercises
        val current = shown[index]
        val next = shown[index + 1]
        val target = current.supersetId ?: next.supersetId ?: newId()
        val joining = (groupOf(shown, next.exerciseId) ?: listOf(next)).mapTo(HashSet()) { it.exerciseId } + current.exerciseId
        val linked = exercises.map { if (it.exerciseId in joining && it.supersetId != target) it.copy(supersetId = target) else it }
        return normalized(linked, newId, isShown)
    }

    /**
     * « Délier » (§ 15.20.1) : [exerciseId] sort de sa supersérie ; les voisins qui restent liés deux à
     * deux gardent leur lien, un exercice resté seul n'est plus lié.
     */
    fun unlink(
        exercises: List<WorkoutExercise>,
        exerciseId: String,
        newId: () -> String,
        isShown: (String) -> Boolean = { true },
    ): List<WorkoutExercise> {
        if (exercises.none { it.exerciseId == exerciseId && it.supersetId != null }) return exercises
        return normalized(exercises.map { if (it.exerciseId == exerciseId) it.copy(supersetId = null) else it }, newId, isShown)
    }

    /**
     * Remise en forme (§ 15.20.7), après toute lecture ou tout changement des exercices : une suite de
     * voisins au même identifiant est une supersérie ; un exercice sans voisin au même identifiant le perd ;
     * deux suites séparées au même identifiant → la seconde en reçoit un nouveau. Renvoie [exercises]
     * lui-même si rien ne change.
     */
    fun normalized(
        exercises: List<WorkoutExercise>,
        newId: () -> String,
        isShown: (String) -> Boolean = { true },
    ): List<WorkoutExercise> {
        val shownIndices = exercises.indices.filter { isShown(exercises[it].exerciseId) }
        val ids = shownIndices.map { exercises[it].supersetId }
        val fixed = normalizedIds(ids, newId)
        if (fixed == ids) return exercises
        val result = exercises.toMutableList()
        shownIndices.forEachIndexed { k, i -> if (result[i].supersetId != fixed[k]) result[i] = result[i].copy(supersetId = fixed[k]) }
        return result
    }

    private fun normalizedIds(ids: List<String?>, newId: () -> String): List<String?> {
        val out = ids.toMutableList()
        val used = HashSet<String>()
        var start = 0
        while (start < ids.size) {
            val id = ids[start]
            var end = start + 1
            while (id != null && end < ids.size && ids[end] == id) end++
            if (id != null) {
                val label = when {
                    end - start < 2 -> null
                    used.add(id) -> id
                    else -> newId().also { used.add(it) }
                }
                for (k in start until end) out[k] = label
            }
            start = end
        }
        return out
    }

    /**
     * Ordre d'enchaînement (§ 15.20.3, § 15.21.4) : les exercices dans l'ordre ; ceux d'une supersérie
     * tour par tour — la série de travail n de chacun, un rang absent sauté —, les séries de chauffe d'un
     * exercice juste avant sa première série de travail ; hors supersérie, les séries d'un exercice à la
     * suite. Chaque série porte son numéro (§ 15.21.2).
     */
    fun chainOrder(exercises: List<WorkoutExercise>): List<NextSet> = buildList {
        for (run in runs(exercises)) {
            if (run.size == 1) {
                val exercise = run.single()
                val numbers = SetNumbering.of(exercise.sets)
                exercise.sets.forEachIndexed { i, set -> add(NextSet(exercise, set, numbers[i].rank, numbers[i].warmup)) }
                continue
            }
            val work = run.map { e -> e.sets.filterNot { it.warmup } }
            val rounds = maxOf(1, work.maxOf { it.size })
            for (round in 0 until rounds) {
                run.forEachIndexed { k, exercise ->
                    if (round == 0) {
                        exercise.sets.filter { it.warmup }.forEachIndexed { i, set -> add(NextSet(exercise, set, i + 1, warmup = true)) }
                    }
                    work[k].getOrNull(round)?.let { add(NextSet(exercise, it, round + 1)) }
                }
            }
        }
    }

    /**
     * Après la validation de la série [setId] de [exerciseId] (§ 15.20.3) : transition ou fin du tour, et
     * la série suivante. Une série de chauffe n'enchaîne jamais (§ 15.21.4).
     */
    fun afterCompleting(exercises: List<WorkoutExercise>, exerciseId: String, setId: String): ChainNext {
        val exercise = exercises.firstOrNull { it.exerciseId == exerciseId } ?: return ChainNext(false, null)
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return ChainNext(false, null)
        val group = groupOf(exercises, exerciseId) ?: listOf(exercise)
        val order = chainOrder(group)
        val position = order.indexOfFirst { it.set.id == setId }
        val next = order.drop(position + 1).firstOrNull { !it.set.completed }
        val rank = SetNumbering.of(exercise.sets, setId)!!.rank
        val following = group.drop(group.indexOfFirst { it.exerciseId == exerciseId } + 1)
        val transition = !set.warmup && following.any { e -> e.sets.filterNot { it.warmup }.getOrNull(rank - 1)?.completed == false }
        return ChainNext(transition, next)
    }

    /** Suites d'exercices voisins au même identifiant (un exercice sans lien : une suite à lui seul). */
    private fun runs(exercises: List<WorkoutExercise>): List<List<WorkoutExercise>> {
        val runs = mutableListOf<MutableList<WorkoutExercise>>()
        for (exercise in exercises) {
            val last = runs.lastOrNull()
            val id = exercise.supersetId
            if (last != null && id != null && last.last().supersetId == id) last += exercise else runs += mutableListOf(exercise)
        }
        return runs
    }
}
