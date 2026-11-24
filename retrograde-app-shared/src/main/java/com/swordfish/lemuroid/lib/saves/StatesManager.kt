package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.gzipped
import com.swordfish.lemuroid.common.kotlin.readBytesUncompressedAtomic
import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.uncompressedIfGzipped
import com.swordfish.lemuroid.common.kotlin.writeBytesCompressedAtomic
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Save states (resume point and slots). Like battery saves they live only in the SAVES folder:
 * Lemuroid-S/states/<system> or the ROMs folder. App storage (states/<core>) is a fallback used
 * while that folder can't be reached; anything left there moves into the folder on the next access.
 */
class StatesManager(
    private val directoriesManager: DirectoriesManager,
    private val externalSavesStorage: ExternalSavesStorage? = null,
) {
    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
    ): SaveState? =
        withContext(Dispatchers.IO) {
            assert(index in 0 until MAX_STATES)
            getSaveState(game, getSlotSaveFileName(game, index), coreID.coreName)
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        setSaveState(game, getSlotSaveFileName(game, index), coreID.coreName, saveState)
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            stateInfos(game, coreID.coreName, listOf(getAutoSaveFileName(game))).first()
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        getSaveState(game, getAutoSaveFileName(game), coreID.coreName)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ) = withContext(Dispatchers.IO) {
        setSaveState(game, getAutoSaveFileName(game), coreID.coreName, saveState)
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
    ): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            stateInfos(game, coreID.coreName, (0 until MAX_STATES).map { getSlotSaveFileName(game, it) })
        }

    /** Moves [game]'s states from app storage into the SAVES folder, if any are left there. */
    suspend fun moveFallbackToFolder(
        game: Game,
        coreName: String,
    ) = withContext(Dispatchers.IO) {
        openFolder(game, coreName)
        Unit
    }

    /**
     * [game]'s states folder, ready to use: states left in app storage have been moved in, and
     * imported RetroArch states converted. Null if the folder can't be reached.
     */
    private fun openFolder(
        game: Game,
        coreName: String,
    ): ExternalSavesStorage.SaveFolder? {
        val storage = externalSavesStorage ?: return null
        val folder = ExternalSavesStorage.safely("open states") { storage.statesFolder(game, coreName) } ?: return null
        ExternalSavesStorage.safely("move states") { moveFallbackStates(game, coreName, folder) }
        ExternalSavesStorage.safely("convert states") {
            convertImportedRetroArchStates(game, coreName, folder, folder)
            storage.importedStatesFolder()?.let { convertImportedRetroArchStates(game, coreName, it, folder) }
        }
        return folder
    }

    private fun stateInfos(
        game: Game,
        coreName: String,
        fileNames: List<String>,
    ): List<SaveInfo> {
        val folderTimes =
            openFolder(game, coreName)?.let { folder ->
                ExternalSavesStorage.safely("list states") { folder.lastModifiedTimes() }
            } ?: emptyMap()
        return fileNames.map { name ->
            val folderTime = folderTimes[name]
            val fallbackFile = getFallbackStateFile(name, coreName)
            when {
                folderTime != null -> SaveInfo(true, folderTime)
                fallbackFile.exists() && fallbackFile.length() > 0 -> SaveInfo(true, fallbackFile.lastModified())
                else -> SaveInfo(false, 0)
            }
        }
    }

    private suspend fun getSaveState(
        game: Game,
        fileName: String,
        coreName: String,
    ): SaveState? {
        val folder = openFolder(game, coreName)
        val fromFolder =
            folder?.let {
                ExternalSavesStorage.safely("read state") {
                    val state = it.read(fileName)?.data?.takeIf { data -> data.isNotEmpty() } ?: return@safely null
                    SaveState(state.uncompressedIfGzipped(), decodeMetadata(it.read(metadataName(fileName))?.data))
                }
            }
        if (fromFolder != null) return fromFolder

        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val stateFile = getFallbackStateFile(fileName, coreName)
            if (!stateFile.exists()) return@runCatchingWithRetry null
            val metadataFile = getFallbackStateFile(metadataName(fileName), coreName)
            val metadata = if (metadataFile.exists()) metadataFile.readTextAtomic().toByteArray() else null
            SaveState(stateFile.readBytesUncompressedAtomic(), decodeMetadata(metadata))
        }.getOrNull()
    }

    private suspend fun setSaveState(
        game: Game,
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        val metadata = saveState.metadata.copy(coreName = coreName)
        val savedToFolder =
            openFolder(game, coreName)?.let { folder ->
                ExternalSavesStorage.safely("write state") {
                    writeToFolder(folder, fileName, saveState.state, metadata)
                    true
                }
            } == true

        val stateFile = getFallbackStateFile(fileName, coreName)
        val metadataFile = getFallbackStateFile(metadataName(fileName), coreName)
        if (savedToFolder) {
            stateFile.delete()
            metadataFile.delete()
            return
        }
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            stateFile.parentFile?.mkdirs()
            stateFile.writeBytesCompressedAtomic(saveState.state)
            metadataFile.writeTextAtomic(encodeMetadata(metadata))
        }
    }

    /** Writes a state (GZIP-compressed, as Lemuroid always stored them) and its metadata. */
    private fun writeToFolder(
        folder: ExternalSavesStorage.SaveFolder,
        fileName: String,
        state: ByteArray,
        metadata: SaveState.Metadata,
    ) {
        folder.write(fileName, state.gzipped())
        folder.write(metadataName(fileName), encodeMetadata(metadata).toByteArray())
    }

    private fun decodeMetadata(data: ByteArray?): SaveState.Metadata {
        if (data == null) return SaveState.Metadata()
        return runCatching {
            Json.Default.decodeFromString(SaveState.Metadata.serializer(), data.decodeToString())
        }.getOrDefault(SaveState.Metadata())
    }

    private fun encodeMetadata(metadata: SaveState.Metadata): String {
        return Json.encodeToString(SaveState.Metadata.serializer(), metadata)
    }

    private fun metadataName(stateFileName: String) = "$stateFileName.metadata"

    private fun getFallbackStateFile(
        fileName: String,
        coreName: String,
    ): File = File(File(directoriesManager.getStatesDirectory(), coreName), fileName)

    /** All state file names Lemuroid-S uses for [game] (auto save, slots, and their metadata). */
    private fun allStateFileNames(game: Game): List<String> {
        val states = listOf(getAutoSaveFileName(game)) + (0 until MAX_STATES).map { getSlotSaveFileName(game, it) }
        return states.flatMap { listOf(it, metadataName(it)) }
    }

    /**
     * Moves [game]'s states left in app storage into [folder] (unless the folder has a newer copy),
     * then deletes them from app storage.
     */
    private fun moveFallbackStates(
        game: Game,
        coreName: String,
        folder: ExternalSavesStorage.SaveFolder,
    ) {
        val files = allStateFileNames(game).map { getFallbackStateFile(it, coreName) }.filter { it.exists() }
        if (files.isEmpty()) return
        val folderTimes = folder.lastModifiedTimes()
        for (file in files) {
            val folderTime = folderTimes[file.name]
            if (file.length() > 0 && (folderTime == null || !isCurrent(folderTime, file.lastModified()))) {
                folder.write(file.name, file.readBytes())
            }
            file.delete()
        }
    }

    /**
     * States imported from RetroArch keep RetroArch's names ("Game.state.auto", "Game.state",
     * "Game.state1"...) and format. When [game] is opened they become Lemuroid-S states in
     * [target]: the resume point and save slots 1-4. A RetroArch state is converted again only if
     * it is newer than the Lemuroid-S state.
     */
    private fun convertImportedRetroArchStates(
        game: Game,
        coreName: String,
        source: ExternalSavesStorage.SaveFolder,
        target: ExternalSavesStorage.SaveFolder,
    ) {
        val romName = game.fileName.substringBeforeLast(".")
        val retroArchNames =
            mapOf("$romName.state.auto" to getAutoSaveFileName(game)) +
                (0 until MAX_STATES).associate { slot ->
                    val suffix = if (slot == 0) "" else "$slot"
                    "$romName.state$suffix" to getSlotSaveFileName(game, slot)
                }

        val sourceTimes = source.lastModifiedTimes()
        if (retroArchNames.keys.none { it in sourceTimes }) return
        val targetTimes = target.lastModifiedTimes()
        for ((retroArchName, lemuroidName) in retroArchNames) {
            // The auto save is named "<rom file>.state", which can equal a RetroArch slot name.
            if (retroArchName == lemuroidName) continue
            val sourceTime = sourceTimes[retroArchName] ?: continue
            val targetTime = targetTimes[lemuroidName]
            if (targetTime != null && isCurrent(targetTime, sourceTime)) continue

            val external = source.read(retroArchName) ?: continue
            val state = RetroArchStates.decode(external.data) ?: continue
            val metadata = SaveState.Metadata(0, statesVersion(game, coreName), coreName)
            writeToFolder(target, lemuroidName, state, metadata)
        }
    }

    /** True if a file dated [time] is at least as new as one dated [otherTime]. */
    private fun isCurrent(
        time: Long,
        otherTime: Long,
    ): Boolean = time + SavesManager.SYNC_TOLERANCE_MS >= otherTime

    private fun statesVersion(
        game: Game,
        coreName: String,
    ): Int {
        val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return 0
        return system.systemCoreConfigs.firstOrNull { it.coreID.coreName == coreName }?.statesVersion ?: 0
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"

    private fun getSlotSaveFileName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val MAX_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
