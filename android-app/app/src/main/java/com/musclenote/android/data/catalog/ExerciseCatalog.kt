package com.musclenote.android.data.catalog

import androidx.annotation.StringRes
import com.musclenote.android.R
import com.musclenote.android.data.model.MuscleGroup

/** Exercice intégré : jamais stocké, nom affiché tiré de la chaîne [nameRes] (SPEC § 6.1). */
data class BuiltInExercise(
    val id: String,
    val muscleGroup: MuscleGroup,
    @param:StringRes val nameRes: Int,
)

/**
 * Catalogue des 42 exercices intégrés, dans l'ordre d'affichage. ⚠️ Les ids sont figés : les
 * séances sauvegardées les référencent (fautes comprises : `calfraize`, `legrise`).
 * « Perso ou intégré » se décide uniquement par l'appartenance de l'id à ce catalogue (§ 3.9).
 */
object ExerciseCatalog {
    val all: List<BuiltInExercise> = listOf(
        BuiltInExercise("deadlift", MuscleGroup.Dos, R.string.exercise_deadlift),
        BuiltInExercise("pullup", MuscleGroup.Dos, R.string.exercise_pullup),
        BuiltInExercise("row", MuscleGroup.Dos, R.string.exercise_row),
        BuiltInExercise("latpull", MuscleGroup.Dos, R.string.exercise_latpull),
        BuiltInExercise("tiragebucheron", MuscleGroup.Dos, R.string.exercise_tiragebucheron),
        BuiltInExercise("tirageh", MuscleGroup.Dos, R.string.exercise_tirageh),
        BuiltInExercise("bench", MuscleGroup.Pecs, R.string.exercise_bench),
        BuiltInExercise("incline", MuscleGroup.Pecs, R.string.exercise_incline),
        BuiltInExercise("fly", MuscleGroup.Pecs, R.string.exercise_fly),
        BuiltInExercise("dips", MuscleGroup.Pecs, R.string.exercise_dips),
        BuiltInExercise("dbench", MuscleGroup.Pecs, R.string.exercise_dbench),
        BuiltInExercise("dincline", MuscleGroup.Pecs, R.string.exercise_dincline),
        BuiltInExercise("pecfly", MuscleGroup.Pecs, R.string.exercise_pecfly),
        BuiltInExercise("curl", MuscleGroup.Biceps, R.string.exercise_curl),
        BuiltInExercise("hammercurl", MuscleGroup.Biceps, R.string.exercise_hammercurl),
        BuiltInExercise("concentcurl", MuscleGroup.Biceps, R.string.exercise_concentcurl),
        BuiltInExercise("pushdown", MuscleGroup.Triceps, R.string.exercise_pushdown),
        BuiltInExercise("overheadtri", MuscleGroup.Triceps, R.string.exercise_overheadtri),
        BuiltInExercise("skullcrusher", MuscleGroup.Triceps, R.string.exercise_skullcrusher),
        BuiltInExercise("barfront", MuscleGroup.Triceps, R.string.exercise_barfront),
        BuiltInExercise("squat", MuscleGroup.Jambes, R.string.exercise_squat),
        BuiltInExercise("legpress", MuscleGroup.Jambes, R.string.exercise_legpress),
        BuiltInExercise("legcurl", MuscleGroup.Jambes, R.string.exercise_legcurl),
        BuiltInExercise("legext", MuscleGroup.Jambes, R.string.exercise_legext),
        BuiltInExercise("lunges", MuscleGroup.Jambes, R.string.exercise_lunges),
        BuiltInExercise("calfraize", MuscleGroup.Jambes, R.string.exercise_calfraize),
        BuiltInExercise("adductorpress", MuscleGroup.Jambes, R.string.exercise_adductorpress),
        BuiltInExercise("abductorpress", MuscleGroup.Jambes, R.string.exercise_abductorpress),
        BuiltInExercise("calfsit", MuscleGroup.Jambes, R.string.exercise_calfsit),
        BuiltInExercise("ohp", MuscleGroup.Epaules, R.string.exercise_ohp),
        BuiltInExercise("lateralraise", MuscleGroup.Epaules, R.string.exercise_lateralraise),
        BuiltInExercise("frontraise", MuscleGroup.Epaules, R.string.exercise_frontraise),
        BuiltInExercise("crunch", MuscleGroup.Abdos, R.string.exercise_crunch),
        BuiltInExercise("plank", MuscleGroup.Abdos, R.string.exercise_plank),
        BuiltInExercise("legrise", MuscleGroup.Abdos, R.string.exercise_legrise),
        BuiltInExercise("treadmill", MuscleGroup.Cardio, R.string.exercise_treadmill),
        BuiltInExercise("bike", MuscleGroup.Cardio, R.string.exercise_bike),
        BuiltInExercise("wristcurl", MuscleGroup.AvantBras, R.string.exercise_wristcurl),
        BuiltInExercise("wristext", MuscleGroup.AvantBras, R.string.exercise_wristext),
        BuiltInExercise("reversecurl", MuscleGroup.AvantBras, R.string.exercise_reversecurl),
        BuiltInExercise("pinchgrip", MuscleGroup.AvantBras, R.string.exercise_pinchgrip),
        BuiltInExercise("farmerwalk", MuscleGroup.AvantBras, R.string.exercise_farmerwalk),
    )

    private val byId: Map<String, BuiltInExercise> = all.associateBy { it.id }

    fun isBuiltIn(id: String): Boolean = id in byId

    fun find(id: String): BuiltInExercise? = byId[id]
}
