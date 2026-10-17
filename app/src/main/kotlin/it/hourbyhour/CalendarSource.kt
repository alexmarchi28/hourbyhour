package it.hourbyhour

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Instances

/**
 * Reads events from the calendar provider of the phone, which Google Calendar keeps in sync.
 * Using [Instances] instead of [Events] gives one row per occurrence of recurring events.
 */
object CalendarSource {
    /** The columns read for each event occurrence. */
    val EVENT_COLUMNS = arrayOf(
        Instances.TITLE,
        Instances.BEGIN,
        Instances.END,
        Instances.ALL_DAY,
        Instances.DISPLAY_COLOR,
        Instances.CALENDAR_ID,
        Instances.EVENT_ID,
    )

    /** The columns read for each calendar. */
    val CALENDAR_COLUMNS = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR)

    /** The columns read for each event marked as a birthday. */
    val BIRTHDAY_COLUMNS = arrayOf(ExtendedProperties.EVENT_ID)

    /**
     * Google Calendar marks the birthdays of contacts with this extended property. It is not
     * documented: if it changes, birthdays look like the other events of their calendar.
     */
    const val EVENT_TYPE_PROPERTY = "shared:calendarProviderEventType"
    const val BIRTHDAY_EVENT_TYPE = "BIRTHDAY"

    // Only calendars whose events are synced to the phone. Which of them to show is a setting of
    // each widget: the provider's "visible" flag is ignored, because it does not follow the
    // calendars checked in Google Calendar. Like Google Calendar, cancelled events and invitations
    // the user declined are not shown. Status columns can be NULL.
    private val SELECTION = """
        ${Instances.SYNC_EVENTS} = 1
        AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})
        AND (${Instances.SELF_ATTENDEE_STATUS} IS NULL
             OR ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED})
    """.trimIndent()

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Event instances overlapping [begin, end), in epoch millis. Requires [hasPermission]. */
    fun load(context: Context, begin: Long, end: Long): List<CalendarEvent> {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin) }
            .also { ContentUris.appendId(it, end) }
            .build()
        val untitled = context.getString(R.string.untitled_event)
        val birthdays = loadBirthdays(context).toSet()
        return context.query(uri, EVENT_COLUMNS, SELECTION, order = "${Instances.BEGIN} ASC") { readEvents(it, untitled, birthdays) }
    }

    /** The ids of the events that are birthdays of contacts. */
    private fun loadBirthdays(context: Context): List<Long> {
        val selection = "${ExtendedProperties.NAME} = ? AND ${ExtendedProperties.VALUE} = ?"
        val args = arrayOf(EVENT_TYPE_PROPERTY, BIRTHDAY_EVENT_TYPE)
        return context.query(ExtendedProperties.CONTENT_URI, BIRTHDAY_COLUMNS, selection, args) { readEventIds(it) }
    }

    /** The calendars whose events are synced to the phone, the primary one first. Requires [hasPermission]. */
    fun loadCalendars(context: Context): List<CalendarInfo> {
        val order = "${Calendars.IS_PRIMARY} DESC, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE LOCALIZED ASC"
        return context.query(Calendars.CONTENT_URI, CALENDAR_COLUMNS, "${Calendars.SYNC_EVENTS} = 1", order = order) { readCalendars(it) }
    }

    /** The rows of a query on the calendar provider, turned into items by [read]; none if the provider is missing. */
    private fun <T> Context.query(
        uri: Uri,
        columns: Array<String>,
        selection: String,
        args: Array<String>? = null,
        order: String? = null,
        read: (Cursor) -> List<T>,
    ): List<T> = contentResolver.query(uri, columns, selection, args, order)?.use(read).orEmpty()

    /**
     * The events in the rows of [cursor], with [EVENT_COLUMNS]. Events without a title are called
     * [untitled]; the events in [birthdays] are birthdays, in the color Google Calendar gives them.
     */
    fun readEvents(cursor: Cursor, untitled: String, birthdays: Set<Long> = emptySet()): List<CalendarEvent> {
        val title = cursor.getColumnIndexOrThrow(Instances.TITLE)
        val begin = cursor.getColumnIndexOrThrow(Instances.BEGIN)
        val end = cursor.getColumnIndexOrThrow(Instances.END)
        val allDay = cursor.getColumnIndexOrThrow(Instances.ALL_DAY)
        val color = cursor.getColumnIndexOrThrow(Instances.DISPLAY_COLOR)
        val calendarId = cursor.getColumnIndexOrThrow(Instances.CALENDAR_ID)
        val eventId = cursor.getColumnIndexOrThrow(Instances.EVENT_ID)
        return cursor.rows {
            val id = cursor.getLong(eventId)
            val birthday = id in birthdays
            CalendarEvent(
                title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: untitled,
                begin = cursor.getLong(begin),
                end = cursor.getLong(end),
                allDay = cursor.getInt(allDay) == 1,
                color = if (birthday) GoogleColors.BIRTHDAYS else opaque(cursor.getInt(color)),
                calendarId = cursor.getLong(calendarId),
                eventId = id,
                birthday = birthday,
            )
        }
    }

    /** The event ids in the rows of [cursor], with [BIRTHDAY_COLUMNS]. */
    fun readEventIds(cursor: Cursor): List<Long> {
        val eventId = cursor.getColumnIndexOrThrow(ExtendedProperties.EVENT_ID)
        return cursor.rows { cursor.getLong(eventId) }
    }

    /** The calendars in the rows of [cursor], with [CALENDAR_COLUMNS]. */
    fun readCalendars(cursor: Cursor): List<CalendarInfo> {
        val id = cursor.getColumnIndexOrThrow(Calendars._ID)
        val name = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
        val color = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR)
        return cursor.rows { CalendarInfo(id = cursor.getLong(id), name = cursor.getString(name) ?: "", color = opaque(cursor.getInt(color))) }
    }

    /** Reads every row of [this] with [read], which reads the current row. */
    private inline fun <T> Cursor.rows(read: () -> T): List<T> {
        val items = mutableListOf<T>()
        while (moveToNext()) items += read()
        return items
    }

    /** Provider colors may lack the alpha channel. */
    private fun opaque(color: Int) = color or 0xFF000000.toInt()
}
