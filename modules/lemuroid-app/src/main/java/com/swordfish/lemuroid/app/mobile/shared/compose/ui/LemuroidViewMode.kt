package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.paging.compose.LazyPagingItems
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Lemuroid-S: screens whose items can be shown as tiles or as a list, switched with the top bar
 * button. Each screen remembers its own choice (all consoles' game lists share one).
 */
enum class LemuroidViewMode(
    private val preferenceKey: String,
    private val tilesByDefault: Boolean,
) {
    FAVORITES("view_mode_tiles_favorites", true),
    SYSTEMS("view_mode_tiles_systems", true),
    SYSTEM_GAMES("view_mode_tiles_system_games", false),
    ;

    @Composable
    fun rememberTiles(): State<Boolean> = rememberBooleanPreference(preferenceKey, tilesByDefault)

    fun toggle(context: Context) {
        val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        val tiles = preferences.getBoolean(preferenceKey, tilesByDefault)
        preferences.edit { putBoolean(preferenceKey, !tiles) }
    }

    companion object {
        fun forRoute(route: MainRoute): LemuroidViewMode? =
            when (route) {
                MainRoute.FAVORITES -> FAVORITES
                MainRoute.SYSTEMS -> SYSTEMS
                MainRoute.SYSTEM_GAMES -> SYSTEM_GAMES
                else -> null
            }
    }
}

/** Games as tiles (like Favorites) or as a list (like a console's games), per [mode]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LemuroidGamesCollection(
    modifier: Modifier = Modifier,
    mode: LemuroidViewMode,
    games: LazyPagingItems<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val tiles = mode.rememberTiles().value
    if (tiles) {
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
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
                val game = games[index] ?: return@items
                LemuroidGameListRow(
                    modifier = Modifier.animateItem(),
                    game = game,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                    onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(game, isFavorite) },
                )
            }
        }
    }
}
