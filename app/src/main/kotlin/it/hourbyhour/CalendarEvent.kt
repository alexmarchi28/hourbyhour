package it.hourbyhour

/** An event instance as read from the calendar provider. Times are epoch millis. */
data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    /** The calendar the event belongs to, as in the calendar provider. */
    val calendarId: Long = 0,
    /** The event in the calendar provider; occurrences of a recurring event share it. */
    val eventId: Long = 0,
    /**
     * A birthday of a contact, which Google Calendar puts in the primary calendar but shows as a
     * calendar of its own ("Birthdays"), with its own color.
     */
    val birthday: Boolean = false,
)

/** A calendar of the phone whose events are synced, as offered in the widget settings. */
data class CalendarInfo(val id: Long, val name: String, val color: Int)
