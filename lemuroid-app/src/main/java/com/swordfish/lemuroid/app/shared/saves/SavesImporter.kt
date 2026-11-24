package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage
import com.swordfish.lemuroid.lib.saves.RetroArchStates
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

            val files = mutableListOf<SourceFile>()
            if (savesSource == null && statesSource == null) {
                // Another emulator's folder (e.g. Pizza Boy): only battery saves, with a limited search.
                collectBatterySaves(picked, pickedName ?: "", files, depth = 0, budget = intArrayOf(MAX_ENTRIES))
            } else {
                savesSource?.let { collect(it, SAVES, isStates = false, files, depth = 0) }
                statesSource?.let { collect(it, STATES, isStates = true, files, depth = 0) }
            }
            if (files.isEmpty()) return@withContext null

            // RetroArch's folder names first; the game database only for files they don't place.
            val unplaced = files.filter { folderSystem(it.parentName) == null }.map { stemOf(it.file.name) }.toSet()
            val systemsByStem =
                runCatching { libretroDBManager.findSystemsByFileStem(unplaced) }
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
                copy(source, target, system, counters)
            }
            Result(counters.saves, counters.states, counters.skipped)
        }

    private class Target(val folder: ExternalSavesStorage.SaveFolder) {
        val existing: Map<String, Long> by lazy { folder.lastModifiedTimes() }
    }

    /** ES-DE system folder for a file: from its folder name (RetroArch core or system), else the game database. */
    private fun systemFolderFor(
        source: SourceFile,
        systemsByStem: Map<String, String>,
    ): String? {
        folderSystem(source.parentName)?.let { return it }
        return systemsByStem[stemOf(source.file.name)]?.let { MainFolder.systemFolderName(it) }
    }

    private fun folderSystem(folderName: String): String? {
        val name = folderName.lowercase()
        if (name in MainFolder.SYSTEM_FOLDERS.values) return name
        return CORE_FOLDERS[name]
    }

    /**
     * Searches another emulator's folder for battery saves (.srm, .sav). Limited to a few folder
     * levels and [MAX_ENTRIES] entries, so picking a big unrelated folder can't take too long.
     */
    private fun collectBatterySaves(
        folder: DocumentFolder,
        folderName: String,
        files: MutableList<SourceFile>,
        depth: Int,
        budget: IntArray,
    ) {
        for (child in folder.children().values) {
            if (--budget[0] < 0) return
            if (child.isDirectory) {
                if (depth < MAX_DEPTH) {
                    val subFolder = DocumentFolder(appContext.contentResolver, child.uri)
                    collectBatterySaves(subFolder, child.name, files, depth + 1, budget)
                }
            } else if (BATTERY_SAVE.matches(child.name)) {
                files.add(SourceFile(folder, child, folderName, isStates = false))
            }
        }
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

    /**
     * Copies one file, doing the conversion work now rather than when a game is opened:
     *  - battery saves are stored as "<name>.srm"; GBA saves with extra data after the save
     *    (e.g. Pizza Boy) are trimmed to the real save (GBA saves are at most 128 KB)
     *  - RetroArch states are unpacked (rzip / RASTATE) to the core's plain state data; their
     *    screenshots (.png) are skipped
     */
    private fun copy(
        source: SourceFile,
        target: Target,
        system: String?,
        counters: Counters,
    ) {
        val file = source.file
        if (source.isStates && file.name.endsWith(".png", ignoreCase = true)) return

        val targetName = if (source.isStates) file.name else file.name.substringBeforeLast('.') + ".srm"
        val targetTime = target.existing[targetName]
        if (targetTime != null && targetTime >= file.lastModified) {
            counters.skipped++
            return
        }
        if (!source.isStates && hasNewerSave(targetName, file.lastModified)) {
            counters.skipped++
            return
        }

        runCatching {
            val bytes = source.folder.read(file) ?: return
            val converted =
                when {
                    source.isStates -> RetroArchStates.decode(bytes)
                    system == GBA && bytes.size > GBA_MAX_SAVE_SIZE -> bytes.copyOf(GBA_MAX_SAVE_SIZE)
                    else -> bytes
                }
            if (converted == null) {
                counters.skipped++
                return
            }
            target.folder.write(targetName, converted)
            if (source.isStates) counters.states++ else counters.saves++
        }.onFailure { Timber.w(it, "Could not import ${file.name}") }
    }

    /** "Game.state1.png" / "Game.state.auto" / "Game.srm" -> "Game". */
    private fun stemOf(name: String): String = name.replace(SAVE_SUFFIXES, "")

    /** Lemuroid-S keeps battery saves by name, so a newer one there is kept. */
    private fun hasNewerSave(
        saveName: String,
        lastModified: Long,
    ): Boolean {
        val current = File(directoriesManager.getSavesDirectory(), saveName)
        return current.exists() && current.length() > 0 && current.lastModified() >= lastModified
    }

    companion object {
        private const val SAVES = "saves"
        private const val STATES = "states"
        private const val MAX_DEPTH = 2
        private const val MAX_ENTRIES = 5000
        private const val GBA = "gba"
        private const val GBA_MAX_SAVE_SIZE = 128 * 1024
        private val BATTERY_SAVE = Regex(""".+\.(srm|sav)$""", RegexOption.IGNORE_CASE)
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
                // Pizza Boy (standalone GBA / GBC emulators)
                "pizzaboygba" to "gba",
                "pizza boy gba" to "gba",
            )
    }
}
