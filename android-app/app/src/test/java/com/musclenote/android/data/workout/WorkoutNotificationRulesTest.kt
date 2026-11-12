package com.musclenote.android.data.workout

import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Notification de séance (§ 15.5) : choix de la prochaine série et texte, avec les vrais textes
 * FR et EN de l'app.
 */
class WorkoutNotificationRulesTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val start = Instant.parse("2026-11-12T17:00:00Z")
    private val now = start.plusSeconds(25 * 60).toEpochMilli()

    private fun set(id: String, reps: Int = 8, weight: Double = 80.0, completed: Boolean = false, duration: Double? = null, distance: Double? = null) =
        WorkoutSet(id = id, reps = reps, weight = weight, completed = completed, duration = duration, distance = distance)

    private fun workout(vararg exercises: WorkoutExercise) = Workout(
        id = "w",
        name = "Push",
        preset = PresetId.Push,
        date = start.toString(),
        bodyWeight = 78.0,
        exercises = exercises.toList(),
    )

    private fun exercise(id: String, vararg sets: WorkoutSet) = WorkoutExercise(exerciseId = id, sets = sets.toList())

    private val names = mapOf(
        "bench" to NotificationExercise("Développé couché", cardio = false),
        "incline" to NotificationExercise("Développé incliné haltères", cardio = false),
        "pullup" to NotificationExercise("Tractions", cardio = false),
        "run" to NotificationExercise("Course", cardio = true),
    )

    /** Séance du signalement : 2 séries validées sur 4 au développé couché, puis l'incliné. */
    private val push = workout(
        exercise("bench", set("b1", completed = true), set("b2", completed = true), set("b3"), set("b4", weight = 82.5)),
        exercise("incline", set("i1", reps = 10, weight = 28.0), set("i2", reps = 10, weight = 28.0)),
    )

    private fun content(workout: Workout, rest: RestTimerState? = null, texts: WorkoutNotificationTexts = FR, exercises: Map<String, NotificationExercise> = names) =
        WorkoutNotificationRules.content(workout, "Push", rest, now, zone, { exercises[it] }, texts)

    // ─── Prochaine série ─────────────────────────────────────────────────────────

    @Test
    fun `la prochaine série suit la dernière série validée`() {
        val next = WorkoutNotificationRules.nextSet(push)!!
        assertEquals("b3", next.set.id)
        assertEquals("bench", next.exercise.exerciseId)
        assertEquals(3, next.number)
    }

    @Test
    fun `une série sautée plus haut ne ramène pas en arrière`() {
        // Développé couché arrêté à 1/4, incliné commencé : on continue l'incliné.
        val moved = workout(
            exercise("bench", set("b1", completed = true), set("b2"), set("b3")),
            exercise("incline", set("i1", completed = true), set("i2")),
        )
        assertEquals("i2", WorkoutNotificationRules.nextSet(moved)!!.set.id)
    }

    @Test
    fun `sans série après la dernière validée, on reprend la première non validée`() {
        val tail = workout(
            exercise("bench", set("b1"), set("b2", completed = true)),
            exercise("incline", set("i1", completed = true)),
        )
        val next = WorkoutNotificationRules.nextSet(tail)!!
        assertEquals("b1", next.set.id)
        assertEquals(1, next.number)
    }

    @Test
    fun `rien de validé, c'est la première série, tout validé ou vide il n'y en a pas`() {
        val fresh = workout(exercise("bench", set("b1"), set("b2")))
        assertEquals("b1", WorkoutNotificationRules.nextSet(fresh)!!.set.id)
        assertNull(WorkoutNotificationRules.nextSet(workout(exercise("bench", set("b1", completed = true)))))
        assertNull(WorkoutNotificationRules.nextSet(workout()))
        assertNull(WorkoutNotificationRules.nextSet(workout(exercise("bench"))))
    }

    @Test
    fun `un exercice absent de l'écran de séance est ignoré`() {
        val withGhost = workout(exercise("ghost", set("g1")), exercise("bench", set("b1")))
        assertEquals("b1", WorkoutNotificationRules.nextSet(withGhost) { it != "ghost" }!!.set.id)
        assertEquals("Développé couché · Série 1 · 8 × 80 kg", content(withGhost).text)
    }

    // ─── Texte et actions ────────────────────────────────────────────────────────

    @Test
    fun `hors repos - séance, chrono de la séance, exercice et prochaine série, Valider la série`() {
        val c = content(push)
        assertEquals("Push", c.title)
        assertEquals("Développé couché · Série 3 · 8 × 80 kg", c.text)
        assertEquals(start.toEpochMilli(), c.chronometerBase)
        assertFalse(c.countDown)
        assertEquals("Série 3", c.chipText)
        assertEquals(listOf(WorkoutAction.CompleteSet), c.actions)
    }

    @Test
    fun `pendant le repos - compte à rebours, prochaine série, moins 30 s, plus 30 s et Passer`() {
        val rest = RestTimerState(endsAt = now + 95_000, total = 150, exerciseName = "Développé couché")
        val c = content(push, rest)
        assertEquals("Push · Repos", c.title)
        assertEquals("Prochaine : Développé couché · Série 3 · 8 × 80 kg", c.text)
        assertEquals(now + 95_000, c.chronometerBase)
        assertTrue(c.countDown)
        assertNull(c.chipText)
        assertEquals(listOf(WorkoutAction.RestMinus, WorkoutAction.RestPlus, WorkoutAction.SkipRest), c.actions)
    }

    @Test
    fun `un repos fini dont l'alarme n'est pas encore passée ne compte plus`() {
        val finished = RestTimerState(endsAt = now, total = 150, exerciseName = "Développé couché")
        assertEquals(content(push), content(push, finished))
    }

    @Test
    fun `tout validé - plus de série à annoncer ni à valider`() {
        val done = workout(exercise("bench", set("b1", completed = true)))
        val c = content(done)
        assertEquals("Toutes les séries sont validées", c.text)
        assertTrue(c.actions.isEmpty())
        assertNull(c.chipText)
        val resting = content(done, RestTimerState(endsAt = now + 60_000, total = 90, exerciseName = "x"))
        assertEquals("Toutes les séries sont validées", resting.text)
        assertEquals(3, resting.actions.size)
    }

    @Test
    fun `poids décimal à la locale, poids nul en répétitions, cardio en durée et distance`() {
        val decimal = workout(exercise("bench", set("b1", completed = true), set("b2", weight = 82.5)))
        assertEquals("Développé couché · Série 2 · 8 × 82,5 kg", content(decimal).text)
        assertEquals("Bench press · Set 2 · 8 × 82.5 kg", content(decimal, texts = EN, exercises = mapOf("bench" to NotificationExercise("Bench press", false))).text)

        assertEquals("Tractions · Série 1 · 8 reps", content(workout(exercise("pullup", set("p1", weight = 0.0)))).text)

        val run = workout(exercise("run", set("r1", reps = 0, weight = 0.0, duration = 30.0, distance = 5.2)))
        assertEquals("Course · Série 1 · 30 min · 5,2 km", content(run).text)
        val runEmpty = workout(exercise("run", set("r1", reps = 0, weight = 0.0)))
        assertEquals("Course · Série 1", content(runEmpty).text)
    }

    @Test
    fun `textes anglais`() {
        val en = mapOf("bench" to NotificationExercise("Bench press", false), "incline" to NotificationExercise("Incline dumbbell press", false))
        assertEquals("Bench press · Set 3 · 8 × 80 kg", content(push, texts = EN, exercises = en).text)
        assertEquals("Set 3", content(push, texts = EN, exercises = en).chipText)
        val rest = RestTimerState(endsAt = now + 30_000, total = 90, exerciseName = "Bench press")
        assertEquals("Push · Rest", content(push, rest, EN, en).title)
        assertEquals("Next: Bench press · Set 3 · 8 × 80 kg", content(push, rest, EN, en).text)
        assertEquals("All sets are done", content(workout(), texts = EN).text)
    }

    private companion object {
        val FR = texts("src/main/res/values/strings.xml", Locale.FRENCH)
        val EN = texts("src/main/res/values-en/strings.xml", Locale.ENGLISH)

        /** Textes de la notification lus dans les ressources de l'app. */
        fun texts(path: String, locale: Locale): WorkoutNotificationTexts {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).getElementsByTagName("string")
            val strings = (0 until nodes.length).associate { i ->
                val node = nodes.item(i)
                node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
            }
            return WorkoutNotificationTexts(
                locale = locale,
                restTitle = strings.getValue("workout_notif_rest_title"),
                setWithDetail = strings.getValue("workout_notif_set"),
                setOnly = strings.getValue("workout_notif_set_only"),
                strength = strings.getValue("workout_notif_strength"),
                repsOnly = strings.getValue("workout_notif_reps"),
                minutes = strings.getValue("workout_notif_minutes"),
                kilometers = strings.getValue("workout_notif_km"),
                next = strings.getValue("workout_notif_next"),
                allDone = strings.getValue("workout_notif_all_done"),
            )
        }
    }
}
