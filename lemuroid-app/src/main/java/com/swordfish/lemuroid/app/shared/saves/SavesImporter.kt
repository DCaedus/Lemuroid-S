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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * Imports battery saves and save states from another emulator's folder: RetroArch (its whole
 * folder, or its saves/states folders) or a copy of Lemuroid's files folder. Subfolders are
 * searched too. Files are matched to library games by name:
 *  - battery saves:      "<rom name>.srm" (Lemuroid, RetroArch and Lemuroid-S all use this)
 *  - Lemuroid states:    "<rom file>.state" (resume point) and "<rom file>.slot1".."slot4"
 *  - RetroArch states:   "<rom name>.state.auto" (resume point), ".state", ".state1".."state3" (slots 1-4)
 *
 * Existing Lemuroid-S files are only replaced by newer ones; replaced battery saves are backed up.
 * Save states only work with the same emulator core (and usually the same core version).
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
    )

    private data class FoundFile(val uri: Uri, val lastModified: Long, val parentName: String)

    private class Counters {
        var saves = 0
        var states = 0
        var skipped = 0
        var unsupported = 0
    }

    suspend fun importFrom(treeUri: Uri): Result =
        withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, FoundFile>()
            collectFiles(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", depth = 0, found)

            val counters = Counters()
            val games = retrogradeDb.gameDao().selectByLastIndexedAtLessThan(Long.MAX_VALUE)
            for (game in games) {
                runCatching { importBatterySave(game, found, counters) }
                    .onFailure { Timber.w(it, "Could not import battery save for ${game.fileName}") }
                runCatching { importStates(game, found, counters) }
                    .onFailure { Timber.w(it, "Could not import states for ${game.fileName}") }
            }
            Result(counters.saves, counters.states, counters.skipped, counters.unsupported)
        }

    private suspend fun importBatterySave(
        game: Game,
        found: Map<String, FoundFile>,
        counters: Counters,
    ) {
        val file = found[savesManager.saveRAMFileName(game)] ?: return
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

        val coreName = coreConfig.coreID.coreName
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

            // Lemuroid keeps states per core; a state from another core can't be loaded.
            if (isLemuroidFormat && isCoreFolderName(file.parentName) && file.parentName != coreName) continue

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
                // Newer RetroArch state container; not supported yet.
                startsWith(bytes, RASTATE_MAGIC) -> return null
                else -> bytes
            }
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

    private fun isCurrentNewer(
        current: SaveInfo,
        file: FoundFile,
    ) = current.exists && current.date >= file.lastModified

    private fun isCoreFolderName(name: String) =
        com.swordfish.lemuroid.lib.library.CoreID.values().any { it.coreName == name }

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
            lower.endsWith(".metadata") ||
            lower.endsWith(".state.auto") ||
            STATE_SUFFIX.containsMatchIn(lower)
    }

    companion object {
        private const val MAX_DEPTH = 4
        private const val RZIP_HEADER_SIZE = 20
        private val RZIP_MAGIC = "#RZIPv".toByteArray()
        private val RASTATE_MAGIC = "RASTATE".toByteArray()
        private val STATE_SUFFIX = Regex("""\.(state\d*|slot\d)$""")
    }
}
