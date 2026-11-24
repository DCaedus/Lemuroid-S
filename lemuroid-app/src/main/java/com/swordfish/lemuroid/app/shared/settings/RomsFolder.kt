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
            runCatching { MainFolder.createSystemFoldersIfEmpty(roms) }
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
        runCatching { MainFolder.createSystemFoldersIfEmpty(DocumentFolder(context.contentResolver, folderUri)) }
            .onFailure { Timber.w(it, "Could not create system folders") }
        setLocation(context, LOCATION_CUSTOM)
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
