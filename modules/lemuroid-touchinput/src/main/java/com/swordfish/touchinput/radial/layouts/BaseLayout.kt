package com.swordfish.touchinput.radial.layouts

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
    LayoutRadial(
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
    LayoutRadial(
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
