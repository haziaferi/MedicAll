package com.hazia.medicall

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.UUID

/** A fresh tree in the debug build's [StubDocumentsProvider], with its files reachable directly for checks. */
class StubTree(context: Context) {
    private val id = "tree-${UUID.randomUUID()}"
    val dir: File = File(StubDocumentsProvider.base(context.filesDir), id).also { it.mkdirs() }
    val uri: Uri = DocumentsContract.buildTreeDocumentUri(StubDocumentsProvider.AUTHORITY, id)

    fun clean() {
        StubDocumentsProvider.revoked = false
        StubDocumentsProvider.renameUnsupported = false
        dir.deleteRecursively()
    }
}

/** Stops the process, as a crash would: nothing that follows runs, and no catch for an I/O error sees it. */
class Crash : Error("invented crash")
