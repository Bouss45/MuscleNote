package com.musclenote.android.ui.settings

import android.content.Context
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.export.CsvColumn
import com.musclenote.android.data.export.CsvFormat
import com.musclenote.android.data.export.CsvTexts
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.util.parseInstantOrNull
import com.musclenote.android.ui.components.appFormattingLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Textes et format de l'export CSV (§ 8) dans la langue de l'app : [localized] est un contexte
 * déjà dans cette langue (`withAppLocale()`).
 */
object CsvExportTexts {

    /**
     * Langue des en-têtes (§ 3.14) : anglais → `,` et point ; sinon (français, ou autre langue
     * du système, affichée en français) → `;` et virgule.
     */
    fun format(localized: Context): CsvFormat =
        if (localized.resources.configuration.appFormattingLocale().language == "en") CsvFormat.ENGLISH else CsvFormat.FRENCH

    fun texts(localized: Context, customExercises: List<Exercise>, zone: ZoneId): CsvTexts {
        // Archivés compris : ils nomment encore l'historique (§ 3.9).
        val definitions = exerciseDefinitions(customExercises).associateBy { it.id }
        val locale = localized.resources.configuration.appFormattingLocale()
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return CsvTexts(
            header = { localized.getString(it.labelRes()) },
            workoutName = { workout ->
                // Nom affiché (§ 3.1) : préréglage traduit, nom saisi, sinon la date.
                workout.preset?.let { localized.getString(it.labelRes) }
                    ?: workout.name.ifBlank {
                        parseInstantOrNull(workout.date, zone)?.atZone(zone)?.format(dateFormatter).orEmpty()
                    }
            },
            exerciseName = { id ->
                val definition = definitions[id]
                when {
                    definition == null -> id
                    definition.nameRes != null -> localized.getString(definition.nameRes)
                    else -> definition.customName.orEmpty()
                }
            },
            muscleGroupName = { id -> definitions[id]?.muscleGroup?.let { localized.getString(it.labelRes) }.orEmpty() },
            isCardio = { id -> definitions[id]?.muscleGroup == MuscleGroup.Cardio },
        )
    }

    private fun CsvColumn.labelRes(): Int = when (this) {
        CsvColumn.Date -> R.string.excel_col_date
        CsvColumn.Time -> R.string.excel_col_time
        CsvColumn.SessionName -> R.string.excel_col_session_name
        CsvColumn.Session -> R.string.excel_col_session
        CsvColumn.Duration -> R.string.excel_col_duration
        CsvColumn.BodyWeight -> R.string.excel_col_body_weight
        CsvColumn.Exercises -> R.string.excel_col_exercises
        CsvColumn.TotalSets -> R.string.excel_col_total_sets
        CsvColumn.TotalVolume -> R.string.excel_col_total_volume
        CsvColumn.Exercise -> R.string.excel_col_exercise
        CsvColumn.MuscleGroup -> R.string.excel_col_muscle_group
        CsvColumn.SetNumber -> R.string.excel_col_set_number
        CsvColumn.Reps -> R.string.excel_col_reps
        CsvColumn.Weight -> R.string.excel_col_weight
        CsvColumn.Distance -> R.string.excel_col_distance
        CsvColumn.Speed -> R.string.excel_col_speed
        CsvColumn.Incline -> R.string.excel_col_incline
        CsvColumn.Calories -> R.string.excel_col_calories
        CsvColumn.Water -> R.string.excel_col_water
    }
}
