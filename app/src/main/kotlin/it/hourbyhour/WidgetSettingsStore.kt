package it.hourbyhour

import android.content.Context

/** Keeps the [WidgetSettings] of each widget on the home screen, by widget id. */
class WidgetSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    fun load(widgetId: Int) = WidgetSettings(
        transparency = preferences.getInt(transparencyKey(widgetId), WidgetSettings.DEFAULT_TRANSPARENCY).coerceIn(0, 100),
        dayCount = preferences.getInt(dayCountKey(widgetId), WidgetSettings.DEFAULT_DAY_COUNT)
            .coerceIn(1, WidgetSettings.MAX_DAY_COUNT),
        startOnMonday = preferences.getBoolean(startOnMondayKey(widgetId), false),
    )

    fun save(widgetId: Int, settings: WidgetSettings) {
        preferences.edit()
            .putInt(transparencyKey(widgetId), settings.transparency)
            .putInt(dayCountKey(widgetId), settings.dayCount)
            .putBoolean(startOnMondayKey(widgetId), settings.startOnMonday)
            .apply()
    }

    /** Forgets the settings of widgets removed from the home screen. */
    fun delete(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (id in widgetIds) {
            editor.remove(transparencyKey(id)).remove(dayCountKey(id)).remove(startOnMondayKey(id))
        }
        editor.apply()
    }

    private fun transparencyKey(widgetId: Int) = "transparency_$widgetId"
    private fun dayCountKey(widgetId: Int) = "days_$widgetId"
    private fun startOnMondayKey(widgetId: Int) = "from_monday_$widgetId"
}
