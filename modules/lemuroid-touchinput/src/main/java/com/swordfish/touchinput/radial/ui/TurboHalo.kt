package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.LocalTurboKeys

private val haloColor = Color(0xFF7FDBFF)

/** Lemuroid-S: draws a halo of light around a button that has turbo on. */
@Composable
fun TurboHalo(
    keyCode: Int,
    content: @Composable () -> Unit,
) {
    val on = keyCode in LocalTurboKeys.current
    val inset = LocalLemuroidPadTheme.current.foregroundPadding
    val modifier =
        if (on) {
            Modifier.drawBehind {
                val buttonRadius = size.minDimension / 2 - inset.toPx()
                val radius = buttonRadius + 10.dp.toPx()
                val edge = buttonRadius / radius
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            edge * 0.85f to Color.Transparent,
                            edge to haloColor.copy(alpha = 0.9f),
                            1f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                )
            }
        } else {
            Modifier
        }
    Box(modifier = Modifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
        content()
    }
}
