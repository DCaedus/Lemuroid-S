package com.swordfish.lemuroid.app.mobile.feature.settings.fastforward

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.library.GameSystem

/** Show or hide the on-screen fast-forward button for each console, or follow the global setting. */
@Composable
fun FastForwardButtonSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val globalEnabled = booleanPreferenceState(R.string.pref_key_fast_forward_button, true)
    val showName = stringResource(R.string.fast_forward_button_names_show)
    val hideName = stringResource(R.string.fast_forward_button_names_hide)
    val globalName = if (globalEnabled.value) showName else hideName

    val values = stringListResource(R.array.pref_key_fast_forward_button_system_values)
    val names = listOf(stringResource(R.string.artwork_type_names_default, globalName), showName, hideName)

    // Only consoles that support fast-forward (it's off for some, e.g. 3DS).
    val systems =
        remember {
            GameSystem.all()
                .filter { it.fastForwardSupport }
                .sortedBy { context.getString(it.titleResId) }
        }

    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        LemuroidCardSettingsGroup(
            title = { Text(text = stringResource(id = R.string.settings_title_fast_forward_button_per_system)) },
        ) {
            systems.forEach { system ->
                LemuroidSettingsList(
                    state =
                        indexPreferenceState(
                            SettingsManager.fastForwardButtonSystemKey(context, system.id.dbname),
                            SettingsManager.FAST_FORWARD_BUTTON_DEFAULT,
                            values,
                        ),
                    title = { Text(text = stringResource(id = system.titleResId)) },
                    items = names,
                )
            }
        }
    }
}
