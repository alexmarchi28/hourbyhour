package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetViewsTest {
    /**
     * Where the content ends up with these weight sums, as LinearLayout places it (see event.xml):
     * a single child of weight 1 gets 1/weightSum of its parent, on the far side for the outer one.
     */
    private fun span(sums: Pair<Float, Float>): Pair<Float, Float> {
        val (outer, inner) = sums
        val start = 1f - 1f / outer
        return start to start + (1f - start) / inner
    }

    private fun assertSpan(from: Float, to: Float, actual: Pair<Float, Float>) {
        assertEquals("start of [$from, $to]", from, actual.first, 0.0001f)
        assertEquals("end of [$from, $to]", to, actual.second, 0.0001f)
    }

    @Test
    fun contentSpansTheGivenFractions() {
        for ((from, to) in listOf(0f to 1f, 0.25f to 0.5f, 0f to 0.1f, 0.9f to 1f, 0.6f to 0.61f)) {
            assertSpan(from, to, span(spanWeightSums(from, to)))
        }
    }

    @Test
    fun contentStaysInsideTheLayout() {
        assertSpan(0f, 1f, span(spanWeightSums(-0.2f, 1.3f)))
    }

    @Test
    fun contentAtTheVeryEndIsAThinSliceNotAnInfiniteSum() {
        val sums = spanWeightSums(1f, 1f)

        assertTrue(sums.first.isFinite() && sums.second.isFinite())
        assertSpan(0.999f, 1f, span(sums))
    }

    @Test
    fun emptyOrReversedSpanGetsAThinSlice() {
        assertSpan(0.5f, 0.501f, span(spanWeightSums(0.5f, 0.5f)))
        assertSpan(0.5f, 0.501f, span(spanWeightSums(0.5f, 0.2f)))
    }
}
