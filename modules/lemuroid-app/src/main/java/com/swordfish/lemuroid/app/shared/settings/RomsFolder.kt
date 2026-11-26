package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** "Select ROMS folder": Lemuroid-S/roms or a custom folder, with ES-DE style system subfolders. */
object RomsFolder {
    const val LOCATION_MAIN = "main"
    const val LOCATION_CUSTOM = "custom"

    /** Uses Lemuroid-S/roms as the ROMs folder. Returns false if the Lemuroid-S folder isn't chosen yet. */
    suspend fun useMainFolder(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val roms = MainFolder(context).folder(MainFolder.ROMS) ?: return@withContext false
            runCatching { MainFolder.createMissingSystemFolders(roms) }
            setRomsFolder(context, roms.uri, LOCATION_MAIN)
            true
        }

    /** Called after the user picked a custom ROMs folder with Android's folder picker. */
    suspend fun onCustomFolderPicked(
        context: Context,
        treeUri: Uri,
    ) = withContext(Dispatchers.IO) {
        val folderUri =
            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri),
            )
        runCatching {
            MainFolder.createMissingSystemFolders(
                DocumentFolder(context.contentResolver, folderUri),
                onlyIfOrganized = true,
            )
        }
            .onFailure { Timber.w(it, "Could not create system folders") }
        setLocation(context, LOCATION_CUSTOM)
    }

    /** True once a ROMs folder (Lemuroid-S/roms or a custom one) has been chosen. */
    fun isSet(context: Context): Boolean {
        @Suppress("DEPRECATION")
        return SharedPreferencesHelper.getLegacySharedPreferences(context)
            .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null) != null
    }

    /** True if the ROMs folder is Lemuroid-S/roms (the default) rather than a custom folder. */
    fun isMainLocation(context: Context): Boolean {
        val location =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_roms_location), null)
        return location == LOCATION_MAIN
    }

    /**
     * Adds folders for systems added since the ROMs folder was set up (run before each library scan).
     * A custom ROMs folder only gets them if it is already organized in system folders.
     */
    fun createMissingSystemFolders(context: Context) {
        val location =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_roms_location), null)

        @Suppress("DEPRECATION")
        val folderUri =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null)
                ?.let { Uri.parse(it) } ?: return
        val documentUri =
            if (android.provider.DocumentsContract.isDocumentUri(context, folderUri)) {
                folderUri
            } else {
                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    folderUri,
                    android.provider.DocumentsContract.getTreeDocumentId(folderUri),
                )
            }
        runCatching {
            MainFolder.createMissingSystemFolders(
                DocumentFolder(context.contentResolver, documentUri),
                onlyIfOrganized = location != LOCATION_MAIN,
            )
        }.onFailure { Timber.w(it, "Could not create system folders") }
    }

    private fun setRomsFolder(
        context: Context,
        folderUri: Uri,
        location: String,
    ) {
        @Suppress("DEPRECATION")
        SharedPreferencesHelper.getLegacySharedPreferences(context).edit()
            .putString(
                context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder),
                folderUri.toString(),
            )
            .apply()
        setLocation(context, location)
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
    }

    private fun setLocation(
        context: Context,
        location: String,
    ) {
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_roms_location), location)
            .apply()
    }
}
