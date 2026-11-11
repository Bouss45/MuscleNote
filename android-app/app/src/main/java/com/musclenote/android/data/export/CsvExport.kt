package com.musclenote.android.data.export

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.isDayKey
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.data.workout.WorkoutStats
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Séparateur et décimales selon la langue de l'app (§ 8) : FR → `;` et virgule décimale (Excel
 * français), EN → `,` et point décimal. Jamais de séparateur de milliers.
 */
data class CsvFormat(val separator: Char, val decimalComma: Boolean) {
    companion object {
        val FRENCH = CsvFormat(';', decimalComma = true)
        val ENGLISH = CsvFormat(',', decimalComma = false)
    }
}

/** Colonnes des fichiers CSV ; leurs en-têtes traduits viennent des chaînes `excel_col_*`. */
enum class CsvColumn {
    Date, Time, SessionName, Session, Duration, BodyWeight, Exercises, TotalSets, TotalVolume,
    Exercise, MuscleGroup, SetNumber, Reps, Weight, Distance, Speed, Incline, Calories, Water,
}

/**
 * Textes de l'export dans la langue de l'app, fournis par l'appelant (ressources Android) : la
 * génération reste testable sur la JVM.
 */
class CsvTexts(
    val header: (CsvColumn) -> String,
    /** Nom affiché d'une séance (§ 3.1). */
    val workoutName: (Workout) -> String,
    /** Nom affiché d'un exercice, ou son id s'il est inconnu (§ 10.5). */
    val exerciseName: (exerciseId: String) -> String,
    /** Groupe musculaire traduit, vide si l'exercice est inconnu. */
    val muscleGroupName: (exerciseId: String) -> String,
    val isCardio: (exerciseId: String) -> Boolean,
)

/** Un fichier de l'export : nom et contenu complet (BOM compris). */
data class CsvFile(val name: String, val content: String)

/**
 * Export CSV (SPEC § 8, 🔄 D1) : jusqu'à 4 fichiers lisibles directement par Excel et Google
 * Sheets, seulement pour les tables non vides. UTF-8 avec BOM, fins de ligne CRLF, guillemets
 * RFC 4180, dates `AAAA-MM-JJ` et heures `HH:mm` locales, cellule vide = champ vide. Seules les
 * **séries validées** sont exportées et comptées (🔄 D15).
 */
object CsvExport {
    const val BOM = '﻿'
    const val LINE_END = "\r\n"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Les fichiers non vides, dans l'ordre sessions, sets, bodyweight, nutrition ; liste vide → rien à exporter. */
    fun export(
        workouts: List<Workout>,
        bodyWeights: List<BodyWeightEntry>,
        tracker: DailyTracker,
        texts: CsvTexts,
        format: CsvFormat,
        today: LocalDate,
        zone: ZoneId,
    ): List<CsvFile> = listOfNotNull(
        sessions(workouts, texts, format, zone)?.let { CsvFile("musclenote_sessions_$today.csv", it) },
        sets(workouts, texts, format, zone)?.let { CsvFile("musclenote_sets_$today.csv", it) },
        bodyWeight(bodyWeights, texts, format, zone)?.let { CsvFile("musclenote_bodyweight_$today.csv", it) },
        nutrition(tracker, texts, format)?.let { CsvFile("musclenote_nutrition_$today.csv", it) },
    )

    /** Une ligne par séance terminée, la plus récente d'abord. */
    fun sessions(workouts: List<Workout>, texts: CsvTexts, format: CsvFormat, zone: ZoneId): String? {
        if (workouts.isEmpty()) return null
        val header = listOf(
            CsvColumn.Date, CsvColumn.Time, CsvColumn.SessionName, CsvColumn.Duration, CsvColumn.BodyWeight,
            CsvColumn.Exercises, CsvColumn.TotalSets, CsvColumn.TotalVolume,
        )
        val rows = workouts.map { workout ->
            val start = parseInstantOrNull(workout.date, zone)?.atZone(zone)
            listOf(
                start?.toLocalDate()?.toString().orEmpty(),
                start?.format(timeFormatter).orEmpty(),
                texts.workoutName(workout),
                workout.durationMinutes?.toString().orEmpty(),
                if (workout.bodyWeight == 0.0) "" else number(workout.bodyWeight, format),
                WorkoutStats.exercisesWithCompletedSets(workout).toString(),
                WorkoutStats.completedSetCount(workout).toString(),
                number(WorkoutStats.volume(workout), format),
            )
        }
        return table(header.map(texts.header), rows, format)
    }

