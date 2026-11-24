package com.swordfish.lemuroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresRelease
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class PPSSPPAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        getAssetsDirectory(directoriesManager).deleteRecursively()
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        if (!updatedRequested(directoriesManager, sharedPreferences)) {
            return
        }

        try {
            val response = coreUpdaterApi.downloadZip(CoresRelease.PPSSPP_ASSETS_URL)
            handleSuccess(directoriesManager, response, sharedPreferences)
        } catch (e: Throwable) {
            getAssetsDirectory(directoriesManager).deleteRecursively()
        }
    }

    private suspend fun handleSuccess(
        directoriesManager: DirectoriesManager,
        response: Response<ZipInputStream>,
        sharedPreferences: SharedPreferences,
    ) {
        val coreAssetsDirectory = getAssetsDirectory(directoriesManager)
        coreAssetsDirectory.deleteRecursively()
        coreAssetsDirectory.mkdirs()

        response.body()?.use { zipInputStream ->
            val rootPath = coreAssetsDirectory.canonicalPath + File.separator
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                // libretro's PPSSPP.zip keeps everything inside a "PPSSPP/" folder.
                val name = entry.name.removePrefix("$PPSSPP_ASSETS_FOLDER_NAME/")
                if (name.isEmpty()) continue
                val destFile = File(coreAssetsDirectory, name)
                if (!destFile.canonicalPath.startsWith(rootPath)) continue
                Timber.d("Writing file: $name")
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { zipInputStream.copyTo(it) }
                }
            }
        }

        sharedPreferences.edit()
            .putString(PPSSPP_ASSETS_VERSION_KEY, PPSSPP_ASSETS_VERSION)
            .commit()
    }

    private suspend fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val directoryExists = getAssetsDirectory(directoriesManager).exists()

            val currentVersion = sharedPreferences.getString(PPSSPP_ASSETS_VERSION_KEY, "none")
            val hasCurrentVersion = currentVersion == PPSSPP_ASSETS_VERSION

            !directoryExists || !hasCurrentVersion
        }

    private suspend fun getAssetsDirectory(directoriesManager: DirectoriesManager): File {
        return withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), PPSSPP_ASSETS_FOLDER_NAME)
        }
    }

    companion object {
        const val PPSSPP_ASSETS_VERSION = CoresRelease.VERSION

        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"

        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
    }
}
