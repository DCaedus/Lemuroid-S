package com.swordfish.lemuroid.app.mobile.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink

/**
 * Lemuroid-S: "Reset all systems" at the top of a per-system setting, with a confirmation. [onReset]
 * removes the per-system values; the list below is rebuilt afterwards (see [resetCount]).
 */
@Composable
fun ResetAllSystemsButton(onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    LemuroidSettingsMenuLink(
        title = { Text(text = stringResource(R.string.reset_all_systems_title)) },
        subtitle = { Text(text = stringResource(R.string.reset_all_systems_description)) },
        onClick = { confirm = true },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(text = stringResource(R.string.reset_settings_warning_message_title)) },
            text = { Text(text = stringResource(R.string.reset_all_systems_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onReset()
                }) { Text(text = stringResource(R.string.reset_all_systems_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(text = stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Removes every preference whose key starts with [prefix]. */
fun resetPreferencesWithPrefix(
    context: android.content.Context,
    prefix: String,
) {
    val preferences = com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.getSharedPreferences(context)
    val editor = preferences.edit()
    preferences.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
    editor.apply()
}
