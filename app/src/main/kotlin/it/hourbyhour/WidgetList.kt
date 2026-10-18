package it.hourbyhour

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * The widgets on the home screen, listed in [activity]: a tap on one opens its settings. Some
 * launchers hide the way to reopen them (on Motorola, the gear shows only while resizing) and some
 * have none. [section] is hidden while there are no widgets; [list] holds one entry per widget.
 */
class WidgetList(private val activity: Activity, private val section: View, private val list: ViewGroup) {
    private val context = activity.applicationContext
    private val services = AppServices(context)

    /** Reads the widgets and the calendars again, off the main thread, and shows them. */
    fun reload() {
        thread {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, HourByHourWidget::class.java))
            val events = services.events
            val calendars = if (events.hasPermission()) events.calendars().map { it.id }.toSet() else null
            val entries = widgetEntries(ids, services.settings::load, calendars)
            activity.runOnUiThread { if (!activity.isDestroyed) show(entries) }
        }
    }

    private fun show(entries: List<WidgetEntry>) {
        section.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        list.removeAllViews()
        for (entry in entries) {
            val view = activity.layoutInflater.inflate(R.layout.widget_entry, list, false)
            view.tag = entry.widgetId
            view.findViewById<TextView>(R.id.widget_name).text = activity.getString(R.string.widget_number, entry.number)
            view.findViewById<TextView>(R.id.widget_summary).text = describe(activity.resources, entry.summary)
            view.setOnClickListener {
                activity.startActivity(
                    Intent(activity, WidgetSettingsActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, entry.widgetId),
                )
            }
            list.addView(view)
        }
    }
}

/** The [summary] of a widget in words, e.g. "7 days from Monday · 2 calendars". */
fun describe(resources: Resources, summary: List<SettingSummary>): String = summary.joinToString(resources.getString(R.string.summary_separator)) {
    when (it) {
        is SettingSummary.Days -> if (it.fromMonday) {
            resources.getQuantityString(R.plurals.summary_days_from_monday, it.count, it.count)
        } else {
            resources.getQuantityString(R.plurals.summary_days, it.count, it.count)
        }
        is SettingSummary.Hours -> resources.getString(R.string.summary_hours, it.start, it.end)
        is SettingSummary.Calendars -> if (it.count == 0) {
            resources.getString(R.string.summary_no_calendars)
        } else {
            resources.getQuantityString(R.plurals.summary_calendars, it.count, it.count)
        }
        SettingSummary.NoBirthdays -> resources.getString(R.string.summary_no_birthdays)
        is SettingSummary.Transparency -> resources.getString(R.string.summary_transparency, it.percent)
    }
}
