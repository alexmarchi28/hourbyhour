package it.hourbyhour

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Settings of one widget, opened by the launcher ("Widget settings" on Android 12+). The preview
 * uses the same RemoteViews as the widget and shows over the wallpaper, like on the home screen.
 */
class WidgetSettingsActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: WidgetSettingsStore
    private lateinit var settings: WidgetSettings
    private lateinit var preview: WidgetPreview
    private lateinit var transparencyValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Leaving without "Done" keeps the previous settings.
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!isOurWidget(widgetId)) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_settings)
        store = WidgetSettingsStore(this)
        settings = store.load(widgetId)
        preview = WidgetPreview(this, findViewById(R.id.preview))
        transparencyValue = findViewById(R.id.transparency_value)

        findViewById<SeekBar>(R.id.transparency).apply {
            max = 100 / WidgetSettings.TRANSPARENCY_STEP
            progress = settings.transparency / WidgetSettings.TRANSPARENCY_STEP
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    settings = settings.copy(transparency = progress * WidgetSettings.TRANSPARENCY_STEP)
                    showSettings()
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        findViewById<Button>(R.id.done).setOnClickListener { save() }
        showSettings()
        preview.reload()
    }

    /** The activity is exported for the launcher: ignore ids that are not widgets of this app. */
    private fun isOurWidget(id: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ?: return false
        return provider == ComponentName(this, HourByHourWidget::class.java)
    }

    private fun showSettings() {
        transparencyValue.text = getString(R.string.transparency_value, settings.transparency)
        preview.settings = settings
    }

    private fun save() {
        store.save(widgetId, settings)
        val context = applicationContext
        val id = widgetId
        thread { WidgetUpdater.update(context, AppWidgetManager.getInstance(context), intArrayOf(id)) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
