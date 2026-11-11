package com.musclenote.android.data.storage

import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Un document persistant = un fichier JSON (SPEC § 4.2, 🔄 D2).
 *
 * - Écriture **atomique** ([AtomicFile] : fichier temporaire puis renommage), sur [ioDispatcher].
 * - [save] planifie l'écriture et rend la main ; si plusieurs écritures s'accumulent, seule la
 *   plus récente est faite. [saveNow] écrit et attend (import).
 * - Un fichier illisible (JSON invalide ou mauvaise forme) est **renommé**
 *   `<nom>.corrupt-<timestamp ms>.json` et lu comme absent : la prochaine écriture ne détruit
 *   donc pas l'unique copie des données.
 * - Une erreur d'écriture est journalisée, jamais propagée.
 * - `null` = document absent : [save]`(null)` supprime le fichier.
 */
class JsonDocument<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val atomicFile = AtomicFile(file)
    private val ioMutex = Mutex()

    /** Numéro de la dernière écriture demandée : une écriture dépassée n'est pas faite. */
    private val generation = AtomicLong()

    val name: String get() = file.name

    /** Contenu du fichier, ou `null` s'il est absent ou illisible (alors mis de côté). */
    suspend fun read(): T? = withContext(ioDispatcher) { ioMutex.withLock { readLocked() } }

    fun save(value: T?) {
        val gen = generation.incrementAndGet()
        scope.launch(ioDispatcher) {
            ioMutex.withLock { if (gen == generation.get()) writeLocked(value) }
        }
    }

    suspend fun saveNow(value: T?) {
        generation.incrementAndGet()
        withContext(ioDispatcher) { ioMutex.withLock { writeLocked(value) } }
    }

    private fun readLocked(): T? {
        val bytes = try {
            atomicFile.readFully()
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: IOException) {
            Log.w(TAG, "Lecture impossible de $name", e)
            return null
        }
        return try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: SerializationException) {
            quarantine(e)
            null
        } catch (e: IllegalArgumentException) {
            quarantine(e)
            null
        }
    }

    private fun quarantine(cause: Exception) {
        val aside = File(file.parentFile, "${file.nameWithoutExtension}.corrupt-${clock()}.json")
        val moved = file.renameTo(aside)
        Log.w(TAG, "$name illisible, " + if (moved) "mis de côté dans ${aside.name}" else "impossible de le renommer", cause)
    }

    private fun writeLocked(value: T?) {
        if (value == null) {
            atomicFile.delete()
            return
        }
        var stream: FileOutputStream? = null
        try {
            val bytes = json.encodeToString(serializer, value).encodeToByteArray()
            file.parentFile?.mkdirs()
            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) atomicFile.failWrite(stream)
            Log.e(TAG, "Écriture impossible de $name", e)
        }
    }

    private companion object {
        const val TAG = "JsonDocument"
    }
}
