package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * "Import Saves from other Emulators" (RetroArch): copies everything in RetroArch's "saves" and
 * "states" folders, as is, into Lemuroid-S: the top of Lemuroid-S/saves and Lemuroid-S/states, or
 * the top of the ROMs folder when saves are kept there. No ROM matching, so it's quick.
 *
 * Lemuroid-S picks these files up when a game is opened: battery saves by name ("Game.srm", or
 * ".sav"), and RetroArch states ("Game.state.auto", "Game.state", "Game.state1"...) are converted
 * to its resume point and save slots.
 */
class SavesImporter(
    private val appContext: Context,
    private val externalSavesStorage: ExternalSavesStorage,
    private val directoriesManager: DirectoriesManager,
) {
    data class Result(
        val saves: Int,
        val states: Int,
        /** Files not copied because Lemuroid-S already had a newer copy. */
        val skipped: Int,
    )

    private class Counters {
        var saves = 0
        var states = 0
        var skipped = 0
    }

    /** Returns null if RetroArch's saves/states folders or the Lemuroid-S folder can't be found. */
    suspend fun importFrom(treeUri: Uri): Result? =
        withContext(Dispatchers.IO) {
            val savesTarget = externalSavesStorage.importedSavesFolder() ?: return@withContext null
            val statesTarget = externalSavesStorage.importedStatesFolder() ?: return@withContext null

            val picked =
                DocumentFolder(
                    appContext.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                )
            val pickedName = MainFolder.displayName(appContext, picked.uri)?.lowercase()
            val children = picked.children().values.filter { it.isDirectory }

            fun child(name: String) =
                children.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.let { DocumentFolder(appContext.contentResolver, it.uri) }

            // Works with RetroArch's main folder, or its saves/states folder picked directly.
            val savesSource = if (pickedName == SAVES) picked else child(SAVES)
            val statesSource = if (pickedName == STATES) picked else child(STATES)
            if (savesSource == null && statesSource == null) return@withContext null

            val counters = Counters()
            savesSource?.let { copyAll(it, savesTarget, isSaves = true, counters, depth = 0) }
            statesSource?.let { copyAll(it, statesTarget, isSaves = false, counters, depth = 0) }
            Result(counters.saves, counters.states, counters.skipped)
        }

    /**
     * Copies every file in [source] (and its subfolders, which RetroArch uses when sorting saves by
     * core) into [target], flattened. Battery saves that Lemuroid-S already has a newer copy of are
     * skipped.
     */
    private fun copyAll(
        source: DocumentFolder,
        target: ExternalSavesStorage.SaveFolder,
        isSaves: Boolean,
        counters: Counters,
        depth: Int,
    ) {
        val existing = target.lastModifiedTimes()
        for (file in source.children().values) {
            if (file.isDirectory) {
                if (depth < MAX_DEPTH) {
                    copyAll(DocumentFolder(appContext.contentResolver, file.uri), target, isSaves, counters, depth + 1)
                }
                continue
            }

            val targetTime = existing[file.name]
            if (targetTime != null && targetTime >= file.lastModified) {
                counters.skipped++
                continue
            }
            if (isSaves && hasNewerSave(file)) {
                counters.skipped++
                continue
            }

            runCatching { source.read(file)?.let { target.write(file.name, it) } }
                .onSuccess {
                    if (it != null) {
                        if (isSaves) counters.saves++ else counters.states++
                    }
                }
                .onFailure { Timber.w(it, "Could not import ${file.name}") }
        }
    }

    /** Lemuroid-S keeps battery saves by name, so a newer one there is kept. */
    private fun hasNewerSave(file: DocumentFolder.Child): Boolean {
        val saveName = file.name.substringBeforeLast('.') + ".srm"
        val current = File(directoriesManager.getSavesDirectory(), saveName)
        return current.exists() && current.length() > 0 && current.lastModified() >= file.lastModified
    }

    companion object {
        private const val SAVES = "saves"
        private const val STATES = "states"
        private const val MAX_DEPTH = 2
    }
}
