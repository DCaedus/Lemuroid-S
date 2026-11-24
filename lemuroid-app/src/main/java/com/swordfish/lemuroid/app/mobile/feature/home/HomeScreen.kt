package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.general.RomsFolderSettings
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SavesFolderSettings
import com.swordfish.lemuroid.app.mobile.feature.settings.general.rememberMainFolderPicker
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                viewModel.updatePermissions(applicationContext)
            }
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())
    val setupCards = viewModel.getSetupCards().collectAsState(HomeViewModel.SetupCards())

    var showSavesLocationDialog by remember { mutableStateOf(false) }
    var showRomsFolderDialog by remember { mutableStateOf(false) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    val mainFolderPicker = rememberMainFolderPicker { viewModel.updatePermissions(applicationContext) }
    val importFolderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            viewModel.importSaves(uri) { result ->
                val message =
                    if (result != null) {
                        context.getString(
                            R.string.import_saves_result,
                            result.imported,
                            result.importedStates,
                            result.skippedOlder,
                        )
                    } else {
                        context.getString(R.string.import_saves_failed)
                    }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

    if (showSavesLocationDialog) {
        SettingsDialog(
            titleId = R.string.select_saves_folder,
            onDone = {
                showSavesLocationDialog = false
                viewModel.dismissSavesLocationCard()
            },
        ) { SavesFolderSettings() }
    }

    if (showRomsFolderDialog) {
        SettingsDialog(
            titleId = R.string.select_roms_folder,
            onDone = { showRomsFolderDialog = false },
        ) { RomsFolderSettings() }
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text(text = stringResource(R.string.import_saves_confirm_title)) },
            text = { Text(text = stringResource(R.string.import_saves_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        importFolderPicker.launch(null)
                    },
                ) { Text(text = stringResource(R.string.import_saves_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }

    HomeScreen(
        modifier,
        state.value,
        setupCards.value,
        mainFolderPicker,
        { showSavesLocationDialog = true },
        { showImportConfirmation = true },
        { viewModel.dismissImportSavesCard() },
        onGameClick,
        onGameLongClick,
        onOpenCoreSelection,
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@HomeScreen
            }

            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        { showRomsFolderDialog = true },
    ) // TODO COMPOSE We need to understand what's going to happen here.
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    setupCards: HomeViewModel.SetupCards,
    onChooseMainFolder: () -> Unit,
    onChooseSavesLocation: () -> Unit,
    onImportSaves: () -> Unit,
    onDismissImportSaves: () -> Unit,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(setupCards.showMainFolder) {
            HomeNotification(
                titleId = R.string.main_folder_card_title,
                messageId = R.string.main_folder_card_message,
                actionId = R.string.main_folder_card_action,
                onAction = onChooseMainFolder,
            )
        }
        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            HomeNotification(
                titleId = R.string.home_notification_title,
                messageId = R.string.home_notification_message,
                actionId = R.string.home_notification_action,
                onAction = onEnableNotificationsClicked,
            )
        }
        AnimatedVisibility(state.showNoGamesCard) {
            HomeNotification(
                titleId = R.string.home_empty_title,
                messageId = R.string.home_empty_message,
                actionId = R.string.home_empty_action,
                onAction = onSetDirectoryClicked,
                enabled = !state.indexInProgress,
            )
        }
        AnimatedVisibility(setupCards.showSavesLocation) {
            HomeNotification(
                titleId = R.string.select_saves_folder,
                messageId = R.string.home_saves_folder_message,
                actionId = R.string.home_choose,
                onAction = onChooseSavesLocation,
            )
        }
        // Import only makes sense once there are games to match the saves to.
        AnimatedVisibility(setupCards.showImportSaves && !state.showNoGamesCard && !state.indexInProgress) {
            HomeNotification(
                titleId = R.string.home_import_saves_title,
                messageId = R.string.home_import_saves_message,
                actionId = R.string.home_import_saves_action,
                onAction = onImportSaves,
                secondaryActionId = R.string.home_card_not_now,
                onSecondaryAction = onDismissImportSaves,
            )
        }
        AnimatedVisibility(state.showNoMicrophonePermissionCard) {
            HomeNotification(
                titleId = R.string.home_microphone_title,
                messageId = R.string.home_microphone_message,
                actionId = R.string.home_microphone_action,
                onAction = onEnableMicrophoneClicked,
            )
        }
        AnimatedVisibility(state.showDesmumeDeprecatedCard) {
            HomeNotification(
                titleId = R.string.home_notification_desmume_deprecated_title,
                messageId = R.string.home_notification_desmume_deprecated_message,
                actionId = R.string.home_notification_desmume_deprecated_action,
                onAction = onOpenCoreSelection,
            )
        }
        HomeRow(
            stringResource(id = R.string.recent),
            state.recentGames,
            onGameClicked,
            onGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.favorites),
            state.favoritesGames,
            onGameClicked,
            onGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.discover),
            state.discoveryGames,
            onGameClicked,
            onGameLongClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRow(
    title: String,
    games: List<Game>,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    if (games.isEmpty()) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        )
        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                LemuroidGameCard(
                    modifier =
                        Modifier
                            .widthIn(0.dp, 144.dp)
                            .animateItem(),
                    game = game,
                    onClick = { onGameClicked(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit = { },
    secondaryActionId: Int? = null,
    onSecondaryAction: () -> Unit = { },
) {
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(titleId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (secondaryActionId != null) {
                    TextButton(onClick = onSecondaryAction) {
                        Text(stringResource(id = secondaryActionId))
                    }
                }
                OutlinedButton(
                    onClick = onAction,
                    enabled = enabled,
                ) {
                    Text(stringResource(id = actionId))
                }
            }
        }
    }
}

/** A small window with folder settings, opened from the home-screen setup cards. */
@Composable
private fun SettingsDialog(
    titleId: Int,
    onDone: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(text = stringResource(titleId)) },
        text = { Column { content() } },
        confirmButton = {
            TextButton(onClick = onDone) { Text(text = stringResource(R.string.done)) }
        },
    )
}
