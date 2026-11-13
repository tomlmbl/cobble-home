package io.github.tomlmbl.launcher.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize

/**
 * Current screen, recorded to be redrawn blurred under a sheet. The wallpaper is not part of it: it is blurred by
 * the system layer under Home's window.
 * The sheet draws the recorded layer as a reference: a new recording shows without the sheet redrawing.
 */
class Backdrop internal constructor(internal val content: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    /**
     * Shape of the sheet on top, in window coordinates: the original screen is not drawn there, otherwise it would stay
     * sharp through the translucent surface. Computed at draw time, to follow the moving sheet.
     */
    internal var hole by mutableStateOf<(() -> Path)?>(null)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val content = rememberGraphicsLayer()
    return remember(content) { Backdrop(content) }
}

/** Records what the element draws into [backdrop] and draws it, outside the shape of the sheet on top. */
fun Modifier.backdropSource(backdrop: Backdrop) = this
    .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
    .drawWithContent {
        backdrop.content.record { this@drawWithContent.drawContent() }
        val hole = backdrop.hole?.invoke()
        if (hole == null) drawLayer(backdrop.content)
        else translate(-backdrop.origin.x, -backdrop.origin.y) {
            clipPath(hole, ClipOp.Difference) { translate(backdrop.origin.x, backdrop.origin.y) { drawLayer(backdrop.content) } }
        }
    }

/**
 * Sheet background: the current screen blurred by [radius], in [shape], under [content] (veil and surface).
 * Without [backdrop], [content] alone. The shape is removed from the original screen while this background is composed.
 * [scale] shrinks the whole from the middle of its bottom edge (predictive back), the removed shape included.
 */
@Composable
fun BackdropBlur(
    backdrop: Backdrop?,
    shape: Shape,
    radius: Dp,
    modifier: Modifier,
    scale: ((Size) -> ScaleFactor)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = if (scale == null) Modifier else Modifier.graphicsLayer {
        val factor = scale(size)
        scaleX = factor.scaleX
        scaleY = factor.scaleY
        transformOrigin = BottomCenter
    }
    if (backdrop == null) {
        Box(modifier.then(layer), content = content)
        return
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var position by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }
    DisposableEffect(backdrop) { onDispose { backdrop.hole = null } }
    Box(
        // Position and size before the shrink, applied afterwards by the layer; the shape reads them at draw time.
        modifier.onGloballyPositioned { c ->
            position = c.positionInWindow()
            size = c.size.toSize()
            if (backdrop.hole == null) backdrop.hole = {
                Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, density))
                    val factor = scale?.invoke(size)
                    if (factor != null) {
                        // Shrink around the middle of the bottom edge, like the layer.
                        val pivot = Offset(size.width / 2, size.height)
                        translate(-pivot)
                        transform(Matrix().apply { scale(factor.scaleX, factor.scaleY) })
                        translate(pivot)
                    }
                    translate(position)
                }
            }
        }.then(layer),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .graphicsLayer {
                    val px = radius.toPx()
                    renderEffect = BlurEffect(px, px, TileMode.Decal)
                }
                .drawBehind { translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) { drawLayer(backdrop.content) } },
        )
        content()
    }
}

private val BottomCenter = TransformOrigin(0.5f, 1f)

/**
 * True while the system allows cross-window blur (battery saver, developer option, device); otherwise sheets use
 * their opaque surface.
 */
@Composable
fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember { mutableStateOf(manager.isCrossWindowBlurEnabled) }
    DisposableEffect(manager) {
        val listener = java.util.function.Consumer<Boolean> { enabled = it }
        manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}
