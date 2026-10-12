package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarIntentsTest {
    @Test
    fun eventOpensByItsId() {
        assertEquals("content://com.android.calendar/events/77", calendarUri(ClickTarget.Event(77, begin = 1_000, end = 2_000)))
    }

    @Test
    fun dayOpensAtItsTime() {
        assertEquals("content://com.android.calendar/time/1791871200000", calendarUri(ClickTarget.Time(1_791_871_200_000)))
    }
}
