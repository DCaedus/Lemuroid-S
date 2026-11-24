package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.saves.SavesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Imports in-game saves (.srm) from another emulator's saves folder, e.g. RetroArch or a copy of
 * Lemuroid's. Lemuroid, RetroArch and Lemuroid-S all name saves after the ROM ("Game (USA).srm"),
 * so files are matched to the games in the library by name.
 *
 * Subfolders are searched too (RetroArch can sort saves by core or content folder).
 * An existing Lemuroid-S save is only replaced if the imported one is newer, and is backed up first.
 */
class SavesImporter(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val savesManager: SavesManager,
) {
    data class Result(val imported: Int, val skippedOlder: Int, val notInLibrary: Int)

    private data class FoundSave(val uri: Uri, val lastModified: Long)

    suspend fun importFrom(treeUri: Uri): Result =
        withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, FoundSave>()
            collectSaves(treeUri, DocumentsContract.getTreeDocumentId(treeUri), depth = 0, found)

            val games = retrogradeDb.gameDao().selectByLastIndexedAtLessThan(Long.MAX_VALUE)
            var imported = 0
            var skippedOlder = 0
            val matched = mutableSetOf<String>()

            for (game in games) {
                val fileName = savesManager.saveRAMFileName(game)
                val save = found[fileName] ?: continue
                matched.add(fileName)

                val current = savesManager.getSaveRAMInfo(game)
                if (current.exists && current.date >= save.lastModified) {
                    skippedOlder++
                    continue
                }

                val bytes =
                    runCatching {
                        appContext.contentResolver.openInputStream(save.uri)?.use { it.readBytes() }
                    }.onFailure { Timber.w(it, "Could not read $fileName") }.getOrNull()

                if (bytes == null || bytes.isEmpty()) continue

                savesManager.backupSaveRAM(game)
                savesManager.setSaveRAM(game, bytes)
                imported++
            }

            Result(imported, skippedOlder, (found.keys - matched).size)
        }

    private fun collectSaves(
        treeUri: Uri,
        folderId: String,
        depth: Int,
        found: MutableMap<String, FoundSave>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
        val subFolders = mutableListOf<String>()

        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2)
                val lastModified = cursor.getLong(3)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    subFolders.add(id)
                } else if (name.endsWith(".srm", ignoreCase = true)) {
                    val save = FoundSave(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), lastModified)
                    // If the same game appears in several subfolders, keep the newest save.
                    val previous = found[name]
                    if (previous == null || previous.lastModified < lastModified) found[name] = save
                }
            }
        }

        if (depth < MAX_DEPTH) {
            subFolders.forEach { collectSaves(treeUri, it, depth + 1, found) }
        }
    }

    companion object {
        private const val MAX_DEPTH = 3
    }
}
