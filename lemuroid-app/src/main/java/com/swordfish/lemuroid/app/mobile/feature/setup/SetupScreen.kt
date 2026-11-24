package com.swordfish.lemuroid.app.mobile.feature.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.general.ArtworkTypeSetting
import com.swordfish.lemuroid.app.mobile.feature.settings.general.RetroArchThumbnailsFolderSetting
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SavesLocationSettings
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/** One-time welcome screen shown on first launch: ROM folder, saves location, artwork, notifications. */
object Setup {
    fun isCompleted(context: Context): Boolean {
        return SharedPreferencesHelper.getSharedPreferences(context)
            .getBoolean(context.getString(R.string.pref_key_setup_completed), false)
    }

    fun markCompleted(context: Context) {
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.pref_key_setup_completed), true)
            .apply()
    }
}

@Composable
fun SetupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current

    val finish = {
        Setup.markCompleted(context)
        onFinished()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LemuroidSettingsPage(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = stringResource(R.string.setup_message), style = MaterialTheme.typography.bodyMedium)
            }

            LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.setup_category_games)) }) {
                RomFolderSetting()
            }

            LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.setup_category_saves)) }) {
                SavesLocationSettings()
            }

            LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.settings_category_artwork)) }) {
                ArtworkTypeSetting()
                RetroArchThumbnailsFolderSetting()
            }

            NotificationsSetting()

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = finish) { Text(text = stringResource(R.string.setup_skip)) }
                Button(onClick = finish) { Text(text = stringResource(R.string.setup_done)) }
            }
        }
    }
}

@Composable
private fun RomFolderSetting() {
    val context = LocalContext.current
    var folderUri by remember { mutableStateOf(readRomFolder(context)) }

    // The folder is picked in a separate screen; refresh when coming back.
    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) folderUri = readRomFolder(context)
    }

    val folderName =
        remember(folderUri) {
            folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
        }

    LemuroidSettingsMenuLink(
        title = { Text(text = stringResource(R.string.setup_rom_folder)) },
        subtitle = { Text(text = folderName ?: stringResource(R.string.setup_rom_folder_not_set)) },
        onClick = { StorageFrameworkPickerLauncher.pickFolder(context) },
    )
}

@Suppress("DEPRECATION")
private fun readRomFolder(context: Context): String? {
    return SharedPreferencesHelper.getLegacySharedPreferences(context)
        .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null)
}

@Composable
private fun NotificationsSetting() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var granted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    if (granted) return

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.setup_category_notifications)) }) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(R.string.setup_notifications_title)) },
            subtitle = { Text(text = stringResource(R.string.setup_notifications_message)) },
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
