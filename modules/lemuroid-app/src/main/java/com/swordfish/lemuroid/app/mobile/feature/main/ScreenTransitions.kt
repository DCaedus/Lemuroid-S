package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Length of the crossfade between app screens. */
const val SCREEN_FADE_MS = 200

/**
 * Shows a screen inside a navigation crossfade. While the screen is fading out it ignores touches,
 * so a quick tap during the animation can't hit a button of the screen being left.
 */
@Composable
fun AnimatedContentScope.ScreenTransitionContent(content: @Composable () -> Unit) {
    val leaving = transition.targetState == EnterExitState.PostExit
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (leaving) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        },
            )
        }
    }
}
