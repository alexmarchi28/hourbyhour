package it.hourbyhour

import android.content.Context
import java.time.Clock

/**
 * The real implementations of what the app depends on, built from [context]: the one place that
 * chooses them. The widget provider, the job and the screens take them from here; the tests use fakes.
 */
class AppServices(context: Context) {
    private val appContext = context.applicationContext

    val events: EventSource = PhoneCalendar(appContext)
    val settings: SettingsRepository = WidgetSettingsStore(appContext)

    /** The system clock, in the time zone of the phone at the time of the call. */
    val clock: Clock get() = Clock.systemDefaultZone()

    fun widgetRefresh(clock: Clock = this.clock) = WidgetRefresh(events, settings, clock)
}
