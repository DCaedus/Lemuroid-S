package com.swordfish.lemuroid.lib.saves

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber
import java.io.File

/**
 * Optional second location for in-game saves (.srm), like RetroArch's "write saves to content
 * directory" and custom save directory options.
 *
 * Lemuroid keeps its own copy in app storage as before; when a location is chosen, saves are also
 * written there, and on load the copy found there is preferred. This way nothing is lost if the
 * folder becomes unavailable (SD card removed, permission revoked).
 */
class ExternalSavesStorage(private val appContext: Context) {
    enum class Location(val prefValue: String) {
        APP_STORAGE("app"),
        NEXT_TO_ROMS("roms"),
        CUSTOM_FOLDER("custom"),
    }

    private val contentResolver: ContentResolver get() = appContext.contentResolver

    fun getLocation(): Location {
        val value = preferences().getString(appContext.getString(R.string.pref_key_saves_location), null)
        return Location.values().firstOrNull { it.prefValue == value } ?: Location.APP_STORAGE
    }

    /** Returns the save from the chosen location, or null if there is none (or no location is chosen). */
    fun read(
        game: Game,
        fileName: String,
    ): ByteArray? {
        val folder = resolveFolder(game) ?: return null
        return runCatching { folder.read(fileName) }
            .onFailure { Timber.w(it, "Could not read external save $fileName") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Writes the save to the chosen location. Failures are logged: the app storage copy still exists. */
    fun write(
        game: Game,
        fileName: String,
        data: ByteArray,
    ) {
        val folder = resolveFolder(game) ?: return
        runCatching { folder.write(fileName, data) }
            .onFailure { Timber.w(it, "Could not write external save $fileName") }
    }

    private fun resolveFolder(game: Game): SaveFolder? {
        return when (getLocation()) {
            Location.APP_STORAGE -> null
            Location.NEXT_TO_ROMS -> romFolder(game)
            Location.CUSTOM_FOLDER -> customFolder()
        }
    }

    private fun customFolder(): SaveFolder? {
        val treeUri =
            preferences().getString(appContext.getString(R.string.pref_key_saves_custom_folder), null)
                ?.let { Uri.parse(it) }
                ?: return null
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return SaveFolder.Document(contentResolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId))
    }

    private fun romFolder(game: Game): SaveFolder? {
        val romUri = Uri.parse(game.fileUri)
        if (romUri.scheme == ContentResolver.SCHEME_FILE) {
            return romUri.path?.let { File(it).parentFile }?.let { SaveFolder.Local(it) }
        }
        if (romUri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val parentId = findParentDocumentId(romUri) ?: return null
        return SaveFolder.Document(contentResolver, DocumentsContract.buildDocumentUriUsingTree(romUri, parentId))
    }

    private fun findParentDocumentId(documentUri: Uri): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = runCatching { DocumentsContract.findDocumentPath(contentResolver, documentUri) }.getOrNull()
            val ids = path?.path
            if (ids != null && ids.size >= 2) return ids[ids.size - 2]
        }
        // Fallback for local storage providers, whose ids look like "primary:Roms/GBA/game.gba".
        val documentId = DocumentsContract.getDocumentId(documentUri)
        return documentId.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
    }

    private fun preferences() = SharedPreferencesHelper.getSharedPreferences(appContext)

    private sealed interface SaveFolder {
        fun read(fileName: String): ByteArray?

        fun write(
            fileName: String,
            data: ByteArray,
        )

        class Local(private val directory: File) : SaveFolder {
            override fun read(fileName: String): ByteArray? {
                val file = File(directory, fileName)
                return if (file.exists()) file.readBytes() else null
            }

            override fun write(
                fileName: String,
                data: ByteArray,
            ) {
                File(directory, fileName).writeBytesAtomic(data)
            }
        }

        class Document(
            private val contentResolver: ContentResolver,
            private val directoryUri: Uri,
        ) : SaveFolder {
            override fun read(fileName: String): ByteArray? {
                val fileUri = findChild(fileName) ?: return null
                return contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            }

            override fun write(
                fileName: String,
                data: ByteArray,
            ) {
                val fileUri =
                    findChild(fileName)
                        ?: DocumentsContract.createDocument(contentResolver, directoryUri, MIME_TYPE, fileName)
                        ?: throw IllegalStateException("Could not create $fileName")
                contentResolver.openOutputStream(fileUri, "wt")?.use { it.write(data) }
                    ?: throw IllegalStateException("Could not open $fileName")
            }

            private fun findChild(fileName: String): Uri? {
                val directoryId = DocumentsContract.getDocumentId(directoryUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, directoryId)
                val projection =
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    )
                contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == fileName) {
                            return DocumentsContract.buildDocumentUriUsingTree(directoryUri, cursor.getString(0))
                        }
                    }
                }
                return null
            }
        }
    }

    companion object {
        private const val MIME_TYPE = "application/octet-stream"
    }
}
