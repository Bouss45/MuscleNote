package com.musclenote.android.data.catalog

import androidx.annotation.StringRes
import com.musclenote.android.R
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.data.model.Muscle.Abdominaux
import com.musclenote.android.data.model.Muscle.Adducteurs
import com.musclenote.android.data.model.Muscle.AvantBras
import com.musclenote.android.data.model.Muscle.Biceps
import com.musclenote.android.data.model.Muscle.Dorsaux
import com.musclenote.android.data.model.Muscle.EpaulesArriere
import com.musclenote.android.data.model.Muscle.EpaulesAvant
import com.musclenote.android.data.model.Muscle.EpaulesCote
import com.musclenote.android.data.model.Muscle.Fessiers
import com.musclenote.android.data.model.Muscle.IschioJambiers
import com.musclenote.android.data.model.Muscle.Lombaires
import com.musclenote.android.data.model.Muscle.Mollets
import com.musclenote.android.data.model.Muscle.Obliques
import com.musclenote.android.data.model.Muscle.Pectoraux
import com.musclenote.android.data.model.Muscle.Quadriceps
import com.musclenote.android.data.model.Muscle.Trapezes
import com.musclenote.android.data.model.Muscle.Triceps
import com.musclenote.android.data.model.MuscleGroup

/**
 * Exercice intégré : jamais stocké, nom affiché tiré de la chaîne [nameRes] (SPEC § 6.1). Ses muscles
 * (§ 15.17.2, 🔄 D43) sont embarqués comme son nom : au moins un principal, dans l'ordre de [Muscle].
 */
data class BuiltInExercise(
    val id: String,
    val muscleGroup: MuscleGroup,
    @param:StringRes val nameRes: Int,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
)

/**
 * Catalogue des 42 exercices intégrés, dans l'ordre d'affichage, avec leurs muscles (§ 15.17.2). ⚠️ Les ids sont figés : les
 * séances sauvegardées les référencent (fautes comprises : `calfraize`, `legrise`).
 * « Perso ou intégré » se décide uniquement par l'appartenance de l'id à ce catalogue (§ 3.9).
 */
