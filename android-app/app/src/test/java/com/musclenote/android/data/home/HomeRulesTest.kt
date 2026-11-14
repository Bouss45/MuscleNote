package com.musclenote.android.data.home

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Accueil (SPEC § 15.8.2, § 15.8.3) : choix de la surface, type suggéré, semaine en cours. */
class HomeRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    /** Jeudi 12 novembre 2026, comme les aperçus. */
    private val today = LocalDate.of(2026, 11, 12)

    private var nextId = 0

    private fun exercise(id: String) = WorkoutExercise(id, sets = listOf(WorkoutSet("s${++nextId}", 8, 80.0, true)))

    /** Séance terminée le [day] à 18 h à Paris, de [exercises] exercices. */
    private fun session(
        day: String,
        preset: PresetId? = null,
        name: String = preset?.name.orEmpty(),
        exercises: Int = 4,
        hourUtc: Int = 17,
        id: String = "w${++nextId}",
    ) = Workout(
        id = id,
        name = name,
        preset = preset,
        date = "${day}T${hourUtc.toString().padStart(2, '0')}:00:00.000Z",
        bodyWeight = 80.0,
        exercises = (1..exercises).map { exercise("e$it") },
        durationMinutes = 60,
    )

    /** Historique trié du plus récent au plus ancien, comme le dépôt. */
    private fun history(vararg workouts: Workout) = workouts.sortedByDescending { it.date }

    /** Six semaines de Push / Pull / Legs, trois séances par semaine, la dernière Pull hier (mercredi 11). */
    private fun ppl(): List<Workout> {
        val presets = listOf(PresetId.Push, PresetId.Legs, PresetId.Pull)
        val workouts = mutableListOf<Workout>()
        for (week in 0 until 6) {
            val monday = LocalDate.of(2026, 11, 9).minusWeeks(week.toLong())
            // Lundi Legs, mercredi Pull, et Push le jeudi de la semaine précédente.
            workouts += session(monday.toString(), PresetId.Legs, exercises = 6)
            workouts += session(monday.plusDays(2).toString(), PresetId.Pull, exercises = 5)
            workouts += session(monday.minusDays(4).toString(), PresetId.Push, exercises = 5)
        }
        return history(*workouts.toTypedArray()).filter { it.date < "2026-11-12" }
    }

    // ─── Surface ──────────────────────────────────────────────────────────────────

    @Test
    fun `une séance en cours prend la surface, sinon la prochaine séance`() {
        val current = session("2026-11-12", PresetId.Push, hourUtc = 16)
        val live = HomeRules.highlight(current, ppl(), today, zone)
        assertTrue(live is HomeHighlight.Live)
        assertSame(current, (live as HomeHighlight.Live).workout)

        assertTrue(HomeRules.highlight(null, ppl(), today, zone) is HomeHighlight.Next)
        // Même sans aucun historique.
        assertTrue(HomeRules.highlight(current, emptyList(), today, zone) is HomeHighlight.Live)
        assertEquals(HomeHighlight.Next(emptyList()), HomeRules.highlight(null, emptyList(), today, zone))
    }

    // ─── Type suggéré ─────────────────────────────────────────────────────────────

    @Test
    fun `dans une rotation, le type suggéré est celui fait le moins récemment`() {
        val types = HomeRules.regularTypes(ppl(), today, zone)
        assertEquals(listOf("preset:push", "preset:legs", "preset:pull"), types.map { it.key })
        val push = types.first()
        assertEquals(PresetId.Push, push.preset)
        // Dernier Push le jeudi 5 novembre : 7 jours.
        assertEquals(7, push.daysSince)
        assertEquals(5, push.exerciseCount)
        assertEquals("2026-11-05T17:00:00.000Z", push.lastWorkout.date)
        assertEquals(1, types.last().daysSince)
    }

    @Test
    fun `un type occasionnel n'est pas un type régulier`() {
        val workouts = history(
            session("2026-11-11", PresetId.Push), session("2026-11-09", PresetId.Push), session("2026-11-06", PresetId.Push),
            session("2026-11-04", PresetId.Push), session("2026-11-02", PresetId.Push), session("2026-10-30", PresetId.Push),
            // Deux Cardio : moins de la moitié des 6 Push.
            session("2026-11-10", PresetId.Cardio), session("2026-10-20", PresetId.Cardio),
            // Un seul Full Body : jamais régulier.
            session("2026-10-01", PresetId.FullBody),
        )
        assertEquals(listOf("preset:push"), HomeRules.regularTypes(workouts, today, zone).map { it.key })
    }

    @Test
    fun `au moins deux séances, et la moitié de la plus fréquente`() {
        // Push 4, Pull 2 : 2 × 2 ≥ 4, Pull est régulier.
        val workouts = history(
            session("2026-11-11", PresetId.Push), session("2026-11-08", PresetId.Push),
            session("2026-11-04", PresetId.Push), session("2026-11-01", PresetId.Push),
            session("2026-11-06", PresetId.Pull), session("2026-10-28", PresetId.Pull),
        )
        assertEquals(listOf("preset:pull", "preset:push"), HomeRules.regularTypes(workouts, today, zone).map { it.key })
    }

    @Test
    fun `la fenêtre couvre les 42 derniers jours, aujourd'hui compris`() {
        // today − 41 = 2 octobre : compté ; today − 42 = 1er octobre : ignoré.
        val inWindow = history(session("2026-11-05", PresetId.Legs), session("2026-10-02", PresetId.Legs))
        assertEquals(listOf("preset:legs"), HomeRules.regularTypes(inWindow, today, zone).map { it.key })

        val outOfWindow = history(session("2026-11-05", PresetId.Legs), session("2026-10-01", PresetId.Legs))
        assertTrue(HomeRules.regularTypes(outOfWindow, today, zone).isEmpty())
    }

    @Test
    fun `sans type régulier, pas de suggestion`() {
        val workouts = history(session("2026-11-10", PresetId.Push), session("2026-11-08", PresetId.Pull), session("2026-11-05", PresetId.Legs))
        assertEquals(HomeHighlight.Next(emptyList()), HomeRules.highlight(null, workouts, today, zone))
    }

    @Test
    fun `trois types au plus`() {
        val workouts = history(
            session("2026-11-11", PresetId.Pecs), session("2026-11-04", PresetId.Pecs),
            session("2026-11-10", PresetId.Dos), session("2026-11-03", PresetId.Dos),
            session("2026-11-09", PresetId.Shoulders), session("2026-11-02", PresetId.Shoulders),
            session("2026-11-06", PresetId.LegsSplit), session("2026-10-30", PresetId.LegsSplit),
            session("2026-11-05", PresetId.Arms), session("2026-10-29", PresetId.Arms),
        )
        assertEquals(
            listOf("preset:arms", "preset:legsSplit", "preset:shoulders"),
            HomeRules.suggestedTypes(workouts, today, zone).map { it.key },
        )
        assertEquals(5, HomeRules.regularTypes(workouts, today, zone).size)
    }

    @Test
    fun `un nom perso est regroupé par sa clé et repris de sa séance la plus récente`() {
        val workouts = history(
            session("2026-11-10", name = "épaules / mollets"),
            session("2026-11-03", name = "Epaules / Mollets "),
        )
        val type = HomeRules.regularTypes(workouts, today, zone).single()
        assertEquals("name:epaules / mollets", type.key)
        assertNull(type.preset)
        assertEquals("épaules / mollets", type.lastWorkout.name)
        assertEquals(2, type.count)
    }

    @Test
    fun `à égalité de dernière séance, le plus fréquent puis l'ordre de la clé`() {
        val workouts = history(
            session("2026-11-10", PresetId.Push, id = "push-1"), session("2026-11-10", PresetId.Pull, id = "pull-1"),
            session("2026-11-03", PresetId.Pull), session("2026-10-27", PresetId.Pull), session("2026-11-03", PresetId.Push),
            session("2026-11-10", PresetId.Legs), session("2026-11-03", PresetId.Legs),
        )
        // Pull 3 séances, puis Legs et Push (2 chacun) dans l'ordre de la clé.
        assertEquals(listOf("preset:pull", "preset:legs", "preset:push"), HomeRules.regularTypes(workouts, today, zone).map { it.key })
    }

    @Test
    fun `les jours écoulés se comptent en jours locaux`() {
        // Mercredi 11 à 23 h à Paris (22 h UTC) : hier.
        val yesterday = history(session("2026-11-11", PresetId.Push, hourUtc = 22), session("2026-11-04", PresetId.Push))
        assertEquals(1, HomeRules.regularTypes(yesterday, today, zone).single().daysSince)
        // Mercredi 11 à 23 h UTC = jeudi 12 à minuit à Paris : aujourd'hui.
        val sameDay = history(session("2026-11-11", PresetId.Push, hourUtc = 23), session("2026-11-04", PresetId.Push))
        assertEquals(0, HomeRules.regularTypes(sameDay, today, zone).single().daysSince)
    }

    // ─── Cette semaine ────────────────────────────────────────────────────────────

    @Test
    fun `cette semaine - séances de la semaine, série en vie, record`() {
        val week = HomeRules.thisWeek(ppl(), today, zone)
        // Lundi 9 (Legs) et mercredi 11 (Pull) ; le Push suivant serait vendredi.
        assertEquals(2, week.sessions)
        // Cinq semaines passées validées (3 séances chacune), la semaine en cours pas encore.
        assertEquals(5, week.streakWeeks)
        assertEquals(5, week.bestWeeks)
        assertEquals(8, week.bars.size)
        assertEquals(2, week.bars.last().count)
    }

    @Test
    fun `une série cassée affiche 0 semaine en cours, mais garde son record`() {
        val workouts = history(
            // Trois semaines validées en septembre, puis plus rien jusqu'à cette semaine.
            session("2026-09-07", PresetId.Push), session("2026-09-08", PresetId.Pull), session("2026-09-09", PresetId.Legs),
            session("2026-09-14", PresetId.Push), session("2026-09-15", PresetId.Pull), session("2026-09-16", PresetId.Legs),
            session("2026-09-21", PresetId.Push), session("2026-09-22", PresetId.Pull), session("2026-09-23", PresetId.Legs),
            session("2026-11-10", PresetId.Push),
        )
        val week = HomeRules.thisWeek(workouts, today, zone)
        assertEquals(1, week.sessions)
        assertEquals(0, week.streakWeeks)
        assertEquals(3, week.bestWeeks)
    }
}
