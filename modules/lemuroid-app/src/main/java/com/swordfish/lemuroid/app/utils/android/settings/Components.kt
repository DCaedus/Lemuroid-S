package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.material3.Slider
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSlider
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt

@Composable
fun LemuroidSettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

/**
 * Lemuroid-S: when set (the in-game menu), every settings row has this height, whether it shows a
 * subtitle or not, and the slider becomes a single row.
 */
val LocalCompactSettingsRowHeight = compositionLocalOf<Dp?> { null }

@Composable
private fun compactRowModifier(): Modifier =
    LocalCompactSettingsRowHeight.current?.let { Modifier.height(it) } ?: Modifier

@Composable
fun LemuroidSettingsSwitch(
    enabled: Boolean = true,
    state: SettingValueState<Boolean>,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    SettingsSwitch(
        modifier = compactRowModifier(),
        enabled = enabled,
        state = state.value,
        icon = icon,
        title = title,
        subtitle = subtitle,
        onCheckedChange = {
            state.value = it
            onCheckedChange(it)
        },
        colors = lemuroidSettingsColor(enabled),
    )
}

@Composable
fun LemuroidSettingsMenuLink(
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SettingsMenuLink(
        modifier = compactRowModifier(),
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = onClick,
        colors = lemuroidSettingsColor(enabled),
    )
}

@Composable
fun LemuroidSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun LemuroidCardSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            OutlinedCard {
                if (title != null) {
                    SettingsGroupTitleSmall(title)
                }
                content()
            }
        }
    }
}

@Composable
fun LemuroidSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit = { },
    icon: (@Composable () -> Unit)? = null,
    valueLabels: List<String> = emptyList(),
) {
    val defaultColors = ListItemDefaults.colors()
    val disabledColors =
        ListItemDefaults.colors(
            headlineColor = defaultColors.disabledHeadlineColor,
            leadingIconColor = defaultColors.disabledLeadingIconColor,
            trailingIconColor = defaultColors.disabledTrailingIconColor,
            supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
        )
    val colors = if (enabled) defaultColors else disabledColors

    val compactHeight = LocalCompactSettingsRowHeight.current
    if (compactHeight != null) {
        // One row: icon, title with the current value under it, and the slider on the right.
        ListItem(
            modifier = modifier.height(compactHeight),
            colors = colors,
            leadingContent = icon,
            headlineContent = title,
            supportingContent =
                valueLabels.getOrNull(state.value)?.let { label -> { Text(text = label) } },
            trailingContent = {
                Slider(
                    modifier = Modifier.width(COMPACT_SLIDER_WIDTH),
                    value = state.value.toFloat(),
                    onValueChange = { state.value = it.roundToInt() },
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                )
            },
        )
        return
    }

    Column(modifier = modifier) {
        SettingsSlider(
            steps = steps,
            value = state.value.toFloat(),
            onValueChange = { state.value = it.roundToInt() },
            valueRange = valueRange,
            title = title,
            subtitle = subtitle,
            icon = icon,
            enabled = enabled,
            colors = colors,
        )
        // Lemuroid-S: optional labels under the slider, spread from the first to the last step.
        if (valueLabels.isNotEmpty()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        // Lined up with the slider, which starts after the icon when there's one.
                        start = if (icon != null) 56.dp else 16.dp,
                        end = 24.dp,
                        bottom = 8.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                valueLabels.forEach {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = colors.supportingTextColor)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupTitleSmall(title: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val titleStyle = MaterialTheme.typography.labelLarge.copy(color = primary)
        ProvideTextStyle(value = titleStyle) { title() }
    }
}

@Composable
private fun lemuroidSettingsColor(enabled: Boolean): ListItemColors {
    val defaultColors = ListItemDefaults.colors()

    if (enabled) {
        return defaultColors
    }

    return ListItemDefaults.colors(
        headlineColor = defaultColors.disabledHeadlineColor,
        leadingIconColor = defaultColors.disabledLeadingIconColor,
        trailingIconColor = defaultColors.disabledTrailingIconColor,
        supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
    )
}

private val COMPACT_SLIDER_WIDTH = 140.dp
