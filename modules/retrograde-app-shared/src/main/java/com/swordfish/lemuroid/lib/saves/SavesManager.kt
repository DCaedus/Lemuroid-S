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

/**
 * In-game (battery) saves. They live only in the SAVES folder ([ExternalSavesStorage]): the
 * Lemuroid-S folder (saves/<system>/<rom>.srm) or the ROMs folder. App storage is a fallback used
 * while that folder can't be reached; anything left there moves into the folder on the next access.
 */
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
                    val save = findSave(game, readData = true)
                    if (save != null && save.data.isNotEmpty()) {
                        save.data
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
        if (data.isEmpty()) return
        withContext(Dispatchers.IO) {
            val fileName = getSaveRAMFileName(game)
            val fallbackFile = getFallbackFile(fileName)
            val savedToFolder =
                ExternalSavesStorage.safely("write save") {
                    val folder = externalSavesStorage.savesFolder(game) ?: return@safely false
                    folder.write(fileName, data)
                    true
                } == true

            if (savedToFolder) {
                fallbackFile.delete()
            } else {
                runCatchingWithRetry(FILE_ACCESS_RETRIES) { fallbackFile.writeBytesAtomic(data) }
            }
        }
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo {
        return withContext(Dispatchers.IO) {
            val save = findSave(game, readData = false)
            SaveInfo(save != null, save?.lastModified ?: 0)
        }
    }

    /** Moves [game]'s battery save from app storage into the SAVES folder, if one is left there. */
    suspend fun moveFallbackToFolder(game: Game) {
        withContext(Dispatchers.IO) {
            ExternalSavesStorage.safely("move save") {
                val folder = externalSavesStorage.savesFolder(game) ?: return@safely
                moveFallbackToFolder(getFallbackFile(getSaveRAMFileName(game)), folder)
            }
        }
    }

    /**
     * Finds [game]'s save: in its SAVES folder (also under RetroArch's ".sav" name), then among
     * imported saves at the top of the saves folder, then in app storage. With [readData] false only
     * the date is read.
     */
    private fun findSave(
        game: Game,
        readData: Boolean,
    ): ExternalSavesStorage.ExternalFile? {
        val fileName = getSaveRAMFileName(game)
        val savName = fileName.removeSuffix(".srm") + ".sav"
        val fallbackFile = getFallbackFile(fileName)

        val fromFolder =
            ExternalSavesStorage.safely("read save") {
                val folder = externalSavesStorage.savesFolder(game) ?: return@safely null
                moveFallbackToFolder(fallbackFile, folder)
                listOfNotNull(folder, externalSavesStorage.importedSavesFolder())
                    .firstNotNullOfOrNull { it.find(fileName, readData) ?: it.find(savName, readData) }
            }
        if (fromFolder != null) return fromFolder

        if (!fallbackFile.exists() || fallbackFile.length() == 0L) return null
        val data = if (readData) fallbackFile.readBytes() else ByteArray(0)
        return ExternalSavesStorage.ExternalFile(data, fallbackFile.lastModified())
    }

    private fun ExternalSavesStorage.SaveFolder.find(
        fileName: String,
        readData: Boolean,
    ): ExternalSavesStorage.ExternalFile? {
        val lastModified = lastModifiedTimes()[fileName] ?: return null
        if (!readData) return ExternalSavesStorage.ExternalFile(ByteArray(0), lastModified)
        return read(fileName)?.takeIf { it.data.isNotEmpty() }
    }

    /** Copies [file] into [folder] unless the folder's copy is as new, then deletes [file]. */
    private fun moveFallbackToFolder(
        file: File,
        folder: ExternalSavesStorage.SaveFolder,
    ) {
        if (!file.exists()) return
        if (file.length() > 0) {
            val folderTime = folder.lastModifiedTimes()[file.name]
            if (folderTime == null || file.lastModified() > folderTime + SYNC_TOLERANCE_MS) {
                folder.write(file.name, file.readBytes())
            }
        }
        file.delete()
    }

    fun saveRAMFileName(game: Game) = getSaveRAMFileName(game)

    private fun getFallbackFile(fileName: String): File = File(directoriesManager.getSavesDirectory(), fileName)

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
        const val SYNC_TOLERANCE_MS = 10_000L
    }
}
