package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.covers.ArtworkType
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    LemuroidSettingsPage(modifier = modifier) {
        RomsSettings(
            state = state,
            onChangeFolder = { viewModel.changeLocalStorageFolder() },
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
        )
        GeneralSettings()
        FastForwardSettings()
        ArtworkSettings(navController = navController)
        TestSettings()
        InputSettings(navController = navController)
        MiscSettings(
            indexingInProgress = indexingInProgress,
            isSaveSyncSupported = state.isSaveSyncSupported,
            navController = navController,
        )
    }
}

@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        if (isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_advanced_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )
    }
}

@Composable
private fun FastForwardSettings() {
    val audioModeValues = stringListResource(R.array.pref_key_fast_forward_audio_values)
    val audioMode =
        indexPreferenceState(
            R.string.pref_key_fast_forward_audio,
            SettingsManager.FAST_FORWARD_AUDIO_NORMAL,
            audioModeValues,
        )
    val volume =
        intPreferenceState(
            key = stringResource(id = R.string.pref_key_fast_forward_volume),
            default = SettingsManager.DEFAULT_FAST_FORWARD_VOLUME_STEPS,
        )
    val isLowerVolumeMode = audioModeValues.getOrNull(audioMode.value) == SettingsManager.FAST_FORWARD_AUDIO_LOWER

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_fast_forward)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_fast_forward_speed,
                    SettingsManager.DEFAULT_FAST_FORWARD_SPEED,
                    stringListResource(R.array.pref_key_fast_forward_speed_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_fast_forward_speed)) },
            items = stringListResource(R.array.pref_key_fast_forward_speed_display_names),
        )
        LemuroidSettingsList(
            state = audioMode,
            title = { Text(text = stringResource(id = R.string.settings_title_fast_forward_audio)) },
            items = stringListResource(R.array.pref_key_fast_forward_audio_display_names),
        )
        if (isLowerVolumeMode) {
            LemuroidSettingsSlider(
                enabled = true,
                state = volume,
                steps = 9,
                valueRange = 0f..10f,
                title = { Text(text = stringResource(R.string.settings_title_fast_forward_volume)) },
                subtitle = {
                    Text(text = stringResource(R.string.settings_description_fast_forward_volume, volume.value * 10))
                },
            )
        }
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_fast_forward_button, true),
            title = { Text(text = stringResource(id = R.string.settings_title_fast_forward_button)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_fast_forward_button)) },
        )
    }
}

/** Lemuroid-S settings that are new and still being tested. */
@Composable
private fun TestSettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_test)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_periodic_autosave_minutes,
                    "0",
                    stringListResource(R.array.pref_key_periodic_autosave_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_periodic_autosave)) },
            items = stringListResource(R.array.pref_key_periodic_autosave_display_names),
        )
        SavesLocationSettings()
    }
}

/** Where in-game saves (.srm) are stored, besides the app's own copy. */
@Composable
private fun SavesLocationSettings() {
    val context = LocalContext.current
    val locationValues = stringListResource(R.array.pref_key_saves_location_values)
    val location =
        indexPreferenceState(
            com.swordfish.lemuroid.lib.R.string.pref_key_saves_location,
            ExternalSavesStorage.Location.APP_STORAGE.prefValue,
            locationValues,
        )

    LemuroidSettingsList(
        state = location,
        title = { Text(text = stringResource(id = R.string.settings_title_saves_location)) },
        items = stringListResource(R.array.pref_key_saves_location_display_names),
    )

    val isCustomFolder =
        locationValues.getOrNull(location.value) == ExternalSavesStorage.Location.CUSTOM_FOLDER.prefValue
    if (!isCustomFolder) return

    val folderKey = stringResource(com.swordfish.lemuroid.lib.R.string.pref_key_saves_custom_folder)
    val preferences = remember { SharedPreferencesHelper.getSharedPreferences(context) }
    var folderUri by remember { mutableStateOf(preferences.getString(folderKey, null)) }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, readWrite) }
            preferences.edit().putString(folderKey, uri.toString()).apply()
            folderUri = uri.toString()
        }

    val folderName =
        remember(folderUri) {
            folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
        } ?: stringResource(R.string.none)

    LemuroidSettingsMenuLink(
        title = { Text(text = stringResource(id = R.string.settings_title_saves_folder)) },
        subtitle = { Text(text = folderName) },
        onClick = { folderPicker.launch(null) },
    )
}

@Composable
private fun ArtworkSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_artwork)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_artwork_type,
                    ArtworkType.DEFAULT.prefValue,
                    stringListResource(R.array.pref_key_artwork_type_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_artwork_type)) },
            items = stringListResource(R.array.pref_key_artwork_type_display_names),
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_artwork_per_system)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_artwork_per_system)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ARTWORK) },
        )
    }
}

@Composable
private fun InputSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = {
                Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
            },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
    }
}

@Composable
private fun GeneralSettings() {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        LemuroidSettingsSwitch(
            state = immersiveMode,
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
        LemuroidSettingsSwitch(
            state = hdMode,
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_mode)) },
        )
        LemuroidSettingsSlider(
            enabled = hdMode.value,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
        )
        LemuroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_viewport_alignment,
                    "center",
                    stringListResource(R.array.pref_key_viewport_alignment_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_viewport_alignment)) },
            items = stringListResource(R.array.pref_key_viewport_alignment_display_names),
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    val currentDirectoryName =
        remember(state.currentDirectory) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
            }.getOrNull() ?: emptyDirectory
        }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.rescan)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}
