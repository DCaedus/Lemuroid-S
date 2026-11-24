package com.swordfish.lemuroid.app.shared.storage

import android.content.Context
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * After the Lemuroid-S folder is chosen, copies what Lemuroid-S had stored in its private app folder
 * (Android/data) into it, in the per-system layout:
 *   saves/<system>/, states/<system>/, thumbnails/<system>/Named_*
 * Files already there are only replaced by newer ones. App storage keeps a working copy.
 */
class MainFolderMigration(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
) {
    /** Runs the copy in the background, outside any screen's lifetime, then calls [onDone] on the main thread. */
    @OptIn(DelicateCoroutinesApi::class)
    fun startInBackground(onDone: () -> Unit) {
        GlobalScope.launch {
            runCatching { copyAppStorageToMainFolder() }.onFailure { Timber.e(it, "Main folder migration failed") }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    suspend fun copyAppStorageToMainFolder(): Int =
        withContext(Dispatchers.IO) {
            val mainFolder = MainFolder(appContext)
            if (!mainFolder.isSet()) return@withContext 0

            val games = retrogradeDb.gameDao().selectByLastIndexedAtLessThan(Long.MAX_VALUE)
            for (game in games) {
                runCatching {
                    savesManager.exportToExternal(game)
                    val system = GameSystem.findById(game.systemId)
                    system.systemCoreConfigs.forEach { statesManager.exportToExternal(game, it.coreID.coreName) }
                }.onFailure { Timber.w(it, "Could not move saves for ${game.fileName}") }
            }

            val thumbnails = copyThumbnails(mainFolder)
            Timber.i("Main folder migration done: ${games.size} games, $thumbnails artwork files")
            games.size
        }

    /** App storage used RetroArch's console names (thumbnails/<libretro name>/...); the main folder uses ES-DE's. */
    private fun copyThumbnails(mainFolder: MainFolder): Int {
        val source = File(appContext.getExternalFilesDir(null), MainFolder.THUMBNAILS)
        val consoles = source.listFiles()?.filter { it.isDirectory } ?: return 0
        var copied = 0

        for (console in consoles) {
            val systemFolder = esdeNameForLibretroName(console.name) ?: continue
            for (typeFolder in console.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
                val target =
                    runCatching { mainFolder.folder(MainFolder.THUMBNAILS, systemFolder, typeFolder.name) }.getOrNull()
                        ?: continue
                copied += copyFiles(typeFolder, target)
            }
        }
        return copied
    }

    private fun copyFiles(
        source: File,
        target: DocumentFolder,
    ): Int {
        val existing = runCatching { target.children() }.getOrDefault(emptyMap())
        var copied = 0
        source.listFiles()?.filter { it.isFile && it.name !in existing }?.forEach { file ->
            runCatching { target.write(file.name, file.readBytes(), null) }.onSuccess { copied++ }
        }
        return copied
    }

    private fun esdeNameForLibretroName(libretroName: String): String? {
        if (libretroName == "MAME") return MainFolder.SYSTEM_FOLDERS[SystemID.MAME2003PLUS]
        val system = GameSystem.all().firstOrNull { it.libretroFullName == libretroName } ?: return null
        return MainFolder.SYSTEM_FOLDERS[system.id]
    }
}
