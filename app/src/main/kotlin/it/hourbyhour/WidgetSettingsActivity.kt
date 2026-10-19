package it.hourbyhour

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Settings of one widget, opened by the launcher ("Widget settings" on Android 12+). The preview
 * uses the same RemoteViews as the widget and shows over the wallpaper, like on the home screen.
 */
class WidgetSettingsActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var services: AppServices
    private lateinit var editor: WidgetSettingsEditor
    private lateinit var preview: WidgetPreview
    private lateinit var transparencyValue: TextView
    private lateinit var dayCountValue: TextView
    private lateinit var startOnMonday: Switch
    private lateinit var customHours: Switch
    private lateinit var hoursFrom: SeekBar
    private lateinit var hoursTo: SeekBar
    private lateinit var hoursFromValue: TextView
    private lateinit var hoursToValue: TextView

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
        services = AppServices(this)
        // Recreated (rotation, dark theme): the changes not saved yet are kept.
        editor = WidgetSettingsEditor(savedInstanceState?.editedSettings() ?: services.settings.load(widgetId))
        preview = WidgetPreview(this, findViewById(R.id.preview))
        transparencyValue = findViewById(R.id.transparency_value)
        dayCountValue = findViewById(R.id.day_count_value)
        hoursFromValue = findViewById(R.id.hours_from_value)
        hoursToValue = findViewById(R.id.hours_to_value)

        findViewById<SeekBar>(R.id.transparency).apply {
            max = WidgetSettingsEditor.TRANSPARENCY_MAX
            progress = editor.transparencyProgress
            onProgress { edit { transparencyMoved(it) } }
        }
        findViewById<Switch>(R.id.system_colors).apply {
            isChecked = editor.settings.systemColors
            setOnCheckedChangeListener { _, checked -> edit { systemColorsChecked(checked) } }
        }
        findViewById<SeekBar>(R.id.day_count).apply {
            max = WidgetSettingsEditor.DAY_COUNT_MAX
            progress = editor.dayCountProgress
            onProgress { edit { dayCountMoved(it) } }
        }
        startOnMonday = findViewById<Switch>(R.id.start_on_monday).apply {
            isChecked = editor.settings.startOnMonday
            setOnCheckedChangeListener { _, checked -> edit { startOnMondayChecked(checked) } }
        }
        customHours = findViewById<Switch>(R.id.custom_hours).apply {
            isChecked = editor.settings.customHours
            setOnCheckedChangeListener { _, checked -> edit { customHoursChecked(checked) } }
        }
        hoursFrom = findViewById<SeekBar>(R.id.hours_from).apply {
            max = WidgetSettingsEditor.HOURS_FROM_MAX
            progress = editor.hoursFromProgress
            onProgress { edit { hoursFromMoved(it) } }
        }
        hoursTo = findViewById<SeekBar>(R.id.hours_to).apply {
            max = WidgetSettingsEditor.HOURS_TO_MAX
            min = WidgetSettingsEditor.HOURS_TO_MIN
            progress = editor.hoursToProgress
            onProgress { edit { hoursToMoved(it) } }
        }
        findViewById<Button>(R.id.done).setOnClickListener { save() }
        showSettings()
        preview.reload()

        val events = services.events
        thread {
            val calendars = if (events.hasPermission()) events.calendars() else emptyList()
            runOnUiThread { if (!isDestroyed) showCalendars(calendars) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::editor.isInitialized) outState.putSerializable(EDITED_SETTINGS, editor.settings)
    }

    private fun Bundle.editedSettings(): WidgetSettings? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(EDITED_SETTINGS, WidgetSettings::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(EDITED_SETTINGS) as? WidgetSettings
    }

    /** Applies [change] to the settings being edited and shows the result. */
    private fun edit(change: WidgetSettingsEditor.() -> WidgetSettingsEditor) {
        editor = editor.change()
        showSettings()
    }

    /** A check box per calendar, then one for birthdays, which Google Calendar shows as a calendar of their own. */
    private fun showCalendars(calendars: List<CalendarInfo>) {
        val container = findViewById<LinearLayout>(R.id.calendars)
        if (calendars.isEmpty()) {
            container.addView(TextView(this).apply { setText(R.string.no_calendars) })
            return
        }
        val available = calendars.map { it.id }
        for (calendar in calendars) {
            container.addView(calendarBox(calendar.name, calendar.color, editor.isChecked(calendar.id)) { checked ->
                edit { calendarChecked(calendar.id, checked, available) }
            })
        }
        container.addView(calendarBox(getString(R.string.birthdays), GoogleColors.BIRTHDAYS, editor.settings.showBirthdays) { checked ->
            edit { birthdaysChecked(checked) }
        })
    }

    /** A check box tinted with [color] as Google Calendar shows it. [onChecked] gets each new state. */
    private fun calendarBox(name: String, color: Int, checked: Boolean, onChecked: (Boolean) -> Unit) = CheckBox(this).apply {
        text = name
        isChecked = checked
        buttonTintList = ColorStateList.valueOf(GoogleColors.forDisplay(color, resources.configuration.isNightModeActive))
        setOnCheckedChangeListener { _, isChecked -> onChecked(isChecked) }
    }

    /** The activity is exported for the launcher: ignore ids that are not widgets of this app. */
    private fun isOurWidget(id: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ?: return false
        return provider == ComponentName(this, HourByHourWidget::class.java)
    }

    private fun showSettings() {
        val settings = editor.settings
        transparencyValue.text = getString(R.string.transparency_value, settings.transparency)
        dayCountValue.text = getString(R.string.day_count_value, settings.dayCount)
        startOnMonday.isEnabled = editor.startOnMondayEnabled
        hoursFromValue.text = getString(R.string.hour_value, settings.hours.start)
        hoursToValue.text = getString(R.string.hour_value, settings.hours.end)
        // Moving one bar may move the other.
        hoursFrom.progress = editor.hoursFromProgress
        hoursTo.progress = editor.hoursToProgress
        hoursFrom.isEnabled = editor.hoursEnabled
        hoursTo.isEnabled = editor.hoursEnabled
        preview.settings = settings
    }

    /** Runs [update] with each new progress. */
    private fun SeekBar.onProgress(update: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = update(progress)

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun save() {
        services.settings.save(widgetId, editor.settings)
        HourByHourWidget.redraw(applicationContext, intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private companion object {
        const val EDITED_SETTINGS = "edited_settings"
    }
}
