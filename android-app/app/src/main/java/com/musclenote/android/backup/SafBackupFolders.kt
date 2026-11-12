package com.musclenote.android.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.musclenote.android.data.repository.BackupFolderUnavailableException
import com.musclenote.android.data.repository.BackupFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Dossier de la sauvegarde automatique (SPEC § 15.2) par le Storage Access Framework : arborescence
 * choisie dans le sélecteur du système (`ACTION_OPEN_DOCUMENT_TREE`), gardée par une autorisation
 * persistante, sans permission de stockage.
 */
class SafBackupFolders(context: Context) : BackupFolders {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private class Child(val documentId: String, val name: String, val isDirectory: Boolean)

    override suspend fun adopt(folderUri: String): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folderUri)
        resolver.takePersistableUriPermission(tree, FLAGS)
        accessing { displayName(tree) } ?: tree.lastPathSegment ?: folderUri
    }

    override suspend fun release(folderUri: String) {
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(folderUri)
            if (resolver.persistedUriPermissions.any { it.uri == tree }) resolver.releasePersistableUriPermission(tree, FLAGS)
        }
    }

    override suspend fun list(folderUri: String): List<String> = withContext(Dispatchers.IO) {
        accessing { children(Uri.parse(folderUri)).filterNot { it.isDirectory }.map { it.name } }
    }

    override suspend fun write(folderUri: String, name: String, content: String) {
        val tree = Uri.parse(folderUri)
        val bytes = content.toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            accessing<Unit> {
                val existing = children(tree).firstOrNull { it.name == name && !it.isDirectory }
                if (existing == null) {
                    writeNew(tree, name, bytes)
                } else {
                    val document = DocumentsContract.buildDocumentUriUsingTree(tree, existing.documentId)
                    try {
                        // « wt » : le fichier du jour est vidé puis réécrit.
                        output(document, "wt", bytes)
                    } catch (e: UnsupportedOperationException) {
                        replace(tree, document, name, bytes)
                    } catch (e: IllegalArgumentException) {
                        // Fournisseur sans le mode « t » : on supprime puis on recrée le fichier.
                        replace(tree, document, name, bytes)
                    }
                }
            }
        }
    }

    override suspend fun delete(folderUri: String, name: String) {
        val tree = Uri.parse(folderUri)
        withContext(Dispatchers.IO) {
            accessing<Unit> {
                val child = children(tree).firstOrNull { it.name == name && !it.isDirectory }
                if (child != null) {
                    DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, child.documentId))
                }
            }
        }
    }

    private fun replace(tree: Uri, document: Uri, name: String, bytes: ByteArray) {
        DocumentsContract.deleteDocument(resolver, document)
        writeNew(tree, name, bytes)
    }

    private fun writeNew(tree: Uri, name: String, bytes: ByteArray) {
        val created = DocumentsContract.createDocument(resolver, treeDocument(tree), MIME_JSON, name)
            ?: throw IOException("Impossible de créer $name")
        output(created, "w", bytes)
    }

    private fun output(document: Uri, mode: String, bytes: ByteArray) {
        val stream = resolver.openOutputStream(document, mode) ?: throw IOException("Flux d'écriture indisponible")
        stream.use { it.write(bytes) }
    }

    private fun treeDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun displayName(tree: Uri): String? =
        resolver.query(treeDocument(tree), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Contenu du dossier ; l'autorisation persistante doit toujours exister. */
    private fun children(tree: Uri): List<Child> {
        val granted = resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }
        if (!granted) throw BackupFolderUnavailableException("Autorisation du dossier absente")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(uri, projection, null, null, null)
            ?: throw BackupFolderUnavailableException("Dossier illisible")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(0) ?: continue
                    add(Child(id, it.getString(1).orEmpty(), it.getString(2) == Document.MIME_TYPE_DIR))
                }
            }
        }
    }

    /** Les erreurs d'accès au dossier deviennent « dossier inaccessible » (§ 15.2). */
    private inline fun <T> accessing(block: () -> T): T = try {
        block()
    } catch (e: BackupFolderUnavailableException) {
        throw e
    } catch (e: SecurityException) {
        throw BackupFolderUnavailableException(e.localizedMessage ?: "Accès refusé", e)
    } catch (e: FileNotFoundException) {
        throw BackupFolderUnavailableException(e.localizedMessage ?: "Dossier introuvable", e)
    } catch (e: IllegalArgumentException) {
        throw BackupFolderUnavailableException(e.localizedMessage ?: "Dossier introuvable", e)
    } catch (e: IllegalStateException) {
        throw BackupFolderUnavailableException(e.localizedMessage ?: "Dossier indisponible", e)
    }

    private companion object {
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val MIME_JSON = "application/json"
    }
}
