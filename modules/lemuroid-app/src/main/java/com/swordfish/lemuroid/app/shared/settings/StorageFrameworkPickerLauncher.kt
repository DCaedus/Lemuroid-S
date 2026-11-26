package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Write access lets Lemuroid-S store saves next to the ROMs, if chosen in Settings.
                    this.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    this.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                }
            try {
                folderPicker.launch(intent)
            } catch (e: Exception) {
                showStorageAccessFrameworkNotSupportedDialog()
            }
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_not_found, directoriesManager.getInternalRomsDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    // Lemuroid-S: folder picker result through the activity result API (was onActivityResult).
    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onFolderPicked(result.resultCode, result.data)
        }

    private fun onFolderPicked(
        resultCode: Int,
        resultData: Intent?,
    ) {
        if (resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.data

            if (newValue != null && newValue.toString() != currentValue) {
                updatePersistableUris(newValue)

                sharedPreferences.edit().apply {
                    this.putString(preferenceKey, newValue.toString())
                    this.apply()
                }
            }

            val pickedUri = resultData?.data
            if (pickedUri != null) {
                // Creates one folder per system if the chosen folder is empty, then scans.
                GlobalScope.launch {
                    RomsFolder.onCustomFolderPicked(applicationContext, pickedUri)
                    startLibraryIndexWork()
                }
            } else {
                startLibraryIndexWork()
            }
        }
        finish()
    }

    private fun updatePersistableUris(uri: Uri) {
        // Keep the permissions for the Lemuroid-S folder and RetroArch thumbnails folder, if chosen.
        val preferences = SharedPreferencesHelper.getSharedPreferences(this)
        val keptFolders =
            setOfNotNull(
                preferences.getString(getString(com.swordfish.lemuroid.lib.R.string.pref_key_main_folder_tree), null),
                preferences.getString(getString(R.string.pref_key_retroarch_folder), null),
            )

        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .filter { it.uri != uri }
            .filter { it.uri.toString() !in keptFolders }
            .forEach {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                contentResolver.releasePersistableUriPermission(it.uri, flags)
            }

        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, readWrite) }
            .onFailure { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }
}
