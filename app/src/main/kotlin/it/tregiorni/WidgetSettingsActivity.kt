package it.tregiorni

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
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
    private lateinit var preview: FrameLayout
    private lateinit var transparencyValue: TextView
    private var snapshot: Snapshot? = null
    private var snapshotLoaded = false

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
        preview = findViewById(R.id.preview)
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

        val context = applicationContext
        thread {
            val loaded = WidgetUpdater.loadSnapshot(context)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                snapshot = loaded
                snapshotLoaded = true
                renderPreview()
            }
        }
    }

    /** The activity is exported for the launcher: ignore ids that are not widgets of this app. */
    private fun isOurWidget(id: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ?: return false
        return provider == ComponentName(this, ThreeDaysWidget::class.java)
    }

    private fun showSettings() {
        transparencyValue.text = getString(R.string.transparency_value, settings.transparency)
        renderPreview()
    }

    private fun renderPreview() {
        if (!snapshotLoaded) return
        val heightDp = preview.layoutParams.height / resources.displayMetrics.density
        preview.removeAllViews()
        preview.addView(WidgetUpdater.views(applicationContext, snapshot, heightDp, settings).apply(this, preview))
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
