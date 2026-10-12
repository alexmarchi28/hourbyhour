package it.hourbyhour

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances

/**
 * Reads events from the calendar provider of the phone, which Google Calendar keeps in sync.
 * Using [Instances] instead of [Events] gives one row per occurrence of recurring events.
 */
object CalendarSource {
    private val PROJECTION = arrayOf(
        Instances.TITLE,
        Instances.BEGIN,
        Instances.END,
        Instances.ALL_DAY,
        Instances.DISPLAY_COLOR,
        Instances.CALENDAR_ID,
    )

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
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, PROJECTION, SELECTION, null, "${Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: untitled,
                    begin = cursor.getLong(1),
                    end = cursor.getLong(2),
                    allDay = cursor.getInt(3) == 1,
                    // Provider colors may lack the alpha channel.
                    color = cursor.getInt(4) or 0xFF000000.toInt(),
                    calendarId = cursor.getLong(5),
                )
            }
        }
        return events
    }

    /** The calendars whose events are synced to the phone, the primary one first. Requires [hasPermission]. */
    fun loadCalendars(context: Context): List<CalendarInfo> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR)
        val order = "${Calendars.IS_PRIMARY} DESC, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE LOCALIZED ASC"
        val calendars = mutableListOf<CalendarInfo>()
        context.contentResolver.query(Calendars.CONTENT_URI, projection, "${Calendars.SYNC_EVENTS} = 1", null, order)?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += CalendarInfo(
                    id = cursor.getLong(0),
                    name = cursor.getString(1) ?: "",
                    color = cursor.getInt(2) or 0xFF000000.toInt(),
                )
            }
        }
        return calendars
    }
}
