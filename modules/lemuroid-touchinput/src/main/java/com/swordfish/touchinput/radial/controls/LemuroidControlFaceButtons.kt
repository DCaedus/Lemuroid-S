package com.swordfish.touchinput.radial.controls

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.persistentSetOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.ui.LemuroidCompositeForeground
import com.swordfish.touchinput.radial.ui.LemuroidControlBackground
import com.swordfish.touchinput.radial.ui.TurboHalo
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlFaceButtons
import gg.padkit.ids.Id
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf

context(PadKitScope)
@Composable
fun LemuroidControlFaceButtons(
    modifier: Modifier = Modifier,
    rotationInDegrees: Float = 0f,
    ids: PersistentList<Id.Key>,
    includeComposite: Boolean = true,
    applyPadding: Boolean = true,
    trackPointers: Boolean = true,
    background: (@Composable () -> Unit)? = null,
    idsForegrounds: PersistentMap<Id.Key, @Composable (State<Boolean>) -> Unit>,
) {
    val theme = LocalLemuroidPadTheme.current

    // Lemuroid-S: with 1 to 3 buttons, the background hugs them (a circle, a capsule, a rounded
    // triangle) instead of being the full circle that four buttons fill.
    if (ids.size in 1..HUGGING_MAX_BUTTONS) {
        val geometry = remember(ids.size, rotationInDegrees) { HuggingFaceButtons(ids.size, rotationInDegrees) }
        val primaryAnchors =
            remember(ids, geometry) {
                ids.mapIndexed { index, id -> Anchor(geometry.anchorPositions[index], persistentSetOf(id), geometry.buttonSize) }
                    .toPersistentList()
            }
        val compositeAnchors =
            remember(ids, geometry, includeComposite) {
                if (includeComposite) geometry.compositeAnchors(ids) else persistentListOf()
            }
        ControlFaceButtons(
            modifier = modifier.run { if (applyPadding) padding(theme.padding) else modifier },
            primaryAnchors = primaryAnchors,
            compositeAnchors = compositeAnchors,
            trackPointers = trackPointers,
            foreground = { id, pressed -> TurboHalo(id.value) { (idsForegrounds[id]!!)(pressed) } },
            background = background ?: { HuggingFaceButtonsBackground(geometry) },
            foregroundComposite = { LemuroidCompositeForeground(it) },
        )
        return
    }

    ControlFaceButtons(
        modifier =
            modifier
                .run { if (applyPadding) padding(theme.padding) else modifier },
        includeComposite = includeComposite,
        ids = ids,
        trackPointers = trackPointers,
        rotationInDegrees = rotationInDegrees,
        foreground = { id, pressed -> TurboHalo(id.value) { (idsForegrounds[id]!!)(pressed) } },
        background = background ?: { LemuroidControlBackground() },
        foregroundComposite = { LemuroidCompositeForeground(it) },
    )
}

context(PadKitScope)
@Composable
fun LemuroidControlFaceButtons(
    modifier: Modifier = Modifier,
    primaryAnchors: PersistentList<Anchor<Id.Key>>,
    background: @Composable () -> Unit = { LemuroidControlBackground() },
    applyPadding: Boolean = true,
    trackPointers: Boolean = true,
    idsForegrounds: PersistentMap<Id.Key, @Composable (State<Boolean>) -> Unit>,
) {
    val theme = LocalLemuroidPadTheme.current
    ControlFaceButtons(
        modifier =
            modifier
                .run { if (applyPadding) padding(theme.padding) else modifier },
        primaryAnchors = primaryAnchors,
        compositeAnchors = persistentListOf(),
        trackPointers = trackPointers,
        foreground = { id, pressed -> TurboHalo(id.value) { (idsForegrounds[id]!!)(pressed) } },
        background = background,
        foregroundComposite = { LemuroidCompositeForeground(it) },
    )
}

private const val HUGGING_MAX_BUTTONS = 3

/** Size (fraction of the control) of a composite button, as padkit uses. */
private const val COMPOSITE_SIZE = 0.1f

/**
 * Lemuroid-S: where 1 to 3 face buttons go, as padkit arranges them on a circle, in the control's
 * own units: positions are fractions of the control's size from its center.
 */
internal class HuggingFaceButtons(count: Int, rotationInDegrees: Float) {
    /** Diameter of each button, as a fraction of the control. A lone button is a bit bigger. */
    val buttonSize: Float =
        if (count == 1) {
            SINGLE_BUTTON_SIZE
        } else {
            sin(PI.toFloat() / count).let { it / (1f + it) }
        }

    /** Button centers, as fractions of the control's size from its center. */
    val centers: List<Offset> =
        if (count == 1) {
            listOf(Offset.Zero)
        } else {
            val radius = (1f - buttonSize) / 2f
            (0 until count).map { index ->
                val angle = rotationInDegrees * PI.toFloat() / 180f + 2f * PI.toFloat() * index / count
                Offset(cos(angle), sin(angle)) * radius
            }
        }

    /** padkit's anchor positions: the center offset divided by the distance it can move. */
    val anchorPositions: List<Offset> = centers.map { if (count == 1) it else it / ((1f - buttonSize) / 2f) }

    /** Pressing between two buttons presses both: one spot between each pair. */
    fun compositeAnchors(ids: List<Id.Key>): PersistentList<Anchor<Id.Key>> {
        if (ids.size < 2) return persistentListOf()
        val pairs = if (ids.size == 2) listOf(0 to 1) else listOf(0 to 1, 1 to 2, 2 to 0)
        return pairs.map { (a, b) ->
            val middle = (centers[a] + centers[b]) / 2f
            Anchor(middle / ((1f - COMPOSITE_SIZE) / 2f), persistentSetOf(ids[a], ids[b]), COMPOSITE_SIZE)
        }.toPersistentList()
    }

    companion object {
        private const val SINGLE_BUTTON_SIZE = 0.6f
    }
}

@Composable
private fun HuggingFaceButtonsBackground(geometry: HuggingFaceButtons) {
    val theme = LocalLemuroidPadTheme.current
    // Each shape is drawn opaque on its own layer, which then gets the colour's transparency once:
    // overlapping parts of the shape don't come out lighter.
    Box(modifier = Modifier.fillMaxSize()) {
        HuggingShape(geometry, theme.level1Shadow, theme.level1ShadowWidth)
        HuggingShape(geometry, theme.level1Fill, 0.dp)
    }
}

@Composable
private fun HuggingShape(
    geometry: HuggingFaceButtons,
    color: Color,
    extra: Dp,
) {
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = color.alpha
                    compositingStrategy = CompositingStrategy.Offscreen
                },
    ) {
        val opaque = color.copy(alpha = 1f)
        val side = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val points = geometry.centers.map { center + it * side }
        val width = geometry.buttonSize * side + extra.toPx() * 2f
        when (points.size) {
            1 -> drawCircle(opaque, radius = width / 2f, center = points[0])
            2 -> drawLine(opaque, points[0], points[1], strokeWidth = width, cap = StrokeCap.Round)
            else -> {
                val path =
                    Path().apply {
                        moveTo(points[0].x, points[0].y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                drawPath(path, opaque)
                drawPath(path, opaque, style = Stroke(width = width, join = StrokeJoin.Round))
            }
        }
    }
}
