package com.musclenote.android.data.catalog

import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filtre « Par muscle », exercices d'un muscle et muscles d'un exercice perso (§ 15.17, 🔄 D43). */
class MuscleRulesTest {

    private val hipThrust = Exercise(
        id = "c1",
        name = "Hip thrust",
        muscleGroup = MuscleGroup.Jambes,
        primaryMuscles = listOf(Muscle.Fessiers),
        secondaryMuscles = listOf(Muscle.IschioJambiers),
    )
    private val noMuscles = Exercise(id = "c2", name = "Machine mystère", muscleGroup = MuscleGroup.Dos)
    private val archived = Exercise(
        id = "c3",
        name = "Ancien pull-over",
        muscleGroup = MuscleGroup.Dos,
        archived = true,
        primaryMuscles = listOf(Muscle.Dorsaux),
    )
    private val definitions = exerciseDefinitions(listOf(hipThrust, noMuscles, archived))

    /** Noms affichés sans ressources : l'id d'un intégré, le nom d'un perso. */
    private fun nameOf(definition: ExerciseDefinition) = definition.customName ?: definition.id

    private fun filter(vararg muscles: Muscle, query: String = "") =
        MuscleRules.filter(definitions, muscles.toSet(), query, ::nameOf).map { it.id }

    // ─── Filtre « Par muscle » (§ 15.17.6) ──────────────────────────────────────

    @Test
    fun `un muscle retient les exercices dont il est principal`() {
        val ids = filter(Muscle.Pectoraux)
        assertTrue(ids.containsAll(listOf("bench", "incline", "fly", "dips", "dbench", "dincline", "pecfly")))
        assertFalse("squat" in ids)
    }

    @Test
    fun `un muscle retient aussi les exercices dont il est secondaire, après les principaux`() {
        // Pecs principal : 7 exercices de pecs ; secondaire : développé militaire, élévations frontales.
        assertEquals(
            listOf("bench", "incline", "fly", "dips", "dbench", "dincline", "pecfly", "ohp", "frontraise"),
            filter(Muscle.Pectoraux),
        )
        // Mollets : principaux d'abord (dans l'ordre du sélecteur), puis secondaires.
        assertEquals(listOf("calfraize", "calfsit", "treadmill", "legcurl", "bike"), filter(Muscle.Mollets))
    }

    @Test
    fun `plusieurs muscles retiennent l'union, principaux de l'un ou de l'autre d'abord`() {
        val ids = filter(Muscle.Triceps, Muscle.Adducteurs)
        // Principal de l'un des deux : dips, les 4 exercices de triceps, adducteur press.
        assertEquals(listOf("dips", "pushdown", "overheadtri", "skullcrusher", "barfront", "adductorpress"), ids.take(6))
        // Puis secondaire seulement : développés (triceps), soulevé de terre, squat… (adducteurs).
        assertEquals(
            listOf("deadlift", "bench", "incline", "dbench", "dincline", "squat", "legpress", "lunges", "wallsit", "ohp"),
            ids.drop(6),
        )
    }

    @Test
    fun `le filtre par muscle se combine à la recherche`() {
        assertEquals(listOf("dincline", "incline"), filter(Muscle.Pectoraux, query = "incline").sorted())
        assertEquals(emptyList<String>(), filter(Muscle.Mollets, query = "bench"))
        // Recherche sans accents ni casse (§ 10.6).
        assertEquals(listOf("c1"), filter(Muscle.Fessiers, query = "HIP"))
    }

    @Test
    fun `sans muscle choisi la liste entière reste, filtrée par le texte seul`() {
        assertEquals(definitions.filterNot { it.archived }.map { it.id }, filter())
        assertEquals(listOf("c2"), filter(query = "mystère"))
    }

    @Test
    fun `un exercice perso sans muscle n'est jamais retenu par un muscle`() {
        for (muscle in Muscle.entries) assertFalse(muscle.id, "c2" in filter(muscle))
        assertTrue("c2" in filter())
    }

