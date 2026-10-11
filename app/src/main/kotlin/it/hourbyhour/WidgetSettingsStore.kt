package it.hourbyhour

import android.content.Context

/** Keeps the [WidgetSettings] of each widget on the home screen, by widget id. */
class WidgetSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    fun load(widgetId: Int) = WidgetSettings(
        preferences.getInt(transparencyKey(widgetId), WidgetSettings.DEFAULT_TRANSPARENCY).coerceIn(0, 100),
    )

    fun save(widgetId: Int, settings: WidgetSettings) {
        preferences.edit().putInt(transparencyKey(widgetId), settings.transparency).apply()
    }

    /** Forgets the settings of widgets removed from the home screen. */
    fun delete(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (id in widgetIds) editor.remove(transparencyKey(id))
        editor.apply()
    }

    private fun transparencyKey(widgetId: Int) = "transparency_$widgetId"
}
