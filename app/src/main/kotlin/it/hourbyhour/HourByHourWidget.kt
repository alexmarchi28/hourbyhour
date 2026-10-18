package it.hourbyhour

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlin.concurrent.thread

/** The home screen widget. Taps open events and days in the calendar app (see CalendarIntents). */
class HourByHourWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // From redraw: only the widgets it names. From the quarter-hour alarm (and adb): all of them.
            ACTION_REFRESH -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                updateAsync(context) {
                    if (ids == null) WidgetUpdater.updateAll(context) else WidgetUpdater.update(context, AppWidgetManager.getInstance(context), ids)
                }
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAsync(context) { WidgetUpdater.updateAll(context) }
            // The picker preview is in the language of the phone and comes from the code of the app.
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAsync(context) {
                // Also without widgets on the home screen, when nothing else publishes it.
                WidgetPickerPreview.publishIfNeeded(context)
                WidgetUpdater.updateAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        updateAsync(context) { WidgetUpdater.update(context, manager, widgetIds) }
    }

    /** Called when the widget is resized: redraw it for the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        updateAsync(context) { WidgetUpdater.update(context, manager, intArrayOf(widgetId)) }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        AppServices(context).settings.delete(widgetIds)
    }

    override fun onDisabled(context: Context) {
        RefreshScheduler(context).cancel()
    }

    /**
     * Runs [redraw] off the main thread, where reading the calendar and drawing belong. When it drew
     * some widget, the next refresh and the calendar job are scheduled.
     */
    private fun updateAsync(context: Context, redraw: () -> Boolean) {
        val pending = goAsync()
        thread {
            try {
                if (redraw()) RefreshScheduler(context).afterRedraw()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "it.hourbyhour.action.REFRESH"

        /** Redraws every widget in the background of the widget provider, as [redraw] does. */
        fun redrawAll(context: Context) {
            context.sendBroadcast(Intent(context, HourByHourWidget::class.java).setAction(ACTION_REFRESH))
        }

        /**
         * Redraws [widgetIds] in the background of the widget provider, which keeps the app alive until
         * it is done: a thread started by an activity that is closing could die with the process.
         */
        fun redraw(context: Context, widgetIds: IntArray) {
            context.sendBroadcast(
                Intent(context, HourByHourWidget::class.java).setAction(ACTION_REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds),
            )
        }
    }
}
