package com.swordfish.lemuroid.lib.storage

import android.content.Context
import java.io.File

class DirectoriesManager(private val appContext: Context) {
    /** Lemuroid-S: a file packed in the app's assets (the "full" APK includes core support files), or null. */
    fun openBundledAsset(name: String): java.io.InputStream? = runCatching { appContext.assets.open(name) }.getOrNull()

    @Deprecated("Use the external states directory")
    fun getInternalStatesDirectory(): File =
        File(appContext.filesDir, "states").apply {
            mkdirs()
        }

    fun getCoresDirectory(): File =
        File(appContext.filesDir, "cores").apply {
            mkdirs()
        }

    fun getSystemDirectory(): File =
        File(appContext.filesDir, "system").apply {
            mkdirs()
        }

    fun getStatesDirectory(): File =
        File(appContext.getExternalFilesDir(null), "states").apply {
            mkdirs()
        }

    fun getStatesPreviewDirectory(): File =
        File(appContext.getExternalFilesDir(null), "state-previews").apply {
            mkdirs()
        }

    fun getSavesDirectory(): File =
        File(appContext.getExternalFilesDir(null), "saves").apply {
            mkdirs()
        }

    fun getInternalRomsDirectory(): File =
        File(appContext.getExternalFilesDir(null), "roms").apply {
            mkdirs()
        }
}
