package com.musclenote.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.R
import com.musclenote.android.data.crash.CrashReport
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Au-delà, le rapport n'est partagé qu'en fichier (le texte passerait par une transaction Binder). */
private const val MAX_SHARED_TEXT_CHARS = 64 * 1024

/**
 * Rapport de plantage (SPEC § 2.12 point 6, § 10.9, 🔄 D17), affiché seulement si un rapport
 * existe : « Dernier plantage le {date} » + [Partager] (le plus récent, `text/plain`) et
 * [Supprimer] (tous les rapports). Un rapport ne peut apparaître qu'après un redémarrage de
 * l'app : il est lu une fois, à l'affichage de l'écran.
 */
@Composable
fun CrashReportCard() {
    val context = LocalContext.current
    val reports = remember(context) { (context.applicationContext as MuscleNoteApplication).crashReports }
    val zone = LocalAppContainer.current.time.zone()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var latest by remember { mutableStateOf<CrashReport?>(null) }
    LaunchedEffect(reports) {
        latest = withContext(Dispatchers.IO) { reports.latest() }
    }
    val report = latest ?: return

    val colors = MuscleNoteTheme.colors
    val locale = appLocale()
    val date = remember(report, locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(report.instant.atZone(zone))
    }
    val chooserTitle = stringResource(R.string.settings_crash_title)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardIcon(R.drawable.ic_error, colors.destructive)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.settings_crash_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_crash_desc, date), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val buttonShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .border(1.dp, colors.border, buttonShape)
                    .clickable {
                        haptics.light()
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { readForSharing(report) }
                            shareCrashReport(context, report, text, chooserTitle)
                        }
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.settings_crash_share), color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .background(colors.destructive)
                    .clickable {
                        haptics.success()
                        scope.launch {
                            withContext(Dispatchers.IO) { reports.deleteAll() }
                            latest = null
                        }
                    }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.common_delete), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
