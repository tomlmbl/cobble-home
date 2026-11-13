package io.github.tomlmbl.launcher.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sheet position: downward offset from its place, in pixels, and predictive back progress. Held by [SheetHost],
 * applied by [SheetSurface] through [sheetMotion].
 */
class SheetMotion internal constructor(
    private val scope: CoroutineScope,
    private val velocityThreshold: Float,
    private val backScaleX: Float,
    private val backScaleY: Float,
) {
    /** Surface height; 0 until measured. */
    internal var height by mutableFloatStateOf(0f)
    /** Window height, to recognize a tall sheet. */
    internal var window = 0f
    /** Offset; starts off screen so the first frame does not show the sheet in place. */
    internal val offset = Animatable(Float.MAX_VALUE)
    internal val back = Animatable(0f)
    internal var dismiss: () -> Unit = {}

    /**
     * Displayed offset: never above its place, where the spring overshoot would leave a gap under the sheet, nor lower
     * than its height; off screen until the surface is measured.
     */
    internal val shown: Float get() = if (height <= 0f) UNMEASURED else offset.value.coerceIn(0f, height)
    internal val dragState = DraggableState { drag(it) }

    /** Predictive back shrink, like the M3 modal sheet: up to 48 dp in width and 24 in height. */
    internal fun backScale(size: Size): ScaleFactor {
        val p = back.value
        if (p == 0f || size.width <= 0f || size.height <= 0f) return ScaleFactor(1f, 1f)
        return ScaleFactor(
            1f - lerp(0f, min(backScaleX, size.width), p) / size.width,
            1f - lerp(0f, min(backScaleY, size.height), p) / size.height,
        )
    }

    internal suspend fun show() {
        snapshotFlow { height }.first { it > 0f }
        offset.snapTo(height)
        back.snapTo(0f)
        // A spring's duration barely depends on distance: beyond half the screen, the slow spring, otherwise a tall
        // sheet would rise faster than a short one.
        val tall = window > 0f && height > window * Tokens.tallSheetFraction
        offset.animateTo(0f, if (tall) Tokens.spatialSlow() else Tokens.spatialDefault())
    }

    internal suspend fun hide() {
        if (height > 0f) offset.animateTo(height, Tokens.effectsFast())
        forget()
    }

    /**
     * After an exit: the next sheet, often of another height (the same [SheetHost] serves every Home sheet), starts off
     * screen. Otherwise its first frame used the previous sheet's height and offset, before [show] restarted from its
     * own height: one frame of the sheet partly up, then nothing, then the opening.
     */
    internal suspend fun forget() {
        height = 0f
        offset.snapTo(Float.MAX_VALUE)
    }

    internal fun drag(delta: Float) {
        scope.launch { offset.snapTo((shown + delta).coerceAtLeast(0f)) }
    }

    /** End of a drag: closes beyond half the height or on a downward fling. */
    internal fun settle(velocity: Float) {
        if (shown > height / 2 || velocity > velocityThreshold) dismiss()
        else scope.launch { offset.animateTo(0f, Tokens.spatialDefault(), velocity) }
    }

    /** The sheet's list yields the downward drag when at the top; the sheet moves back up first. */
    internal val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || shown <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            val consumed = available.y.coerceAtLeast(-shown)
            drag(consumed)
            return Offset(0f, consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            drag(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (shown <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

val LocalSheetMotion = staticCompositionLocalOf<SheetMotion?> { null }

/**
 * Animated sheet: appears when [target] becomes non-null, stays composed during its exit when it becomes null again,
 * whatever the cause (tap above, choice, Back, Home). Predictive back shrinks the sheet with the gesture, like the
 * M3 modal sheet, and [onDismiss] closes it on commit.
 */
@Composable
fun <T : Any> SheetHost(target: T?, onDismiss: () -> Unit, content: @Composable (T) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motion = remember {
        with(density) {
            SheetMotion(scope, Tokens.sheetVelocityThreshold.toPx(), Tokens.sheetBackScaleX.toPx(), Tokens.sheetBackScaleY.toPx())
        }
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val window = LocalWindowInfo.current.containerSize.height.toFloat()
    SideEffect { motion.dismiss = { dismiss() }; motion.window = window }
    var last by remember { mutableStateOf(target) }
    SideEffect { if (target != null) last = target }
    val visible = target != null
    LaunchedEffect(visible) {
        if (visible) motion.show() else if (last != null) { motion.hide(); last = null }
    }
    // Registered at each appearance: the last registered wins, so the sheet goes before the screen open below it
    // (catalog, settings), even if that screen was opened after Home's first display.
    if (visible) PredictiveBackHandler { progress ->
        try {
            progress.collect { motion.back.snapTo(Tokens.predictiveBackEasing.transform(it.progress)) }
            dismiss()
        } catch (e: CancellationException) {
            // Gesture cancelled: the sheet returns to its size.
            motion.back.animateTo(0f, Tokens.spatialDefault())
        }
    }
    val shown = target ?: last ?: return
    Box {
        CompositionLocalProvider(LocalSheetMotion provides motion) { content(shown) }
        // During the exit, no touch reaches the sheet: a quick second tap would pick another row.
        if (!visible) Box(Modifier.matchParentSize().blockTouches())
    }
}

/** Measure, offset and drag of a sheet surface; no effect outside a [SheetHost]. */
internal fun Modifier.sheetMotion(motion: SheetMotion?): Modifier = if (motion == null) this else this
    .onSizeChanged { motion.height = it.height.toFloat() }
    .offset { IntOffset(0, motion.shown.roundToInt()) }
    .nestedScroll(motion.nestedScroll)
    .draggable(motion.dragState, Orientation.Vertical, onDragStopped = { motion.settle(it) })

/** Offset of a sheet not yet measured: far below the screen. */
private const val UNMEASURED = 100_000f
