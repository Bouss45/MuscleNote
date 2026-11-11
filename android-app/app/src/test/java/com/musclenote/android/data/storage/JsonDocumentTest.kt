package com.musclenote.android.data.storage

import com.musclenote.android.data.backup.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Fichier JSON par document (§ 4.2) : écriture atomique, fichiers illisibles mis de côté. */
class JsonDocumentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "numbers.json")

    private fun document(scope: CoroutineScope) =
        JsonDocument(file, ListSerializer(Int.serializer()), AppJson.storage, scope, Dispatchers.IO, clock = { 1234L })

    @Test
    fun `un fichier absent se lit comme absent`() = runBlocking<Unit> {
        assertNull(document(this).read())
    }

    @Test
    fun `une écriture se relit`() = runBlocking<Unit> {
        document(this).saveNow(listOf(1, 2, 3))
        assertEquals("[1,2,3]", file.readText())
        assertEquals(listOf(1, 2, 3), document(this).read())
    }

    @Test
    fun `seule la dernière écriture planifiée compte`() = runBlocking<Unit> {
        coroutineScope {
            val doc = document(this)
            doc.save(listOf(1))
            doc.save(listOf(2))
            doc.save(listOf(3))
        }
        assertEquals(listOf(3), document(this).read())
    }

    @Test
    fun `les écritures différées rapprochées n'en font qu'une, avec la dernière valeur`() = runBlocking<Unit> {
        coroutineScope {
            val doc = document(this)
            doc.saveDebounced(listOf(1), 200)
            doc.saveDebounced(listOf(2), 200)
            assertFalse(file.exists())
        }
        assertEquals("[2]", file.readText())
    }

    @Test
    fun `flush fait tout de suite l'écriture différée en attente`() = runBlocking<Unit> {
        val doc = document(this)
        doc.saveDebounced(listOf(4), 60_000)
        doc.flush()
        assertEquals("[4]", file.readText())
        doc.flush() // plus rien en attente
        assertEquals("[4]", file.readText())
    }

    @Test
    fun `une écriture immédiate rend caduque l'écriture différée en attente`() = runBlocking<Unit> {
        coroutineScope {
            val doc = document(this)
            doc.saveNow(listOf(1))
            doc.saveDebounced(listOf(5), 100)
            doc.save(null)
        }
        assertFalse(file.exists())
    }

    @Test
    fun `écrire null supprime le fichier`() = runBlocking<Unit> {
        val doc = document(this)
        doc.saveNow(listOf(1))
        doc.saveNow(null)
        assertFalse(file.exists())
        assertNull(doc.read())
    }

    @Test
    fun `un JSON invalide est mis de côté et lu comme absent`() = runBlocking<Unit> {
        file.writeText("[1, 2,")
        assertNull(document(this).read())
        assertFalse(file.exists())
        val aside = File(tmp.root, "numbers.corrupt-1234.json")
        assertTrue(aside.exists())
        assertEquals("[1, 2,", aside.readText())
    }

    @Test
    fun `un JSON de mauvaise forme est mis de côté et la prochaine écriture ne l'écrase pas`() = runBlocking<Unit> {
        file.writeText("""{ "pas": "une liste" }""")
        val doc = document(this)
        assertNull(doc.read())
        doc.saveNow(listOf(7))
        assertEquals(listOf(7), doc.read())
        assertEquals("""{ "pas": "une liste" }""", File(tmp.root, "numbers.corrupt-1234.json").readText())
    }
}
