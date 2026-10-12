package it.hourbyhour

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events

/**
 * Opens the calendar app at an event or a day, for the taps on the widget. Google Calendar when it
 * is installed (the phone may have other calendar apps, such as Outlook), else the default one.
 */
class CalendarIntents(private val context: Context) {
    private val calendarPackage = GOOGLE_CALENDAR.takeIf { isInstalled(it) }

    fun pendingIntent(target: ClickTarget): PendingIntent {
        val intent = when (target) {
            is ClickTarget.Event -> Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, target.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.end)
            is ClickTarget.Time -> Intent(Intent.ACTION_VIEW, timeUri(target.millis))
        }
        intent.setPackage(calendarPackage)
        // Pending intents are told apart by their data, not by extras, and the occurrences of a
        // recurring event share the data: without its own identifier, a tap could open another one.
        intent.identifier = target.toString()
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** content://com.android.calendar/time/<millis>: shows the calendar at that moment. */
    private fun timeUri(millis: Long) = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .also { ContentUris.appendId(it, millis) }
        .build()

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
