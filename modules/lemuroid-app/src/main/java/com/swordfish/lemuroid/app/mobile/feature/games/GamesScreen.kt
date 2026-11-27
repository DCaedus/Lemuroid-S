package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGamesCollection
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidViewMode

@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.games.collectAsLazyPagingItems()

    if (games.itemCount == 0) {
        LemuroidEmptyView()
        return
    }

    LemuroidGamesCollection(
        modifier = modifier,
        mode = LemuroidViewMode.SYSTEM_GAMES,
        games = games,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
        onGameFavoriteToggle = onGameFavoriteToggle,
    )
}
