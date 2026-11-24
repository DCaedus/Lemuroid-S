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

    /**
     * Returns the subfolder [name], creating it if needed (and allowed). Lookups and creation are
     * serialized and remembered, so parallel callers (e.g. many covers downloading at once) never
     * create the same folder twice (Android would name the copies "name (1)").
     */
    fun subFolder(
        name: String,
        create: Boolean = true,
    ): DocumentFolder? {
        val key = "$uri/$name"
        knownFolders[key]?.let { return DocumentFolder(contentResolver, it) }

        synchronized(folderLock) {
            knownFolders[key]?.let { return DocumentFolder(contentResolver, it) }
            val existing = children()[name]
            val folderUri =
                when {
                    existing != null && existing.isDirectory -> existing.uri
                    existing != null -> return null
                    !create -> return null
                    else ->
                        DocumentsContract.createDocument(
                            contentResolver,
                            uri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            name,
                        ) ?: return null
                }
            knownFolders[key] = folderUri
            return DocumentFolder(contentResolver, folderUri)
        }
    }

    fun delete(child: Child): Boolean {
        knownFolders.keys.removeAll { it.startsWith(child.uri.toString()) || it.endsWith("/${child.name}") }
        return runCatching { DocumentsContract.deleteDocument(contentResolver, child.uri) }.getOrDefault(false)
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

    /**
     * Writes [name] so that an interrupted write never leaves a half-written file in its place: the
     * data goes to "[name].tmp" first, which then replaces the old file. If the app is killed
     * between the two steps, "[name].tmp" holds the complete new data (see [TEMP_SUFFIX] readers).
     * [children] is a listing of this folder taken before the write.
     */
    fun writeSafely(
        name: String,
        data: ByteArray,
        children: Map<String, Child> = children(),
    ) {
        val tempName = name + TEMP_SUFFIX
        children[tempName]?.let { deleteFile(it.uri) }
        val existing = children[name]
        if (existing == null) {
            write(name, data, null)
            return
        }
        val tempUri = write(tempName, data, null)
        deleteFile(existing.uri)
        val renamed = runCatching { DocumentsContract.renameDocument(contentResolver, tempUri, name) }.getOrNull()
        if (renamed == null) {
            // This storage can't rename files: write the file itself, then drop the temporary copy.
            write(name, data, null)
            deleteFile(tempUri)
        }
    }

    fun deleteFile(fileUri: Uri): Boolean {
        return runCatching { DocumentsContract.deleteDocument(contentResolver, fileUri) }.getOrDefault(false)
    }

    companion object {
        const val MIME_TYPE_BINARY = "application/octet-stream"
        const val TEMP_SUFFIX = ".tmp"

        private val folderLock = Any()
        private val knownFolders = java.util.concurrent.ConcurrentHashMap<String, Uri>()

        /** Forget remembered folders, e.g. after the Lemuroid-S folder is chosen again. */
        fun clearKnownFolders() = knownFolders.clear()
    }
}
