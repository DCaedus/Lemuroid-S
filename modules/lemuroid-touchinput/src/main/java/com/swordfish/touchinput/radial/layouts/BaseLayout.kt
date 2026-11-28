package com.swordfish.touchinput.radial.layouts

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.Box
import android.content.res.Configuration
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.layouts.radial.LayoutRadial
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope)
@Composable
fun BaseLayoutLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    SharedSizeLayoutRadial(
        side = DialSizeGroup.Side.LEFT,
        modifier =
            modifier
                .absolutePadding(
                    left = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize = primaryDialMaxSize(settings),
        secondaryDialsBaseRotationInDegrees = settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}

context(PadKitScope)
@Composable
fun BaseLayoutRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    SharedSizeLayoutRadial(
        side = DialSizeGroup.Side.RIGHT,
        modifier =
            modifier
                .absolutePadding(
                    right = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize = primaryDialMaxSize(settings),
        secondaryDialsBaseRotationInDegrees = -settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}

/**
 * Lemuroid-S: the d-pad and the face buttons get the same size. padkit shrinks a side's main dial
 * until its buttons around it fit, so a side with more around it (an analog stick under the d-pad)
 * came out smaller. Each side reports the biggest dial it could fit, and both use the smaller one.
 */
class DialSizeGroup {
    enum class Side { LEFT, RIGHT }

    private val fits = mutableStateMapOf<Side, Int>()

    fun report(
        side: Side,
        fitPx: Int,
    ) {
        if (fits[side] != fitPx) fits[side] = fitPx
    }

    /** The size both sides use, once both have reported. */
    val sharedPx: Int?
        get() = if (fits.size == Side.entries.size) fits.values.min() else null
}

/** Provided by the game screen around both sides; without it each side keeps its own size. */
val LocalDialSizeGroup = compositionLocalOf<DialSizeGroup?> { null }

context(PadKitScope)
@Composable
private fun SharedSizeLayoutRadial(
    side: DialSizeGroup.Side,
    modifier: Modifier,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
    primaryDialMaxSize: Dp,
    secondaryDialsBaseRotationInDegrees: Float,
) {
    val group = LocalDialSizeGroup.current
    val density = LocalDensity.current
    val shared = group?.sharedPx?.let { with(density) { it.toDp() } }
    val maxSize = if (shared != null) minOf(primaryDialMaxSize, shared) else primaryDialMaxSize
    // Size padkit gave the main dial in the last measure.
    val primarySize = remember { intArrayOf(0) }

    Layout(
        modifier = modifier,
        content = {
            LayoutRadial(
                primaryDial = {
                    Box(
                        modifier =
                            Modifier.layout { measurable, constraints ->
                                primarySize[0] = constraints.maxWidth
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            },
                    ) { primaryDial() }
                },
                secondaryDials = secondaryDials,
                primaryDialMaxSize = maxSize,
                secondaryDialsBaseRotationInDegrees = secondaryDialsBaseRotationInDegrees,
            )
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        val dial = primarySize[0]
        if (group != null && dial > 0 && constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            // The layout is the dial times a fixed ratio, so the biggest dial that fits follows from it.
            val fit =
                minOf(
                    constraints.maxWidth * dial / placeable.width.coerceAtLeast(1),
                    constraints.maxHeight * dial / placeable.height.coerceAtLeast(1),
                )
            group.report(side, fit)
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * Lemuroid-S: portrait has room below the game, so the dials may grow until each side fills half
 * the screen's width (padkit shrinks them to fit).
 */
@Composable
private fun primaryDialMaxSize(settings: TouchControllerSettingsManager.Settings): Dp {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val base = if (isPortrait) PORTRAIT_DIAL_SIZE else LANDSCAPE_DIAL_SIZE
    return base *
        lerp(
            TouchControllerSettingsManager.MIN_SCALE,
            TouchControllerSettingsManager.MAX_SCALE,
            settings.scale,
        )
}

private val LANDSCAPE_DIAL_SIZE = 160.dp
private val PORTRAIT_DIAL_SIZE = 200.dp
