package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.RetroArchCoversImporter
import com.swordfish.lemuroid.app.shared.saves.SavesImportWork
import com.swordfish.lemuroid.app.shared.storage.rememberRetroArchFolderAccess
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Import saves?" confirmation for Import Saves from RetroArch. Uses RetroArch's folder
 * (confirmed once in the folder picker, then remembered).
 * Always keep this in the composition; [show] controls the dialog (the folder pickers must stay
 * registered while they are open).
 */
@Composable
fun ImportSavesDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onImportStarted: () -> Unit = { },
) {
    val context = LocalContext.current
    val runImport = { folder: Uri ->
        // Runs in the background like the library scan (same "working" animation); the result
        // is shown when it finishes.
        SavesImportWork.start(context, folder)
        Toast.makeText(context, R.string.import_saves_started, Toast.LENGTH_SHORT).show()
        onImportStarted()
    }
    val retroArch = rememberRetroArchFolderAccess { runImport(it) }
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.import_saves_confirm_title)) },
        text = { Text(text = stringResource(R.string.import_saves_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    retroArch()
                },
            ) { Text(text = stringResource(R.string.import_saves_from_retroarch)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        },
    )
}

/** "Import RetroArch Covers": copies RetroArch's thumbnails into the Lemuroid-S folder. */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ImportRetroArchCoversSetting() {
    val context = LocalContext.current
    var importing by remember { mutableStateOf(false) }

    val access =
        rememberRetroArchFolderAccess { folder ->
            importing = true
            val appContext = context.applicationContext
            // Can take a while for big collections; keep going even if the user leaves Settings.
            GlobalScope.launch {
                val copied = runCatching { RetroArchCoversImporter(appContext).importFrom(folder) }.getOrNull()
                withContext(Dispatchers.Main) {
                    importing = false
                    val message =
                        if (copied != null) {
                            appContext.getString(R.string.import_covers_result, copied)
                        } else {
                            appContext.getString(R.string.import_covers_failed)
                        }
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }

    LemuroidSettingsMenuLink(
        enabled = !importing,
        title = { Text(text = stringResource(R.string.import_covers_title)) },
        subtitle = {
            Text(
                text =
                    stringResource(
                        if (importing) R.string.import_covers_running else R.string.import_covers_description,
                    ),
            )
        },
        onClick = access,
    )
}
