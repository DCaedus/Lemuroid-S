package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Lemuroid-S: grid used by Favorites and Systems. Portrait uses narrower tiles so phones show 3 columns
 * (tablets more); landscape keeps Lemuroid's tile size (5 columns on a phone). Tiles are closer together.
 */
object LemuroidGrid {
    private val PORTRAIT_MIN_TILE = 104.dp
    private val LANDSCAPE_MIN_TILE = 144.dp
    private val SPACING = 4.dp

    @Composable
    fun columns(): GridCells {
        val portrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
        return GridCells.Adaptive(if (portrait) PORTRAIT_MIN_TILE else LANDSCAPE_MIN_TILE)
    }

    val contentPadding = PaddingValues(8.dp)
    val arrangement = Arrangement.spacedBy(SPACING)
}
