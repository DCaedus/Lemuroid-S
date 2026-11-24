package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveInfo
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * Imports battery saves and save states from another emulator's folder: RetroArch (its whole
 * folder, or its saves/states folders) or a copy of Lemuroid's files folder. Subfolders are
 * searched too. Files are matched to library games by name:
 *  - battery saves:      "<rom name>.srm" (Lemuroid, RetroArch, Lemuroid-S) or "<rom name>.sav"
 *  - Lemuroid states:    "<rom file>.state" (resume point) and "<rom file>.slot1".."slot4"
 *  - RetroArch states:   "<rom name>.state.auto" (resume point), ".state", ".state1".."state3" (slots 1-4)
 *
 * Existing Lemuroid-S files are only replaced by newer ones; replaced battery saves are backed up.
 * Save states generally only load in the same emulator core (and usually the same core version).
 * RetroArch states may be compressed (rzip) and/or wrapped in a RASTATE container; both are handled.
 */
class SavesImporter(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val coresSelection: CoresSelection,
) {
    data class Result(
        val imported: Int,
        val importedStates: Int,
        val skippedOlder: Int,
        val unsupportedStates: Int,
        /** Files kept aside because their game isn't in the library yet; imported after the next scan. */
        val pending: Int = 0,
    )

    private data class FoundFile(val uri: Uri, val lastModified: Long, val parentName: String)

    private class Counters {
        var saves = 0
        var states = 0
        var skipped = 0
        var unsupported = 0

        /** Found file names that were imported or deliberately skipped (Lemuroid-S's copy was newer). */
        val handled = mutableSetOf<String>()
    }

    /** Folder (in app storage) for saves whose game isn't in the library yet. */
    private val pendingDirectory: File get() = File(appContext.filesDir, "import-pending").apply { mkdirs() }

    /**
     * Imports from a picked folder. Saves for games already in the library are imported right away;
     * the others are kept aside and imported automatically after the next library scan, so importing
     * works even before any ROMs are added.
     */
    suspend fun importFrom(treeUri: Uri): Result =
        withContext(Dispatchers.IO) {
            val found = collectFromFolder(treeUri)

            val counters = importFound(found)
            importUnmatchedBatterySaves(found, counters)

            var pending = 0
            for ((name, file) in found) {
                if (name in counters.handled) continue
                val bytes = readBytes(file) ?: continue
                runCatching {
                    val target = File(pendingDirectory, name)
                    target.writeBytes(bytes)
                    target.setLastModified(file.lastModified)
                }.onSuccess { pending++ }
            }
            Result(counters.saves, counters.states, counters.skipped, counters.unsupported, pending)
        }

    /** Imports kept-aside saves whose games are now in the library. Called after library scans. */
    suspend fun importPending(): Result =
        withContext(Dispatchers.IO) {
            val files = pendingDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
            if (files.isEmpty()) return@withContext Result(0, 0, 0, 0)

            val found = files.associate { it.name to FoundFile(Uri.fromFile(it), it.lastModified(), "") }
            val counters = importFound(found)
            files.filter { it.name in counters.handled }.forEach { it.delete() }
            Timber.i("Imported pending saves: ${counters.saves} saves, ${counters.states} states")
            Result(
                counters.saves,
                counters.states,
                counters.skipped,
                counters.unsupported,
                files.size - counters.handled.size,
            )
        }

    /**
     * Battery saves are found by name ("Game (USA).srm"), so they can go straight into Lemuroid-S
     * even when the game isn't in the library yet. (".sav" files are renamed to ".srm".)
     */
    private suspend fun importUnmatchedBatterySaves(
        found: Map<String, FoundFile>,
        counters: Counters,
    ) {
        for ((name, file) in found) {
            if (name in counters.handled) continue
            val lower = name.lowercase()
            if (!lower.endsWith(".srm") && !lower.endsWith(".sav")) continue
            val saveName = name.substringBeforeLast('.') + ".srm"
            val bytes = readBytes(file)?.takeIf { it.isNotEmpty() } ?: continue
            val imported = savesManager.importSaveRAMFile(saveName, bytes, file.lastModified)
            if (imported) counters.saves++ else counters.skipped++
            counters.handled.add(name)
        }
    }

    private suspend fun importFound(found: Map<String, FoundFile>): Counters {
        val counters = Counters()
        // Only games that have at least one matching file are checked (checking a game reads its
        // current saves, which is slow; most games in a library usually have nothing to import).
        val foundStems = found.keys.map { stemOf(it) }.toSet()
        val games =
            retrogradeDb.gameDao().selectByLastIndexedAtLessThan(Long.MAX_VALUE).filter { game ->
                game.fileName in foundStems || game.fileName.substringBeforeLast(".") in foundStems
            }
        for (game in games) {
            runCatching { importBatterySave(game, found, counters) }
                .onFailure { Timber.w(it, "Could not import battery save for ${game.fileName}") }
            runCatching { importStates(game, found, counters) }
                .onFailure { Timber.w(it, "Could not import states for ${game.fileName}") }
        }
        return counters
    }

    private suspend fun importBatterySave(
        game: Game,
        found: Map<String, FoundFile>,
        counters: Counters,
    ) {
        // ".srm" (RetroArch, Lemuroid) first, then ".sav" (used by some standalone emulators).
        val saveName = savesManager.saveRAMFileName(game)
        val savName = saveName.removeSuffix(".srm") + ".sav"
        val foundName = listOf(saveName, savName).firstOrNull { it in found } ?: return
        val file = found.getValue(foundName)
        counters.handled.add(foundName)
        if (isCurrentNewer(savesManager.getSaveRAMInfo(game), file)) {
            counters.skipped++
            return
        }
        val bytes = readBytes(file) ?: return
        if (bytes.isEmpty()) return

        savesManager.backupSaveRAM(game)
        savesManager.setSaveRAM(game, bytes)
        counters.saves++
    }

    private suspend fun importStates(
        game: Game,
        found: Map<String, FoundFile>,
        counters: Counters,
    ) {
        val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return
        val coreConfig = coresSelection.getCoreConfigForSystem(system)
        if (!coreConfig.statesSupported) return

        val romName = game.fileName.substringBeforeLast(".")
        val slotsInfo = statesManager.getSavedSlotsInfo(game, coreConfig.coreID)
        val autoInfo = statesManager.getAutoSaveInfo(game, coreConfig.coreID)

        // slot = null means the resume point (auto save).
        val candidates =
            listOf<Triple<Int?, String, Boolean>>(
                Triple(null, "${game.fileName}.state", true),
                Triple(null, "$romName.state.auto", false),
            ) +
                (0 until StatesManager.MAX_STATES).flatMap { slot ->
                    val retroArchSuffix = if (slot == 0) "" else "$slot"
                    listOf(
                        Triple(slot, "${game.fileName}.slot${slot + 1}", true),
                        Triple(slot, "$romName.state$retroArchSuffix", false),
                    )
                }

        val handledSlots = mutableSetOf<Int?>()
        for ((slot, fileName, isLemuroidFormat) in candidates) {
            if (slot in handledSlots) continue
            val file = found[fileName] ?: continue
            counters.handled.add(fileName)
            counters.handled.add("$fileName.metadata")

            val current = if (slot == null) autoInfo else slotsInfo[slot]
            if (isCurrentNewer(current, file)) {
                counters.skipped++
                handledSlots.add(slot)
                continue
            }

            val saveState =
                if (isLemuroidFormat) {
                    readLemuroidState(
                        file,
                        found["$fileName.metadata"],
                    )
                } else {
                    readRetroArchState(file, coreConfig)
                }

            if (saveState == null) {
                counters.unsupported++
                continue
            }

            if (slot == null) {
                statesManager.setAutoSave(game, coreConfig.coreID, saveState)
            } else {
                statesManager.setSlotSave(game, saveState, coreConfig.coreID, slot)
            }
            handledSlots.add(slot)
            counters.states++
        }
    }

    private fun readLemuroidState(
        file: FoundFile,
        metadataFile: FoundFile?,
    ): SaveState? {
        val bytes = readBytes(file) ?: return null
        val state = if (isGzip(bytes)) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        val metadata =
            metadataFile?.let { readBytes(it) }?.let { json ->
                val text = json.decodeToString()
                runCatching { Json.decodeFromString(SaveState.Metadata.serializer(), text) }.getOrNull()
            } ?: SaveState.Metadata()
        return SaveState(state, metadata)
    }

    private fun readRetroArchState(
        file: FoundFile,
        coreConfig: SystemCoreConfig,
    ): SaveState? {
        val bytes = readBytes(file) ?: return null
        val state =
            when {
                startsWith(bytes, RZIP_MAGIC) -> decodeRzip(bytes) ?: return null
                else -> bytes
            }.let { if (startsWith(it, RASTATE_MAGIC)) extractRastateMemory(it) ?: return null else it }
        return SaveState(state, SaveState.Metadata(diskIndex = 0, version = coreConfig.statesVersion))
    }

    /**
     * RetroArch's compressed state format (rzip): "#RZIPv<version>#", chunk size (uint32),
     * total size (uint64), then chunks of [compressed size (uint32) + zlib data].
     */
    private fun decodeRzip(bytes: ByteArray): ByteArray? {
        return runCatching {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(RZIP_HEADER_SIZE - 12)
            buffer.int // chunk size
            val totalSize = buffer.long
            val output = ByteArrayOutputStream(totalSize.toInt())
            val chunk = ByteArray(64 * 1024)

            while (buffer.remaining() >= 4) {
                val compressedSize = buffer.int
                val compressed = ByteArray(compressedSize)
                buffer.get(compressed)
                val inflater = Inflater()
                inflater.setInput(compressed)
                while (!inflater.finished()) {
                    val count = inflater.inflate(chunk)
                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    output.write(chunk, 0, count)
                }
                inflater.end()
            }
            output.toByteArray().takeIf { it.size.toLong() == totalSize }
        }.onFailure { Timber.w(it, "Could not decode RetroArch state") }.getOrNull()
    }

    /**
     * Newer RetroArch versions wrap states in a "RASTATE" container: an 8-byte header ("RASTATE" +
     * version), then blocks of [4-byte id, 4-byte little-endian size, data] padded to 8 bytes.
     * The "MEM " block holds the core's own state data, which is what Lemuroid-S needs.
     */
    private fun extractRastateMemory(bytes: ByteArray): ByteArray? {
        for (aligned in listOf(true, false)) {
            runCatching { findRastateBlock(bytes, "MEM ", aligned) }.getOrNull()?.let { return it }
        }
        Timber.w("Could not read RetroArch RASTATE state")
        return null
    }

    private fun findRastateBlock(
        bytes: ByteArray,
        blockId: String,
        aligned: Boolean,
    ): ByteArray? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(RASTATE_HEADER_SIZE)
        while (buffer.remaining() >= 8) {
            val id = ByteArray(4).also { buffer.get(it) }.decodeToString()
            val size = buffer.int
            if (size < 0 || size > buffer.remaining()) return null
            if (id == blockId) return ByteArray(size).also { buffer.get(it) }
            if (id == "END ") return null
            val skip = if (aligned) (size + 7) / 8 * 8 else size
            if (skip > buffer.remaining()) return null
            buffer.position(buffer.position() + skip)
        }
        return null
    }

    /** "Game.gba.slot1.metadata" -> "Game.gba", "Game.state.auto" -> "Game", "Game.srm" -> "Game". */
    private fun stemOf(name: String): String {
        var stem = name.removeSuffix(".metadata").removeSuffix(".auto")
        stem = stem.replace(STEM_SUFFIX, "")
        return stem
    }

    /**
     * Searches the picked folder. For RetroArch's folder only its "saves" and "states" folders are
     * searched (RetroArch keeps thousands of other files, e.g. covers, that would make it slow).
     */
    private fun collectFromFolder(treeUri: Uri): Map<String, FoundFile> {
        val found = mutableMapOf<String, FoundFile>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val saveFolders = listFolders(treeUri, rootId).filterKeys { it.lowercase() in RETROARCH_SAVE_FOLDERS }
        if (saveFolders.isEmpty()) {
            collectFiles(treeUri, rootId, "", depth = 0, found)
        } else {
            saveFolders.forEach { (name, id) -> collectFiles(treeUri, id, name, depth = 1, found) }
        }
        return found
    }

    private fun listFolders(
        treeUri: Uri,
        folderId: String,
    ): Map<String, String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        val result = mutableMapOf<String, String>()
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    result[cursor.getString(1)] = cursor.getString(0)
                }
            }
        }
        return result
    }

    private fun isCurrentNewer(
        current: SaveInfo,
        file: FoundFile,
    ) = current.exists && current.date >= file.lastModified

    private fun readBytes(file: FoundFile): ByteArray? {
        return runCatching { appContext.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } }
            .onFailure { Timber.w(it, "Could not read ${file.uri}") }
            .getOrNull()
    }

    private fun isGzip(bytes: ByteArray) = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun startsWith(
        bytes: ByteArray,
        prefix: ByteArray,
    ) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    private fun collectFiles(
        treeUri: Uri,
        folderId: String,
        folderName: String,
        depth: Int,
        found: MutableMap<String, FoundFile>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
        val subFolders = mutableListOf<Pair<String, String>>()

        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val lastModified = cursor.getLong(3)

                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    subFolders.add(id to name)
                } else if (isSaveFile(name)) {
                    val file =
                        FoundFile(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), lastModified, folderName)
                    // If the same file appears in several subfolders, keep the newest.
                    val previous = found[name]
                    if (previous == null || previous.lastModified < lastModified) found[name] = file
                }
            }
        }

        if (depth < MAX_DEPTH) {
            subFolders.forEach { (id, name) -> collectFiles(treeUri, id, name, depth + 1, found) }
        }
    }

    private fun isSaveFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".srm") ||
            lower.endsWith(".sav") ||
            lower.endsWith(".metadata") ||
            lower.endsWith(".state.auto") ||
            STATE_SUFFIX.containsMatchIn(lower)
    }

    companion object {
        private const val MAX_DEPTH = 4
        private const val RZIP_HEADER_SIZE = 20
        private val RZIP_MAGIC = "#RZIPv".toByteArray()
        private val RASTATE_MAGIC = "RASTATE".toByteArray()
        private const val RASTATE_HEADER_SIZE = 8
        private val STATE_SUFFIX = Regex("""\.(state\d*|slot\d)$""")
        private val STEM_SUFFIX = Regex("""\.(srm|sav|state\d*|slot\d)$""", RegexOption.IGNORE_CASE)
        private val RETROARCH_SAVE_FOLDERS = setOf("saves", "states")
    }
}
