package com.musclenote.android.ui.preview

import com.musclenote.android.data.backup.AutoBackupState
import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.LocalPreferences
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.Reminder
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.model.WorkoutExercise
import com.musclenote.android.data.model.WorkoutSet
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.formatInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Données d'exemple des aperçus : six semaines de séances Push / Pull / Legs avec progression,
 * pesées, compléments et suivi du jour, autour d'un instant fixe ([PreviewTime]) pour que les
 * rendus ne changent pas d'un jour à l'autre.
 */
object PreviewData {
    val zone: ZoneId = ZoneId.of("Europe/Paris")

    /** Jeudi 12 novembre 2026, 18 h 30 à Paris. */
    val now: Instant = Instant.parse("2026-11-12T17:30:00Z")
    val today: LocalDate = now.atZone(zone).toLocalDate()

    const val HIP_THRUST_ID = "1760000000000hipthrust"

    val customExercises = listOf(
        Exercise(id = HIP_THRUST_ID, name = "Hip thrust", muscleGroup = MuscleGroup.Jambes),
        Exercise(id = "1755000000000shrugarch", name = "Shrug", muscleGroup = MuscleGroup.Dos, archived = true),
    )

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    /** Force : [count] séries de [reps] à [weight] kg ; [completed] premières validées (toutes par défaut). */
    private fun strength(prefix: String, count: Int, reps: Int, weight: Double, completed: Int = count) =
        List(count) { i -> WorkoutSet(id = "$prefix-$i", reps = reps, weight = weight, completed = i < completed) }

    private fun cardio(prefix: String, minutes: Double, km: Double, speed: Double, incline: Double) = listOf(
        WorkoutSet(id = "$prefix-0", reps = 0, weight = 0.0, completed = true, duration = minutes, distance = km, speed = speed, incline = incline),
    )

    /** Arrondi au 1,25 kg (disques de 0,625 kg de chaque côté). */
    private fun plates(kg: Double) = (kg / 1.25).roundToInt() * 1.25

    private fun push(id: String, start: Instant, step: Int, bodyWeight: Double, minutes: Int) = Workout(
        id = id, name = "Push", preset = PresetId.Push, date = formatInstant(start), bodyWeight = bodyWeight, durationMinutes = minutes,
        exercises = listOf(
            WorkoutExercise("bench", note = if (step == 5) "Prise un peu plus serrée" else null, sets = strength("$id-bench", 4, 8, plates(72.5 + 1.25 * step))),
            WorkoutExercise("dincline", sets = strength("$id-dincline", 3, 10, 24.0 + (step / 2) * 2)),
            WorkoutExercise("ohp", sets = strength("$id-ohp", 3, 8, plates(42.5 + 1.25 * step))),
            WorkoutExercise("lateralraise", sets = strength("$id-lat", 3, 12, 10.0)),
            WorkoutExercise("pushdown", sets = strength("$id-push", 3, 12, 25.0 + 2.5 * (step / 3))),
        ),
    )

    private fun pull(id: String, start: Instant, step: Int, bodyWeight: Double, minutes: Int) = Workout(
        id = id, name = "Pull", preset = PresetId.Pull, date = formatInstant(start), bodyWeight = bodyWeight, durationMinutes = minutes,
        exercises = listOf(
            WorkoutExercise("deadlift", sets = strength("$id-dl", 3, 5, plates(115.0 + 2.5 * step))),
            WorkoutExercise("pullup", sets = strength("$id-pu", 4, 8, 0.0)),
            WorkoutExercise("row", sets = strength("$id-row", 4, 10, plates(57.5 + 1.25 * step))),
            WorkoutExercise("latpull", sets = strength("$id-lat", 3, 10, 55.0)),
            WorkoutExercise("hammercurl", sets = strength("$id-hc", 3, 10, 14.0)),
        ),
    )

    private fun legs(id: String, start: Instant, step: Int, bodyWeight: Double, minutes: Int) = Workout(
        id = id, name = "Legs", preset = PresetId.Legs, date = formatInstant(start), bodyWeight = bodyWeight, durationMinutes = minutes,
        exercises = listOf(
            WorkoutExercise("squat", sets = strength("$id-sq", 4, 6, plates(92.5 + 2.5 * step))),
            WorkoutExercise("legpress", sets = strength("$id-lp", 3, 10, 180.0 + 10 * step)),
            WorkoutExercise(HIP_THRUST_ID, sets = strength("$id-ht", 3, 10, 100.0 + 5 * step)),
            WorkoutExercise("legcurl", sets = strength("$id-lc", 3, 12, 40.0)),
            WorkoutExercise("calfraize", sets = strength("$id-cr", 4, 15, 60.0)),
            WorkoutExercise("treadmill", sets = cardio("$id-tm", 15.0, 2.4, 9.6, 2.0)),
        ),
    )

    /** Poids de corps du jour : légère baisse sur six semaines, de 79,4 à 77,9 kg. */
    private fun bodyWeightOn(day: LocalDate): Double {
        val daysBefore = today.toEpochDay() - day.toEpochDay()
        return ((77.9 + daysBefore * 0.036) * 10).roundToInt() / 10.0
    }

