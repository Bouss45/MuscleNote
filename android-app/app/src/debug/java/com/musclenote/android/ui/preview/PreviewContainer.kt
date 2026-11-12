package com.musclenote.android.ui.preview

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.data.AppContainer
import com.musclenote.android.data.crash.CrashReports
import com.musclenote.android.data.storage.DataFiles
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/** Contenu des données d'un aperçu. */
enum class PreviewDataSet {
    /** Six semaines de séances, pesées, compléments, suivi du jour ; pas de séance en cours. */
    Full,

    /** [Full] plus une séance en cours avec un repos qui tourne. */
    WithCurrentWorkout,

    /** Première ouverture : aucune donnée. */
    Empty,
}

/**
 * Conteneur de données de l'app, rempli avec [PreviewData] et chargé avant le premier rendu, sur
 * l'horloge fixe [PreviewTime]. Les fichiers vont dans un dossier temporaire propre à l'aperçu :
 * rien ne touche aux données de l'app. Aucun accès réseau, aucune alarme, aucune tâche planifiée.
 */
fun previewContainer(context: Context, dataSet: PreviewDataSet): AppContainer {
    val dir = Files.createTempDirectory(context.cacheDir.toPath(), "preview-").toFile()
    if (dataSet != PreviewDataSet.Empty) writePreviewData(File(dir, "data"), dataSet)
    val container = AppContainer(File(dir, "data"), time = PreviewTime, ioDispatcher = Dispatchers.IO, currentVersion = "1.0.5")
    runBlocking { container.awaitReady() }
    return container
}

private fun writePreviewData(dataDir: File, dataSet: PreviewDataSet) = runBlocking(Dispatchers.IO) {
    coroutineScope {
        val files = DataFiles(dataDir, this, Dispatchers.IO)
        files.workouts.saveNow(PreviewData.workouts)
        files.exercises.saveNow(PreviewData.customExercises)
        files.bodyWeights.saveNow(PreviewData.bodyWeights)
        files.supplements.saveNow(PreviewData.supplements)
        files.supplementsTaken.saveNow(PreviewData.taken)
        files.dailyTracker.saveNow(PreviewData.tracker)
        files.preferences.saveNow(PreviewData.preferences)
        files.localPreferences.saveNow(PreviewData.localPreferences)
        files.autoBackup.saveNow(PreviewData.autoBackup)
        if (dataSet == PreviewDataSet.WithCurrentWorkout) {
            files.currentWorkout.saveNow(PreviewData.currentWorkout)
            files.restTimer.saveNow(PreviewData.restTimer)
        }
    }
}

/**
 * Contexte des aperçus dont `applicationContext` est une [MuscleNoteApplication] : le moteur de
 * rendu fournit une `Application` générique, or la carte du rapport de plantage lit
 * `MuscleNoteApplication.crashReports`.
 */
private class PreviewContext(base: Context, private val application: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = application
}

/**
 * [MuscleNoteApplication] d'aperçu, jamais démarrée (pas d'`onCreate`, donc pas de données ni de
 * planification) : seul son dossier de rapports de plantage, vide, est renseigné.
 */
private fun previewApplication(context: Context): MuscleNoteApplication = MuscleNoteApplication().also { app ->
    val reports = CrashReports(Files.createTempDirectory(context.cacheDir.toPath(), "preview-crashes-").toFile())
    MuscleNoteApplication::class.java.getDeclaredField("crashReports").apply { isAccessible = true }.set(app, reports)
}

/**
 * Aperçu d'un écran : conteneur de données [dataSet] fourni par [LocalAppContainer], application
 * d'aperçu (voir [PreviewContext]), thème de l'aperçu (clair ou sombre), fond de l'app.
 */
@Composable
fun ScreenPreview(dataSet: PreviewDataSet = PreviewDataSet.Full, content: @Composable (AppContainer) -> Unit) {
    val context = LocalContext.current
    val container = remember(dataSet) { previewContainer(context, dataSet) }
    val previewContext = remember(context) { PreviewContext(context, previewApplication(context)) }
    CompositionLocalProvider(LocalAppContainer provides container, LocalContext provides previewContext) {
        PreviewTheme {
            Box(Modifier.fillMaxSize().background(MuscleNoteTheme.colors.background)) {
                content(container)
            }
        }
    }
}
