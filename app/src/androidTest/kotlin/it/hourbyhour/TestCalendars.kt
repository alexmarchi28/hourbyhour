package it.hourbyhour

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Calendars that a test creates on the phone only (local calendars, never synced to Google), filled
 * as their sync adapter, the way Google Calendar fills its own. [close] deletes them with their events.
 * The app may only read calendars: the test writes them with the permissions of the adb shell.
 */
class TestCalendars : AutoCloseable {
    private val resolver = targetContext.contentResolver
    private val zone = ZoneId.systemDefault()

    init {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        // Calendars left by a test that crashed.
        deleteAll()
    }

    fun insertCalendar(name: String, color: Int, synced: Boolean = true, visible: Boolean = true, primary: Boolean = false): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.SYNC_EVENTS, if (synced) 1 else 0)
            put(Calendars.VISIBLE, if (visible) 1 else 0)
            put(Calendars.IS_PRIMARY, if (primary) 1 else 0)
        }
        return insert(Calendars.CONTENT_URI, values)
    }

    /** An event from [begin] to [end] in epoch millis, in the time zone of the phone; [more] sets other columns. */
    fun insertEvent(calendar: Long, title: String?, begin: Long, end: Long, more: ContentValues.() -> Unit = {}): Long {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendar)
            put(Events.TITLE, title)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.EVENT_TIMEZONE, zone.id)
            more()
        }
        return insert(Events.CONTENT_URI, values)
    }

    /** An event lasting all of [date], stored at UTC midnights like the provider does. */
    fun insertAllDay(calendar: Long, title: String, date: LocalDate): Long {
        val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return insertEvent(calendar, title, midnight, midnight + DAY) {
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
        }
    }

    /** Marks [eventId] as the birthday of a contact, as Google Calendar does. */
    fun markAsBirthday(eventId: Long) {
        insert(
            ExtendedProperties.CONTENT_URI,
            ContentValues().apply {
                put(ExtendedProperties.EVENT_ID, eventId)
                put(ExtendedProperties.NAME, CalendarSource.EVENT_TYPE_PROPERTY)
                put(ExtendedProperties.VALUE, CalendarSource.BIRTHDAY_EVENT_TYPE)
            },
        )
    }

    override fun close() {
        deleteAll()
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    /** Deleting a calendar deletes its events too. */
    private fun deleteAll() {
        resolver.delete(asSyncAdapter(Calendars.CONTENT_URI), "${Calendars.ACCOUNT_NAME} = ?", arrayOf(ACCOUNT))
    }

    private fun insert(uri: Uri, values: ContentValues) = ContentUris.parseId(resolver.insert(asSyncAdapter(uri), values)!!)

    private fun asSyncAdapter(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        private const val ACCOUNT = "hourbyhour-test"
    }
}
