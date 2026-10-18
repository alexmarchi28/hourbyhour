package it.hourbyhour

import java.time.Clock
import java.util.Locale

/** Where the events come from: the calendar of the phone ([PhoneCalendar]), or a fake in the tests. */
interface EventSource {
    fun hasPermission(): Boolean

    /** Event instances overlapping [begin, end), in epoch millis. Requires [hasPermission]. */
    fun events(begin: Long, end: Long): List<CalendarEvent>

    /** The calendars whose events are synced to the phone, as offered in the settings. Requires [hasPermission]. */
    fun calendars(): List<CalendarInfo>
}

/** Where the settings of each widget are kept ([WidgetSettingsStore], or a fake in the tests). */
interface SettingsRepository {
    fun load(widgetId: Int): WidgetSettings

    fun save(widgetId: Int, settings: WidgetSettings)

    fun delete(widgetIds: IntArray)
}

/** What one widget shows with its [settings]: the [layout] of its days, or a request for calendar access when null. */
data class WidgetContent(val settings: WidgetSettings, val layout: WidgetLayout?)

/** What a widget with [settings] shows from [agenda] (null without calendar access). */
fun widgetContent(agenda: Agenda?, settings: WidgetSettings, size: WidgetSize, fontScale: Float, locale: Locale) =
    WidgetContent(settings, agenda?.let { layoutWidget(it.snapshot(settings), size, fontScale, locale) })

/** Decides what every widget shows, without Android: the shell only measures the widgets and draws the result. */
class WidgetRefresh(private val events: EventSource, private val settings: SettingsRepository, private val clock: Clock) {
    /** The events of the current and next week at the time of the clock, or null without calendar access. */
    fun agenda(): Agenda? = if (events.hasPermission()) Agenda.read(clock, events::events) else null

    /**
     * Hands [draw] the content of each of [widgetIds], from a single read of the calendar. A widget
     * that fails goes to [onFailure] and does not stop the others. When the calendar cannot be read the
     * error reaches the caller and no widget is drawn, so they all keep their last content.
     */
    fun refresh(
        widgetIds: IntArray,
        size: (Int) -> WidgetSize,
        fontScale: Float,
        locale: Locale,
        draw: (Int, WidgetContent) -> Unit,
        onFailure: (Int, RuntimeException) -> Unit,
    ) {
        if (widgetIds.isEmpty()) return
        val agenda = agenda()
        drawEach(widgetIds, draw = { id -> draw(id, widgetContent(agenda, settings.load(id), size(id), fontScale, locale)) }, onFailure)
    }
}

/**
 * Runs [draw] for each of [widgetIds]. A widget that fails keeps its last content and does not stop
 * the others: [onFailure] gets its id and the error, and the next refresh tries again.
 */
fun drawEach(widgetIds: IntArray, draw: (Int) -> Unit, onFailure: (Int, RuntimeException) -> Unit) {
    for (id in widgetIds) {
        try {
            draw(id)
        } catch (e: RuntimeException) {
            onFailure(id, e)
        }
    }
}
