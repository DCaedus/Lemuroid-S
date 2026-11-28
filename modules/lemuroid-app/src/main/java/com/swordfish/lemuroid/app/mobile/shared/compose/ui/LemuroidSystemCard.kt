package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo

@Composable
fun LemuroidSystemCard(
    modifier: Modifier = Modifier,
    system: MetaSystemInfo,
    textSize: LemuroidTextSize = LemuroidTextSize.TILE,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    val title =
        remember(system.metaSystem.titleResId) {
            system.getName(context)
        }

    // Lemuroid-S: not remembered by title only, so the game count updates after a rescan.
    val subtitle = stringResource(R.string.system_grid_details, system.count.toString())

    ElevatedCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            LemuroidSystemImage(system)
            LemuroidTexts(title = title, subtitle = subtitle, textSize = textSize)
        }
    }
}

/** Lemuroid-S: a console in the Systems list view, laid out like a game in a console's list. */
@Composable
fun LemuroidSystemListRow(
    modifier: Modifier = Modifier,
    system: MetaSystemInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val title = remember(system.metaSystem.titleResId) { system.getName(context) }
    val subtitle = stringResource(R.string.system_grid_details, system.count.toString())

    Surface(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = LemuroidGrid.LIST_ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LemuroidGrid.LIST_IMAGE_SIZE)
                        .clip(RoundedCornerShape(4.dp)),
            ) {
                LemuroidSystemImage(system)
            }
            LemuroidTexts(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                title = title,
                subtitle = subtitle,
            )
        }
    }
}
