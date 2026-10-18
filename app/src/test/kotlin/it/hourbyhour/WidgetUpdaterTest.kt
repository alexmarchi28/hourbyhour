package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUpdaterTest {
    @Test
    fun sizeIsTheOneReportedByTheLauncher() {
        assertEquals(WidgetSize(310f, 420f), estimatedSize(minWidthDp = 310, maxHeightDp = 420))
    }

    @Test
    fun sizeNotReportedYetIsAboutTheDefaultWidget() {
        assertEquals(WidgetSize(250f, 180f), estimatedSize(minWidthDp = 0, maxHeightDp = 0))
        assertEquals(WidgetSize(310f, 180f), estimatedSize(minWidthDp = 310, maxHeightDp = 0))
        assertEquals(WidgetSize(250f, 420f), estimatedSize(minWidthDp = 0, maxHeightDp = 420))
    }
}
