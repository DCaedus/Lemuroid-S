package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.paging.compose.LazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/** Lemuroid-S: how a screen shows its games or consoles. */
enum class LemuroidLayout(val icon: ImageVector, val labelId: Int) {
    TILES_LARGE(Icons.Outlined.GridView, R.string.view_layout_large_tiles),
    TILES_SMALL(Icons.Outlined.Apps, R.string.view_layout_small_tiles),
    LIST(Icons.AutoMirrored.Outlined.ViewList, R.string.view_layout_list),
}

/**
 * Lemuroid-S: screens whose layout the top bar button switches (it shows the layout in use). Each
 * screen remembers its own (all consoles' game lists share one). Home has no list.
 */
enum class LemuroidViewMode(
    private val preferenceKey: String,
    private val default: LemuroidLayout,
    private val layouts: List<LemuroidLayout> = LemuroidLayout.entries,
) {
    HOME("view_layout_home", LemuroidLayout.TILES_LARGE, listOf(LemuroidLayout.TILES_LARGE, LemuroidLayout.TILES_SMALL)),
    FAVORITES("view_layout_favorites", LemuroidLayout.TILES_LARGE),
    SEARCH("view_layout_search", LemuroidLayout.LIST),
    SYSTEMS("view_layout_systems", LemuroidLayout.TILES_LARGE),
    SYSTEM_GAMES("view_layout_system_games", LemuroidLayout.LIST),
    ;

    private fun read(preferences: SharedPreferences): LemuroidLayout =
        preferences.getString(preferenceKey, null)
            ?.let { name -> layouts.firstOrNull { it.name == name } }
            ?: default

    /** The layout in use; recomposes when the button changes it. */
    @Composable
    fun rememberLayout(): State<LemuroidLayout> {
        val context = LocalContext.current
        val preferences = remember { SharedPreferencesHelper.getSharedPreferences(context.applicationContext) }
        val state = remember(this) { mutableStateOf(read(preferences)) }
        DisposableEffect(this) {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == preferenceKey) state.value = read(prefs)
                }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }

    /** Switches to the next layout: large tiles, small tiles, list, then large tiles again. */
    fun next(context: Context) {
        val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        val current = read(preferences)
        val next = layouts[(layouts.indexOf(current) + 1) % layouts.size]
        preferences.edit { putString(preferenceKey, next.name) }
    }

    companion object {
        fun forRoute(route: MainRoute): LemuroidViewMode? =
            when (route) {
                MainRoute.HOME -> HOME
                MainRoute.FAVORITES -> FAVORITES
                MainRoute.SEARCH -> SEARCH
                MainRoute.SYSTEMS -> SYSTEMS
                MainRoute.SYSTEM_GAMES -> SYSTEM_GAMES
                else -> null
            }
    }
}

/** Games in the layout chosen for [mode]: large or small tiles, or a list. */
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
    val layout = mode.rememberLayout().value
    if (layout == LemuroidLayout.LIST) {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = LemuroidGrid.listPadding) {
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
        return
    }

    val small = layout == LemuroidLayout.TILES_SMALL
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        contentPadding = LemuroidGrid.contentPadding(small),
        columns = LemuroidGrid.columns(small),
        horizontalArrangement = LemuroidGrid.arrangement(small),
        verticalArrangement = LemuroidGrid.arrangement(small),
    ) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items
            LemuroidGameCard(
                modifier = Modifier.animateItem(),
                game = game,
                textSize = if (small) LemuroidTextSize.TILE_SMALL else LemuroidTextSize.TILE,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
            )
        }
    }
}
