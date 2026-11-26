package com.swordfish.lemuroid.app.mobile.feature.settings.artwork

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.ArtworkType
import com.swordfish.lemuroid.app.shared.covers.CoverUrls
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.library.GameSystem

/** Lets the user pick the artwork type for each console, or follow the global setting. */
@Composable
fun ArtworkSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val globalValues = stringListResource(R.array.pref_key_artwork_type_values)
    val globalNames = stringListResource(R.array.pref_key_artwork_type_display_names)
    val globalState =
        indexPreferenceState(
            R.string.pref_key_artwork_type,
            ArtworkType.DEFAULT.prefValue,
            globalValues,
        )
    val globalName = globalNames.getOrElse(globalState.value) { globalNames.first() }

    val systemValues = stringListResource(R.array.pref_key_artwork_type_system_values)
    val systemNames = listOf(stringResource(R.string.artwork_type_names_default, globalName)) + globalNames

    val systems =
        remember(resources) {
            GameSystem.all().sortedBy { resources.getString(it.titleResId) }
        }

    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        LemuroidCardSettingsGroup(
            title = { Text(text = stringResource(id = R.string.settings_title_artwork_per_system)) },
        ) {
            systems.forEach { system ->
                LemuroidSettingsList(
                    state =
                        indexPreferenceState(
                            CoverUrls.systemArtworkKey(context, system),
                            ArtworkType.PREF_VALUE_DEFAULT,
                            systemValues,
                        ),
                    title = { Text(text = stringResource(id = system.titleResId)) },
                    items = systemNames,
                )
            }
        }
    }
}
