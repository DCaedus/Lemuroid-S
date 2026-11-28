package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Lemuroid-S: grid used by Favorites, Systems, Search and consoles' games; its spacing is also used
 * between Home cards. Large tiles show 3 columns on a phone in portrait (5 in landscape), small
 * tiles 4 (6 in landscape); tablets more.
 */
object LemuroidGrid {
    private val PORTRAIT_MIN_TILE = 104.dp
    private val PORTRAIT_MIN_SMALL_TILE = 80.dp
    private val LANDSCAPE_MIN_TILE = 144.dp
    private val LANDSCAPE_MIN_SMALL_TILE = 112.dp
    private val SPACING = 6.dp
    private val SMALL_SPACING = 3.dp

    @Composable
    fun columns(small: Boolean = false): GridCells {
        val portrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
        val minTile =
            when {
                portrait && small -> PORTRAIT_MIN_SMALL_TILE
                portrait -> PORTRAIT_MIN_TILE
                small -> LANDSCAPE_MIN_SMALL_TILE
                else -> LANDSCAPE_MIN_TILE
            }
        return GridCells.Adaptive(minTile)
    }

    /** List rows: a bigger cover with less space around it than Lemuroid's 40dp with 8dp. */
    val LIST_IMAGE_SIZE = 52.dp
    val LIST_ROW_VERTICAL_PADDING = 3.dp

    val contentPadding = PaddingValues(8.dp)
    val smallContentPadding = PaddingValues(6.dp)
    val listPadding = PaddingValues(vertical = 4.dp)
    val arrangement = Arrangement.spacedBy(SPACING)

    /** Small tiles sit closer together, so the gaps don't look heavy next to them. */
    val smallArrangement = Arrangement.spacedBy(SMALL_SPACING)

    fun arrangement(small: Boolean) = if (small) smallArrangement else arrangement

    fun contentPadding(small: Boolean) = if (small) smallContentPadding else contentPadding
}
