package it.hourbyhour

import android.app.Activity
import android.widget.FrameLayout
import kotlin.concurrent.thread

/**
 * Shows the widget inside [container] of [activity], built from the same RemoteViews the launcher
 * gets. The calendar is read off the main thread; the preview appears once it has been read.
 */
class WidgetPreview(private val activity: Activity, private val container: FrameLayout) {
    private val context = activity.applicationContext
    private val services = AppServices(context)
    private var calendar: CalendarState = CalendarState.Loading

    /** The reads started: only the last one is shown, even if an earlier one finishes after it. */
    private var reads = 0

    /** Changing the settings redraws the preview with them. */
    var settings = WidgetSettings()
        set(value) {
            field = value
            render()
        }

    /** Reads the calendar again and redraws. Call it on the main thread. */
    fun reload() {
        val read = ++reads
        thread {
            val agenda = services.widgetRefresh().agenda()
            activity.runOnUiThread {
                if (activity.isDestroyed || read != reads) return@runOnUiThread
                calendar = if (agenda == null) CalendarState.NeedsPermission else CalendarState.Loaded(agenda)
                render()
            }
        }
    }

    private fun render() {
        val agenda = when (val state = calendar) {
            CalendarState.Loading -> return
            CalendarState.NeedsPermission -> null
            is CalendarState.Loaded -> state.agenda
        }
        val metrics = activity.resources.displayMetrics
        val widthPx = container.width.takeIf { it > 0 } ?: metrics.widthPixels
        val size = WidgetSize(widthPx / metrics.density, container.layoutParams.height / metrics.density)
        container.removeAllViews()
        container.addView(WidgetUpdater.previewViews(context, agenda, size, settings).apply(activity, container))
    }

    /** What the preview knows of the calendar. */
    private sealed interface CalendarState {
        /** Not read yet: the preview stays empty. */
        data object Loading : CalendarState

        /** Without calendar access the preview shows the same message as the widget. */
        data object NeedsPermission : CalendarState

        data class Loaded(val agenda: Agenda) : CalendarState
    }
}
