package it.hourbyhour

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import java.util.Locale

/** Loads the events, builds the widget views and pushes them to the widgets on the home screen. */
object WidgetUpdater {
    private const val TAG = "HourByHour"

    /** The views of [content]: its days, or a message asking for calendar access. Previews are not [clickable]. */
    fun views(context: Context, content: WidgetContent, clickable: Boolean = true): RemoteViews {
        // A tap on a preview would open the calendar app and leave the preview.
        val views = WidgetViews(context, content.settings, if (clickable) CalendarIntents(context)::pendingIntent else null)
        val layout = content.layout ?: return views.message()
        return views.build(layout)
    }

    /** The views of a widget with [settings] showing [agenda] (null without calendar access), as [views] above. */
    fun views(context: Context, agenda: Agenda?, size: WidgetSize, settings: WidgetSettings, clickable: Boolean = true): RemoteViews =
        views(context, widgetContent(agenda, settings, size, context.resources.configuration.fontScale, Locale.getDefault()), clickable)

    /** Redraws every widget. Returns false when none is on the home screen. */
    fun updateAll(context: Context, services: AppServices = AppServices(context)): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return update(context, manager, manager.getAppWidgetIds(ComponentName(context, HourByHourWidget::class.java)), services)
    }

    /**
     * Redraws [widgetIds]. Returns false when there are none. What follows a redraw (the next
     * refresh, the calendar job) is up to the caller, see [RefreshScheduler].
     */
    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray, services: AppServices = AppServices(context)): Boolean {
        if (widgetIds.isEmpty()) return false
        try {
            services.widgetRefresh().refresh(
                widgetIds,
                size = { id ->
                    val options = manager.getAppWidgetOptions(id)
                    estimatedSize(
                        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                        maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                    )
                },
                fontScale = context.resources.configuration.fontScale,
                locale = Locale.getDefault(),
                draw = { id, content -> manager.updateAppWidget(id, views(context, content)) },
                onFailure = { id, e -> Log.e(TAG, "Update of widget $id failed", e) },
            )
        } catch (e: RuntimeException) {
            // The widgets keep their last content and the next refresh tries again, instead of
            // crashing the app in the background.
            Log.e(TAG, "Widget update failed", e)
        }
        return true
    }
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
