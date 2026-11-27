package com.swordfish.lemuroid.app.mobile.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGamesCollection
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidViewMode

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.favorites.collectAsLazyPagingItems()

    if (games.itemCount == 0) {
        LemuroidEmptyView()
        return
    }

    LemuroidGamesCollection(
        modifier = modifier,
        mode = LemuroidViewMode.FAVORITES,
        games = games,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
        onGameFavoriteToggle = onGameFavoriteToggle,
    )
}
