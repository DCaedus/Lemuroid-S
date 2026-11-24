package com.swordfish.lemuroid.lib.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Copies what Lemuroid-S stored in its private app folder (Android/data) into the Lemuroid-S main
 * folder: battery saves, save states and artwork. Files already in the main folder are only
 * replaced by newer ones. App storage keeps its copy as a working copy.
 */
class MainFolderMigration(private val appContext: Context) {
    data class Result(val copied: Int, val failed: Int)

    suspend fun copyAppStorageToMainFolder(): Result =
        withContext(Dispatchers.IO) {
            val mainFolder = MainFolder(appContext)
            val root = mainFolder.getRoot() ?: return@withContext Result(0, 0)
            val directories = DirectoriesManager(appContext)
            var result = Result(0, 0)

            val sources =
                listOf(
                    directories.getSavesDirectory() to MainFolder.SAVES,
                    directories.getStatesDirectory() to MainFolder.STATES,
                    File(appContext.getExternalFilesDir(null), MainFolder.THUMBNAILS) to MainFolder.THUMBNAILS,
                )

            for ((source, name) in sources) {
                if (!source.isDirectory) continue
                val target = runCatching { root.subFolder(name) }.getOrNull() ?: continue
                result = copyDirectory(source, target, result)
            }
            Timber.i("Main folder migration: $result")
            result
        }

    private fun copyDirectory(
        source: File,
        target: DocumentFolder,
        initial: Result,
    ): Result {
        var result = initial
        val existing = runCatching { target.children() }.getOrDefault(emptyMap())

        source.listFiles()?.forEach { file ->
            // Skip internal leftovers such as backups and atomic-write temp files.
            if (file.name.startsWith(".") || file.name.endsWith(".bak") || file.name == "import-backups") return@forEach

            if (file.isDirectory) {
                val subFolder = runCatching { target.subFolder(file.name) }.getOrNull() ?: return@forEach
                result = copyDirectory(file, subFolder, result)
                return@forEach
            }

            val current = existing[file.name]
            if (current != null && current.lastModified >= file.lastModified()) return@forEach

            result =
                runCatching { target.write(file.name, file.readBytes(), current) }
                    .fold({ result.copy(copied = result.copied + 1) }, { result.copy(failed = result.failed + 1) })
        }
        return result
    }
}
