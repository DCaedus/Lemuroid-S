package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import com.swordfish.lemuroid.app.shared.storage.RetroArchFolder
import com.swordfish.lemuroid.app.shared.storage.rememberRetroArchFolderAccess
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import kotlinx.coroutines.launch

/**
 * Import Saves: a list of sources. Always keep this in the composition; [show] controls the dialog
 * (the pickers must stay registered while they are open).
 *  - RetroArch: its folder (confirmed once in the folder picker, then remembered)
 *  - Folder: any folder with saves, e.g. another emulator's saves folder or a copy of Lemuroid's files
 *  - ZIP file: a ZIP of saves, e.g. Pizza Boy's "export saves"
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

    // RetroArch's hint is only needed while its folder still has to be confirmed in the picker.
    val retroArchNeedsPicker = remember { RetroArchFolder.savedFolder(context) == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.import_saves_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_saves_confirm_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    text = stringResource(R.string.import_saves_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ImportSourceButton(
                    titleId = R.string.import_source_retroarch,
                    descriptionId = R.string.import_source_retroarch_description.takeIf { retroArchNeedsPicker },
                ) { choose { retroArch() } }
                ImportSourceButton(R.string.import_source_folder) { choose { folder.launch(null) } }
                ImportSourceButton(R.string.import_source_zip) {
                    choose { zipFile.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        },
    )
}

/** A full-width tonal button for one import source, with an optional hint under its name. */
@Composable
private fun ImportSourceButton(
    titleId: Int,
    descriptionId: Int? = null,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(titleId), style = MaterialTheme.typography.titleSmall)
            if (descriptionId != null) {
                Text(text = stringResource(descriptionId), style = MaterialTheme.typography.bodySmall)
            }
        }
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
