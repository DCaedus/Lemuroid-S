package com.swordfish.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class LemuroidPadTheme {
    private fun gray(
        luminosity: Float,
        opacity: Float,
    ): Color {
        return Color(luminosity, luminosity, luminosity, opacity)
    }

    val foregroundPadding: Dp = 8.dp
    val padding: Dp = 4.dp

    private val icons = gray(0.0f, 0.50f)
    private val iconsPressed = gray(1.0f, 0.50f)

    private val level3Fill = gray(1.0f, 0.50f)
    private val level3FillPressed = gray(0.0f, 0.50f)
    val level3Shadow = DefaultShadowColor.copy(0.05f)
    val level3ShadowWidth = 4.dp

    private val level2Fill = gray(1.0f, 0.125f)
    private val level2FillPressed = gray(0.0f, 0.125f)
    val level2Shadow = DefaultShadowColor.copy(0.05f)
    val level2ShadowWidth = 4.dp

    // Lemuroid-S: dark, see-through backgrounds behind the controls (Lemuroid used white at 10%),
    // so the light buttons stand out over bright and dark games alike.
    val level1Fill = gray(0.08f, 0.45f)
    val level1Shadow = DefaultShadowColor.copy(0.10f)
    val level1ShadowWidth = 4.dp

    val level0CornerRadius = 0.dp
    // Lemuroid-S: the panel behind all the controls (portrait, and landscape beside the game) is
    // darker than Lemuroid's white at 5%, still see-through.
    val level0Fill = gray(0.0f, 0.35f)
    val level0Shadow = DefaultShadowColor.copy(0.10f)
    val level0ShadowWidth = 2.dp

    fun compositeFill(pressed: Boolean): Color {
        return if (pressed) {
            level2FillPressed
        } else {
            level2Fill
        }
    }

    fun foregroundFill(pressed: Boolean): Color {
        return if (pressed) {
            level3FillPressed
        } else {
            level3Fill
        }
    }

    fun icons(pressed: Boolean): Color {
        return if (pressed) {
            iconsPressed
        } else {
            icons
        }
    }
}

/** Whether to show the on-screen fast-forward button (in the spot mirroring the menu button). */
val LocalFastForwardButtonEnabled = compositionLocalOf { false }

/** Whether fast-forward is on: the fast-forward button then keeps its pressed (dark) look. */
val LocalFastForwardActive = compositionLocalOf { false }

/** Whether to show the on-screen turbo button (next to the fast-forward button). */
val LocalTurboButtonEnabled = compositionLocalOf { false }

/** Key codes with turbo on: their buttons get a halo. */
val LocalTurboKeys = compositionLocalOf { emptySet<Int>() }

val LocalLemuroidPadTheme =
    compositionLocalOf<LemuroidPadTheme> {
        error("LemuroidPadTheme is missing")
    }
