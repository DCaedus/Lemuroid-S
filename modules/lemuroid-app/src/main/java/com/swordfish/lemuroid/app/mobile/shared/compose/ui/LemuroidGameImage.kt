package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.app.mobile.feature.main.SCREEN_FADE_MS
import com.swordfish.lemuroid.app.shared.covers.CoverFallbackInterceptor
import com.swordfish.lemuroid.app.shared.covers.CoverStore
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun LemuroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    LemuroidCoverImage(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1.0f),
        game = game,
    )
}

/**
 * Lemuroid-S: a game's artwork over its generic tile. Artwork already on the phone shows right
 * away; artwork that had to be downloaded fades in over the tile.
 */
@Composable
fun LemuroidCoverImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val context = LocalContext.current
    val fallbackDrawable = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    val coversVersion by CoverStore.version.collectAsState()
    val coverData = remember(game, coversVersion) { CoverUtils.coverData(context, game) }
    val painter =
        rememberAsyncImagePainter(
            model =
                ImageRequest.Builder(context)
                    .data(coverData)
                    .setParameter(CoverFallbackInterceptor.PARAM_FADES_ITSELF, true, memoryCacheKey = null)
                    .build(),
            contentScale = ContentScale.Crop,
        )

    val state = painter.state
    val loaded = state is AsyncImagePainter.State.Success
    val alpha = remember(coverData) { Animatable(if (loaded) 1f else 0f) }
    LaunchedEffect(state) {
        when {
            state !is AsyncImagePainter.State.Success -> alpha.snapTo(0f)
            state.result.request.parameters.value<Boolean>(CoverFallbackInterceptor.PARAM_DOWNLOADED) == true ->
                alpha.animateTo(1f, tween(SCREEN_FADE_MS))
            else -> alpha.snapTo(1f)
        }
    }

    Box(modifier = modifier) {
        if (alpha.value < 1f) {
            Image(
                painter = fallbackPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Image(
            painter = painter,
            contentDescription = game.displayName(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
        )
    }
}
