package io.github.tomlmbl.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSlopesTest {

    /** Hermite curve of a segment, as in the shader. */
    private fun at(y: FloatArray, a: FloatArray, m: FloatArray, k: Int, s: Float): Float {
        val h = y[k + 1] - y[k]
        val s2 = s * s
        val s3 = s2 * s
        return (2 * s3 - 3 * s2 + 1) * a[k] + (s3 - 2 * s2 + s) * h * m[k] + (3 * s2 - 2 * s3) * a[k + 1] + (s3 - s2) * h * m[k + 1]
    }

    @Test
    fun endsAreFlatAndInteriorFollowsTheCurve() {
        val m = slopes(floatArrayOf(0f, 100f, 150f, 300f), Tokens.listEdgeAlpha)
        assertEquals(0f, m[0])
        assertEquals(0f, m[3])
        assertTrue(m[1] < 0f && m[2] < 0f)
    }

    @Test
    fun repeatedLastStopGivesAFlatJoin() {
        val m = slopes(floatArrayOf(0f, 60f, 120f, 120f), Tokens.listTopEdgeAlpha)
        assertTrue(m[1] > 0f)
        assertEquals(0f, m[2])
    }

    @Test
    fun curveStaysMonotoneBetweenStops() {
        for ((y, a) in listOf(
            floatArrayOf(0f, 100f, 150f, 300f) to Tokens.listEdgeAlpha,
            floatArrayOf(0f, 60f, 120f, 120f) to Tokens.listTopEdgeAlpha,
        )) {
            val m = slopes(y, a)
            var previous = a[0]
            for (k in 0..2) {
                if (y[k + 1] == y[k]) continue
                for (i in 1..20) {
                    val v = at(y, a, m, k, i / 20f)
                    assertTrue(if (a[3] < a[0]) v <= previous + 1e-4f else v >= previous - 1e-4f)
                    previous = v
                }
            }
        }
    }
}
