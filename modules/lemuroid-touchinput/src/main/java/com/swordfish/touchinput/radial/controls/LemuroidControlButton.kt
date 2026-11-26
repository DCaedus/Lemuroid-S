package com.swordfish.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import com.swordfish.touchinput.radial.ui.LemuroidControlBackground
import com.swordfish.touchinput.radial.ui.TurboHalo
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun LemuroidControlButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String? = null,
    icon: Int? = null,
    active: Boolean = false,
) {
    val theme = LocalLemuroidPadTheme.current
    ControlButton(
        modifier = modifier.padding(theme.padding),
        id = id,
        foreground = { pressed ->
            // An active toggle (e.g. fast-forward on) keeps the pressed look.
            val shown = remember(pressed, active) { derivedStateOf { pressed.value || active } }
            TurboHalo(id.value) { LemuroidButtonForeground(pressed = shown, icon = icon, label = label) }
        },
        background = { LemuroidControlBackground() },
    )
}
