package com.swordfish.lemuroid.app.tv.folderpicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class TVFolderPickerLauncher : ImmersiveActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            folderPicker.launch(Intent(this, TVFolderPickerActivity::class.java))
        }
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
            val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.extras?.getString(TVFolderPickerActivity.RESULT_DIRECTORY_PATH)

            if (newValue.toString() != currentValue) {
                sharedPreferences.edit().apply {
                    this.putString(preferenceKey, newValue.toString())
                    this.commit()
                }
            }

            startLibraryIndexWork()
        }
        finish()
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, TVFolderPickerLauncher::class.java))
        }
    }
}
