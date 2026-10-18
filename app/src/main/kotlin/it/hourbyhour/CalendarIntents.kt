package it.hourbyhour

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract

/**
 * Opens the calendar app at an event or a day, for the taps on the widget. Google Calendar when it
 * is installed (the phone may have other calendar apps, such as Outlook), else the default one.
 */
class CalendarIntents(private val context: Context) {
    private val calendarPackage = GOOGLE_CALENDAR.takeIf { isInstalled(it) }

    fun pendingIntent(target: ClickTarget): PendingIntent =
        PendingIntent.getActivity(context, 0, intent(target), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun intent(target: ClickTarget): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(calendarUri(target)))
        if (target is ClickTarget.Event) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.begin)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.end)
        }
        intent.setPackage(calendarPackage)
        // Pending intents are told apart by their data, not by extras, and the occurrences of a
        // recurring event share the data: without its own identifier, a tap could open another one.
        intent.identifier = target.key
        return intent
    }

    private fun isInstalled(packageName: String) = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        /** Declared in the manifest <queries>, so that the app can see it is installed. */
        const val GOOGLE_CALENDAR = "com.google.android.calendar"
    }
}

/**
 * What the calendar app opens for [target]: content://com.android.calendar/events/<id> shows an
 * event, content://com.android.calendar/time/<millis> the calendar at that moment.
 */
fun calendarUri(target: ClickTarget): String = when (target) {
    is ClickTarget.Event -> "content://${CalendarContract.AUTHORITY}/events/${target.eventId}"
    is ClickTarget.Time -> "content://${CalendarContract.AUTHORITY}/time/${target.millis}"
}
