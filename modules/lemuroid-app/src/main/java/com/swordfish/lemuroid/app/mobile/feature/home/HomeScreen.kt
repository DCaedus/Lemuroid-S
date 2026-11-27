package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
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

    // Lemuroid-S: the setup cards (folders, notifications, Import Saves) are on the first-launch
    // setup layer (FirstLaunchSetup.kt); only the microphone card stays here.
    HomeScreen(
        modifier,
        state.value,
        onGameClick,
        onGameLongClick,
        { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
) {
    val rows =
        listOf(state.recentGames, state.frequentGames, state.favoritesGames, state.discoveryGames)
            .count { it.isNotEmpty() }

    BoxWithConstraints(modifier = modifier) {
        val cardWidth = homeCardWidth(maxHeight, rows)
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = HOME_VERTICAL_PADDING),
        ) {
        AnimatedVisibility(state.showNoMicrophonePermissionCard) {
            HomeNotification(
                titleId = R.string.home_microphone_title,
                messageId = R.string.home_microphone_message,
                actionId = R.string.home_microphone_action,
                onAction = onEnableMicrophoneClicked,
            )
        }
        HomeRow(
            stringResource(id = R.string.recent),
            state.recentGames,
            cardWidth,
            onGameClicked,
            onGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.home_frequently_played),
            state.frequentGames,
            cardWidth,
            onGameClicked,
            onGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.favorites),
            state.favoritesGames,
            cardWidth,
            onGameClicked,
            onGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.discover),
            state.discoveryGames,
            cardWidth,
            onGameClicked,
            onGameLongClick,
        )
        }
    }
}

/**
 * Lemuroid-S: the cards shrink so every row fits on screen without scrolling (between
 * [MIN_CARD_WIDTH] and Lemuroid's [MAX_CARD_WIDTH]). Text heights follow the font size setting.
 */
@Composable
private fun homeCardWidth(
    availableHeight: Dp,
    rows: Int,
): Dp {
    if (rows == 0) return MAX_CARD_WIDTH
    val fontScale = LocalDensity.current.fontScale
    // Row title, the space around the cards and the card's two lines of text.
    val rowExtra = ROW_PADDING * 2 + CARD_TEXT_PADDING * 2 + (ROW_TITLE_SP + CARD_TEXT_SP).dp * fontScale
    val perRow = (availableHeight - HOME_VERTICAL_PADDING * 2) / rows
    return (perRow - rowExtra).coerceIn(MIN_CARD_WIDTH, MAX_CARD_WIDTH)
}

private val HOME_VERTICAL_PADDING = 4.dp
private val ROW_PADDING = 4.dp
private val CARD_TEXT_PADDING = 6.dp
private const val ROW_TITLE_SP = 16f
private const val CARD_TEXT_SP = 20f + 16f
private val MIN_CARD_WIDTH = 80.dp
private val MAX_CARD_WIDTH = 144.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRow(
    title: String,
    games: List<Game>,
    cardWidth: Dp,
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
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
        )
        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = ROW_PADDING),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                LemuroidGameCard(
                    modifier =
                        Modifier
                            .width(cardWidth)
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
