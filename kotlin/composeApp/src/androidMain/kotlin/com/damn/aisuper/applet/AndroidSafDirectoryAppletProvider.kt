package com.damn.aisuper.applet

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.damn.aisuper.runtime.AppletResourceLoader
import java.io.FileNotFoundException

class AndroidSafDirectoryAppletProvider(
    private val context: Context,
    private val treeUri: Uri,
    private val rootDocumentId: String
) : AppletProvider {

    private var cachedIndex: Map<String, Uri>? = null
    private val indexLock = Any()

    override fun createLoader(): AppletResourceLoader = AppletResourceLoader { path ->
        val cleanPath = path.removePrefix("/").replace('\\', '/')
        val uri = findUri(cleanPath)
            ?: throw FileNotFoundException("Resource '$cleanPath' not found in SAF folder '$rootDocumentId'")
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("Unable to read SAF resource '$cleanPath' ($uri)")
    }

    private fun findUri(cleanPath: String): Uri? {
        synchronized(indexLock) {
            val current = cachedIndex ?: buildIndex().also { cachedIndex = it }
            current[cleanPath]?.let { return it }
            val rebuilt = buildIndex().also { cachedIndex = it }
            return rebuilt[cleanPath]
        }
    }

    private fun buildIndex(): Map<String, Uri> {
        val map = LinkedHashMap<String, Uri>()
        collectDocuments(rootDocumentId, "", map)
        return map
    }

    private fun collectDocuments(dirDocId: String, prefix: String, out: MutableMap<String, Uri>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (name == ".git" || name == "node_modules") continue
                    collectDocuments(docId, relPath, out)
                } else {
                    out[relPath] = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }
            }
        }
    }
}
