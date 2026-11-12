package com.musclenote.android.data.repository

import android.util.Log
import com.musclenote.android.data.backup.AutoBackupFailure
import com.musclenote.android.data.backup.AutoBackupRules
import com.musclenote.android.data.backup.AutoBackupState
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Le dossier n'est plus accessible : autorisation retirée, dossier supprimé ou renommé (§ 15.2). */
class BackupFolderUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Accès au dossier de la sauvegarde automatique (le sélecteur du système sur l'appareil, un
 * dossier en mémoire dans les tests). Toute méthode lève [BackupFolderUnavailableException] si le
 * dossier n'est plus accessible, une autre exception pour une erreur d'écriture.
 */
interface BackupFolders {
    /** Garde l'autorisation persistante du dossier choisi et renvoie son nom affiché. */
    suspend fun adopt(folderUri: String): String

    /** Rend l'autorisation d'un dossier qui n'est plus utilisé (sans erreur s'il n'en avait pas). */
    suspend fun release(folderUri: String)

    /** Noms des fichiers du dossier (sous-dossiers exclus). */
    suspend fun list(folderUri: String): List<String>

    /** Écrit [content] dans le fichier [name] : remplacé s'il existe, créé sinon. */
    suspend fun write(folderUri: String, name: String, content: String)

    suspend fun delete(folderUri: String, name: String)

    companion object {
        /** Aucun dossier accessible (tests qui n'en ont pas besoin). */
        val None: BackupFolders = object : BackupFolders {
            private fun unavailable(): Nothing = throw BackupFolderUnavailableException("Aucun accès aux dossiers")
            override suspend fun adopt(folderUri: String): String = unavailable()
            override suspend fun release(folderUri: String) = Unit
            override suspend fun list(folderUri: String): List<String> = unavailable()
            override suspend fun write(folderUri: String, name: String, content: String) = unavailable()
            override suspend fun delete(folderUri: String, name: String) = unavailable()
        }
    }
}

/** Tâche périodique de la sauvegarde automatique (WorkManager sur l'appareil). */
interface AutoBackupScheduler {
    /** (Re)planifie la tâche : première échéance dans 24 h. */
    fun schedule()

    /** Planifie la tâche si elle ne l'est pas déjà (démarrage de l'app). */
    fun ensureScheduled()

    fun cancel()

    companion object {
        val None: AutoBackupScheduler = object : AutoBackupScheduler {
            override fun schedule() = Unit
            override fun ensureScheduled() = Unit
            override fun cancel() = Unit
        }
    }
}

/** Issue d'une sauvegarde. */
sealed interface AutoBackupResult {
    data class Saved(val fileName: String) : AutoBackupResult
    data class Failed(val failure: AutoBackupFailure, val detail: String?) : AutoBackupResult
}

/**
 * Sauvegarde automatique (SPEC § 15.2, 🔄 D28) : export JSON v2 quotidien dans un dossier choisi,
 * même nom que l'export manuel (remplacé le même jour), [AutoBackupRules.KEEP] fichiers gardés.
 * Une sauvegarde à la fois : la tâche planifiée et « Sauvegarder maintenant » partagent
 * [backupMutex]. [exportJson] = contenu de « Exporter mes données » (§ 5.6).
 */
