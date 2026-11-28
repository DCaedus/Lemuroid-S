package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lemuroid-S: a list setting's choices, shown on their own page instead of in a dialog. */
class SettingsChoice(
    val title: @Composable () -> Unit,
    val items: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

/** When set (the in-game menu), list settings open their choices with it instead of a dialog. */
val LocalSettingsChoicePage = compositionLocalOf<((SettingsChoice) -> Unit)?> { null }

/** The choices of a list setting as a page: one row each, the current one marked. */
@Composable
fun SettingsChoicePage(
    choice: SettingsChoice,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
    ) {
        choice.items.forEachIndexed { index, item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            role = Role.RadioButton,
                            selected = index == choice.selected,
                            onClick = {
                                choice.onSelect(index)
                                onDone()
                            },
                        )
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = index == choice.selected, onClick = null)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
fun LemuroidSettingsList(
    enabled: Boolean = true,
    state: SettingValueState<Int>,
    title: @Composable () -> Unit,
    items: List<String>,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    closeDialogDelay: Long = 200,
    action: (@Composable () -> Unit)? = null,
    onItemSelected: ((Int, String) -> Unit)? = null,
) {
    if (state.value >= items.size) {
        throw IndexOutOfBoundsException("Current value for $title list setting cannot be grater than items size")
    }

    var showDialog by remember { mutableStateOf(false) }

    val safeSubtitle =
        if (state.value >= 0 && useSelectedValueAsSubtitle) {
            { Text(text = items[state.value], maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            subtitle
        }

    val choicePage = LocalSettingsChoicePage.current
    LemuroidSettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = safeSubtitle,
        action = action,
        onClick = {
            if (choicePage != null) {
                choicePage(
                    SettingsChoice(title, items, state.value) { index ->
                        if (index != state.value) state.value = index
                        onItemSelected?.invoke(index, items[index])
                    },
                )
            } else {
                showDialog = true
            }
        },
    )

    if (!showDialog) return

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val onSelected: (Int, Boolean) -> Unit = { selectedIndex, updateState ->
        coroutineScope.launch {
            if (updateState) state.value = selectedIndex
            delay(closeDialogDelay)
            showDialog = false
        }
    }

    AlertDialog(
        title = title,
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .selectableGroup(),
            ) {
                if (subtitle != null) {
                    subtitle()
                    Spacer(modifier = Modifier.size(8.dp))
                }

                items.forEachIndexed { index, item ->
                    val isSelected by rememberUpdatedState(newValue = state.value == index)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    role = Role.RadioButton,
                                    selected = isSelected,
                                    onClick = {
                                        onSelected(index, !isSelected)
                                        onItemSelected?.invoke(index, items[index])
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        onDismissRequest = { showDialog = false },
        confirmButton = {},
        dismissButton = {},
    )
}
