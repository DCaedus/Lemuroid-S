package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.library.GameSystem
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
import java.util.zip.ZipInputStream

/**
 * Import Saves: copies battery saves and save states from other emulators into Lemuroid-S's saves
 * and states folders (or the ROMs folder when saves are kept there), sorted into system folders.
 * Sources:
 *  - a folder: RetroArch's folder (its "saves" and "states"), a copy of Lemuroid's files folder
 *    ("saves", "states/<core>"), Pizza Boy's "save" folder, or any folder with saves
 *  - a ZIP file, e.g. Pizza Boy's "export saves"
 *
 * No ROM matching: the system comes from folder names (RetroArch / Lemuroid core folders, system
 * folders), the ROM extension in the name ("Game.gba.state"), or the game database bundled with the
 * app ("Pokemon - Emerald Version (USA, Europe)" -> gba). Unknown files go to the top folder, where
 * games still find them by name.
 *
 * Conversion happens here rather than when a game opens: RetroArch states are unpacked (rzip,
 * RASTATE), ".sav" saves become ".srm", GBA saves with extra data appended (Pizza Boy) are trimmed
 * to the 128 KB save, and other emulators' own state formats (Pizza Boy ".stat"), screenshots and
 * backups are skipped.
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
        /** Files not copied: in a format Lemuroid-S can't use, or older copies of a file imported in the same go. */
        val skipped: Int,
    )

    private class Counters {
        var saves = 0
        var states = 0
        var skipped = 0
    }

    private class SourceFile(
        val name: String,
        val lastModified: Long,
        val parentName: String,
        val isState: Boolean,
        val read: () -> ByteArray?,
    )

    private class Target(val folder: ExternalSavesStorage.SaveFolder) {
        /** Source dates of what this import wrote: two sources can map to one name (Game.srm and Game.sav). */
        val imported = mutableMapOf<String, Long>()
    }

    /** Imports from a picked folder (tree uri) or a picked ZIP file (document uri). Null if nothing usable. */
    suspend fun importFrom(uri: Uri): Result? =
        withContext(Dispatchers.IO) {
            if (externalSavesStorage.importedSavesFolder() == null) return@withContext null

            val files =
                if (isZip(uri)) collectFromZip(uri) else collectFromFolder(uri)
            if (files.isEmpty()) return@withContext null

            // Folder names and ROM extensions first; the game database only for what's left.
            val unplaced = files.filter { quickSystem(it) == null }.map { gameStem(it.name) }.toSet()
            val systemsByStem =
                runCatching { libretroDBManager.findSystemsByFileStem(unplaced) }
                    .onFailure { Timber.w(it, "Game database lookup failed") }
                    .getOrDefault(emptyMap())

            val counters = Counters()
            val targets = mutableMapOf<Pair<Boolean, String?>, Target?>()
            val targetFor = { isState: Boolean, system: String? ->
                targets.getOrPut(isState to system) {
                    externalSavesStorage.importTargetFolder(isState, system)?.let { Target(it) }
                }
            }
            for (file in files) {
                val databaseSystem = systemsByStem[gameStem(file.name)]?.let { MainFolder.systemFolderName(it) }
                val system = quickSystem(file) ?: databaseSystem
                val target = targetFor(file.isState, system) ?: continue
                copy(file, target, system, counters) { targetFor(true, system) }
            }
            Result(counters.saves, counters.states, counters.skipped)
        }

    // region Sources

    private fun isZip(uri: Uri): Boolean {
        // A picked folder (tree), not a file. isTreeUri needs Android 7, so check the path on Android 6.
        if (uri.pathSegments.firstOrNull() == "tree") return false
        val type = appContext.contentResolver.getType(uri)
        return type == "application/zip" || type == "application/x-zip-compressed" ||
            uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) == true
    }

    private fun collectFromZip(uri: Uri): List<SourceFile> {
        val files = mutableListOf<SourceFile>()
        val stream = appContext.contentResolver.openInputStream(uri) ?: return files
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val path = entry.name.replace('\\', '/')
                val name = path.substringAfterLast('/')
                val kind = classify(name) ?: continue
                val data = zip.readBytes()
                val parent = path.substringBeforeLast('/', "").substringAfterLast('/')
                files.add(SourceFile(name, entry.time, parent, kind == Kind.STATE) { data })
            }
        }
        return files
    }

    private fun collectFromFolder(treeUri: Uri): List<SourceFile> {
        val picked =
            DocumentFolder(
                appContext.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
            )
        val files = mutableListOf<SourceFile>()
        val pickedName = MainFolder.displayName(appContext, picked.uri) ?: ""
        val saveFolders = picked.children().values.filter { it.isDirectory && it.name.lowercase() in SAVE_FOLDER_NAMES }

        if (saveFolders.isNotEmpty()) {
            // An emulator's main folder (RetroArch, Lemuroid copy...): only its saves/states folders.
            for (folder in saveFolders) {
                collect(
                    DocumentFolder(appContext.contentResolver, folder.uri),
                    folder.name,
                    files,
                    1,
                    intArrayOf(MAX_ENTRIES),
                )
            }
        } else {
            // Any other folder: searched a few levels deep, with a limit so big folders can't stall it.
            collect(picked, pickedName, files, 0, intArrayOf(MAX_ENTRIES))
        }
        return files
    }

    private fun collect(
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
                    collect(DocumentFolder(appContext.contentResolver, child.uri), child.name, files, depth + 1, budget)
                }
                continue
            }
            val kind = classify(child.name) ?: continue
            files.add(SourceFile(child.name, child.lastModified, folderName, kind == Kind.STATE) { folder.read(child) })
        }
    }

    private enum class Kind { BATTERY, STATE }

    /** What a file is, by name. Null for files Lemuroid-S can't use (Pizza Boy .stat, screenshots, backups...). */
    private fun classify(name: String): Kind? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".srm") || lower.endsWith(".sav") -> Kind.BATTERY
            STATE_FILE.containsMatchIn(lower) -> Kind.STATE
            else -> null
        }
    }

    // endregion

    // region Systems

    /** System from folder names (RetroArch/Lemuroid core folders, ES-DE system folders) or the ROM extension. */
    private fun quickSystem(file: SourceFile): String? {
        val folder = file.parentName.lowercase()
        if (folder in MainFolder.SYSTEM_FOLDERS.values) return folder
        CORE_FOLDERS[folder]?.let { return it }

        // Lemuroid names states after the ROM file: "Game.gba.state" -> .gba
        val romExtension = gameStem(file.name).substringAfterLast('.', "").lowercase()
        if (romExtension.isEmpty()) return null
        val system = GameSystem.findByUniqueFileExtension(romExtension) ?: return null
        return MainFolder.SYSTEM_FOLDERS[system.id]
    }

    /** "Game.state1" / "Game.state.auto" / "Game.srm" -> "Game"; "Game.gba.slot1.metadata" -> "Game.gba". */
    private fun gameStem(name: String): String = name.replace(SAVE_SUFFIXES, "")

    // endregion

    /** Copies one file, converting it on the way (see class description). */
    private fun copy(
        file: SourceFile,
        target: Target,
        system: String?,
        counters: Counters,
        statesTarget: () -> Target?,
    ) {
        val targetName = if (file.isState) retroArchStateName(file.name) else file.name.substringBeforeLast('.') + ".srm"
        // Within one import the newest of two sources with the same name wins.
        val importedTime = target.imported[targetName]
        if (importedTime != null && importedTime >= file.lastModified) {
            counters.skipped++
            return
        }

        runCatching {
            val bytes = file.read() ?: return
            val converted =
                when {
                    file.name.endsWith(".metadata") -> bytes
                    // Stored like Lemuroid-S's own states: RetroArch's rzip format.
                    file.isState -> RetroArchStates.decode(bytes)?.let { RetroArchStates.encodeRzip(it) }
                    // Pizza Boy appends extra data after GBA saves (the save itself is the first
                    // 128 KB, the most a GBA game uses); keep just the save.
                    system == GBA && bytes.size > GBA_MAX_SAVE_SIZE -> bytes.copyOf(GBA_MAX_SAVE_SIZE)
                    else -> bytes
                }
            if (converted == null) {
                counters.skipped++
                return
            }
            // Lemuroid-S: imported saves replace Lemuroid-S's whatever their dates; the replaced file
            // is kept next to it as "<name>.backup1" (".backup2"... when there are older backups).
            if (importedTime == null) {
                target.folder.backup(targetName)
                if (!file.isState) {
                    backupFallbackSave(targetName)
                    backupAutoSaveState(targetName, statesTarget())
                }
            }
            target.folder.write(targetName, converted)
            target.imported[targetName] = file.lastModified
            if (file.isState) counters.states++ else counters.saves++
        }.onFailure { Timber.w(it, "Could not import ${file.name}") }
    }

    /**
     * Lemuroid-S names states like RetroArch ("Game.state.auto", "Game.state1"...). Lemuroid's own
     * names ("Game.gba.state" for the resume point, "Game.gba.slot1"...) are renamed to match.
     */
    private fun retroArchStateName(name: String): String {
        LEMUROID_SLOT.matchEntire(name)?.let { m ->
            return "${m.groupValues[1]}.state${m.groupValues[3]}${m.groupValues[4]}"
        }
        LEMUROID_AUTO.matchEntire(name)?.let { m ->
            if (GameSystem.findByUniqueFileExtension(m.groupValues[2].lowercase()) != null) {
                return "${m.groupValues[1]}.state.auto${m.groupValues[3]}"
            }
        }
        return name
    }

    /**
     * With "Autosave on exit" a game opens from its autosave state, which holds the old in-game save
     * too: it's kept as a backup so the game starts from the imported save. An autosave state
     * imported in the same go is left alone.
     */
    private fun backupAutoSaveState(
        saveName: String,
        statesTarget: Target?,
    ) {
        if (statesTarget == null) return
        val autoSaveName = saveName.removeSuffix(".srm") + ".state.auto"
        if (autoSaveName in statesTarget.imported) return
        runCatching {
            statesTarget.folder.backup(autoSaveName)
            statesTarget.folder.backup("$autoSaveName.metadata")
        }.onFailure { Timber.w(it, "Could not keep $autoSaveName as a backup") }
    }

    /**
     * A copy of the save left in app storage (when writing to the folder failed) would otherwise be
     * moved over the imported one: it's kept as a backup there too.
     */
    private fun backupFallbackSave(saveName: String) {
        val current = File(directoriesManager.getSavesDirectory(), saveName)
        if (!current.exists()) return
        val backup =
            generateSequence(1) { it + 1 }
                .map { File(current.parentFile, "$saveName${ExternalSavesStorage.BACKUP_SUFFIX}$it") }
                .first { !it.exists() }
        if (!current.renameTo(backup)) current.delete()
    }

    companion object {
        private const val MAX_DEPTH = 2
        private const val MAX_ENTRIES = 5000
        private const val GBA = "gba"
        private const val GBA_MAX_SAVE_SIZE = 128 * 1024
        private val LEMUROID_SLOT = Regex("""^(.+)\.([A-Za-z0-9]+)\.slot(\d)(\.metadata)?$""")
        private val LEMUROID_AUTO = Regex("""^(.+)\.([A-Za-z0-9]+)\.state(\.metadata)?$""")

        /** Folders holding saves inside an emulator's main folder (RetroArch, Lemuroid, Pizza Boy). */
        private val SAVE_FOLDER_NAMES = setOf("saves", "states", "save")

        private val STATE_FILE = Regex("""\.(state\d*|state\.auto|slot\d|state\.metadata|slot\d\.metadata)$""")
        private val SAVE_SUFFIXES =
            Regex("""(\.(srm|sav|png|auto|metadata|slot\d|state\d*))+$""", RegexOption.IGNORE_CASE)

        /**
         * Core folder names (RetroArch's "sort by core" names and Lemuroid's states/<core> names) for
         * cores that run a single system. Multi-system cores (Gambatte, Genesis Plus GX...) rely on the
         * ROM extension or the game database instead.
         */
        private val CORE_FOLDERS =
            mapOf(
                "fceumm" to "nes",
                "nestopia" to "nes",
                "snes9x" to "snes",
                "mgba" to "gba",
                "pcsx-rearmed" to "psx",
                "pcsx_rearmed" to "psx",
                "mupen64plus-next" to "n64",
                "mupen64plus_next_gles3" to "n64",
                "ppsspp" to "psp",
                "melonds" to "nds",
                "melonds ds" to "nds",
                "melondsds" to "nds",
                "desmume" to "nds",
                "finalburn neo" to "fbneo",
                "fbneo" to "fbneo",
                "mame 2003-plus" to "mame",
                "mame2003_plus" to "mame",
                "stella" to "atari2600",
                "prosystem" to "atari7800",
                "handy" to "atarilynx",
                "beetle pce fast" to "pcengine",
                "mednafen_pce_fast" to "pcengine",
                "dosbox-pure" to "dos",
                "dosbox_pure" to "dos",
                "citra" to "n3ds",
                "azahar" to "n3ds",
                "a5200" to "atari5200",
                "flycast" to "dreamcast",
                "neocd" to "neogeocd",
                "beetle vb" to "virtualboy",
                "mednafen_vb" to "virtualboy",
                "pokemini" to "pokemini",
                "vecx" to "vectrex",
                "gearcoleco" to "colecovision",
                "freeintv" to "intellivision",
            )
    }
}
