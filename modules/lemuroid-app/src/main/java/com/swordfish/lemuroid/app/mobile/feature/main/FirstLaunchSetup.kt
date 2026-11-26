package com.swordfish.lemuroid.app.mobile.feature.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.general.ImportSavesDialog
import com.swordfish.lemuroid.app.mobile.feature.settings.general.rememberMainFolderPicker
import com.swordfish.lemuroid.app.mobile.feature.settings.general.useMainRomsFolder
import com.swordfish.lemuroid.app.shared.settings.RomsFolder
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * First-launch setup: the setup cards (Lemuroid-S folder, ROMs folder, notifications, Import Saves)
 * on a layer above the app, shown until the user taps Done. Settings > Advanced opens it again.
 */
object FirstLaunchSetup {
    private val visibleState = MutableStateFlow<Boolean?>(null)

    /** Whether the setup layer is shown; read from the preferences the first time. */
    fun visible(context: Context): StateFlow<Boolean?> {
        if (visibleState.value == null) visibleState.value = !isDone(context)
        return visibleState
    }

    /** Opens the setup again (Settings > Advanced). */
    fun show() {
        visibleState.value = true
    }

    fun finish(context: Context) {
        preferences(context).edit { putBoolean(context.getString(R.string.pref_key_first_launch_setup_done), true) }
        visibleState.value = false
    }

    private fun isDone(context: Context) =
        preferences(context).getBoolean(context.getString(R.string.pref_key_first_launch_setup_done), false)

    private fun preferences(context: Context) = SharedPreferencesHelper.getSharedPreferences(context)
}

/** Shows [content] with the first-launch setup layer on top of it while that's open. */
@Composable
fun FirstLaunchSetupLayer(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val visible by remember { FirstLaunchSetup.visible(context.applicationContext) }.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = visible == true,
            enter = fadeIn(tween(SCREEN_FADE_MS)),
            exit = fadeOut(tween(SCREEN_FADE_MS)),
        ) {
            FirstLaunchSetupScreen(onDone = { FirstLaunchSetup.finish(context.applicationContext) })
        }
    }
}

private data class SetupStatus(
    val mainFolder: Boolean = false,
    val romsFolder: Boolean = false,
    val notifications: Boolean = false,
)

private fun readStatus(context: Context) =
    SetupStatus(
        mainFolder = MainFolder(context).isSet(),
        romsFolder = RomsFolder.isSet(context),
        notifications =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
    )

@Composable
private fun FirstLaunchSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(readStatus(context)) }
    var importStarted by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val refresh = { status = readStatus(context) }

    // Pickers and permission screens are other activities: re-read everything when coming back.
    ComposableLifecycle { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }

    // The setup layer covers the app: Back closes the app instead of acting on the screen below.
    BackHandler { (context as? Activity)?.finish() }

    val mainFolderPicker = rememberMainFolderPicker(onChosen = refresh)
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) context.displayDetailsSettingsScreen()
            refresh()
        }
    ImportSavesDialog(
        show = showImportDialog,
        onDismiss = { showImportDialog = false },
        onImportStarted = { importStarted = true },
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.first_launch_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.first_launch_setup_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SetupCard(
                titleId = R.string.main_folder_card_title,
                messageId = R.string.main_folder_card_message,
                actionId = R.string.main_folder_card_action,
                done = status.mainFolder,
                onAction = mainFolderPicker,
            )
            SetupCard(
                titleId = R.string.home_empty_title,
                messageId = R.string.home_empty_message,
                actionId = R.string.home_roms_main_action,
                done = status.romsFolder,
                enabled = status.mainFolder,
                onAction = {
                    scope.launch {
                        useMainRomsFolder(context)
                        refresh()
                    }
                },
                secondaryActionId = R.string.folder_option_custom,
                onSecondaryAction = { StorageFrameworkPickerLauncher.pickFolder(context) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupCard(
                    titleId = R.string.home_notification_title,
                    messageId = R.string.home_notification_message,
                    actionId = R.string.home_notification_action,
                    done = status.notifications,
                    enabled = !status.notifications,
                    onAction = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            SetupCard(
                titleId = R.string.home_import_saves_title,
                messageId = R.string.home_import_saves_message,
                actionId = R.string.home_import_saves_action,
                done = importStarted,
                enabled = status.mainFolder,
                onAction = { showImportDialog = true },
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!status.mainFolder) {
                    Text(
                        text = stringResource(R.string.first_launch_setup_needs_folder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDone, enabled = status.mainFolder) {
                    Text(text = stringResource(R.string.first_launch_setup_done))
                }
            }
        }
    }
}

/** A setup card, like the ones Lemuroid shows on its home screen, with a "Done" mark once handled. */
@Composable
private fun SetupCard(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    done: Boolean,
    enabled: Boolean = true,
    onAction: () -> Unit,
    secondaryActionId: Int? = null,
    onSecondaryAction: () -> Unit = { },
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(titleId),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.first_launch_setup_step_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(text = stringResource(messageId), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (secondaryActionId != null) {
                    TextButton(onClick = onSecondaryAction, enabled = enabled) {
                        Text(stringResource(id = secondaryActionId))
                    }
                }
                OutlinedButton(onClick = onAction, enabled = enabled) {
                    Text(stringResource(id = actionId))
                }
            }
        }
    }
}