    @Test
    fun `un exercice perso avec muscles est retenu comme un intégré`() {
        val glutes = filter(Muscle.Fessiers)
        // Principal : après les intégrés principaux, dans l'ordre du sélecteur (catalogue puis perso).
        assertEquals(listOf("deadlift", "squat", "lunges", "abductorpress", "c1"), glutes.take(5))
        assertTrue("c1" in filter(Muscle.IschioJambiers))
    }

    @Test
    fun `les exercices archivés ne sont jamais proposés`() {
        assertFalse("c3" in filter(Muscle.Dorsaux))
        assertFalse("c3" in filter())
    }

    // ─── Exercices d'un muscle (sheet « Muscle », § 15.17.7) ────────────────────

    @Test
    fun `les exercices d'un muscle, principaux puis secondaires, perso compris, sans les archivés`() {
        val uses = MuscleRules.usesOf(Muscle.Fessiers, definitions)
        assertEquals(
            listOf("deadlift", "squat", "lunges", "abductorpress", "c1", "legpress", "wallsit", "sideplank", "treadmill", "bike"),
            uses.map { it.definition.id },
        )
        assertEquals(List(5) { MuscleRole.Primary } + List(5) { MuscleRole.Secondary }, uses.map { it.role })
        assertTrue(MuscleRules.usesOf(Muscle.Dorsaux, definitions).none { it.definition.id == "c3" })
    }

    @Test
    fun `le rôle d'un muscle pour un exercice`() {
        val bench = definitions.first { it.id == "bench" }
        assertEquals(MuscleRole.Primary, bench.roleOf(Muscle.Pectoraux))
        assertEquals(MuscleRole.Secondary, bench.roleOf(Muscle.Triceps))
        assertNull(bench.roleOf(Muscle.Mollets))
        assertFalse(definitions.first { it.id == "c2" }.hasMuscles)
        assertTrue(bench.hasMuscles)
    }

    // ─── Muscles d'un exercice perso (§ 15.17.3, § 15.17.8) ─────────────────────

    @Test
    fun `les muscles sont remis dans l'ordre de la liste, sans doublon, un muscle des deux reste principal`() {
        val selection = MuscleSelection.of(
            primary = listOf(Muscle.Triceps, Muscle.Pectoraux, Muscle.Triceps),
            secondary = listOf(Muscle.Pectoraux, Muscle.EpaulesAvant, Muscle.Trapezes),
        )
        assertEquals(listOf(Muscle.Pectoraux, Muscle.Triceps), selection.primary)
        assertEquals(listOf(Muscle.Trapezes, Muscle.EpaulesAvant), selection.secondary)
        val exercise = noMuscles.copy(primaryMuscles = listOf(Muscle.Biceps, Muscle.Biceps), secondaryMuscles = listOf(Muscle.Biceps))
        assertEquals(listOf(Muscle.Biceps), MuscleRules.normalized(exercise).primaryMuscles)
        assertEquals(emptyList<Muscle>(), MuscleRules.normalized(exercise).secondaryMuscles)
    }

    @Test
    fun `dans le formulaire un muscle choisi dans une liste quitte l'autre`() {
        var selection = MuscleSelection()
        assertTrue(selection.isEmpty)
        selection = selection.togglePrimary(Muscle.Pectoraux).toggleSecondary(Muscle.Triceps)
        assertEquals(listOf(Muscle.Pectoraux), selection.primary)
        assertEquals(listOf(Muscle.Triceps), selection.secondary)
        selection = selection.toggleSecondary(Muscle.Pectoraux)
        assertEquals(emptyList<Muscle>(), selection.primary)
        assertEquals(listOf(Muscle.Pectoraux, Muscle.Triceps), selection.secondary)
        selection = selection.togglePrimary(Muscle.Triceps)
        assertEquals(listOf(Muscle.Triceps), selection.primary)
        assertEquals(listOf(Muscle.Pectoraux), selection.secondary)
        // Toucher un muscle choisi le retire.
        selection = selection.togglePrimary(Muscle.Triceps).toggleSecondary(Muscle.Pectoraux)
        assertTrue(selection.isEmpty)
    }

    @Test
    fun `des secondaires sans principal sont acceptés`() {
        val selection = MuscleSelection().toggleSecondary(Muscle.Obliques)
        assertFalse(selection.isEmpty)
        assertEquals(emptyList<Muscle>(), selection.primary)
    }
}
