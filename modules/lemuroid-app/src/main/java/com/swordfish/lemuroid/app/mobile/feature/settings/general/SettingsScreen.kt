package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import android.provider.Settings
import android.content.Intent
import android.os.Build
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
import androidx.navigation.NavController
import com.swordfish.lemuroid.app.mobile.feature.main.AppUpdateRequests
import com.swordfish.lemuroid.ext.feature.update.AppUpdater
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.covers.ArtworkType
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.settings.OrientationSettings
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
import com.swordfish.lemuroid.lib.storage.MainFolder

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
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
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
        )
        InterfaceSettings()
        DisplaySettings()
        SavesSettings(isSaveSyncSupported = state.isSaveSyncSupported, navController = navController)
        FastForwardSettings(navController)
        ArtworkSettings(navController = navController)
        InputSettings(navController = navController)
        MiscSettings(
            indexingInProgress = indexingInProgress,
            navController = navController,
        )
    }
}

@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    navController: NavController,
) {
    val context = LocalContext.current
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        // Lemuroid-S: GitHub version only (Google Play updates the app itself).
        val updater = remember { AppUpdater(context.applicationContext) }
        if (updater.isSupported) {
            LemuroidSettingsList(
                state =
                    indexPreferenceState(
                        AppUpdater.PREF_FREQUENCY,
                        AppUpdater.DEFAULT_FREQUENCY,
                        stringListResource(R.array.pref_key_update_frequency_values),
                    ),
                title = { Text(text = stringResource(id = R.string.settings_title_update_frequency)) },
                items = stringListResource(R.array.pref_key_update_frequency_display_names),
            )
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_check_updates)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_check_updates)) },
                onClick = { AppUpdateRequests.manualCheck.tryEmit(Unit) },
            )
        }
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_core_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_core_settings)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES) },
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
private fun FastForwardSettings(navController: NavController) {
    val audioModeValues = stringListResource(R.array.pref_key_fast_forward_audio_values)
    val audioMode =
        indexPreferenceState(
            R.string.pref_key_fast_forward_audio,
            SettingsManager.DEFAULT_FAST_FORWARD_AUDIO,
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
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_fast_forward_button_per_system)) },
            subtitle = {
                Text(
                    text = stringResource(id = R.string.settings_description_fast_forward_button_per_system),
                )
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_FAST_FORWARD_BUTTON) },
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_turbo_button, true),
            title = { Text(text = stringResource(id = R.string.settings_title_turbo_button)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_turbo_button)) },
        )
    }
}

/** Lemuroid-S: autosave, importing and syncing saves. */
@Composable
private fun SavesSettings(
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_saves)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_periodic_autosave_minutes,
                    "30",
                    stringListResource(R.array.pref_key_periodic_autosave_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_periodic_autosave)) },
            items = stringListResource(R.array.pref_key_periodic_autosave_display_names),
        )
        var showImportConfirmation by remember { mutableStateOf(false) }
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_import_saves)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_import_saves)) },
            onClick = { showImportConfirmation = true },
        )
        ImportSavesDialog(
            show = showImportConfirmation,
            onDismiss = { showImportConfirmation = false },
        )
        if (isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
    }
}

@Composable
private fun ArtworkSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_artwork)) },
    ) {
        ArtworkTypeSetting()
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_artwork_per_system)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_artwork_per_system)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ARTWORK) },
        )
        ImportRetroArchCoversSetting()
    }
}

@Composable
internal fun ArtworkTypeSetting() {
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

/** Lemuroid-S: how the app itself looks and opens. */
@Composable
private fun InterfaceSettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_interface)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_start_screen,
                    "home",
                    stringListResource(R.array.pref_key_start_screen_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_start_screen)) },
            items = stringListResource(R.array.pref_key_start_screen_display_names),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LemuroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_wallpaper_colors, true),
                title = { Text(text = stringResource(id = R.string.settings_title_wallpaper_colors)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_wallpaper_colors)) },
            )
        }
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_pure_black, false),
            title = { Text(text = stringResource(id = R.string.settings_title_pure_black)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_pure_black)) },
        )
        // Lemuroid-S: Android 13+ lets each app have its own language; this opens that page.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val context = LocalContext.current
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_language)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_language)) },
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_menus_portrait, true),
            title = { Text(text = stringResource(id = R.string.settings_title_menus_portrait)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_menus_portrait)) },
        )
    }
}

/** How games are shown: display filter (HD mode included), position on screen. */
@Composable
private fun DisplaySettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_display)) },
    ) {
        val filterValues = stringListResource(R.array.pref_key_shader_filter_values).toList()
        val filter = indexPreferenceState(R.string.pref_key_shader_filter, SettingsManager.DEFAULT_SHADER_FILTER, filterValues)
        LemuroidSettingsList(
            state = filter,
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        // The HD mode quality is used (and can be changed) while HD mode is the chosen filter.
        LemuroidSettingsSlider(
            enabled = filterValues.getOrNull(filter.value) == SettingsManager.HD_FILTER,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = SettingsManager.DEFAULT_HD_MODE_QUALITY,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
            valueLabels =
                listOf(
                    stringResource(R.string.settings_hd_quality_low),
                    stringResource(R.string.settings_hd_quality_mid),
                    stringResource(R.string.settings_hd_quality_high),
                ),
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
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_game_orientation,
                    OrientationSettings.SYSTEM,
                    stringListResource(R.array.pref_key_game_orientation_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_game_orientation)) },
            items = stringListResource(R.array.pref_key_game_orientation_display_names),
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false),
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_show_framerate, false),
            title = { Text(text = stringResource(id = R.string.settings_title_show_framerate)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_show_framerate)) },
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory

    val currentDirectoryName =
        remember(state.currentDirectory) {
            runCatching { MainFolder.displayName(context, Uri.parse(currentDirectory)) }.getOrNull()
        }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.settings_category_library)) }) {
        RomsFolderSettings(enabled = !indexingInProgress, currentCustomFolderName = currentDirectoryName)
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
