package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.saves.SavesMoveWork
import com.swordfish.lemuroid.app.shared.settings.RomsFolder
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android's folder picker opens at the root of the phone's storage, where "Lemuroid-S" should go. */
private val STORAGE_ROOT: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A")

/**
 * Returns a function that opens Android's folder picker to choose (or create) the Lemuroid-S folder.
 * Android only lets apps write to a folder in phone storage that the user picked, so this is a
 * one-time step. [onChosen] runs once it's set (callers then move existing files into it).
 */
@Composable
fun rememberMainFolderPicker(onChosen: () -> Unit = { }): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val chosen = withContext(Dispatchers.IO) { useMainFolder(context, uri) }
                if (!chosen) {
                    Toast.makeText(context, R.string.main_folder_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                onChosen()
            }
        }
    return { launcher.launch(STORAGE_ROOT) }
}

private fun useMainFolder(
    context: Context,
    treeUri: Uri,
): Boolean {
    val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(treeUri, readWrite) }
    val chosen = runCatching { MainFolder(context).setFromPickedTree(treeUri) }.getOrDefault(false)
    // Saves made while no folder was available move into it.
    if (chosen) SavesMoveWork.enqueue(context.applicationContext)
    return chosen
}

/** Shows the Lemuroid-S folder (normally in storage root) and lets the user choose it again. */
@Composable
fun MainFolderSetting() {
    val context = LocalContext.current
    var isSet by remember { mutableStateOf(MainFolder(context).isSet()) }
    ComposableLifecycle { _, _ -> isSet = MainFolder(context).isSet() }
    val picker = rememberMainFolderPicker { isSet = true }

    LemuroidSettingsMenuLink(
        title = { Text(text = stringResource(R.string.main_folder_setting_title)) },
        subtitle = {
            Text(text = if (isSet) MainFolder.FOLDER_NAME else stringResource(R.string.main_folder_not_set))
        },
        onClick = picker,
    )
}

/**
 * "Select ROMS folder": Lemuroid-S/roms or a custom folder. One entry: the selected option shows
 * where ROMs are, and choosing "Custom folder" (again) opens the folder picker.
 */
@Composable
fun RomsFolderSettings(
    enabled: Boolean = true,
    currentCustomFolderName: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values = stringListResource(R.array.pref_key_roms_location_values)
    // A ROMs folder picked before this option existed counts as a custom folder.
    val defaultLocation =
        remember {
            if (currentCustomFolderName != null) RomsFolder.LOCATION_CUSTOM else RomsFolder.LOCATION_MAIN
        }
    val location =
        indexPreferenceState(
            com.swordfish.lemuroid.lib.R.string.pref_key_roms_location,
            defaultLocation,
            values,
        )

    val isCustom = values.getOrNull(location.value) == RomsFolder.LOCATION_CUSTOM
    val items =
        listOf(
            stringResource(R.string.roms_option_main),
            currentCustomFolderName
                ?.takeIf { isCustom }
                ?.let { stringResource(R.string.roms_option_custom_named, it) }
                ?: stringResource(R.string.folder_option_custom),
        )

    LemuroidSettingsList(
        enabled = enabled,
        state = location,
        title = { Text(text = stringResource(R.string.select_roms_folder)) },
        items = items,
        onItemSelected = { index, _ ->
            when (values[index]) {
                RomsFolder.LOCATION_MAIN -> scope.launch { useMainRomsFolder(context) }
                RomsFolder.LOCATION_CUSTOM -> StorageFrameworkPickerLauncher.pickFolder(context)
            }
        },
    )
}

/** Uses Lemuroid-S/roms, or tells the user the Lemuroid-S folder has to be chosen first. */
suspend fun useMainRomsFolder(context: Context) {
    if (!RomsFolder.useMainFolder(context)) {
        Toast.makeText(context, R.string.main_folder_required, Toast.LENGTH_LONG).show()
    }
}

/** "Select SAVES folder": where battery saves and save states go (Lemuroid-S folder or ROMs folder). */
@Composable
fun SavesFolderSettings() {
    val values = stringListResource(R.array.pref_key_saves_location_values)
    val location =
        indexPreferenceState(
            com.swordfish.lemuroid.lib.R.string.pref_key_saves_location,
            ExternalSavesStorage.Location.MAIN_FOLDER.prefValue,
            values,
        )

    LemuroidSettingsList(
        state = location,
        title = { Text(text = stringResource(R.string.select_saves_folder)) },
        items = stringListResource(R.array.pref_key_saves_location_display_names),
    )
}
