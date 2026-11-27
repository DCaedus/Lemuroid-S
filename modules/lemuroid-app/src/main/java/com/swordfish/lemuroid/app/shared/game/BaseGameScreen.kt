package com.swordfish.lemuroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.delay
import com.swordfish.lemuroid.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView

@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    if (isGameReady) {
        // Lemuroid-S: saving, loading or restarting freezes the game for a moment; say so.
        val busy = viewModel.loadingState.collectAsState().value
        var showBusy by remember { mutableStateOf(false) }
        LaunchedEffect(busy) {
            if (busy) delay(BUSY_OVERLAY_DELAY_MS) // no flash for quick saves
            showBusy = busy
        }
        Box(modifier = Modifier.fillMaxSize()) {
            gameScreen(viewModel)
            AnimatedVisibility(visible = showBusy, enter = fadeIn(), exit = fadeOut()) {
                LoadingPanel(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.7f)),
                    message = stringResource(R.string.game_busy),
                )
            }
        }
    } else {
        val message = (gameState as? GameViewModelRetroGameView.GameState.Loading)?.message
        LoadingPanel(message = message ?: stringResource(R.string.game_loading_preparing_game))
    }
}

/** A large spinner with a message, in the middle of the screen. */
@Composable
private fun LoadingPanel(
    modifier: Modifier = Modifier,
    message: String,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
            Text(text = message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private const val BUSY_OVERLAY_DELAY_MS = 150L
