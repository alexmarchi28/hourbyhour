package it.hourbyhour

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName

/**
 * A widget of the app that the test places itself, as the launcher does with the real ones, but
 * off the home screen. [close] removes it, which the app sees as a widget removed by the user.
 */
class TestWidget : AutoCloseable {
    private val manager = AppWidgetManager.getInstance(targetContext)
    private val host = AppWidgetHost(targetContext, HOST_ID)
    val id: Int
    private var closed = false

    init {
        // Widgets left by a test that crashed.
        host.deleteHost()
        host.startListening()
        id = host.allocateAppWidgetId()
        // Only launchers may place widgets without asking the user: the test does it as the adb shell.
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            check(manager.bindAppWidgetIdIfAllowed(id, ComponentName(targetContext, HourByHourWidget::class.java))) {
                "The test cannot place widgets"
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    /** The widget as a launcher shows it: the app updates it with new views. */
    fun createView(): AppWidgetHostView = onMain { host.createView(targetContext, id, manager.getAppWidgetInfo(id)) }

    override fun close() {
        if (closed) return
        closed = true
        host.deleteAppWidgetId(id)
        host.stopListening()
        host.deleteHost()
    }

    private companion object {
        const val HOST_ID = 0x4842
    }
}
