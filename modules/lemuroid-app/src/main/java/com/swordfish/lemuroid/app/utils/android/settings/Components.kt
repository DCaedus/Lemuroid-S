package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.remember
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Switch
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
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
                .padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    val toggle = {
        state.value = !state.value
        onCheckedChange(state.value)
    }
    SettingsRow(
        modifier = compactRowModifier(),
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = if (LocalCompactSettingsRowHeight.current != null) null else subtitle,
        trailing = { Switch(checked = state.value, onCheckedChange = null, enabled = enabled) },
        onClick = toggle,
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
    val compactHeight = LocalCompactSettingsRowHeight.current
    if (compactHeight != null) {
        // One line: the value (subtitle) goes on the right, smaller, instead of under the title.
        val trailing = subtitle ?: action
        ListItem(
            modifier = Modifier.height(compactHeight).clickable(enabled = enabled, onClick = onClick),
            colors = lemuroidSettingsColor(enabled),
            leadingContent = icon,
            headlineContent = title,
            trailingContent =
                trailing?.let { content ->
                    {
                        Box(modifier = Modifier.widthIn(max = COMPACT_VALUE_MAX_WIDTH)) {
                            ProvideTextStyle(MaterialTheme.typography.bodySmall) { content() }
                        }
                    }
                },
        )
        return
    }
    SettingsRow(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = action,
        onClick = onClick,
    )
}

/**
 * Lemuroid-S: a settings row: icon, title with an optional description under it, and something on
 * the right. Less padding than Material's list items (at least 72dp for two lines), so more
 * settings fit on screen; it still grows with long descriptions and the font size.
 */
@Composable
private fun SettingsRow(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    icon: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val colors = lemuroidSettingsColor(enabled)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides colors.leadingIconColor) { icon() }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = colors.headlineColor)) { title() }
            if (subtitle != null) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = colors.supportingTextColor)) {
                    subtitle()
                }
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides colors.trailingIconColor) { trailing() }
        }
    }
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
                SlimSlider(
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

    // Title with the current value on the right, the slider under them.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = ROW_VERTICAL_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides colors.leadingIconColor) { icon() }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = colors.headlineColor)) { title() }
            }
            valueLabels.getOrNull(state.value)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.supportingTextColor)
            }
        }
        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = colors.supportingTextColor)) { subtitle() }
        SlimSlider(
            modifier = Modifier.fillMaxWidth(),
            value = state.value.toFloat(),
            onValueChange = { state.value = it.roundToInt() },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** Lemuroid-S: a slim slider (thin track, round handle) instead of Material's thick one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlimSlider(
    modifier: Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        modifier = modifier.height(SLIDER_HEIGHT),
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                thumbSize = DpSize(SLIDER_THUMB_SIZE, SLIDER_THUMB_SIZE),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(SLIDER_TRACK_HEIGHT),
                enabled = enabled,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}

@Composable
private fun SettingsGroupTitleSmall(title: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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
private val ROW_MIN_HEIGHT = 48.dp
private val SLIDER_HEIGHT = 32.dp
private val SLIDER_THUMB_SIZE = 18.dp
private val SLIDER_TRACK_HEIGHT = 4.dp
private val ROW_VERTICAL_PADDING = 10.dp
private val COMPACT_VALUE_MAX_WIDTH = 160.dp
