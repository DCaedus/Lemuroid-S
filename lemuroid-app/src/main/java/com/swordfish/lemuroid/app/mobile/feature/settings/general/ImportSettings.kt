package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoversImportWork
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.saves.SavesImportWork
import com.swordfish.lemuroid.app.shared.storage.rememberRetroArchFolderAccess
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import kotlinx.coroutines.launch

/**
 * Import Saves: a list of sources. Always keep this in the composition; [show] controls the dialog
 * (the pickers must stay registered while they are open).
 *  - RetroArch: its folder (confirmed once in the folder picker, then remembered)
 *  - Pizza Boy / any ZIP: a ZIP of saves, e.g. Pizza Boy's "export saves"
 *  - Lemuroid / any folder: e.g. a copy of Lemuroid's files folder, or another emulator's saves folder
 */
@Composable
fun ImportSavesDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onImportStarted: () -> Unit = { },
) {
    val context = LocalContext.current
    val runImport = { source: Uri ->
        // Runs in the background like the library scan (same "working" animation and a
        // notification); the result is shown when it finishes.
        SavesImportWork.start(context, source)
        Toast.makeText(context, R.string.import_saves_started, Toast.LENGTH_SHORT).show()
        onImportStarted()
    }
    val retroArch = rememberRetroArchFolderAccess { runImport(it) }
    val zipFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runImport(uri)
        }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) runImport(uri)
        }

    if (!show) return

    val choose = { action: () -> Unit ->
        onDismiss()
        action()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.import_saves_confirm_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.import_saves_confirm_message))
                ImportSource(R.string.import_source_retroarch, R.string.import_source_retroarch_description) {
                    choose { retroArch() }
                }
                ImportSource(R.string.import_source_zip, R.string.import_source_zip_description) {
                    choose { zipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
                }
                ImportSource(R.string.import_source_folder, R.string.import_source_folder_description) {
                    choose { folder.launch(null) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportSource(
    titleId: Int,
    descriptionId: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    ) {
        Text(text = stringResource(titleId), style = MaterialTheme.typography.titleSmall)
        Text(text = stringResource(descriptionId), style = MaterialTheme.typography.bodySmall)
    }
}

/** "Import RetroArch Covers": copies RetroArch's thumbnails into the Lemuroid-S folder (in the background). */
@Composable
fun ImportRetroArchCoversSetting() {
    val context = LocalContext.current
    val importing by
        remember { PendingOperationsMonitor(context.applicationContext).isCoversImportInProgress() }
            .collectAsState(false)

    val access =
        rememberRetroArchFolderAccess { folder ->
            CoversImportWork.start(context, folder)
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