object ExerciseCatalog {
    val all: List<BuiltInExercise> = listOf(
        BuiltInExercise("deadlift", MuscleGroup.Dos, R.string.exercise_deadlift, listOf(Lombaires, Fessiers, IschioJambiers), listOf(Trapezes, AvantBras, Dorsaux, Quadriceps, Adducteurs)),
        BuiltInExercise("pullup", MuscleGroup.Dos, R.string.exercise_pullup, listOf(Dorsaux), listOf(Trapezes, EpaulesArriere, Biceps, AvantBras)),
        BuiltInExercise("row", MuscleGroup.Dos, R.string.exercise_row, listOf(Trapezes, Dorsaux), listOf(EpaulesArriere, Biceps, Lombaires)),
        BuiltInExercise("latpull", MuscleGroup.Dos, R.string.exercise_latpull, listOf(Dorsaux), listOf(Trapezes, EpaulesArriere, Biceps)),
        BuiltInExercise("tiragebucheron", MuscleGroup.Dos, R.string.exercise_tiragebucheron, listOf(Dorsaux), listOf(Trapezes, EpaulesArriere, Biceps)),
        BuiltInExercise("tirageh", MuscleGroup.Dos, R.string.exercise_tirageh, listOf(Trapezes, Dorsaux), listOf(EpaulesArriere, Biceps)),
        BuiltInExercise("bench", MuscleGroup.Pecs, R.string.exercise_bench, listOf(Pectoraux), listOf(EpaulesAvant, Triceps)),
        BuiltInExercise("incline", MuscleGroup.Pecs, R.string.exercise_incline, listOf(Pectoraux), listOf(EpaulesAvant, Triceps)),
        BuiltInExercise("fly", MuscleGroup.Pecs, R.string.exercise_fly, listOf(Pectoraux), listOf(EpaulesAvant)),
        BuiltInExercise("dips", MuscleGroup.Pecs, R.string.exercise_dips, listOf(Pectoraux, Triceps), listOf(EpaulesAvant)),
        BuiltInExercise("dbench", MuscleGroup.Pecs, R.string.exercise_dbench, listOf(Pectoraux), listOf(EpaulesAvant, Triceps)),
        BuiltInExercise("dincline", MuscleGroup.Pecs, R.string.exercise_dincline, listOf(Pectoraux), listOf(EpaulesAvant, Triceps)),
        BuiltInExercise("pecfly", MuscleGroup.Pecs, R.string.exercise_pecfly, listOf(Pectoraux), listOf(EpaulesAvant)),
        BuiltInExercise("curl", MuscleGroup.Biceps, R.string.exercise_curl, listOf(Biceps), listOf(AvantBras)),
        BuiltInExercise("hammercurl", MuscleGroup.Biceps, R.string.exercise_hammercurl, listOf(Biceps, AvantBras)),
        BuiltInExercise("concentcurl", MuscleGroup.Biceps, R.string.exercise_concentcurl, listOf(Biceps), listOf(AvantBras)),
        BuiltInExercise("pushdown", MuscleGroup.Triceps, R.string.exercise_pushdown, listOf(Triceps)),
        BuiltInExercise("overheadtri", MuscleGroup.Triceps, R.string.exercise_overheadtri, listOf(Triceps)),
        BuiltInExercise("skullcrusher", MuscleGroup.Triceps, R.string.exercise_skullcrusher, listOf(Triceps)),
        BuiltInExercise("barfront", MuscleGroup.Triceps, R.string.exercise_barfront, listOf(Triceps)),
        BuiltInExercise("squat", MuscleGroup.Jambes, R.string.exercise_squat, listOf(Fessiers, Quadriceps), listOf(Lombaires, IschioJambiers, Adducteurs)),
        BuiltInExercise("legpress", MuscleGroup.Jambes, R.string.exercise_legpress, listOf(Quadriceps), listOf(Fessiers, IschioJambiers, Adducteurs)),
        BuiltInExercise("legcurl", MuscleGroup.Jambes, R.string.exercise_legcurl, listOf(IschioJambiers), listOf(Mollets)),
        BuiltInExercise("legext", MuscleGroup.Jambes, R.string.exercise_legext, listOf(Quadriceps)),
        BuiltInExercise("lunges", MuscleGroup.Jambes, R.string.exercise_lunges, listOf(Fessiers, Quadriceps), listOf(IschioJambiers, Adducteurs)),
        BuiltInExercise("calfraize", MuscleGroup.Jambes, R.string.exercise_calfraize, listOf(Mollets)),
        BuiltInExercise("adductorpress", MuscleGroup.Jambes, R.string.exercise_adductorpress, listOf(Adducteurs)),
        BuiltInExercise("abductorpress", MuscleGroup.Jambes, R.string.exercise_abductorpress, listOf(Fessiers)),
        BuiltInExercise("calfsit", MuscleGroup.Jambes, R.string.exercise_calfsit, listOf(Mollets)),
        BuiltInExercise("ohp", MuscleGroup.Epaules, R.string.exercise_ohp, listOf(EpaulesAvant), listOf(Trapezes, EpaulesCote, Pectoraux, Triceps)),
        BuiltInExercise("lateralraise", MuscleGroup.Epaules, R.string.exercise_lateralraise, listOf(EpaulesCote), listOf(Trapezes, EpaulesAvant)),
        BuiltInExercise("frontraise", MuscleGroup.Epaules, R.string.exercise_frontraise, listOf(EpaulesAvant), listOf(EpaulesCote, Pectoraux)),
        BuiltInExercise("crunch", MuscleGroup.Abdos, R.string.exercise_crunch, listOf(Abdominaux), listOf(Obliques)),
        BuiltInExercise("plank", MuscleGroup.Abdos, R.string.exercise_plank, listOf(Abdominaux), listOf(Obliques)),
        BuiltInExercise("legrise", MuscleGroup.Abdos, R.string.exercise_legrise, listOf(Abdominaux), listOf(Obliques, Quadriceps)),
        BuiltInExercise("treadmill", MuscleGroup.Cardio, R.string.exercise_treadmill, listOf(Quadriceps, Mollets), listOf(Fessiers, IschioJambiers)),
        BuiltInExercise("bike", MuscleGroup.Cardio, R.string.exercise_bike, listOf(Quadriceps), listOf(Fessiers, IschioJambiers, Mollets)),
        BuiltInExercise("wristcurl", MuscleGroup.AvantBras, R.string.exercise_wristcurl, listOf(AvantBras)),
        BuiltInExercise("wristext", MuscleGroup.AvantBras, R.string.exercise_wristext, listOf(AvantBras)),
        BuiltInExercise("reversecurl", MuscleGroup.AvantBras, R.string.exercise_reversecurl, listOf(AvantBras), listOf(Biceps)),
        BuiltInExercise("pinchgrip", MuscleGroup.AvantBras, R.string.exercise_pinchgrip, listOf(AvantBras)),
        BuiltInExercise("farmerwalk", MuscleGroup.AvantBras, R.string.exercise_farmerwalk, listOf(Trapezes, AvantBras), listOf(Abdominaux, Obliques, Lombaires)),
    )

    private val byId: Map<String, BuiltInExercise> = all.associateBy { it.id }

    fun isBuiltIn(id: String): Boolean = id in byId

    fun find(id: String): BuiltInExercise? = byId[id]
}
