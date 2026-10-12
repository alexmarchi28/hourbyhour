package it.hourbyhour

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CalendarIntentsDeviceTest {
    private val intents = CalendarIntents(targetContext)
    private val occurrence = ClickTarget.Event(77, begin = 1_791_871_200_000, end = 1_791_874_800_000)
    private val nextOccurrence = occurrence.copy(begin = occurrence.begin + DAY, end = occurrence.end + DAY)

    private fun googleCalendarInstalled() = try {
        targetContext.packageManager.getPackageInfo(GOOGLE_CALENDAR, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    @Test
    fun eventOpensTheTappedOccurrence() {
        val intent = intents.intent(occurrence)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://com.android.calendar/events/77", intent.dataString)
        assertEquals(occurrence.begin, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(occurrence.end, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test
    fun googleCalendarOpensEventsAndDays() {
        assumeTrue("Google Calendar is not installed", googleCalendarInstalled())

        for (target in listOf(occurrence, ClickTarget.Time(occurrence.begin))) {
            val intent = intents.intent(target)
            assertEquals(GOOGLE_CALENDAR, intent.`package`)
            val activity = targetContext.packageManager.resolveActivity(intent, 0)?.activityInfo
            assertEquals("Who opens $target", GOOGLE_CALENDAR, activity?.packageName)
        }
    }

    @Test
    fun eachOccurrenceHasItsOwnPendingIntent() {
        // Android tells pending intents apart by their data, which the occurrences share.
        assertNotEquals(intents.pendingIntent(occurrence), intents.pendingIntent(nextOccurrence))
        assertNotEquals(intents.pendingIntent(ClickTarget.Time(77)), intents.pendingIntent(ClickTarget.Event(77, 77, 77)))
        assertEquals(intents.pendingIntent(occurrence), intents.pendingIntent(occurrence.copy()))
    }

    @Test
    fun tapsOpenAnActivity() {
        assertTrue(intents.pendingIntent(occurrence).isActivity)
        assertTrue(intents.pendingIntent(ClickTarget.Time(occurrence.begin)).isActivity)
    }

    private companion object {
        const val GOOGLE_CALENDAR = "com.google.android.calendar"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
