package io.github.tomlmbl.launcher.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * One Gaussian blur pass along `direction`, with zero radius at `blurFrom` and `radius` at `blurTo` (eased at both
 * ends), multiplied by `amount`. The kernel spans three standard deviations ([BLUR_SPAN] radii): cut at two, its edge
 * still weighed 13 % and rows entered it abruptly. Its samples stay at most 1.5 px apart (at most 48 on each side):
 * 7 px apart, thin text strokes showed up as copies, in a pattern. With `fade`, opacity goes through four stops
 * (`stopY`, `stopA`) on a smooth curve (slopes `stopM`, [slopes]), brought toward 1 by `amount` (premultiplied
 * colors: everything is scaled), and nothing is rendered outside [`keepFrom`, `keepTo`], already drawn sharp, nor
 * where opacity is zero.
 */
private const val EDGE_PASS = """
uniform shader content;
uniform float2 direction;
uniform float blurFrom;
uniform float blurTo;
uniform float radius;
uniform float4 stopY;
uniform float4 stopA;
uniform float4 stopM;
uniform float amount;
uniform float fade;
uniform float keepFrom;
uniform float keepTo;

float segment(float y, float y0, float y1, float a0, float a1, float m0, float m1) {
    float h = max(y1 - y0, 0.001);
    float s = clamp((y - y0) / h, 0.0, 1.0);
    float s2 = s * s;
    float s3 = s2 * s;
    return (2.0 * s3 - 3.0 * s2 + 1.0) * a0 + (s3 - 2.0 * s2 + s) * h * m0 + (3.0 * s2 - 2.0 * s3) * a1 + (s3 - s2) * h * m1;
}

float alphaAt(float y) {
    if (y <= stopY.y) return segment(y, stopY.x, stopY.y, stopA.x, stopA.y, stopM.x, stopM.y);
    if (y <= stopY.z) return segment(y, stopY.y, stopY.z, stopA.y, stopA.z, stopM.y, stopM.z);
    return segment(y, stopY.z, stopY.w, stopA.z, stopA.w, stopM.z, stopM.w);
}

float radiusAt(float y) {
    return radius * smoothstep(0.0, 1.0, (y - blurFrom) / (blurTo - blurFrom)) * amount;
}

half4 main(float2 p) {
    float alpha = 1.0;
    if (fade > 0.5) {
        if (p.y < keepFrom || p.y > keepTo) return half4(0.0);
        alpha = mix(1.0, alphaAt(p.y), amount);
        if (alpha < 0.004) return half4(0.0);
    }
    float r = radiusAt(p.y);
    half4 c;
    if (r < 0.5) {
        c = content.eval(p);
    } else {
        float sigma = r * 0.5;
        float reach = r * 1.5;
        float n = min(ceil(reach / 1.5), 16.0);
        float step = reach / n;
        half4 sum = content.eval(p);
        float total = 1.0;
        for (int i = 1; i <= 16; i++) {
            if (float(i) > n) break;
            float x = float(i) * step;
            float w = exp(-0.5 * x * x / (sigma * sigma));
            sum += (content.eval(p + direction * x) + content.eval(p - direction * x)) * half(w);
            total += 2.0 * w;
        }
        c = sum / half(total);
    }
    return c * half(alpha);
}
"""

/** Kernel span in radii (three standard deviations); must match the shader's `1.5`. */
private const val BLUR_SPAN = 1.5f

/**
 * Opacity slopes at the stops ([y], [a]) for a monotone curve (Fritsch and Butland): zero at both ends, which join
 * the sharp part or the list edge, and where the curve changes direction or stays flat.
 */
internal fun slopes(y: FloatArray, a: FloatArray): FloatArray {
    val d = FloatArray(3) { (a[it + 1] - a[it]) / (y[it + 1] - y[it]).coerceAtLeast(0.001f) }
    val h = FloatArray(3) { y[it + 1] - y[it] }
    val m = FloatArray(4)
    for (k in 1..2) {
        if (d[k - 1] * d[k] > 0f) {
            val h0 = h[k - 1]
            val h1 = h[k]
            m[k] = 3f * (h0 + h1) / ((2f * h1 + h0) / d[k - 1] + (h1 + 2f * h0) / d[k])
        }
    }
    return m
}

/** Compiled once per process: each effect freezes its shader's values when created, on the UI thread. */
private val across by lazy { RuntimeShader(EDGE_PASS).apply { setFloatUniform("direction", 1f, 0f) } }
private val along by lazy { RuntimeShader(EDGE_PASS).apply { setFloatUniform("direction", 0f, 1f) } }

/**
 * Redraws [content] between [top] and [bottom] into [layer], through both passes; only [keepFrom]..[keepTo] is
 * rendered. All positions are in list px, relative to the band.
 */
