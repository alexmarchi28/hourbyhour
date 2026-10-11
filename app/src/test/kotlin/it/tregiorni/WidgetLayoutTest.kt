package it.tregiorni

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val monday = LocalDate.of(2026, 10, 12)

    // With this height the grid is 360dp tall: one dp per minute when the range spans 6 hours.
    private val heightDp = 398f
    private val delta = 1e-6f

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun timed(title: String, begin: Long, end: Long) = CalendarEvent(title, begin, end, false, 0)

    private fun allDay(title: String, day: Int) = CalendarEvent(
        title,
        LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        LocalDate.of(2026, 10, day + 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true,
        0,
    )

    private fun layout(
        vararg events: CalendarEvent,
        nowHour: Int = 8,
        nowMinute: Int = 0,
        dayCount: Int = 3,
        height: Float = heightDp,
    ): WidgetLayout {
        val now = LocalDateTime.of(2026, 10, 12, nowHour, nowMinute).atZone(zone)
        val snapshot = Snapshot(splitIntoDays(events.toList(), monday, dayCount, zone), now)
        return layoutWidget(snapshot, height, fontScale = 1f, locale = Locale.ITALY)
    }

    @Test
    fun headersAreLocalizedAndMarkToday() {
        val headers = layout().headers

        assertEquals(listOf("Lun 12", "Mar 13", "Mer 14"), headers.map { it.label })
        assertEquals(listOf(true, false, false), headers.map { it.today })
    }

    @Test
    fun anyNumberOfDaysGetsOneHeaderAndColumnEach() {
        val result = layout(dayCount = 5)

        assertEquals(5, result.headers.size)
        assertEquals(5, result.columns.size)
    }

    @Test
    fun noBannerRowsWithoutAllDayEvents() {
        assertEquals(emptyList<List<BannerCell>>(), layout(timed("A", at(12, 9), at(12, 10))).bannerRows)
    }

    @Test
    fun bannerShowsAsManyRowsAsTheBusiestDayUpToTwo() {
        val a = allDay("A", 12)
        val b = allDay("B", 12)
        val c = allDay("C", 13)

        val rows = layout(a, b, c).bannerRows

        assertEquals(
            listOf(
                listOf(BannerCell.Event(a), BannerCell.Event(c), BannerCell.Empty),
                listOf(BannerCell.Event(b), BannerCell.Empty, BannerCell.Empty),
            ),
            rows,
        )
    }

    @Test
    fun bannerOverflowCountsTheHiddenEvents() {
        val a = allDay("A", 12)

        val rows = layout(a, allDay("B", 12), allDay("C", 12)).bannerRows

        assertEquals(listOf(BannerCell.Event(a), BannerCell.Empty, BannerCell.Empty), rows[0])
        assertEquals(listOf(BannerCell.More(2), BannerCell.Empty, BannerCell.Empty), rows[1])
    }

    @Test
    fun eventIsPlacedByFractionOfTheHourRange() {
        // The range is widened to 6 hours around the event: 7-13.
        val box = layout(timed("A", at(12, 9), at(12, 10))).columns[0].events.single()

        assertEquals(2f / 6, box.top, delta)
        assertEquals(3f / 6, box.bottom, delta)
        assertEquals(0f, box.left, delta)
        assertEquals(1f, box.right, delta)
    }

    @Test
    fun hourLabelsCoverTheRange() {
        val result = layout(timed("A", at(12, 9), at(12, 10)))

        assertEquals(listOf("7", "8", "9", "10", "11", "12"), result.hourLabels)
    }

    @Test
    fun hourLabelsGetSparserWhenTheWidgetIsShort() {
        val result = layout(timed("A", at(12, 9), at(12, 10)), height = 60f)

        assertEquals(listOf("7", "", "", "", "11", ""), result.hourLabels)
    }

    @Test
    fun overlappingEventsShareTheColumnWidth() {
        val events = layout(timed("A", at(12, 9), at(12, 11)), timed("B", at(12, 10), at(12, 12))).columns[0].events

        assertEquals(listOf(0f to 0.5f, 0.5f to 1f), events.map { it.left to it.right })
    }

    @Test
    fun shortEventIsDrawnAtLeastOneLineTall() {
        // One line of text plus padding is 16dp, that is 16 minutes at one dp per minute.
        val box = layout(timed("A", at(12, 9), at(12, 9, 5))).columns[0].events.single()

        assertEquals(120f / 360, box.top, delta)
        assertEquals(136f / 360, box.bottom, delta)
        assertEquals(1, box.maxLines)
    }

    @Test
    fun shortEventAtTheEndOfTheRangeGrowsUpwards() {
        // The range is 18-24: the event cannot grow past midnight, so it starts earlier.
        val box = layout(timed("A", at(12, 23, 55), at(13, 0))).columns[0].events.single()

        assertEquals(344f / 360, box.top, delta)
        assertEquals(1f, box.bottom, delta)
    }

    @Test
    fun tallEventsFitMoreLines() {
        val box = layout(timed("A", at(12, 9), at(12, 11))).columns[0].events.single()

        // 120dp minus padding, at 13dp per line.
        assertEquals(9, box.maxLines)
    }

    @Test
    fun eventsThatEndedArePast() {
        val events = layout(timed("A", at(12, 9), at(12, 10)), timed("B", at(12, 11), at(12, 12)), nowHour = 10, nowMinute = 30)
            .columns[0].events

        assertEquals(listOf(true, false), events.map { it.past })
    }

    @Test
    fun nowLineIsOnlyOnTodayColumn() {
        val columns = layout(timed("A", at(12, 9), at(12, 10)), nowHour = 10, nowMinute = 30).columns

        assertEquals(210f / 360, columns[0].nowLine!!, delta)
        assertNull(columns[1].nowLine)
        assertNull(columns[2].nowLine)
    }

    @Test
    fun nowLineIsHiddenOutsideTheRange() {
        assertNull(layout(timed("A", at(12, 9), at(12, 10)), nowHour = 20).columns[0].nowLine)
    }

    @Test
    fun eventsOnOtherDaysGoToTheirColumn() {
        val columns = layout(timed("A", at(13, 9), at(13, 10)), timed("B", at(14, 9), at(14, 10))).columns

        assertEquals(listOf(0, 1, 1), columns.map { it.events.size })
    }

    @Test
    fun fractionsStayWithinTheColumn() {
        val columns = layout(
            timed("Night", at(12, 23), at(13, 1)),
            timed("Morning", at(13, 6), at(13, 6, 1)),
            timed("Long", at(14, 0), at(14, 23, 59)),
        ).columns

        for (box in columns.flatMap { it.events }) {
            assertTrue("$box", box.top in 0f..1f && box.bottom in 0f..1f && box.top < box.bottom)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aWidgetNeedsAtLeastOneDay() {
        layout(dayCount = 0)
    }
}
