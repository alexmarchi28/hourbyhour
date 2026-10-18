package it.hourbyhour

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
    private val heightDp = 360f + LayoutEstimates.CHROME_HEIGHT_DP
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
        width: Float = WIDTH_DP,
    ): WidgetLayout {
        val now = LocalDateTime.of(2026, 10, 12, nowHour, nowMinute).atZone(zone)
        val snapshot = Snapshot(splitIntoDays(events.toList(), monday, dayCount, zone), now)
        return layoutWidget(snapshot, WidgetSize(width, height), fontScale = 1f, locale = Locale.ITALY)
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
    fun theGridShowsTheHoursOfTheSnapshot() {
        val now = LocalDateTime.of(2026, 10, 12, 8, 0).atZone(zone)
        val days = splitIntoDays(listOf(timed("A", at(12, 9), at(12, 10))), monday, 3, zone)

        val result = layoutWidget(Snapshot(days, now, HourRange(8, 20)), WidgetSize(WIDTH_DP, heightDp), fontScale = 1f, locale = Locale.ITALY)

        assertEquals((8 until 20).map { it.toString() }, result.hourLabels)
        val box = result.columns[0].events.single()
        assertEquals(1f / 12, box.top, delta)
        assertEquals(2f / 12, box.bottom, delta)
        assertEquals(ClickTarget.Time(at(13, 8)), result.columns[1].click)
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
    fun shortEventKeepsItsRealLength() {
        val box = layout(timed("A", at(12, 9), at(12, 9, 5))).columns[0].events.single()

        assertEquals(120f / 360, box.top, delta)
        assertEquals(125f / 360, box.bottom, delta)
    }

    @Test
    fun adjacentShortEventsDoNotShareTheWidth() {
        // A 15-minute event followed right away by others: no overlap, so all of them are full width.
        val events = layout(
            timed("Meditazione", at(12, 7), at(12, 7, 15)),
            timed("Lettura", at(12, 7, 15), at(12, 8, 15)),
            timed("Test", at(12, 8, 15), at(12, 9, 15)),
        ).columns[0].events

        assertEquals(listOf(0f to 1f, 0f to 1f, 0f to 1f), events.map { it.left to it.right })
        // The range is 6-12 at one dp per minute: 15 minutes are 15dp, not enough for a regular title.
        assertEquals(60f / 360, events[0].top, delta)
        assertEquals(75f / 360, events[0].bottom, delta)
        assertEquals(75f / 360, events[1].top, delta)
        assertEquals(TitleSize.COMPACT, events[0].title)
        assertEquals(TitleSize.NORMAL, events[1].title)
    }

    @Test
    fun titleSizeFollowsTheBoxHeight() {
        val events = layout(
            timed("30 minutes", at(12, 9), at(12, 9, 30)),
            timed("10 minutes", at(12, 10), at(12, 10, 10)),
            timed("5 minutes", at(12, 11), at(12, 11, 5)),
        ).columns[0].events

        assertEquals(listOf(TitleSize.NORMAL, TitleSize.COMPACT, TitleSize.NONE), events.map { it.title })
    }

    @Test
    fun largerFontsNeedTallerBoxes() {
        val now = LocalDateTime.of(2026, 10, 12, 8, 0).atZone(zone)
        val snapshot = Snapshot(splitIntoDays(listOf(timed("A", at(12, 9), at(12, 9, 20))), monday, 3, zone), now)

        val box = layoutWidget(snapshot, WidgetSize(WIDTH_DP, heightDp), fontScale = 1.5f, locale = Locale.ITALY).columns[0].events.single()

        // 20dp fit a regular title at normal font size, but not at 1.5x.
        assertEquals(TitleSize.COMPACT, box.title)
    }

    @Test
    fun eventWithoutDurationIsAThinBar() {
        val box = layout(timed("Reminder", at(12, 9), at(12, 9))).columns[0].events.single()

        // The range is 6-12. The thinnest bar is 3dp, that is 3 minutes at one dp per minute.
        assertEquals(180f / 360, box.top, delta)
        assertEquals(183f / 360, box.bottom, delta)
        assertEquals(TitleSize.NONE, box.title)
    }

    @Test
    fun eventWithoutDurationBesideAnEventStartingTogether() {
        val events = layout(timed("Reminder", at(12, 9), at(12, 9)), timed("Meeting", at(12, 9), at(12, 10))).columns[0].events

        assertEquals(listOf(2, 2), events.map { (it.right - it.left).let { width -> Math.round(1 / width) } })
    }

    @Test
    fun thinEventGrowsIntoTheFreeTimeAfterIt() {
        // 60dp for 6 hours: the thinnest bar (3dp) lasts 18 minutes.
        val box = layout(timed("A", at(12, 9), at(12, 9, 5)), height = 98f).columns[0].events.single()

        assertEquals(120f / 360, box.top, delta)
        assertEquals(138f / 360, box.bottom, delta)
    }

    @Test
    fun thinEventFollowedRightAwayGrowsUpwardsInsteadOfSideways() {
        // The bar cannot grow over the next event: it takes the free time before it, and both
        // events keep the full width because their times do not overlap.
        val events = layout(timed("A", at(12, 9), at(12, 9, 5)), timed("B", at(12, 9, 5), at(12, 10)), height = 98f)
            .columns[0].events

        assertEquals(listOf(0f to 1f, 0f to 1f), events.map { it.left to it.right })
        assertEquals(107f / 360, events[0].top, delta)
        assertEquals(125f / 360, events[0].bottom, delta)
        assertEquals(125f / 360, events[1].top, delta)
    }

    @Test
    fun thinEventBetweenTwoEventsKeepsItsRealLength() {
        val events = layout(
            timed("Before", at(12, 8), at(12, 9)),
            timed("Thin", at(12, 9), at(12, 9, 5)),
            timed("After", at(12, 9, 5), at(12, 10)),
            height = 98f,
        ).columns[0].events

        // The range is 6-12.
        assertEquals(180f / 360, events[1].top, delta)
        assertEquals(185f / 360, events[1].bottom, delta)
    }

    @Test
    fun eventsWithoutDurationAtTheSameTimeSitSideBySide() {
        val events = layout(timed("A", at(12, 9), at(12, 9)), timed("B", at(12, 9), at(12, 9))).columns[0].events

        assertEquals(listOf(0f to 0.5f, 0.5f to 1f), events.map { it.left to it.right }.sortedBy { it.first })
    }

    @Test
    fun barBesideALongerEventGrowsInItsOwnColumn() {
        // B overlaps A, so it gets the right half: A is not in its way, and B grows to the thinnest bar (18 minutes).
        val events = layout(timed("A", at(12, 9), at(12, 10)), timed("B", at(12, 9), at(12, 9, 5)), height = 98f).columns[0].events
        val b = events.single { it.event.title == "B" }

        assertEquals(0.5f to 1f, b.left to b.right)
        assertEquals(138f / 360, b.bottom, delta)
    }

    @Test
    fun barAtTheEndOfTheRangeGrowsUpwards() {
        // The range is 18-24: a one-minute event at 23:59 cannot grow past midnight, so it starts earlier.
        val box = layout(timed("A", at(12, 23, 59), at(13, 0))).columns[0].events.single()

        assertEquals(357f / 360, box.top, delta)
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
    fun eventEndingNowIsPast() {
        val box = layout(timed("A", at(12, 9), at(12, 10)), nowHour = 10).columns[0].events.single()

        assertTrue(box.past)
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

    @Test
    fun headersUseOneLetterWeekdaysInNarrowColumns() {
        // 296dp for 7 days: columns of about 37dp, too narrow for "Lun 12".
        val headers = layout(dayCount = 7, width = 296f).headers

        assertEquals(listOf("L 12", "M 13", "M 14", "G 15", "V 16", "S 17", "D 18"), headers.map { it.label })
    }

    @Test
    fun columnWidthLeavesOutTheHourLabels() {
        // 330dp for 7 days is 47dp each, but the hour labels and padding take 34dp: 42dp, too few for "Lun 12".
        assertEquals("L 12", layout(dayCount = 7, width = 330f).headers[0].label)
    }

    @Test
    fun headersShowOnlyTheDateInVeryNarrowColumns() {
        assertEquals("12", layout(dayCount = 7, width = 150f).headers[0].label)
    }

    @Test
    fun longWordsStayOnOneLineInNarrowColumns() {
        // Seven days on a widget 400dp wide: "Arrampicata" does not fit the width of the box.
        val box = layout(timed("Arrampicata", at(12, 9), at(12, 11)), dayCount = 7).columns[0].events.single()

        assertEquals(1, box.maxLines)
    }

    @Test
    fun titlesWrapWhenEveryWordFits() {
        val box = layout(timed("Arrampicata al Rock", at(12, 9), at(12, 11))).columns[0].events.single()

        assertEquals(9, box.maxLines)
    }

    @Test
    fun overlappingEventsShareTheWidthAlsoForWrapping() {
        // Side by side, each box is half a column: too narrow for "Arrampicata" on more lines.
        val events = layout(timed("Arrampicata", at(12, 9), at(12, 11)), timed("Riunione", at(12, 10), at(12, 12))).columns[0].events

        assertEquals(1, events.single { it.event.title == "Arrampicata" }.maxLines)
    }

    @Test
    fun aDayOpensAtTheFirstHourShown() {
        // The range is 7-13: tapping Tuesday opens it at 7:00.
        val result = layout(timed("A", at(12, 9), at(12, 10)))

        assertEquals(ClickTarget.Time(at(13, 7)), result.columns[1].click)
        assertEquals(ClickTarget.Time(at(13, 7)), result.headers[1].click)
    }

    @Test
    fun anEventCrossingMidnightOpensTheWholeEventFromBothDays() {
        val event = timed("Festa", at(12, 22), at(13, 2)).copy(eventId = 42)

        val columns = layout(event).columns

        val expected = ClickTarget.Event(42, at(12, 22), at(13, 2))
        assertEquals(expected, columns[0].events.single().event.clickTarget())
        assertEquals(expected, columns[1].events.single().event.clickTarget())
    }

    @Test
    fun occurrencesOfARecurringEventOpenThemselves() {
        val monday = timed("Meditazione", at(12, 7), at(12, 7, 15)).copy(eventId = 7)
        val tuesday = timed("Meditazione", at(13, 7), at(13, 7, 15)).copy(eventId = 7)

        val targets = layout(monday, tuesday).columns.take(2).map { it.events.single().event.clickTarget() }

        assertEquals(listOf(ClickTarget.Event(7, at(12, 7), at(12, 7, 15)), ClickTarget.Event(7, at(13, 7), at(13, 7, 15))), targets)
    }

    private companion object {
        /** About a widget 5 cells wide on a phone: columns of 122dp with three days. */
        const val WIDTH_DP = 400f
    }
}
