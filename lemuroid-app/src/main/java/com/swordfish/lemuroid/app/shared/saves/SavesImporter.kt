package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * "Import Saves from RetroArch": copies everything in RetroArch's "saves" and "states" folders, as
 * is, into Lemuroid-S's saves and states folders (or the ROMs folder when saves are kept there).
 *
 * Files are sorted into system folders (saves/gba, states/snes...) without looking at the user's
 * ROMs: the save's name is looked up in the game database that ships with the app, and RetroArch's
 * per-core folders (e.g. "mGBA") are used too. Files that can't be sorted go to the top folder.
 *
 * Lemuroid-S picks these files up when a game is opened: battery saves by name ("Game.srm", or
 * ".sav"), and RetroArch states ("Game.state.auto", "Game.state", "Game.state1"...) are converted
 * to its resume point and save slots.
 */
class SavesImporter(
    private val appContext: Context,
    private val externalSavesStorage: ExternalSavesStorage,
    private val directoriesManager: DirectoriesManager,
    private val libretroDBManager: LibretroDBManager,
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

    private data class SourceFile(
        val folder: DocumentFolder,
        val file: DocumentFolder.Child,
        val parentName: String,
        val isStates: Boolean,
    )

    /** Returns null if RetroArch's saves/states folders or the Lemuroid-S folder can't be found. */
    suspend fun importFrom(treeUri: Uri): Result? =
        withContext(Dispatchers.IO) {
            if (externalSavesStorage.importedSavesFolder() == null) return@withContext null

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

            val files = mutableListOf<SourceFile>()
            savesSource?.let { collect(it, SAVES, isStates = false, files, depth = 0) }
            statesSource?.let { collect(it, STATES, isStates = true, files, depth = 0) }

            val systemsByStem =
                runCatching { libretroDBManager.findSystemsByFileStem(files.map { stemOf(it.file.name) }.toSet()) }
                    .onFailure { Timber.w(it, "Game database lookup failed") }
                    .getOrDefault(emptyMap())

            val counters = Counters()
            val targets = mutableMapOf<Pair<Boolean, String?>, Target?>()
            for (source in files) {
                val system = systemFolderFor(source, systemsByStem)
                val target =
                    targets.getOrPut(source.isStates to system) {
                        externalSavesStorage.importTargetFolder(source.isStates, system)?.let { Target(it) }
                    } ?: continue
                copy(source, target, counters)
            }
            Result(counters.saves, counters.states, counters.skipped)
        }

    private class Target(val folder: ExternalSavesStorage.SaveFolder) {
        val existing: Map<String, Long> by lazy { folder.lastModifiedTimes() }
    }

    /** ES-DE system folder for a file: from the game database, else from RetroArch's core folder name. */
    private fun systemFolderFor(
        source: SourceFile,
        systemsByStem: Map<String, String>,
    ): String? {
        systemsByStem[stemOf(source.file.name)]?.let { return MainFolder.systemFolderName(it) }
        val folderName = source.parentName.lowercase()
        if (folderName in MainFolder.SYSTEM_FOLDERS.values) return folderName
        return CORE_FOLDERS[folderName]
    }

    private fun collect(
        folder: DocumentFolder,
        folderName: String,
        isStates: Boolean,
        files: MutableList<SourceFile>,
        depth: Int,
    ) {
        for (child in folder.children().values) {
            if (child.isDirectory) {
                if (depth < MAX_DEPTH) {
                    val subFolder = DocumentFolder(appContext.contentResolver, child.uri)
                    collect(subFolder, child.name, isStates, files, depth + 1)
                }
            } else {
                files.add(SourceFile(folder, child, folderName, isStates))
            }
        }
    }

    private fun copy(
        source: SourceFile,
        target: Target,
        counters: Counters,
    ) {
        val file = source.file
        val targetTime = target.existing[file.name]
        if (targetTime != null && targetTime >= file.lastModified) {
            counters.skipped++
            return
        }
        if (!source.isStates && hasNewerSave(file)) {
            counters.skipped++
            return
        }
        runCatching { source.folder.read(file)?.let { target.folder.write(file.name, it) } }
            .onSuccess {
                if (it != null) {
                    if (source.isStates) counters.states++ else counters.saves++
                }
            }
            .onFailure { Timber.w(it, "Could not import ${file.name}") }
    }

    /** "Game.state1.png" / "Game.state.auto" / "Game.srm" -> "Game". */
    private fun stemOf(name: String): String = name.replace(SAVE_SUFFIXES, "")

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
        private val SAVE_SUFFIXES = Regex("""(\.(srm|sav|png|auto|state\d*))+$""", RegexOption.IGNORE_CASE)

        /**
         * RetroArch's per-core folder names (when "sort saves by core" is on), for cores that run a
         * single system. Cores that run several systems (e.g. Gambatte, Genesis Plus GX) rely on the
         * game database instead.
         */
        private val CORE_FOLDERS =
            mapOf(
                "fceumm" to "nes",
                "nestopia" to "nes",
                "snes9x" to "snes",
                "mgba" to "gba",
                "pcsx-rearmed" to "psx",
                "mupen64plus-next" to "n64",
                "ppsspp" to "psp",
                "melonds" to "nds",
                "melonds ds" to "nds",
                "desmume" to "nds",
                "finalburn neo" to "fbneo",
                "mame 2003-plus" to "mame",
                "stella" to "atari2600",
                "prosystem" to "atari7800",
                "handy" to "atarilynx",
                "beetle pce fast" to "pcengine",
                "dosbox-pure" to "dos",
                "citra" to "n3ds",
            )
    }
}
