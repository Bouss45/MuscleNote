package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.R
import com.musclenote.android.data.crash.CrashReport
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Au-delà, le rapport n'est partagé qu'en fichier (le texte passerait par une transaction Binder). */
private const val MAX_SHARED_TEXT_CHARS = 64 * 1024

/**
 * Dernier rapport de plantage (SPEC § 10.9, 🔄 D17), `null` s'il n'y en a pas. Un rapport ne peut
 * apparaître qu'après un redémarrage de l'app : il est lu une fois, à l'affichage de l'écran.
 */
@Composable
internal fun rememberLatestCrashReport(): MutableState<CrashReport?> {
    val context = LocalContext.current
    val reports = remember(context) { (context.applicationContext as MuscleNoteApplication).crashReports }
    val latest = remember { mutableStateOf<CrashReport?>(null) }
    LaunchedEffect(reports) {
        latest.value = withContext(Dispatchers.IO) { reports.latest() }
    }
    return latest
}

/** Date et heure du plantage, dans la locale de l'app. */
@Composable
internal fun crashDate(report: CrashReport): String {
    val locale = appLocale()
    val zone = LocalAppContainer.current.time.zone()
    return remember(report, locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(report.instant.atZone(zone))
    }
}

/**
 * Sheet « Rapport de plantage » (§ 15.10.3) : « Dernier plantage le … », [Partager] (le plus récent,
 * `text/plain`), [Supprimer] (tous les rapports, puis [onDeleted]), [Fermer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportSheet(report: CrashReport, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> CrashReportSheetContent(report, onDeleted, onDismiss, inset) }
}

@Composable
internal fun CrashReportSheetContent(report: CrashReport, onDeleted: () -> Unit, onClose: () -> Unit, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    val reports = remember(context) { (context.applicationContext as MuscleNoteApplication).crashReports }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val chooserTitle = stringResource(R.string.settings_crash_title)
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(
            stringResource(R.string.settings_crash_title),
            AnnotatedString(stringResource(R.string.settings_crash_desc, crashDate(report))),
            messageStyle = Studio.typography.body,
        )
        StudioButton(
            stringResource(R.string.settings_crash_share),
            onClick = {
                haptics.light()
                scope.launch {
                    val text = withContext(Dispatchers.IO) { readForSharing(report) }
                    shareCrashReport(context, report, text, chooserTitle)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        StudioButton(
            stringResource(R.string.common_delete),
            onClick = {
                haptics.success()
                scope.launch {
                    withContext(Dispatchers.IO) { reports.deleteAll() }
                    onDeleted()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            icon = R.drawable.ds_ic_delete,
        )
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/** Texte du rapport, joint au partage s'il est court ; `null` sinon (fichier seul). */
private fun readForSharing(report: CrashReport): String? = try {
    report.file.readText().takeIf { it.length <= MAX_SHARED_TEXT_CHARS }
} catch (e: IOException) {
    Log.w("CrashReport", "Lecture impossible de ${report.file.name}", e)
    null
}

/**
 * Feuille de partage : le fichier (`FileProvider`, `text/plain`) et, s'il est court, son texte —
 * pour les apps qui n'acceptent que du texte et l'action « Copier ».
 */
private fun shareCrashReport(context: Context, report: CrashReport, text: String?, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, report.file.name)
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (e: ActivityNotFoundException) {
        Log.w("CrashReport", "Aucune feuille de partage", e)
    }
}
