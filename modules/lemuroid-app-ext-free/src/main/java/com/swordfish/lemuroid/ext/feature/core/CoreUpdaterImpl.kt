/*
 * CoreManager.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.ext.feature.core

import com.swordfish.lemuroid.lib.core.NativeLibraries
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresRelease
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File

class CoreUpdaterImpl(
    private val directoriesManager: DirectoriesManager,
    retrofit: Retrofit,
) : CoreUpdater {
    private val coresVersion = CoresRelease.VERSION

    private val api = retrofit.create(CoreUpdater.CoreManagerApi::class.java)

    override suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        coreIDs.asFlow()
            .onEach { retrieveAssets(it, sharedPreferences) }
            .onEach { retrieveFile(context, it) }
            .collect()
    }

    override suspend fun coreStatus(
        context: Context,
        coreIDs: List<CoreID>,
    ): Map<CoreID, CoreUpdater.CoreStatus> =
        withContext(Dispatchers.IO) {
            val bundled = NativeLibraries.names(context)
            val coresDirectory = File(directoriesManager.getCoresDirectory(), coresVersion)
            val abi = Build.SUPPORTED_ABIS.first()
            coreIDs.associateWith { coreID ->
                val fileName = coreID.libretroFileName
                val installed = fileName in bundled || File(coresDirectory, fileName).exists()
                CoreUpdater.CoreStatus(installed, CoresRelease.downloadSize(abi, fileName))
            }
        }

    private suspend fun retrieveFile(
        context: Context,
        coreID: CoreID,
    ) {
        findBundledLibrary(context, coreID) ?: downloadCore(coreID)
    }

    private suspend fun retrieveAssets(
        coreID: CoreID,
        sharedPreferences: SharedPreferences,
    ) {
        CoreID.getAssetManager(coreID)
            .retrieveAssetsIfNeeded(api, directoriesManager, sharedPreferences)
    }

    private suspend fun downloadCore(coreID: CoreID): File {
        Timber.i("Downloading core $coreID")

        val mainCoresDirectory = directoriesManager.getCoresDirectory()
        val coresDirectory =
            File(mainCoresDirectory, coresVersion).apply {
                mkdirs()
            }

        val libFileName = coreID.libretroFileName
        val destFile = File(coresDirectory, libFileName)

        if (destFile.exists()) {
            return destFile
        }

        runCatching {
            deleteOutdatedCores(mainCoresDirectory, coresVersion)
        }

        val url = CoresRelease.coreUrl(Build.SUPPORTED_ABIS.first(), libFileName)
        val tempFile = File(coresDirectory, "$libFileName.download")
        try {
            downloadZippedLibrary(url, tempFile)
            if (!tempFile.renameTo(destFile)) throw IllegalStateException("Could not save $libFileName")
            return destFile
        } catch (e: Throwable) {
            tempFile.safeDelete()
            destFile.safeDelete()
            throw e
        }
    }

    /** Downloads a zipped core and writes the library inside it to [destFile]. */
    private suspend fun downloadZippedLibrary(
        url: String,
        destFile: File,
    ) {
        val response = api.downloadZip(url)

        if (!response.isSuccessful) {
            Timber.e("Download core response was unsuccessful")
            throw Exception(response.errorBody()?.string() ?: "Download error")
        }

        withContext(Dispatchers.IO) {
            val zip = response.body() ?: throw Exception("Empty download")
            zip.use {
                while (true) {
                    val entry = it.nextEntry ?: throw Exception("No library in $url")
                    if (entry.isDirectory || !entry.name.endsWith(".so")) continue
                    destFile.outputStream().use { output -> it.copyTo(output) }
                    return@withContext
                }
            }
        }
    }

    private suspend fun findBundledLibrary(
        context: Context,
        coreID: CoreID,
    ): String? =
        withContext(Dispatchers.IO) {
            NativeLibraries.find(context, coreID.libretroFileName)
        }

    private fun deleteOutdatedCores(
        mainCoresDirectory: File,
        applicationVersion: String,
    ) {
        mainCoresDirectory.listFiles()
            ?.filter { it.name != applicationVersion }
            ?.forEach { it.deleteRecursively() }
    }
}
