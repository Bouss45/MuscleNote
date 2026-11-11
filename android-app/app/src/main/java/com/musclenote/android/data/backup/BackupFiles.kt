package com.musclenote.android.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Fichiers échangés avec les autres apps lors d'un export ou d'un import (SPEC § 5.4, § 5.6). */
object BackupFiles {

    /** Au-delà, le fichier n'est pas une sauvegarde MuscleNote (quelques Mo au plus). */
    private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

    /**
     * Écrit l'export dans `cacheDir/exports/musclenote_backup_AAAA-MM-JJ.json` (jour local) et
     * renvoie son URI `FileProvider`, à partager. Les exports précédents sont effacés.
     */
    fun writeExport(context: Context, json: String, today: LocalDate): Uri {
        val dir = File(context.cacheDir, "exports")
        dir.deleteRecursively()
        if (!dir.mkdirs()) throw IOException("Impossible de créer ${dir.name}")
        val file = File(dir, "musclenote_backup_$today.json")
        file.writeText(json, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Lit un fichier choisi ou partagé, en UTF-8. */
    fun readText(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Flux indisponible")
        return stream.use { input ->
            val buffer = ByteArray(64 * 1024)
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_IMPORT_BYTES) throw IOException("Fichier trop volumineux")
            }
            out.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
