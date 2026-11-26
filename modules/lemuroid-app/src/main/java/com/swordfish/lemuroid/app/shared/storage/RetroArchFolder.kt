package com.swordfish.lemuroid.app.shared.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Access to RetroArch's folder (normally "RetroArch" in phone storage), used to import saves and
 * covers. Android only lets apps read it after the user confirms it once in the folder picker; the
 * picker opens directly at RetroArch's default folder, and access is remembered afterwards.
 */
object RetroArchFolder {
    /** RetroArch's default folder on Android: /storage/emulated/0/RetroArch. */
    val DEFAULT_LOCATION: Uri =
        Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ARetroArch",
        )

    /** The remembered folder, if Lemuroid-S still has permission to read it. */
    fun savedFolder(context: Context): Uri? {
        val value =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_retroarch_folder), null) ?: return null
        val uri = Uri.parse(value)
        val stillAllowed = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return uri.takeIf { stillAllowed }
    }

    fun save(
        context: Context,
        uri: Uri,
    ) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putString(context.getString(R.string.pref_key_retroarch_folder), uri.toString())
            .apply()
    }
}

/**
 * Returns a function that runs [onReady] with RetroArch's folder: right away if it was already
 * confirmed, otherwise after the user confirms it in the folder picker (opened at RetroArch's
 * default folder; any other folder, e.g. another emulator's, can be picked too).
 */
@Composable
fun rememberRetroArchFolderAccess(onReady: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            RetroArchFolder.save(context, uri)
            onReady(uri)
        }
    return {
        val saved = RetroArchFolder.savedFolder(context)
        if (saved != null) onReady(saved) else launcher.launch(RetroArchFolder.DEFAULT_LOCATION)
    }
}
