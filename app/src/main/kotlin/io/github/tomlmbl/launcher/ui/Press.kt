package io.github.tomlmbl.launcher.ui

import android.animation.ValueAnimator
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pressed state: the ripple of the element touched through [interaction] is drawn here, in [shape], inset
 * horizontally by [inset]. The touch target stays the element's, which sets `indication = null` on its `clickable`.
 * What follows this modifier is clipped to [shape]: use only for content that fits in it.
 */
fun Modifier.pressedIn(interaction: InteractionSource, shape: Shape, inset: Dp? = null): Modifier = composed {
    (if (inset != null) padding(horizontal = inset) else this).clip(shape).indication(interaction, LocalIndication.current)
}

/**
 * Press shrink: what follows this modifier shrinks on contact, around [pivot] (fraction of its size: start for
 * left-aligned content, center for centered content), and springs back on release or as soon as a drag cancels the
 * press. Reads the raw source, without the [deferredPress] delay. At draw time only: touch target, launch origin and
 * return target keep their place. With animations off: no shrink.
 */
fun Modifier.pressScale(interaction: InteractionSource, pivot: Offset): Modifier = composed {
    val amount = remember { Animatable(1f) }
    LaunchedEffect(interaction) {
        var job: Job? = null
        interaction.interactions.collect { event ->
            val target = when (event) {
                is PressInteraction.Press -> if (ValueAnimator.areAnimatorsEnabled()) Tokens.pressScale else 1f
                is PressInteraction.Release, is PressInteraction.Cancel -> 1f
                else -> return@collect
            }
            job?.cancel()
            job = launch { amount.animateTo(target, if (target < 1f) Tokens.effectsFast() else Tokens.spatialFast()) }
        }
    }
    drawWithContent {
        val s = amount.value
        if (s == 1f) drawContent()
        else scale(s, Offset(size.width * pivot.x, size.height * pivot.y)) { this@drawWithContent.drawContent() }
    }
}

/**
 * Press seen by the indication only, delayed by the system tap timeout as in a scrolling container: a Home drag
 * started on an element cancels it before the ripple appears, instead of letting it finish during the gesture. A
 * short tap still shows it. Cancelled after the delay (slow drag), the press is cleared at once: a fresh source
 * recreates the indication, since the M3 ripple otherwise finishes appearing before fading. The `clickable` keeps
 * [source]: no action is delayed. Only outside scrolling containers, where Compose does not already delay the press.
 */
@Composable
fun deferredPress(source: InteractionSource): InteractionSource {
    var shown by remember(source) { mutableStateOf(MutableInteractionSource()) }
    LaunchedEffect(source) {
        val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
        var pending: Job? = null
        var visible = false
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pending = launch {
                    delay(tapTimeout)
                    visible = true
                    shown.emit(interaction)
                }
                is PressInteraction.Release -> {
                    pending?.cancel()
                    if (!visible) shown.emit(interaction.press)
                    shown.emit(interaction)
                    visible = false
                }
                is PressInteraction.Cancel -> {
                    pending?.cancel()
                    if (visible) shown = MutableInteractionSource()
                    visible = false
                }
                else -> shown.emit(interaction)
            }
        }
    }
    return shown
}

/** True during a Home drag (catalog, shade): no touch takes shape there. */
val LocalContactBlocked = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.State<Boolean>> { androidx.compose.runtime.mutableStateOf(false) }

/**
 * Touch feedback: contact progress from 0 to 1, on the design's spring (stiffness 800, damping 0.6) on press as on
 * release. Reads [interaction] as is: pass [deferredPress] so as not to react to a drag. With animations off: a
 * direct jump.
 */
@Composable
fun contactProgress(interaction: InteractionSource): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    val blocked = LocalContactBlocked.current
    LaunchedEffect(blocked.value) {
        if (blocked.value && progress.targetValue != 0f) {
            if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(0f, spring(Tokens.contactDamping, Tokens.contactStiffness)) else progress.snapTo(0f)
        }
    }
    LaunchedEffect(interaction) {
        var job: Job? = null
        // [deferredPress] cancels a press (Home drag) by replacing its source, without emitting a cancel:
        // a new source therefore brings the shape back to rest.
        if (progress.value != 0f || progress.targetValue != 0f) job = launch {
            if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(0f, spring(Tokens.contactDamping, Tokens.contactStiffness)) else progress.snapTo(0f)
        }
        interaction.interactions.collect { event ->
            val target = when (event) {
                is PressInteraction.Press -> if (blocked.value) return@collect else 1f
                is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                else -> return@collect
            }
            job?.cancel()
            job = launch {
                if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(target, spring(Tokens.contactDamping, Tokens.contactStiffness))
                else progress.snapTo(target)
            }
        }
    }
    return progress
}

/**
 * Favorites touch feedback: the row's surface goes from its corners [radii] (top-start, top-end, bottom-end,
 * bottom-start) to a pill (half height), veil [color] doubled. Size unchanged: neither width nor height moves.
 * At draw time only.
 */
fun Modifier.contactSurface(progress: () -> Float, color: () -> androidx.compose.ui.graphics.Color, radii: List<Dp>): Modifier =
    drawWithContent {
        val p = progress()
        val pill = size.height / 2f
        fun r(d: Dp) = androidx.compose.ui.geometry.CornerRadius(d.toPx() + (pill - d.toPx()) * p.coerceIn(0f, 1.2f))
        val c = color()
        val rr = androidx.compose.ui.geometry.RoundRect(
            0f, 0f, size.width, size.height,
            topLeftCornerRadius = r(radii[0]), topRightCornerRadius = r(radii[1]),
            bottomRightCornerRadius = r(radii[2]), bottomLeftCornerRadius = r(radii[3]),
        )
        drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(rr) }, c.copy(alpha = (c.alpha * (1f + p)).coerceIn(0f, 1f)))
        drawContent()
    }

/**
 * Touch feedback: the dock disc (circle of [diameter], already drawn at [veil] in the icon) becomes a rounded
 * square of [Tokens.contactDockRadius] on touch, veil doubled. Drawn behind the icon: the square minus the disc at
 * twice the veil, the disc at the veil alone (the icon adds its own).
 */
fun Modifier.contactDisc(progress: () -> Float, veil: () -> androidx.compose.ui.graphics.Color, diameter: Dp): Modifier =
    drawWithContent {
        val p = progress()
        if (p > 0.001f) {
            val d = diameter.toPx()
            val left = (size.width - d) / 2f
            val top = (size.height - d) / 2f
            val radius = d / 2f + (Tokens.contactDockRadius.toPx() - d / 2f) * p
            val square = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + d, top + d, androidx.compose.ui.geometry.CornerRadius(radius)))
            }
            val disc = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(left, top, left + d, top + d)) }
            val outside = androidx.compose.ui.graphics.Path().apply { op(square, disc, androidx.compose.ui.graphics.PathOperation.Difference) }
            val v = veil()
            drawPath(outside, v.copy(alpha = v.alpha * 2f * p.coerceIn(0f, 1f)))
            drawPath(disc, v.copy(alpha = v.alpha * p.coerceIn(0f, 1f)))
        }
        drawContent()
    }

/** Layer that absorbs every touch: screen or sheet not yet in place, or leaving. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
}
