package it.tregiorni

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Loads the events, builds the widget views and pushes them to the widgets on the home screen. */
object WidgetUpdater {
    const val DAY_COUNT = 3

    private const val DEFAULT_HEIGHT_DP = 180f
    private const val TAG = "TreGiorni"

    /** The events of the coming days, or null when calendar access has not been granted. */
    fun loadSnapshot(context: Context): Snapshot? {
        if (!CalendarSource.hasPermission(context)) return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val (begin, end) = queryWindow(today, DAY_COUNT, zone)
        val events = CalendarSource.load(context, begin, end)
        return Snapshot(splitIntoDays(events, today, DAY_COUNT, zone), now)
    }

    /** The widget content, or a message asking for calendar access when [snapshot] is null. */
    fun views(context: Context, snapshot: Snapshot?, heightDp: Float, settings: WidgetSettings): RemoteViews {
        val views = WidgetViews(context, settings)
        if (snapshot == null) return views.message()
        val fontScale = context.resources.configuration.fontScale
        return views.build(layoutWidget(snapshot, heightDp, fontScale, Locale.getDefault()))
    }

    /** Redraws every widget. Returns false when none is on the home screen. */
    fun updateAll(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ThreeDaysWidget::class.java))
        update(context, manager, ids)
        return ids.isNotEmpty()
    }

    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        try {
            val snapshot = loadSnapshot(context)
            val store = WidgetSettingsStore(context)
            for (id in widgetIds) {
                val heightDp = estimatedHeightDp(manager.getAppWidgetOptions(id))
                manager.updateAppWidget(id, views(context, snapshot, heightDp, store.load(id)))
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
     * The widget height in portrait as reported by the launcher. Some launchers get it wrong (the
     * Motorola one reports about 3/4 of it), so it is only used for estimates: the layout itself
     * adapts to the real size.
     */
    private fun estimatedHeightDp(options: Bundle): Float =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: DEFAULT_HEIGHT_DP

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
        Intent(context, ThreeDaysWidget::class.java).setAction(ThreeDaysWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L

/**
 * The next quarter hour after [nowMillis], plus a few seconds so the clock is surely past it.
 * Quarter hours of epoch time are also local quarter hours (and midnight) in every time zone whose
 * offset is a multiple of 15 minutes, which covers all current ones.
 */
fun nextRefreshTime(nowMillis: Long): Long = (nowMillis / REFRESH_INTERVAL_MILLIS + 1) * REFRESH_INTERVAL_MILLIS + 5_000