private fun DrawScope.drawEdge(
    layer: GraphicsLayer, content: GraphicsLayer, top: Float, bottom: Float, keepFrom: Float, keepTo: Float,
    blurFrom: Float, blurTo: Float, radius: Float, stopY: FloatArray, stopA: FloatArray, amount: Float,
) {
    val origin = top.coerceAtLeast(0f).toInt()
    val end = bottom.coerceAtMost(size.height).toInt()
    if (end <= origin) return
    val m = slopes(stopY, stopA)
    for (shader in listOf(across, along)) {
        shader.setFloatUniform("blurFrom", blurFrom - origin)
        shader.setFloatUniform("blurTo", blurTo - origin)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("stopY", stopY[0] - origin, stopY[1] - origin, stopY[2] - origin, stopY[3] - origin)
        shader.setFloatUniform("stopA", stopA[0], stopA[1], stopA[2], stopA[3])
        shader.setFloatUniform("stopM", m[0], m[1], m[2], m[3])
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("fade", if (shader === along) 1f else 0f)
        shader.setFloatUniform("keepFrom", keepFrom - origin)
        shader.setFloatUniform("keepTo", keepTo - origin)
    }
    layer.renderEffect = RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(along, "content"),
        RenderEffect.createRuntimeShaderEffect(across, "content"),
    ).asComposeRenderEffect()
    // Clipped to the band: without bounds, the passes' intermediate surfaces took the screen's size, most of the
    // measured cost (median GPU 8 ms instead of 3 ms).
    layer.clip = true
    layer.topLeft = IntOffset(0, origin)
    layer.record(IntSize(size.width.toInt(), end - origin)) { translate(top = -origin.toFloat()) { drawLayer(content) } }
    drawLayer(layer)
}

/**
 * Edges of an edge-to-edge list running under the status bar and the search field: rows fade and blur toward each
 * edge, rows only (the window's blurred background stays uniform). Each edge follows the content left beyond it
 * ([above], [below], in px): none at the top or end of the list, full as soon as a whole zone remains to scroll.
 * Top: over [statusBar] and [Tokens.listTopEdge] beyond. Bottom: from [Tokens.listEdgeAboveField] above the field,
 * whose top is [field] from the list bottom; increasing blur and fade down to the list bottom.
 * Only the edge bands go through the shader; the rest is drawn sharp.
 */
@Composable
fun Modifier.listEdges(above: () -> Float, below: () -> Float, statusBar: Dp, field: () -> Dp): Modifier {
    val content = rememberGraphicsLayer()
    val topLayer = rememberGraphicsLayer()
    val bottomLayer = rememberGraphicsLayer()
    return drawWithContent {
        val bar = statusBar.toPx()
        // Joins on whole pixels: a fractional cut left a line between the sharp part and the band.
        val topEnd = (bar + Tokens.listTopEdge.toPx()).roundToInt().toFloat()
        val topAmount = (above() / (topEnd - bar)).coerceIn(0f, 1f)
        val fieldTop = size.height - field().toPx()
        val zoneTop = (fieldTop - Tokens.listEdgeAboveField.toPx()).roundToInt().toFloat()
        val bottomAmount = (below() / Tokens.listEdgeAboveField.toPx()).coerceIn(0f, 1f)
        if (topAmount == 0f && bottomAmount == 0f) {
            drawContent()
            return@drawWithContent
        }
        content.record { this@drawWithContent.drawContent() }
        val sharpTop = if (topAmount > 0f) topEnd else 0f
        val sharpBottom = if (bottomAmount > 0f) zoneTop else size.height
        clipRect(top = sharpTop, bottom = sharpBottom) { drawLayer(content) }
        if (topAmount > 0f) {
            val radius = Tokens.listTopEdgeBlur.toPx()
            drawEdge(
                topLayer, content, top = 0f, bottom = topEnd + radius * BLUR_SPAN, keepFrom = 0f, keepTo = topEnd,
                blurFrom = topEnd, blurTo = 0f, radius = radius,
                stopY = floatArrayOf(0f, bar, topEnd, topEnd), stopA = Tokens.listTopEdgeAlpha, amount = topAmount,
            )
        }
        if (bottomAmount > 0f) {
            val radius = Tokens.listEdgeBlur.toPx()
            val text = fieldTop + Tokens.listEdgeFieldText.toPx()
            // Down to the list bottom (screen bottom, or keyboard): rows stay visible, blurred, under the field.
            drawEdge(
                bottomLayer, content, top = zoneTop - radius * BLUR_SPAN, bottom = size.height, keepFrom = zoneTop, keepTo = size.height,
                blurFrom = zoneTop, blurTo = size.height, radius = radius,
                stopY = floatArrayOf(zoneTop, fieldTop, text, size.height), stopA = Tokens.listEdgeAlpha, amount = bottomAmount,
            )
        }
    }
}

/** Content left before the start of the list (px), bounded: beyond the visible rows, the effect is already full. */
private fun LazyListState.remainingBefore(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** Content left after the end of the list (px), end padding included. */
private fun LazyListState.remainingAfter(): Float {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index < info.totalItemsCount - 1) return Float.MAX_VALUE
    return (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).coerceAtLeast(0).toFloat()
}

/** Content above the top edge; for a list stacked from the bottom, that is the end of the list. */
fun LazyListState.remainingAbove(): Float = if (layoutInfo.reverseLayout) remainingAfter() else remainingBefore()

/** Content below the bottom edge; for a list stacked from the bottom, those are the first rows. */
fun LazyListState.remainingBelow(): Float = if (layoutInfo.reverseLayout) remainingBefore() else remainingAfter()
