package it.tregiorni

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Loads the events, draws them and pushes the bitmap to the widgets on the home screen. */
object WidgetUpdater {
    const val DAY_COUNT = 3

    private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L
    private const val DEFAULT_WIDTH_DP = 250
    private const val DEFAULT_HEIGHT_DP = 180

    /** The events of the coming days, or null when calendar access has not been granted. */
    fun loadSnapshot(context: Context): Snapshot? {
        if (!CalendarSource.hasPermission(context)) return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        // One extra day on each side: all-day events are stored at UTC midnights and can start or
        // end outside the local window. splitIntoDays keeps only what falls on the shown days.
        val begin = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(DAY_COUNT + 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarSource.load(context, begin, end)
        return Snapshot(splitIntoDays(events, today, DAY_COUNT, zone), now)
    }

    fun render(context: Context, width: Int, height: Int, snapshot: Snapshot?): Bitmap {
        val renderer = WidgetRenderer(context)
        return if (snapshot == null) {
            renderer.renderMessage(width, height, context.getString(R.string.widget_no_permission))
        } else {
            renderer.render(width, height, snapshot)
        }
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
        val snapshot = loadSnapshot(context)
        for (id in widgetIds) {
            val (width, height) = sizeInPixels(context, manager.getAppWidgetOptions(id))
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setImageViewBitmap(R.id.widget_image, render(context, width, height, snapshot))
            manager.updateAppWidget(id, views)
        }
        scheduleRefresh(context)
        CalendarChangeJob.ensureScheduled(context)
    }

    fun cancelRefresh(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    /**
     * The launcher reports a range of sizes: in portrait the widget is as wide as the minimum width
     * and as tall as the maximum height.
     */
    private fun sizeInPixels(context: Context, options: Bundle): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        return (widthDp * density).roundToInt() to (heightDp * density).roundToInt()
    }

    /**
     * Redraws at the next quarter hour, so the current-time line moves and the days roll over at
     * midnight. RTC (not RTC_WAKEUP) never wakes the phone: a sleeping phone redraws when it wakes up.
     */
    private fun scheduleRefresh(context: Context) {
        val now = System.currentTimeMillis()
        val next = (now / REFRESH_INTERVAL_MILLIS + 1) * REFRESH_INTERVAL_MILLIS + 5_000
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ThreeDaysWidget::class.java).setAction(ThreeDaysWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
