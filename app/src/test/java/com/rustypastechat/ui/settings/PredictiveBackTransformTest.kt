package com.rustypastechat.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackTransformTest {

    @Test
    fun `at rest the page is untouched`() {
        assertEquals(1f, PredictiveBackTransform.scale(0f), 0.0001f)
        assertEquals(1f, PredictiveBackTransform.alpha(0f), 0.0001f)
        assertEquals(0f, PredictiveBackTransform.slideFraction(0f, fromLeftEdge = true), 0.0001f)
    }

    @Test
    fun `at full progress the page is shrunk but still visible`() {
        assertEquals(PredictiveBackTransform.MIN_SCALE, PredictiveBackTransform.scale(1f), 0.0001f)
        assertEquals(PredictiveBackTransform.MIN_ALPHA, PredictiveBackTransform.alpha(1f), 0.0001f)
        // A preview that fades to nothing defeats the purpose: the point is to
        // show the user what they are going back to while still seeing this.
        assertTrue(PredictiveBackTransform.alpha(1f) > 0.5f)
        assertTrue(PredictiveBackTransform.scale(1f) > 0.8f)
    }

    @Test
    fun `the system can report progress outside 0 to 1 and must not invert the page`() {
        // Over-swipe and spring-back both deliver values past the ends.
        listOf(-0.5f, -1f, 1.5f, 4f).forEach { p ->
            assertTrue("scale went non-positive at $p", PredictiveBackTransform.scale(p) > 0f)
            assertTrue("alpha left 0..1 at $p", PredictiveBackTransform.alpha(p) in 0f..1f)
            assertTrue(
                "slide exceeded its bound at $p",
                kotlin.math.abs(PredictiveBackTransform.slideFraction(p, true)) <=
                    PredictiveBackTransform.MAX_SLIDE_FRACTION + 0.0001f
            )
        }
    }

    @Test
    fun `the page follows the edge the gesture came from`() {
        val left = PredictiveBackTransform.slideFraction(1f, fromLeftEdge = true)
        val right = PredictiveBackTransform.slideFraction(1f, fromLeftEdge = false)
        assertTrue("a left-edge swipe moves the page right", left > 0f)
        assertEquals("and a right-edge swipe mirrors it", -left, right, 0.0001f)
    }

    @Test
    fun `the transform is monotonic across the gesture`() {
        var lastScale = Float.MAX_VALUE
        var lastAlpha = Float.MAX_VALUE
        (0..10).map { it / 10f }.forEach { p ->
            val s = PredictiveBackTransform.scale(p)
            val a = PredictiveBackTransform.alpha(p)
            assertTrue("scale must not grow as the gesture advances", s <= lastScale + 0.0001f)
            assertTrue("alpha must not grow as the gesture advances", a <= lastAlpha + 0.0001f)
            lastScale = s
            lastAlpha = a
        }
    }
}
