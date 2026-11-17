package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RemovedWorkoutType
import com.musclenote.android.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * « Mes types » (SPEC § 15.19.3, § 15.19.4, 🔄 D45) : noms personnalisés déjà utilisés, calculés à partir
 * des séances terminées, et types retirés. Historiques triés du plus récent au plus ancien, comme le dépôt.
 */
class WorkoutTypeRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private var nextId = 0

    /** Séance terminée le [day] (novembre 2026) à 18 h à Paris. */
    private fun session(day: Int, name: String, preset: PresetId? = null) = Workout(
        id = "w${++nextId}",
        name = name,
        preset = preset,
        date = "2026-11-${day.toString().padStart(2, '0')}T17:00:00.000Z",
        bodyWeight = 80.0,
        exercises = emptyList(),
        durationMinutes = 60,
    )

    private fun history(vararg workouts: Workout) = workouts.sortedByDescending { it.date }

    private fun myTypes(history: List<Workout>, removed: List<RemovedWorkoutType> = emptyList()) =
        WorkoutTypeRules.myTypes(history, removed, zone)

    @Test
    fun `les noms perso viennent des séances, du plus récent au plus ancien`() {
        val workouts = history(
            session(3, "Épaules / Mollets"),
            session(10, "Bras / Abdos"),
            session(5, "Push", PresetId.Push),
            session(7, "Dos / Biceps"),
        )
        assertEquals(listOf("Bras / Abdos", "Dos / Biceps", "Épaules / Mollets"), myTypes(workouts))
    }

    @Test
    fun `huit types au plus, les plus récents`() {
        val workouts = history(*(1..10).map { session(it, "Type $it") }.toTypedArray())
        assertEquals((10 downTo 3).map { "Type $it" }, myTypes(workouts))
        assertEquals(8, WorkoutTypeRules.MAX_MY_TYPES)
    }

    @Test
    fun `casse et espaces en trop donnent une seule puce, à la forme la plus récente`() {
        val workouts = history(
            session(2, "bras / abdos"),
            session(9, "Bras  /  Abdos "),
            session(6, "BRAS / ABDOS"),
            session(4, "Dos"),
        )
        // « Dos » est un préréglage : il n'est pas un type perso.
        assertEquals(listOf("Bras  /  Abdos"), myTypes(workouts))
    }

    @Test
    fun `un nom égal à un préréglage, FR ou EN, n'apparaît pas en double`() {
        val workouts = history(
            session(10, "haut du corps"),
            session(9, "Lower Body"),
            session(8, "abs"),
            session(7, " Fessiers "),
            session(6, "push"),
            session(5, "Legs (split)"),
            session(4, "Mollets"),
        )
        assertEquals(listOf("Mollets"), myTypes(workouts))
    }

    @Test
    fun `un nom vide n'est pas un type`() {
        assertEquals(emptyList<String>(), myTypes(history(session(4, ""), session(3, "   "))))
        assertEquals(emptyList<String>(), myTypes(emptyList()))
    }

    @Test
    fun `un type retiré disparaît, et revient avec une séance commencée après le retrait`() {
        val old = history(session(3, "Bras / Abdos"), session(5, "Mollets"))
        val removedAt = Instant.parse("2026-11-06T09:00:00Z")
        // Retiré sous une autre forme : même nom normalisé.
        val removed = WorkoutTypeRules.remove(emptyList(), "bras  /  ABDOS", removedAt)
        assertEquals(listOf("Mollets"), myTypes(old, removed))

        // Séance du même nom commencée avant le retrait (renommée, par exemple) : toujours masqué.
        assertEquals(listOf("Mollets"), myTypes(history(*(old + session(6, "Bras / Abdos").copy(date = "2026-11-06T08:59:00.000Z")).toTypedArray()), removed))

        // Réutilisé ensuite : il réapparaît, à sa nouvelle forme, en tête.
        val reused = history(*(old + session(12, "bras / abdos")).toTypedArray())
        assertEquals(listOf("bras / abdos", "Mollets"), myTypes(reused, removed))
    }

    @Test
    fun `retirer de nouveau un nom remplace son entrée`() {
        val first = WorkoutTypeRules.remove(emptyList(), "Bras / Abdos", Instant.parse("2026-11-06T09:00:00Z"))
        val again = WorkoutTypeRules.remove(first + RemovedWorkoutType("Mollets", "2026-11-01T10:00:00.000Z"), "BRAS / ABDOS", Instant.parse("2026-11-14T09:00:00Z"))
        assertEquals(
            listOf(RemovedWorkoutType("Mollets", "2026-11-01T10:00:00.000Z"), RemovedWorkoutType("BRAS / ABDOS", "2026-11-14T09:00:00.000Z")),
            again,
        )
        // La séance du 12, entre les deux retraits, ne le fait plus revenir.
        val workouts = history(session(12, "Bras / Abdos"))
        assertEquals(emptyList<String>(), myTypes(workouts, again))
    }

    @Test
    fun `la liste des types retirés relue écarte les entrées invalides et garde le retrait le plus récent`() {
        val read = listOf(
            RemovedWorkoutType("Bras / Abdos", "2026-11-06T09:00:00.000Z"),
            RemovedWorkoutType("  ", "2026-11-06T09:00:00.000Z"),
            RemovedWorkoutType("Mollets", "hier"),
            RemovedWorkoutType("bras  /  abdos", "2026-11-08T09:00:00.000Z"),
            RemovedWorkoutType("Dos / Biceps", "2026-11-02T09:00:00.000Z"),
            RemovedWorkoutType("BRAS / ABDOS", "2026-11-07T09:00:00.000Z"),
        )
        assertEquals(
            listOf(RemovedWorkoutType("bras  /  abdos", "2026-11-08T09:00:00.000Z"), RemovedWorkoutType("Dos / Biceps", "2026-11-02T09:00:00.000Z")),
            WorkoutTypeRules.normalized(read),
        )
        assertEquals(emptyList<RemovedWorkoutType>(), WorkoutTypeRules.normalized(emptyList()))
    }

    @Test
    fun `deux noms sont le même type sans casse, accents ni espaces en trop`() {
        assertTrue(WorkoutTypeRules.isSameType("Bras  /  Abdos", " bras / abdos "))
        assertTrue(WorkoutTypeRules.isSameType("Épaules / Mollets", "epaules / mollets"))
        assertTrue(WorkoutTypeRules.isSameType("Bras / Abdos", "bras / abdos"))
        assertFalse(WorkoutTypeRules.isSameType("Bras / Abdos", "Bras/Abdos"))
        assertFalse(WorkoutTypeRules.isSameType("", "  "))
    }
}
