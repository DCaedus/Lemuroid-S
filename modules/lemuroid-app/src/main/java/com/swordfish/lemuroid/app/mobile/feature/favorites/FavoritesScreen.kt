package com.swordfish.lemuroid.app.mobile.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGrid
import com.swordfish.lemuroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    val games = viewModel.favorites.collectAsLazyPagingItems()

    if (games.itemCount == 0) {
        LemuroidEmptyView()
        return
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        contentPadding = LemuroidGrid.contentPadding,
        columns = LemuroidGrid.columns(),
        horizontalArrangement = LemuroidGrid.arrangement,
        verticalArrangement = LemuroidGrid.arrangement,
    ) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items
            LemuroidGameCard(
                modifier = Modifier.animateItem(),
                game = game,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
            )
        }
    }
}
