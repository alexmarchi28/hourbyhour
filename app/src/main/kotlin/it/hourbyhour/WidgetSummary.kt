package it.hourbyhour

/** One part of the short description of a widget, as the app lists the widgets on the home screen. */
sealed interface SettingSummary {
    /** How many days, and whether they are a week from Monday. */
    data class Days(val count: Int, val fromMonday: Boolean) : SettingSummary

    /** Custom hours, from [start] to [end]. */
    data class Hours(val start: Int, val end: Int) : SettingSummary

    /** Only some calendars: [count] of them. */
    data class Calendars(val count: Int) : SettingSummary

    data object NoBirthdays : SettingSummary

    data class Transparency(val percent: Int) : SettingSummary

    /** The colors of the system palette instead of the Google Calendar ones. */
    data object SystemColors : SettingSummary
}

/**
 * What sets this widget apart: its days, then whatever differs from the defaults. [availableCalendars]
 * are the ids of the calendars on the phone, so that calendars that no longer exist are not
 * counted; null when they are unknown.
 */
fun WidgetSettings.summary(availableCalendars: Set<Long>?): List<SettingSummary> = buildList {
    add(SettingSummary.Days(dayCount, startsOnMonday))
    minimumHours?.let { add(SettingSummary.Hours(it.start, it.end)) }
    calendarIds?.let { ids -> add(SettingSummary.Calendars(availableCalendars?.let { ids.count(it::contains) } ?: ids.size)) }
    if (!showBirthdays) add(SettingSummary.NoBirthdays)
    if (transparency != WidgetSettings.DEFAULT_TRANSPARENCY) add(SettingSummary.Transparency(transparency))
    if (systemColors) add(SettingSummary.SystemColors)
}

/** A widget on the home screen as the app lists it: "Widget [number]", then its [summary]. */
data class WidgetEntry(val widgetId: Int, val number: Int, val summary: List<SettingSummary>)

/**
 * The widgets with [widgetIds], numbered in the order they were added (ids only grow), with the
 * settings from [settings].
 */
fun widgetEntries(widgetIds: IntArray, settings: (Int) -> WidgetSettings, availableCalendars: Set<Long>?): List<WidgetEntry> =
    widgetIds.sorted().mapIndexed { index, id -> WidgetEntry(id, index + 1, settings(id).summary(availableCalendars)) }
