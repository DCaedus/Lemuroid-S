package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.migrators.getSavesMigrator
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(
    private val directoriesManager: DirectoriesManager,
    private val externalSavesStorage: ExternalSavesStorage,
) {
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    syncFromExternal(game, saveFile)

                    if (saveFile.exists() && saveFile.length() > 0) {
                        saveFile.readBytes()
                    } else {
                        val savesMigrator = systemCoreConfig.getSavesMigrator()
                        savesMigrator?.loadPreviousSaveForGame(game, directoriesManager)
                    }
                }
            result.getOrNull()
        }
    }

    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    if (data.isEmpty()) {
                        return@runCatchingWithRetry
                    }

                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    saveFile.writeBytesAtomic(data)
                }
            if (data.isNotEmpty()) {
                ExternalSavesStorage.safely("write save") {
                    externalSavesStorage.savesFolder(game)?.write(getSaveRAMFileName(game), data)
                }
            }
            result.getOrNull()
        }
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo {
        return withContext(Dispatchers.IO) {
            val saveFile = getSaveFile(getSaveRAMFileName(game))
            syncFromExternal(game, saveFile)
            val fileExists = saveFile.exists() && saveFile.length() > 0
            SaveInfo(fileExists, saveFile.lastModified())
        }
    }

    /**
     * If the SAVES folder (Lemuroid-S folder or ROMs folder) has a newer copy of the save, e.g. after
     * reinstalling or copying saves in by hand, bring it into app storage first.
     */
    private fun syncFromExternal(
        game: Game,
        saveFile: File,
    ) {
        ExternalSavesStorage.safely("read save") {
            // Imported RetroArch saves sit at the top of the saves folder, under the same name.
            val external =
                externalSavesStorage.savesFolder(game)?.read(saveFile.name)
                    ?: externalSavesStorage.importedSavesFolder()?.let { folder ->
                        folder.read(saveFile.name) ?: folder.read(saveFile.name.removeSuffix(".srm") + ".sav")
                    }
                    ?: return@safely
            if (external.data.isEmpty()) return@safely
            val internalTime = if (saveFile.exists()) saveFile.lastModified() else Long.MIN_VALUE / 2
            val internalIsCurrent = internalTime + SYNC_TOLERANCE_MS >= external.lastModified
            if (internalIsCurrent) return@safely
            saveFile.writeBytesAtomic(external.data)
            saveFile.setLastModified(external.lastModified)
        }
    }

    fun saveRAMFileName(game: Game) = getSaveRAMFileName(game)

    private suspend fun getSaveFile(fileName: String): File {
        return withContext(Dispatchers.IO) {
            val savesDirectory = directoriesManager.getSavesDirectory()
            File(savesDirectory, fileName)
        }
    }

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
        const val SYNC_TOLERANCE_MS = 10_000L
    }
}
