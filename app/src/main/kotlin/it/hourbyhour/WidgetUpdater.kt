package it.hourbyhour

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Loads the events, builds the widget views and pushes them to the widgets on the home screen. */
object WidgetUpdater {
    private const val TAG = "HourByHour"

    /** The events of the current and next week, or null when calendar access has not been granted. */
    fun loadAgenda(context: Context): Agenda? {
        if (!CalendarSource.hasPermission(context)) return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val (begin, end) = queryWindow(Agenda.firstDay(now.toLocalDate()), Agenda.DAYS, zone)
        return Agenda(CalendarSource.load(context, begin, end), now)
    }

    /** The widget content, or a message asking for calendar access when [agenda] is null. Previews are not [clickable]. */
    fun views(context: Context, agenda: Agenda?, size: WidgetSize, settings: WidgetSettings, clickable: Boolean = true): RemoteViews {
        // A tap on a preview would open the calendar app and leave the preview.
        val views = WidgetViews(context, settings, if (clickable) CalendarIntents(context)::pendingIntent else null)
        if (agenda == null) return views.message()
        val fontScale = context.resources.configuration.fontScale
        return views.build(layoutWidget(agenda.snapshot(settings), size, fontScale, Locale.getDefault()))
    }

    /** Redraws every widget. Returns false when none is on the home screen. */
    fun updateAll(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, HourByHourWidget::class.java))
        update(context, manager, ids)
        return ids.isNotEmpty()
    }

    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        try {
            val agenda = loadAgenda(context)
            val store = WidgetSettingsStore(context)
            for (id in widgetIds) {
                val options = manager.getAppWidgetOptions(id)
                val size = estimatedSize(
                    minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                )
                manager.updateAppWidget(id, views(context, agenda, size, store.load(id)))
            }
        } catch (e: RuntimeException) {
            // The widgets keep their last content and the next refresh tries again, instead of
            // crashing the app in the background.
            Log.e(TAG, "Widget update failed", e)
        } finally {
            scheduleRefresh(context)
            CalendarChangeJob.ensureScheduled(context)
        }
    }

    fun cancelRefresh(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    /**
     * Redraws at the next quarter hour, so the current-time line moves and the days roll over at
     * midnight. RTC (not RTC_WAKEUP) never wakes the phone: a sleeping phone redraws when it wakes up.
     */
    private fun scheduleRefresh(context: Context) {
        val next = nextRefreshTime(System.currentTimeMillis())
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HourByHourWidget::class.java).setAction(HourByHourWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * The widget size in portrait as reported by the launcher: in portrait a widget is as wide as its
 * minimum width and as tall as its maximum height. A launcher that has not reported them yet gives 0.
 * Some launchers get it wrong (the Motorola one reports about 3/4 of the height), so it is only used
 * for estimates: the layout itself adapts to the real size.
 */
fun estimatedSize(minWidthDp: Int, maxHeightDp: Int) = WidgetSize(
    widthDp = minWidthDp.takeIf { it > 0 }?.toFloat() ?: DEFAULT_WIDTH_DP,
    heightDp = maxHeightDp.takeIf { it > 0 }?.toFloat() ?: DEFAULT_HEIGHT_DP,
)

// The minimum size in widget_info.xml.
private const val DEFAULT_WIDTH_DP = 250f
private const val DEFAULT_HEIGHT_DP = 180f

private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L

/**
 * The next quarter hour after [nowMillis], plus a few seconds so the clock is surely past it.
 * Quarter hours of epoch time are also local quarter hours (and midnight) in every time zone whose
 * offset is a multiple of 15 minutes, which covers all current ones.
 */
fun nextRefreshTime(nowMillis: Long): Long = (nowMillis / REFRESH_INTERVAL_MILLIS + 1) * REFRESH_INTERVAL_MILLIS + 5_000
