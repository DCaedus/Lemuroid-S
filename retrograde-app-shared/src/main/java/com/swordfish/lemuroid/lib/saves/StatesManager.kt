package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.readBytesUncompressedAtomic
import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
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
            syncFromExternal(game, coreID.coreName)
            getSaveState(getSlotSaveFileName(game, index), coreID.coreName)
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState)
        copyToExternal(game, coreID.coreName, getSlotSaveFileName(game, index))
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            syncFromExternal(game, coreID.coreName)
            val autoSaveFile = getStateFile(getAutoSaveFileName(game), coreID.coreName)
            val autoSaveHasData = autoSaveFile.length() > 0
            SaveInfo(autoSaveFile.exists() && autoSaveHasData, autoSaveFile.lastModified())
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        syncFromExternal(game, coreID.coreName)
        getSaveState(getAutoSaveFileName(game), coreID.coreName)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ) = withContext(Dispatchers.IO) {
        setSaveState(getAutoSaveFileName(game), coreID.coreName, saveState)
        copyToExternal(game, coreID.coreName, getAutoSaveFileName(game))
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
    ): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            syncFromExternal(game, coreID.coreName)
            (0 until MAX_STATES)
                .map { getStateFile(getSlotSaveFileName(game, it), coreID.coreName) }
                .map { SaveInfo(it.exists(), it.lastModified()) }
                .toList()
        }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getStateFile(fileName, coreName)
            val metadataFile = getMetadataStateFile(fileName, coreName)
            if (saveFile.exists()) {
                val byteArray = saveFile.readBytesUncompressedAtomic()
                val stateMetadata =
                    runCatching {
                        Json.Default.decodeFromString(
                            SaveState.Metadata.serializer(),
                            metadataFile.readTextAtomic(),
                        )
                    }
                SaveState(byteArray, stateMetadata.getOrNull() ?: SaveState.Metadata())
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            writeStateToDisk(fileName, coreName, saveState.state)
            writeMetadataToDisk(fileName, coreName, saveState.metadata.copy(coreName = coreName))
        }
    }

    private fun writeMetadataToDisk(
        fileName: String,
        coreName: String,
        metadata: SaveState.Metadata,
    ) {
        val metadataFile = getMetadataStateFile(fileName, coreName)
        metadataFile.writeTextAtomic(Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    private fun writeStateToDisk(
        fileName: String,
        coreName: String,
        stateArray: ByteArray,
    ) {
        val saveFile = getStateFile(fileName, coreName)
        saveFile.writeBytesCompressedAtomic(stateArray)
    }

    private fun getStateFile(
        fileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getMetadataStateFile(
        stateFileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, "$stateFileName.metadata")
    }

    /** All state file names Lemuroid-S uses for [game] (auto save, slots, and their metadata). */
    private fun allStateFileNames(game: Game): List<String> {
        val states = listOf(getAutoSaveFileName(game)) + (0 until MAX_STATES).map { getSlotSaveFileName(game, it) }
        return states.flatMap { listOf(it, "$it.metadata") }
    }

    /** Copies a state (and its metadata) to the SAVES folder chosen in Settings. */
    private fun copyToExternal(
        game: Game,
        coreName: String,
        fileName: String,
    ) {
        val storage = externalSavesStorage ?: return
        ExternalSavesStorage.safely("write state") {
            val folder = storage.statesFolder(game, coreName) ?: return@safely
            listOf(getStateFile(fileName, coreName), getMetadataStateFile(fileName, coreName))
                .filter { it.exists() }
                .forEach { folder.write(it.name, it.readBytes()) }
        }
    }

    /**
     * Brings newer states from the SAVES folder into app storage, e.g. after reinstalling or copying
     * files in by hand. Only files belonging to [game] are touched.
     */
    private fun syncFromExternal(
        game: Game,
        coreName: String,
    ) {
        val storage = externalSavesStorage ?: return
        ExternalSavesStorage.safely("read states") {
            storage.statesFolder(game, coreName)?.let {
                copyNewerStates(game, coreName, it)
                convertImportedRetroArchStates(game, coreName, it)
            }
        }
        ExternalSavesStorage.safely("read imported states") {
            storage.importedStatesFolder()?.let {
                copyNewerStates(game, coreName, it)
                convertImportedRetroArchStates(game, coreName, it)
            }
        }
    }

    /** Copies Lemuroid-S states for [game] that are newer in [folder] into app storage. */
    private fun copyNewerStates(
        game: Game,
        coreName: String,
        folder: ExternalSavesStorage.SaveFolder,
    ) {
        val externalTimes = folder.lastModifiedTimes()
        for (name in allStateFileNames(game)) {
            val externalTime = externalTimes[name] ?: continue
            val internalFile = File(File(directoriesManager.getStatesDirectory(), coreName), name)
            if (isInternalCurrent(internalFile, externalTime)) continue

            val external = folder.read(name) ?: continue
            internalFile.parentFile?.mkdirs()
            internalFile.writeBytes(external.data)
            internalFile.setLastModified(external.lastModified)
        }
    }

    /**
     * States imported from RetroArch keep RetroArch's names ("Game.state.auto", "Game.state",
     * "Game.state1"...) and format. When [game] is opened they become Lemuroid-S states: the resume
     * point and save slots 1-4.
     */
    private fun convertImportedRetroArchStates(
        game: Game,
        coreName: String,
        folder: ExternalSavesStorage.SaveFolder,
    ) {
        val romName = game.fileName.substringBeforeLast(".")
        val retroArchNames =
            mapOf("$romName.state.auto" to getAutoSaveFileName(game)) +
                (0 until MAX_STATES).associate { slot ->
                    val suffix = if (slot == 0) "" else "$slot"
                    "$romName.state$suffix" to getSlotSaveFileName(game, slot)
                }

        val externalTimes = folder.lastModifiedTimes()
        for ((retroArchName, lemuroidName) in retroArchNames) {
            val externalTime = externalTimes[retroArchName] ?: continue
            val internalFile = getStateFile(lemuroidName, coreName)
            if (isInternalCurrent(internalFile, externalTime)) continue

            val external = folder.read(retroArchName) ?: continue
            val state = RetroArchStates.decode(external.data) ?: continue
            writeStateToDisk(lemuroidName, coreName, state)
            writeMetadataToDisk(lemuroidName, coreName, SaveState.Metadata(0, statesVersion(game, coreName), coreName))
            internalFile.setLastModified(external.lastModified)
            getMetadataStateFile(lemuroidName, coreName).setLastModified(external.lastModified)
        }
    }

    private fun isInternalCurrent(
        internalFile: File,
        externalTime: Long,
    ): Boolean {
        val internalTime = if (internalFile.exists()) internalFile.lastModified() else Long.MIN_VALUE / 2
        return internalTime + SavesManager.SYNC_TOLERANCE_MS >= externalTime
    }

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
