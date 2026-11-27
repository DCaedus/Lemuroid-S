package com.swordfish.lemuroid.app.mobile.feature.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGrid
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
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


    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())

    // Lemuroid-S: the setup cards (folders, notifications, microphone, Import Saves) are on the
    // first-launch setup layer (FirstLaunchSetup.kt).
    HomeScreen(
        modifier,
        state.value,
        onGameClick,
        onGameLongClick,
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
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
            horizontalArrangement = LemuroidGrid.arrangement,
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