    /**
     * Une ligne par série validée : séances les plus récentes d'abord, puis ordre des exercices et
     * des séries. Colonnes fixes : force → reps et poids (vides si 0) ; cardio → durée, distance,
     * vitesse, inclinaison. « Série n° » = rang de la série dans l'exercice, comme dans l'app.
     */
    fun sets(workouts: List<Workout>, texts: CsvTexts, format: CsvFormat, zone: ZoneId): String? {
        val header = listOf(
            CsvColumn.Date, CsvColumn.Session, CsvColumn.Exercise, CsvColumn.MuscleGroup, CsvColumn.SetNumber,
            CsvColumn.Reps, CsvColumn.Weight, CsvColumn.Duration, CsvColumn.Distance, CsvColumn.Speed, CsvColumn.Incline,
        )
        val rows = mutableListOf<List<String>>()
        for (workout in workouts) {
            val day = parseInstantOrNull(workout.date, zone)?.atZone(zone)?.toLocalDate()?.toString().orEmpty()
            val name = texts.workoutName(workout)
            for (exercise in workout.exercises) {
                val cardio = texts.isCardio(exercise.exerciseId)
                exercise.sets.forEachIndexed { index, set ->
                    if (!set.completed) return@forEachIndexed
                    rows += listOf(
                        day,
                        name,
                        texts.exerciseName(exercise.exerciseId),
                        texts.muscleGroupName(exercise.exerciseId),
                        (index + 1).toString(),
                        if (cardio || set.reps == 0) "" else set.reps.toString(),
                        if (cardio || set.weight == 0.0) "" else number(set.weight, format),
                        if (cardio) set.duration?.let { number(it, format) }.orEmpty() else "",
                        if (cardio) set.distance?.let { number(it, format) }.orEmpty() else "",
                        if (cardio) set.speed?.let { number(it, format) }.orEmpty() else "",
                        if (cardio) set.incline?.let { number(it, format) }.orEmpty() else "",
                    )
                }
            }
        }
        if (rows.isEmpty()) return null
        return table(header.map(texts.header), rows, format)
    }

    /** Pesées **manuelles**, la plus récente d'abord : Date | Poids (kg). */
    fun bodyWeight(bodyWeights: List<BodyWeightEntry>, texts: CsvTexts, format: CsvFormat, zone: ZoneId): String? {
        if (bodyWeights.isEmpty()) return null
        val rows = bodyWeights.map { entry ->
            listOf(
                parseInstantOrNull(entry.date, zone)?.atZone(zone)?.toLocalDate()?.toString().orEmpty(),
                number(entry.weight, format),
            )
        }
        return table(listOf(CsvColumn.Date, CsvColumn.Weight).map(texts.header), rows, format)
    }

    /**
     * Un jour par ligne, union des jours des deux journaux, ordre croissant, jours à 0 compris :
     * Date | Calories (kcal) | Eau (ml) ; valeur absente → 0.
     */
    fun nutrition(tracker: DailyTracker, texts: CsvTexts, format: CsvFormat): String? {
        val days = (tracker.caloriesLog.keys + tracker.waterLog.keys).filter(::isDayKey).sorted()
        if (days.isEmpty()) return null
        val rows = days.map { day ->
            listOf(day, (tracker.caloriesLog[day] ?: 0).toString(), (tracker.waterLog[day] ?: 0).toString())
        }
        return table(listOf(CsvColumn.Date, CsvColumn.Calories, CsvColumn.Water).map(texts.header), rows, format)
    }

    /** BOM, en-têtes puis lignes, chaque enregistrement terminé par CRLF. */
    fun table(header: List<String>, rows: List<List<String>>, format: CsvFormat): String = buildString {
        append(BOM)
        for (record in listOf(header) + rows) {
            record.joinTo(this, separator = format.separator.toString()) { field(it, format.separator) }
            append(LINE_END)
        }
    }

    /**
     * Champ RFC 4180 : entouré de `"` s'il contient le séparateur, `"`, CR ou LF ; les `"`
     * internes sont doublés.
     */
    fun field(value: String, separator: Char): String {
        val needsQuotes = value.any { it == separator || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /**
     * Nombre sans séparateur de milliers, au plus 2 décimales (demis vers le haut), sans zéros
     * inutiles (`80`, `62,5`) ; virgule décimale en français.
     */
    fun number(value: Double, format: CsvFormat): String {
        val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        val text = if (rounded.signum() == 0) "0" else rounded.stripTrailingZeros().toPlainString()
        return if (format.decimalComma) text.replace('.', ',') else text
    }
}
