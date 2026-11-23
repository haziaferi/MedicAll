package com.hazia.medicall

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider for the instrumented tests of [SafSyncFolder] and [SafBackupTarget] (debug builds
 * only). Document ids are paths under `files/saf-stub/`; a test makes a directory there and opens it as a
 * tree. It lists no roots, so the system picker never shows it. Setting [revoked] makes every call fail as
 * a revoked grant does, with a [SecurityException].
 */
class StubDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        guard()
        val file = file(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: COLUMNS).also { row(it, documentId, file) }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        guard()
        val cursor = MatrixCursor(projection ?: COLUMNS)
        file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { row(cursor, "$parentDocumentId/${it.name}", it) }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        guard()
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        guard()
        val file = File(file(parentDocumentId), displayName)
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!made) throw IllegalStateException("could not create $displayName")
        return "$parentDocumentId/$displayName"
    }

    override fun deleteDocument(documentId: String) {
        guard()
        if (!file(documentId).deleteRecursively()) throw IllegalStateException("could not delete $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        guard()
        val from = file(documentId)
        val to = File(from.parentFile, displayName)
        if (to.exists() || !from.renameTo(to)) throw IllegalStateException("could not rename $documentId")
        return documentId.substringBeforeLast('/') + "/" + displayName
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = documentId.startsWith("$parentDocumentId/")

    private fun guard() {
        if (revoked) throw SecurityException("the grant on this tree was revoked")
    }

    private fun file(documentId: String): File {
        require(documentId.split('/').none { it == ".." || it.isEmpty() }) { "bad id: $documentId" }
        return File(base(context!!.filesDir), documentId)
    }

    private fun row(cursor: MatrixCursor, id: String, file: File) {
        val dir = file.isDirectory
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (dir) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_SIZE, if (dir) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(
                Document.COLUMN_FLAGS,
                Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                    if (dir) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE,
            )
    }

    companion object {
        const val AUTHORITY = "com.hazia.medicall.stubdocs"

        @Volatile var revoked = false

        fun base(filesDir: File) = File(filesDir, "saf-stub")

        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )
    }
}
