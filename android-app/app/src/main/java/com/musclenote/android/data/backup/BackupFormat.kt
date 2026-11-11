@file:OptIn(ExperimentalSerializationApi::class)

package com.musclenote.android.data.backup

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Exercise
import com.musclenote.android.data.model.Preferences
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.model.Workout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** Clés du format de sauvegarde (SPEC § 5.2), préfixe historique de l'app Expo conservé. */
object BackupKeys {
    const val WORKOUTS = "@gymtracker_workouts"
    const val EXERCISES = "@gymtracker_exercises"
    const val BODY_WEIGHTS = "@gymtracker_body_weights"
    const val SUPPLEMENTS = "@gymtracker_supplements"
    const val SUPPLEMENTS_TAKEN = "@gymtracker_supplements_taken"
    const val DAILY_TRACKER = "@gymtracker_daily_tracker"
    const val PREFERENCES = "@gymtracker_preferences"

    /** Ordre d'écriture dans un export. La séance en cours n'est jamais sauvegardée. */
    val ALL = listOf(WORKOUTS, EXERCISES, BODY_WEIGHTS, SUPPLEMENTS, SUPPLEMENTS_TAKEN, DAILY_TRACKER, PREFERENCES)
}

/** Version écrite par l'app native (🔄 D18). 1 (ou absente) = app Expo. */
const val CURRENT_BACKUP_VERSION = 2

/**
 * Contenu d'une sauvegarde, au format courant. À l'import, une propriété `null` = clé absente
 * du fichier : le document correspondant n'est pas touché (§ 5.4).
 */
data class BackupData(
    val workouts: List<Workout>? = null,
    val exercises: List<Exercise>? = null,
    val bodyWeights: List<BodyWeightEntry>? = null,
    val supplements: List<Supplement>? = null,
    val supplementsTaken: TakenRecord? = null,
    val dailyTracker: DailyTracker? = null,
    val preferences: Preferences? = null,
)

sealed interface BackupReadResult {
    data class Success(val data: BackupData, val sourceVersion: Int) : BackupReadResult

    sealed interface Failure : BackupReadResult

    /** JSON illisible, racine qui n'est pas un objet, ou champ `version` incohérent. */
    data object InvalidFormat : Failure

    /** Aucune clé MuscleNote dans le fichier. */
    data object NoData : Failure

    /** `version` > [CURRENT_BACKUP_VERSION]. */
    data object NewerVersion : Failure

    /** La valeur de [key] n'a pas la forme attendue : tout l'import est refusé. */
    data class InvalidKey(val key: String) : Failure
}

/** Configurations JSON partagées par le stockage et la sauvegarde. */
object AppJson {
    /** Fichiers de `filesDir/data` : compacts, champs optionnels omis, champs inconnus ignorés. */
    val storage: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Export : même forme, indentée de 2 espaces (§ 5.6). */
    val export: Json = Json(from = storage) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
