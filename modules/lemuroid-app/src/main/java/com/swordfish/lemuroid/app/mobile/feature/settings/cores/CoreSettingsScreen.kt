package com.swordfish.lemuroid.app.mobile.feature.settings.cores

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsCache
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem

/**
 * Lemuroid-S: the settings of every core, one category per core. They're the same settings as the
 * in-game menu's core options, and apply to every console that uses the core.
 */
@Composable
fun CoreSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cores =
        remember {
            GameSystem.all()
                .flatMap { it.systemCoreConfigs }
                .filter { it.exposedSettings.isNotEmpty() || it.exposedAdvancedSettings.isNotEmpty() }
                .map { it.coreID }
                .distinct()
                .sortedBy { it.coreDisplayName.lowercase() }
        }

    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        cores.forEach { coreID ->
            val options = remember(coreID) { CoreOptionsCache.load(context, coreID) }
            LemuroidCardSettingsGroup(title = { Text(text = coreTitle(coreID)) }) {
                if (options.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.core_settings_play_first),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    options.forEach { CoreOptionSetting(coreID, it) }
                }
            }
        }
    }
}

@Composable
private fun coreTitle(coreID: CoreID): String {
    val systems =
        GameSystem.all()
            .filter { system -> system.systemCoreConfigs.any { it.coreID == coreID } }
            .map { stringResource(it.shortTitleResId) }
    return "${coreID.coreDisplayName} (${systems.joinToString(", ")})"
}

@Composable
private fun CoreOptionSetting(
    coreID: CoreID,
    option: LemuroidCoreOption,
) {
    val context = LocalContext.current
    val key = CoreVariablesManager.computeSharedPreferenceKey(option.getKey(), coreID.coreName)
    if (option.getEntriesValues().toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(key, option.getCurrentValue() == "enabled"),
            title = { Text(text = option.getDisplayName(context)) },
        )
    } else {
        LemuroidSettingsList(
            title = { Text(text = option.getDisplayName(context)) },
            items = option.getEntries(context),
            state = indexPreferenceState(key, option.getCurrentValue(), option.getEntriesValues()),
        )
    }
}