class AutoBackupRepository(
    private val doc: JsonDocument<AutoBackupState>,
    private val time: TimeSource,
    private val folders: BackupFolders,
    private val scheduler: AutoBackupScheduler,
    private val exportJson: suspend () -> String,
) {
    private val mutex = Mutex()
    private val backupMutex = Mutex()

    private val _state = MutableStateFlow(AutoBackupState.DEFAULT)
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    private val _running = MutableStateFlow(false)

    /** Sauvegarde en cours (indicateur du bouton, interrupteur et boutons désactivés). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun load() {
        mutex.withLock { _state.value = doc.read() ?: AutoBackupState.DEFAULT }
    }

    /** Démarrage de l'app : la tâche planifiée est remise si elle a disparu (`KEEP`). */
    fun ensureScheduled() {
        if (_state.value.enabled) scheduler.ensureScheduled()
    }

    /**
     * Dossier choisi dans le sélecteur (activation ou [Changer]) : il est retenu, l'autorisation de
     * l'ancien est rendue, la sauvegarde automatique est activée et une sauvegarde a lieu tout de
     * suite. Si l'autorisation ne peut pas être gardée, la sauvegarde échoue « dossier
     * inaccessible ».
     */
    suspend fun chooseFolder(folderUri: String): AutoBackupResult {
        val name = try {
            folders.adopt(folderUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Autorisation du dossier refusée", e)
            null
        }
        val previous = _state.value.folderUri
        update { it.copy(enabled = true, folderUri = folderUri, folderName = name ?: it.folderName.takeIf { previous == folderUri }) }
        if (previous != null && previous != folderUri) {
            try {
                folders.release(previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Autorisation de l'ancien dossier non rendue", e)
            }
        }
        scheduler.schedule()
        return backupNow()
    }

    /**
     * Interrupteur. Activé avec un dossier retenu → planifiée et sauvegarde tout de suite (son
     * issue est renvoyée) ; sans dossier → rien (`null`), l'écran ouvre le sélecteur. Désactivé →
     * plus de tâche planifiée ; le dossier reste retenu.
     */
    suspend fun setEnabled(enabled: Boolean): AutoBackupResult? {
        if (!enabled) {
            update { it.copy(enabled = false) }
            scheduler.cancel()
            return null
        }
        if (_state.value.folderUri == null) return null
        update { it.copy(enabled = true) }
        scheduler.schedule()
        return backupNow()
    }

    /** Tâche planifiée : sauvegarde si l'option est toujours activée. */
    suspend fun runScheduled(): AutoBackupResult? = if (_state.value.enabled) backupNow() else null

    /**
     * Sauvegarde : fichier du jour local écrit (remplacé s'il existe), puis rotation des
     * sauvegardes au-delà des [AutoBackupRules.KEEP] plus récentes. Le résultat est enregistré ;
     * une réussite efface l'échec précédent.
     */
    suspend fun backupNow(): AutoBackupResult = backupMutex.withLock {
        _running.value = true
        try {
            val result = backup()
            val at = time.now().toEpochMilli()
            update { state ->
                when (result) {
                    is AutoBackupResult.Saved ->
                        state.copy(lastSuccessAt = at, lastFailureAt = null, failure = null, failureDetail = null)
                    is AutoBackupResult.Failed ->
                        state.copy(lastFailureAt = at, failure = result.failure, failureDetail = result.detail)
                }
            }
            result
        } finally {
            _running.value = false
        }
    }

    private suspend fun backup(): AutoBackupResult {
        val folder = _state.value.folderUri
            ?: return AutoBackupResult.Failed(AutoBackupFailure.Folder, null)
        val name = AutoBackupRules.fileName(time.today())
        try {
            folders.write(folder, name, exportJson())
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFolderUnavailableException) {
            Log.w(TAG, "Dossier inaccessible", e)
            return AutoBackupResult.Failed(AutoBackupFailure.Folder, e.describe())
        } catch (e: Exception) {
            Log.e(TAG, "Sauvegarde impossible", e)
            return AutoBackupResult.Failed(AutoBackupFailure.Write, e.describe())
        }
        // Rotation, seulement après une écriture réussie ; un échec ici ne la rend pas fausse.
        try {
            for (old in AutoBackupRules.filesToDelete(folders.list(folder))) folders.delete(folder, old)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rotation des sauvegardes incomplète", e)
        }
        return AutoBackupResult.Saved(name)
    }

    private suspend fun update(transform: (AutoBackupState) -> AutoBackupState) {
        mutex.withLock {
            val current = _state.value
            val next = transform(current)
            if (next != current) {
                _state.value = next
                doc.save(next)
            }
        }
    }

    private fun Exception.describe(): String = localizedMessage ?: javaClass.simpleName

    private companion object {
        const val TAG = "AutoBackup"
    }
}