    /**
     * Séances terminées, de la plus récente à la plus ancienne : trois par semaine (lundi, mercredi,
     * vendredi) sur six semaines, sauf une semaine à deux séances ; la semaine en cours en a deux.
     */
    val workouts: List<Workout> = buildList {
        var step = 0
        for (weeksAgo in 5 downTo 0) {
            val monday = today.minusWeeks(weeksAgo.toLong()).with(java.time.DayOfWeek.MONDAY)
            val days = when (weeksAgo) {
                3 -> listOf(0L, 3L) // une semaine à deux séances (non validée)
                0 -> listOf(0L, 2L) // semaine en cours : lundi et mercredi
                else -> listOf(0L, 2L, 4L)
            }
            for ((index, offset) in days.withIndex()) {
                val day = monday.plusDays(offset)
                val start = at(day, 18, 5 + index * 10)
                val bodyWeight = bodyWeightOn(day)
                val minutes = 58 + (step * 7) % 23
                val id = "w-$day"
                add(
                    when (index) {
                        0 -> push(id, start, step, bodyWeight, minutes)
                        1 -> pull(id, start, step, bodyWeight, minutes)
                        else -> legs(id, start, step, bodyWeight, minutes)
                    },
                )
            }
            step++
        }
        // Séance au nom libre, sans préréglage.
        add(
            Workout(
                id = "w-custom", name = "Haut du corps", date = formatInstant(at(today.minusWeeks(5).minusDays(3), 10, 30)),
                bodyWeight = 79.6, durationMinutes = 47,
                exercises = listOf(
                    WorkoutExercise("bench", sets = strength("c-bench", 3, 10, 70.0)),
                    WorkoutExercise("row", sets = strength("c-row", 3, 10, 55.0)),
                    WorkoutExercise("curl", sets = strength("c-curl", 3, 10, 30.0, completed = 2)),
                ),
            ),
        )
    }.sortedByDescending { Instant.parse(it.date) }

    /** Séance en cours (écran de séance) : Push démarrée il y a 42 min, à moitié faite. */
    val currentWorkout = Workout(
        id = "w-current", name = "Push", preset = PresetId.Push, date = formatInstant(now.minusSeconds(42 * 60)), bodyWeight = 77.9,
        exercises = listOf(
            WorkoutExercise("bench", note = "Prise un peu plus serrée", sets = strength("cur-bench", 4, 8, 80.0, completed = 3)),
            WorkoutExercise("dincline", sets = strength("cur-dincline", 3, 10, 28.0, completed = 0)),
            WorkoutExercise("ohp", sets = strength("cur-ohp", 3, 8, 50.0, completed = 0)),
            WorkoutExercise("lateralraise", sets = strength("cur-lat", 3, 12, 10.0, completed = 0)),
        ),
    )

    /** Repos en cours : 1 min 35 restante sur 2 min 30. */
    val restTimer = RestTimerState(endsAt = now.toEpochMilli() + 95_000, total = 150, exerciseName = "Développé couché")

    /** Pesées du dimanche, à midi. */
    val bodyWeights: List<BodyWeightEntry> = (0 until 6).map { weeksAgo ->
        val sunday = today.minusWeeks(weeksAgo.toLong()).with(java.time.DayOfWeek.SUNDAY).minusWeeks(1)
        BodyWeightEntry(date = formatInstant(at(sunday, 12)), weight = bodyWeightOn(sunday) + 0.2)
    }

    val supplements = listOf(
        Supplement(
            id = "sup-creatine", name = "Créatine", dosage = "5", unit = "g", reminderEnabled = true,
            reminders = listOf(Reminder(id = "rem-creatine", hour = 8, minute = 0)),
        ),
        Supplement(id = "sup-whey", name = "Whey", dosage = "30", unit = "g", reminderEnabled = false, reminders = emptyList()),
        Supplement(
            id = "sup-vitd", name = "Vitamine D3", dosage = "1000", unit = "ui", reminderEnabled = true,
            reminders = listOf(Reminder(id = "rem-vitd-am", hour = 8, minute = 30), Reminder(id = "rem-vitd-pm", hour = 20, minute = 0)),
        ),
    )

    /** Créatine prise aujourd'hui. */
    val taken: Map<String, String> = mapOf("sup-creatine" to today.toString())

    /** Calories et eau des deux dernières semaines ; aujourd'hui en cours de journée. */
    val tracker: DailyTracker = run {
        val calories = LinkedHashMap<String, Int>()
        val water = LinkedHashMap<String, Int>()
        for (daysAgo in 13 downTo 1) {
            val day = today.minusDays(daysAgo.toLong()).toString()
            calories[day] = 2250 + (daysAgo * 137) % 600
            water[day] = 1750 + (daysAgo * 211) % 1000
        }
        calories[today.toString()] = 1850
        water[today.toString()] = 1500
        DailyTracker(
            calorieGoal = 2600,
            waterGoal = 2500,
            calorieGoalHistory = mapOf("0000-01-01" to 2400, today.minusDays(9).toString() to 2600),
            waterGoalHistory = mapOf("0000-01-01" to 2500),
            caloriesLog = calories,
            waterLog = water,
        )
    }

    val preferences = Preferences(restSeconds = 150, theme = ThemeMode.System)

    val localPreferences = LocalPreferences(migrateCardDismissed = true)

    /** Sauvegarde automatique activée, dernière sauvegarde ce matin. */
    val autoBackup = AutoBackupState(
        enabled = true,
        folderUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMuscleNote",
        folderName = "MuscleNote",
        lastSuccessAt = at(today, 9, 12).toEpochMilli(),
    )
}

/** Horloge des aperçus : toujours [PreviewData.now], à Paris. */
object PreviewTime : TimeSource {
    override fun now(): Instant = PreviewData.now
    override fun zone(): ZoneId = PreviewData.zone
}
