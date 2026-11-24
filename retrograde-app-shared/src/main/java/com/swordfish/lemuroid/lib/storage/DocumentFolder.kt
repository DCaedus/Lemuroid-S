package com.swordfish.lemuroid.lib.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A folder the user granted access to through Android's folder picker (Storage Access Framework).
 * [uri] is a document uri built from the picked tree, so it can point to any subfolder of it.
 */
class DocumentFolder(
    private val contentResolver: ContentResolver,
    val uri: Uri,
) {
    data class Child(
        val name: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val lastModified: Long,
        val size: Long,
    )

    private val documentId: String get() = DocumentsContract.getDocumentId(uri)

    fun children(): Map<String, Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        val result = mutableMapOf<String, Child>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                result[name] =
                    Child(
                        name = name,
                        uri = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)),
                        isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        lastModified = cursor.getLong(3),
                        size = cursor.getLong(4),
                    )
            }
        }
        return result
    }

    fun isEmpty(): Boolean = children().isEmpty()

    /** Returns the subfolder [name], creating it if needed (and allowed). */
    fun subFolder(
        name: String,
        create: Boolean = true,
    ): DocumentFolder? {
        val existing = children()[name]
        if (existing != null) return if (existing.isDirectory) DocumentFolder(contentResolver, existing.uri) else null
        if (!create) return null
        val created =
            DocumentsContract.createDocument(contentResolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: return null
        return DocumentFolder(contentResolver, created)
    }

    /** Follows [path] (e.g. "states", "mgba"), creating missing folders if [create] is true. */
    fun subFolder(
        path: List<String>,
        create: Boolean = true,
    ): DocumentFolder? {
        var current: DocumentFolder? = this
        for (name in path) current = current?.subFolder(name, create)
        return current
    }

    fun read(child: Child): ByteArray? {
        return contentResolver.openInputStream(child.uri)?.use { it.readBytes() }
    }

    fun read(name: String): ByteArray? = children()[name]?.let { read(it) }

    fun write(
        name: String,
        data: ByteArray,
        existing: Child? = children()[name],
    ): Uri {
        val fileUri =
            existing?.uri
                ?: DocumentsContract.createDocument(contentResolver, uri, MIME_TYPE_BINARY, name)
                ?: throw IllegalStateException("Could not create $name")
        contentResolver.openOutputStream(fileUri, "wt")?.use { it.write(data) }
            ?: throw IllegalStateException("Could not open $name")
        return fileUri
    }

    companion object {
        const val MIME_TYPE_BINARY = "application/octet-stream"
    }
}
