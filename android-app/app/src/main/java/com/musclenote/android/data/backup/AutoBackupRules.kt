package com.musclenote.android.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Cause du dernier échec de la sauvegarde automatique (§ 15.2). */
@Serializable
enum class AutoBackupFailure {
    /** Dossier inaccessible : autorisation retirée, dossier supprimé ou renommé. */
    @SerialName("folder")
    Folder,

    /** Autre erreur pendant l'écriture. */
    @SerialName("write")
    Write,
}

/**
 * `auto_backup.json` (SPEC § 15.2, 🔄 D28) : réglage et dernier résultat de la sauvegarde
 * automatique. Propre à l'appareil, hors sauvegarde, non touché par un import.
 */
@Serializable
data class AutoBackupState(
    val enabled: Boolean = false,
    /** URI de l'arborescence choisie dans le sélecteur du système (autorisation persistante). */
    val folderUri: String? = null,
    val folderName: String? = null,
    /** Instant (ms) de la dernière sauvegarde réussie. */
    val lastSuccessAt: Long? = null,
    /** Instant (ms) du dernier échec, effacé par une réussite. */
    val lastFailureAt: Long? = null,
    val failure: AutoBackupFailure? = null,
    val failureDetail: String? = null,
) {
    companion object {
        val DEFAULT = AutoBackupState()
    }
}

/** Règles de la sauvegarde automatique (SPEC § 15.2), en fonctions pures testables sur la JVM. */
object AutoBackupRules {
    /** Nombre de sauvegardes gardées dans le dossier. */
    const val KEEP = 14

    /** Période de la tâche planifiée. */
    const val PERIOD_HOURS = 24L

    private val NAME = Regex("""musclenote_backup_(\d{4}-\d{2}-\d{2})\.json""")

    /** Nom d'une sauvegarde, celui de l'export manuel (§ 5.6) : `musclenote_backup_AAAA-MM-JJ.json`. */
    fun fileName(day: LocalDate): String = "musclenote_backup_$day.json"

    /** Jour d'une sauvegarde d'après son nom exact ; `null` pour tout autre fichier. */
    fun dayOf(name: String): LocalDate? {
        val match = NAME.matchEntire(name) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1])
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Rotation : parmi [names] (fichiers du dossier), les sauvegardes au-delà des [keep] aux dates
     * les plus récentes, à supprimer. Les autres fichiers ne sont jamais proposés.
     */
    fun filesToDelete(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.mapNotNull { name -> dayOf(name)?.let { name to it } }
            .sortedWith(compareByDescending<Pair<String, LocalDate>> { it.second }.thenBy { it.first })
            .drop(keep)
            .map { it.first }
}
