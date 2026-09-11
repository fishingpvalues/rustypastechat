package com.rustypastechat.ui.settings

/**
 * How far the outgoing page shrinks while a predictive back gesture is in
 * progress. Material's back preview scales the leaving surface down toward a
 * minimum and slides it toward the edge the gesture started from, so the user
 * sees where they are going before committing.
 *
 * Pure functions so the curve can be asserted without a device: the gesture
 * itself cannot be unit-tested, but the mapping from its progress to the
 * transform can, and that is where an off-by-one or an unclamped value shows
 * up as a page that vanishes or inverts mid-swipe.
 */
object PredictiveBackTransform {

    /** Smallest scale the leaving page reaches at full progress. */
    const val MIN_SCALE = 0.90f

    /** Largest sideways shift, as a fraction of the page width. */
    const val MAX_SLIDE_FRACTION = 0.08f

    /** Lowest opacity the leaving page reaches. */
    const val MIN_ALPHA = 0.75f

    private fun clamp(progress: Float) = progress.coerceIn(0f, 1f)

    fun scale(progress: Float): Float = 1f - (1f - MIN_SCALE) * clamp(progress)

    fun alpha(progress: Float): Float = 1f - (1f - MIN_ALPHA) * clamp(progress)

    /**
     * Sideways shift as a fraction of width. Positive moves right, which is
     * what a gesture from the left edge should do; [fromLeftEdge] false
     * mirrors it.
     */
    fun slideFraction(progress: Float, fromLeftEdge: Boolean): Float {
        val magnitude = MAX_SLIDE_FRACTION * clamp(progress)
        return if (fromLeftEdge) magnitude else -magnitude
    }
}
