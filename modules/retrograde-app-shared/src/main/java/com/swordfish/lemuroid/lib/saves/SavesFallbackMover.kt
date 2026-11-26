package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Moves battery saves and states left in app storage (Android/data/.../saves and states/<core>)
 * into the SAVES folder, for every game in the library. This empties the copies older versions
 * kept there and anything saved while the folder couldn't be reached. Only Lemuroid's own files
 * are touched: files cores write themselves (PSP, DOS...) stay in app storage.
 */
class SavesFallbackMover(
    private val directoriesManager: DirectoriesManager,
    private val retrogradeDatabase: RetrogradeDatabase,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
) {
    suspend fun moveAll() =
        withContext(Dispatchers.IO) {
            val gameDao = retrogradeDatabase.gameDao()

            val saveStems =
                directoriesManager.getSavesDirectory().listFiles()
                    ?.filter { it.isFile && it.name.endsWith(SRM) }
                    ?.map { it.name.removeSuffix(SRM) }
                    .orEmpty()
            for (stem in saveStems) {
                runCatching {
                    gameDao.selectByFileStem(stem)
                        .filter { it.fileName.substringBeforeLast(".") == stem }
                        .forEach { savesManager.moveFallbackToFolder(it) }
                }.onFailure { Timber.w(it, "Could not move save $stem") }
            }

            val coreFolders = directoriesManager.getStatesDirectory().listFiles()?.filter { it.isDirectory }.orEmpty()
            for (coreFolder in coreFolders) {
                val gameFileNames = coreFolder.listFiles()?.mapNotNull { gameFileName(it) }?.toSet().orEmpty()
                for (fileName in gameFileNames) {
                    runCatching {
                        gameDao.selectByFileName(fileName)
                            .forEach { statesManager.moveFallbackToFolder(it, coreFolder.name) }
                    }.onFailure { Timber.w(it, "Could not move states of $fileName") }
                }
            }
        }

    /** "Game.gba.slot2.metadata" / "Game.gba.state" -> "Game.gba". */
    private fun gameFileName(file: File): String? {
        val name = file.name.removeSuffix(".metadata")
        return STATE_SUFFIX.find(name)?.let { name.substring(0, it.range.first) }
    }

    companion object {
        private const val SRM = ".srm"
        private val STATE_SUFFIX = Regex("""\.(state|slot\d)$""")
    }
}
