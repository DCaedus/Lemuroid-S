package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Lemuroid-S: text sizes; tiles use smaller text so more of a game's name fits. */
enum class LemuroidTextSize(
    val titleSize: Int,
    val titleLineHeight: Int,
    val subtitleSize: Int,
    val subtitleLineHeight: Int,
) {
    NORMAL(14, 20, 11, 16),
    TILE(12, 16, 10, 14),
    TILE_SMALL(11, 14, 9, 12),
}

@Composable
fun LemuroidGameTexts(
    modifier: Modifier = Modifier,
    game: Game,
    textSize: LemuroidTextSize = LemuroidTextSize.NORMAL,
) {
    val context = LocalContext.current
    val subtitle =
        remember(game.id) {
            GameUtils.getGameSubtitle(context, game)
        }

    LemuroidTexts(modifier, game.displayName(), subtitle, textSize)
}

@Composable
fun LemuroidTexts(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    textSize: LemuroidTextSize = LemuroidTextSize.NORMAL,
) {
    val padding = if (textSize == LemuroidTextSize.TILE_SMALL) 6.dp else 8.dp
    Column(
        modifier = modifier.padding(horizontal = padding, vertical = 6.dp),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = textSize.titleSize.sp,
                    lineHeight = textSize.titleLineHeight.sp,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = textSize.subtitleSize.sp,
                    lineHeight = textSize.subtitleLineHeight.sp,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
