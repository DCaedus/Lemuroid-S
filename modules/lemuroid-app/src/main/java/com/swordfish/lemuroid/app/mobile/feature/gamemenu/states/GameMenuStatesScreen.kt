package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    onStateClicked: (Int) -> Unit,
) {
    val state = viewModel.uiStates.collectAsState(initial = GameMenuStatesViewModel.State())

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        state.value.entries.forEach { entry ->
            StateSlotRow(entry, onClick = { onStateClicked(entry.slot) })
        }
    }
}

/**
 * Lemuroid-S: a state slot: its preview, name and date. The row grows with its text (and the
 * font size setting), so nothing is cut off.
 */
@Composable
private fun StateSlotRow(
    entry: GameMenuStatesViewModel.StateEntry,
    onClick: () -> Unit,
) {
    val alpha = if (entry.enabled) 1f else DISABLED_ALPHA
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(enabled = entry.enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(PREVIEW_SIZE)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (entry.preview != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = entry.preview.asImageBitmap(),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PREVIEW_SIZE = 56.dp
private const val DISABLED_ALPHA = 0.38f
