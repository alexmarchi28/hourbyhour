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
    private var agenda: Agenda? = null
    private var loaded = false

    /** Changing the settings redraws the preview with them. */
    var settings = WidgetSettings()
        set(value) {
            field = value
            render()
        }

    /** Reads the calendar again and redraws. [alsoInBackground] runs on the same background thread. */
    fun reload(alsoInBackground: () -> Unit = {}) {
        thread {
            val read = services.widgetRefresh().agenda()
            alsoInBackground()
            activity.runOnUiThread {
                if (activity.isDestroyed) return@runOnUiThread
                agenda = read
                loaded = true
                render()
            }
        }
    }

    private fun render() {
        if (!loaded) return
        val metrics = activity.resources.displayMetrics
        val widthPx = container.width.takeIf { it > 0 } ?: metrics.widthPixels
        val size = WidgetSize(widthPx / metrics.density, container.layoutParams.height / metrics.density)
        container.removeAllViews()
        container.addView(WidgetUpdater.views(context, agenda, size, settings, clickable = false).apply(activity, container))
    }
}
