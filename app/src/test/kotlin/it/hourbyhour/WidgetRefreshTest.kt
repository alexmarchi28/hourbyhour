package it.hourbyhour

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WidgetRefreshTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 10, 14, 10, 0).atZone(zone)
    private val clock = Clock.fixed(now.toInstant(), zone)
    private val size = WidgetSize(300f, 400f)

    private class FakeEvents(var permission: Boolean = true, val events: List<CalendarEvent> = emptyList()) : EventSource {
        val reads = mutableListOf<Pair<Long, Long>>()
        var failure: RuntimeException? = null

        override fun hasPermission() = permission

        override fun events(begin: Long, end: Long): List<CalendarEvent> {
            reads += begin to end
            failure?.let { throw it }
            return events
        }

        override fun calendars() = emptyList<CalendarInfo>()
    }

    private class FakeSettings(val saved: MutableMap<Int, WidgetSettings> = mutableMapOf()) : SettingsRepository {
        val broken = mutableSetOf<Int>()

        override fun load(widgetId: Int): WidgetSettings {
            if (widgetId in broken) throw IllegalStateException("broken settings")
            return saved[widgetId] ?: WidgetSettings()
        }

        override fun save(widgetId: Int, settings: WidgetSettings) {
            saved[widgetId] = settings
        }

        override fun delete(widgetIds: IntArray) {
            widgetIds.forEach { saved.remove(it) }
        }
    }

    private fun refresh(events: EventSource, settings: SettingsRepository, ids: IntArray): Pair<Map<Int, WidgetContent>, Map<Int, RuntimeException>> {
        val drawn = linkedMapOf<Int, WidgetContent>()
        val failed = linkedMapOf<Int, RuntimeException>()
        WidgetRefresh(events, settings, clock).refresh(
            ids,
            size = { size },
            fontScale = 1f,
            locale = Locale.ITALY,
            draw = { id, content -> drawn[id] = content },
            onFailure = { id, e -> failed[id] = e },
        )
        return drawn to failed
    }

    @Test
    fun withoutCalendarAccessEveryWidgetAsksForItAndTheCalendarIsNotRead() {
        val events = FakeEvents(permission = false)
        val settings = FakeSettings(mutableMapOf(4 to WidgetSettings(transparency = 40)))

        val (drawn, _) = refresh(events, settings, intArrayOf(4, 9))

        assertEquals(listOf(4, 9), drawn.keys.toList())
        assertTrue(drawn.values.all { it.layout == null })
        assertEquals(WidgetSettings(transparency = 40), drawn.getValue(4).settings)
        assertEquals(emptyList<Pair<Long, Long>>(), events.reads)
    }

    @Test
    fun theCalendarIsReadOnceForWidgetsWithDifferentSettings() {
        val events = FakeEvents()
        val settings = FakeSettings(mutableMapOf(4 to WidgetSettings(dayCount = 5), 9 to WidgetSettings(dayCount = 2)))

        val (drawn, failed) = refresh(events, settings, intArrayOf(4, 9))

        assertEquals(listOf(Agenda.window(now)), events.reads)
        assertEquals(5, drawn.getValue(4).layout?.headers?.size)
        assertEquals(2, drawn.getValue(9).layout?.headers?.size)
        assertEquals(emptyMap<Int, RuntimeException>(), failed)
    }

    @Test
    fun eachWidgetShowsTheEventsAtTheTimeOfTheClock() {
        val lunch = CalendarEvent(
            "Pranzo",
            now.withHour(12).toInstant().toEpochMilli(),
            now.withHour(13).toInstant().toEpochMilli(),
            false,
            0,
        )

        val (drawn, _) = refresh(FakeEvents(events = listOf(lunch)), FakeSettings(), intArrayOf(4))

        val layout = drawn.getValue(4).layout!!
        assertEquals(WidgetSettings(), drawn.getValue(4).settings)
        assertEquals(layoutWidget(Agenda(listOf(lunch), now).snapshot(WidgetSettings()), size, 1f, Locale.ITALY), layout)
    }

    @Test
    fun aWidgetWhoseSettingsFailDoesNotStopTheOthers() {
        val settings = FakeSettings().apply { broken += 9 }

        val (drawn, failed) = refresh(FakeEvents(), settings, intArrayOf(4, 9, 12))

        assertEquals(listOf(4, 12), drawn.keys.toList())
        assertEquals(listOf(9), failed.keys.toList())
    }

    @Test
    fun noWidgetsReadNothing() {
        val events = FakeEvents()

        val (drawn, _) = refresh(events, FakeSettings(), intArrayOf())

        assertEquals(emptyMap<Int, WidgetContent>(), drawn)
        assertEquals(emptyList<Pair<Long, Long>>(), events.reads)
    }

    @Test
    fun aCalendarThatCannotBeReadLeavesEveryWidgetAsItWas() {
        val events = FakeEvents().apply { failure = IllegalStateException("provider gone") }

        try {
            refresh(events, FakeSettings(), intArrayOf(4, 9))
            fail("The failure should reach the caller")
        } catch (e: IllegalStateException) {
            assertEquals("provider gone", e.message)
        }
    }

    @Test
    fun agendaIsNullWithoutCalendarAccess() {
        assertNull(WidgetRefresh(FakeEvents(permission = false), FakeSettings(), clock).agenda())
        assertEquals(now, WidgetRefresh(FakeEvents(), FakeSettings(), clock).agenda()?.now)
    }

    @Test
    fun everyWidgetIsDrawn() {
        val drawn = mutableListOf<Int>()

        drawEach(intArrayOf(4, 9), draw = { drawn += it }, onFailure = { id, _ -> throw AssertionError("Widget $id failed") })

        assertEquals(listOf(4, 9), drawn)
    }

    @Test
    fun aWidgetThatFailsDoesNotStopTheOthers() {
        val drawn = mutableListOf<Int>()
        val failed = mutableListOf<Pair<Int, String?>>()

        drawEach(
            intArrayOf(4, 9, 12),
            draw = { id -> if (id == 9) throw IllegalStateException("broken") else drawn += id },
            onFailure = { id, e -> failed += id to e.message },
        )

        assertEquals(listOf(4, 12), drawn)
        assertEquals(listOf(9 to "broken"), failed)
    }
}
