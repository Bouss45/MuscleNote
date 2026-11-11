package com.musclenote.android.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.backup.BackupFiles
import com.musclenote.android.data.backup.BackupReadResult
import com.musclenote.android.data.backup.BackupReader
import com.musclenote.android.data.export.CsvExport
import com.musclenote.android.notifications.withAppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opération de sauvegarde en cours : son icône devient un indicateur, les actions sont bloquées. */
enum class BackupOperation { Export, CsvExport, Import }

/** Fichiers prêts à partager : la sauvegarde JSON (§ 5.6) ou les fichiers CSV (§ 8). */
sealed interface ShareRequest {
    data class Json(val uri: Uri) : ShareRequest
    data class Csv(val uris: List<Uri>) : ShareRequest
}

/** Message affiché sous les actions (§ 2.12, point 4). */
sealed interface BackupStatus {
    data object ExportShared : BackupStatus
    data object CsvExported : BackupStatus

    /** Export CSV sans aucune table non vide. */
    data object NothingToExport : BackupStatus
    data class ExportFailed(val detail: String) : BackupStatus
    data object ImportSucceeded : BackupStatus
    data class ReadFailed(val detail: String) : BackupStatus
    data class ImportRejected(val reason: BackupReadResult.Failure) : BackupStatus
    data class ImportFailed(val detail: String) : BackupStatus
}

data class BackupUiState(
    val operation: BackupOperation? = null,
    /** Fichier lu, en attente de la confirmation « Écraser les données actuelles ? ». */
    val pendingImport: String? = null,
    val status: BackupStatus? = null,
)

/**
 * Export et import de la sauvegarde JSON (SPEC § 5.4 – 5.6), depuis Paramètres ou depuis un
 * fichier partagé par une autre app (l'app Expo, § 5.5), et export CSV (§ 8). Portée : l'activité.
 */
class BackupViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    // Deux canaux : chaque événement n'est reçu que par un seul collecteur.
    private val _shareRequests = Channel<ShareRequest>(Channel.BUFFERED)

    /** L'export est prêt : ouvrir la feuille de partage (collecté par l'écran Paramètres). */
    val shareRequests: Flow<ShareRequest> = _shareRequests.receiveAsFlow()

    private val _showSettingsRequests = Channel<Unit>(Channel.CONFLATED)

    /** Fichier reçu d'une autre app : afficher l'onglet Paramètres (collecté par la racine). */
    val showSettingsRequests: Flow<Unit> = _showSettingsRequests.receiveAsFlow()

    // ─── Export ───────────────────────────────────────────────────────────────

    fun export() {
        if (_state.value.operation != null) return
        _state.update { it.copy(operation = BackupOperation.Export, status = null, pendingImport = null) }
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val json = container.exportBackupJson()
                    BackupFiles.writeExport(application, json, container.time.today())
                }
                _shareRequests.send(ShareRequest.Json(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export impossible", e)
                _state.update { it.copy(operation = null, status = BackupStatus.ExportFailed(e.describe())) }
            }
        }
    }

    /**
     * Export CSV (§ 8) : fichiers des tables non vides, dans la langue de l'app (séparateur,
     * décimales, en-têtes), partagés ensemble ; aucune table → « Aucune donnée à exporter. ».
     */
    fun exportCsv() {
        if (_state.value.operation != null) return
        _state.update { it.copy(operation = BackupOperation.CsvExport, status = null, pendingImport = null) }
        viewModelScope.launch {
            try {
                container.awaitReady()
                val localized = application.withAppLocale()
                val time = container.time
                val files = withContext(Dispatchers.Default) {
                    CsvExport.export(
                        workouts = container.workouts.workouts.value,
                        bodyWeights = container.nutrition.bodyWeights.value,
                        tracker = container.nutrition.tracker.value,
                        texts = CsvExportTexts.texts(localized, container.workouts.customExercises.value, time.zone()),
                        format = CsvExportTexts.format(localized),
                        today = time.today(),
                        zone = time.zone(),
                    )
                }
                if (files.isEmpty()) {
                    _state.update { it.copy(operation = null, status = BackupStatus.NothingToExport) }
                    return@launch
                }
                val uris = withContext(Dispatchers.IO) { BackupFiles.writeCsvExport(application, files) }
                _shareRequests.send(ShareRequest.Csv(uris))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export CSV impossible", e)
                _state.update { it.copy(operation = null, status = BackupStatus.ExportFailed(e.describe())) }
            }
        }
    }

    /** Retour de la feuille de partage (l'app Expo affichait aussi le succès à ce moment-là). */
    fun onShareSheetClosed() {
        _state.update {
            val status = if (it.operation == BackupOperation.CsvExport) BackupStatus.CsvExported else BackupStatus.ExportShared
            it.copy(operation = null, status = status)
        }
    }

    fun onShareSheetUnavailable(detail: String) {
        _state.update { it.copy(operation = null, status = BackupStatus.ExportFailed(detail)) }
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    /** Avant d'ouvrir le sélecteur de fichiers. */
    fun onImportRequested() {
        _state.update { it.copy(status = null, pendingImport = null) }
    }

    /** Fichier choisi dans le sélecteur, ou reçu d'une autre app ([fromOtherApp]). */
    fun onFileReceived(uri: Uri, fromOtherApp: Boolean = false) {
        if (fromOtherApp) _showSettingsRequests.trySend(Unit)
        _state.update { it.copy(operation = BackupOperation.Import, status = null, pendingImport = null) }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { BackupFiles.readText(application, uri) }
                _state.update { it.copy(operation = null, pendingImport = text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lecture impossible", e)
                _state.update { it.copy(operation = null, status = BackupStatus.ReadFailed(e.describe())) }
            }
        }
    }

    /** Contenu JSON reçu directement en texte (partage sans fichier). */
    fun onTextReceived(text: String) {
        _showSettingsRequests.trySend(Unit)
        _state.update { it.copy(operation = null, status = null, pendingImport = text) }
    }

    /** Aucun sélecteur de fichiers sur l'appareil. */
    fun onFileReadUnavailable(detail: String) {
        _state.update { it.copy(status = BackupStatus.ReadFailed(detail)) }
    }

    fun cancelImport() {
        _state.update { it.copy(pendingImport = null) }
    }

    /** « Confirmer » : lecture, migration, validation, puis écriture et rechargement (§ 5.4). */
    fun confirmImport() {
        val text = _state.value.pendingImport ?: return
        if (_state.value.operation != null) return
        _state.update { it.copy(operation = BackupOperation.Import, pendingImport = null, status = null) }
        viewModelScope.launch {
            val status = try {
                val time = container.time
                val result = withContext(Dispatchers.Default) { BackupReader(time.today(), time.zone()).read(text) }
                when (result) {
                    is BackupReadResult.Failure -> BackupStatus.ImportRejected(result)
                    is BackupReadResult.Success -> {
                        container.importBackup(result.data)
                        BackupStatus.ImportSucceeded
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import impossible", e)
                BackupStatus.ImportFailed(e.describe())
            }
            _state.update { it.copy(operation = null, status = status) }
        }
    }

    private fun Exception.describe(): String = localizedMessage ?: javaClass.simpleName

    class Factory(private val app: MuscleNoteApplication) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BackupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(app, app.container) as T
        }
    }

    private companion object {
        const val TAG = "Backup"
    }
}
