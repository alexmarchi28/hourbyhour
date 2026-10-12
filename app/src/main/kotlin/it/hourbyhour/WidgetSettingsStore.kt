package it.hourbyhour

import android.content.Context
import android.content.SharedPreferences

/**
 * Keeps the [WidgetSettings] of each widget on the home screen, by widget id. The file and key names
 * are the saved format: renaming them loses the settings of the widgets already on the home screen.
 */
class WidgetSettingsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun load(widgetId: Int) = WidgetSettings(
        transparency = preferences.getInt(transparencyKey(widgetId), WidgetSettings.DEFAULT_TRANSPARENCY).coerceIn(0, 100),
        dayCount = preferences.getInt(dayCountKey(widgetId), WidgetSettings.DEFAULT_DAY_COUNT)
            .coerceIn(1, WidgetSettings.MAX_DAY_COUNT),
        startOnMonday = preferences.getBoolean(startOnMondayKey(widgetId), false),
        // No saved set means all calendars. The set from preferences must not be modified: copy it.
        calendarIds = preferences.getStringSet(calendarsKey(widgetId), null)?.mapNotNull { it.toLongOrNull() }?.toSet(),
    )

    fun save(widgetId: Int, settings: WidgetSettings) {
        val editor = preferences.edit()
            .putInt(transparencyKey(widgetId), settings.transparency)
            .putInt(dayCountKey(widgetId), settings.dayCount)
            .putBoolean(startOnMondayKey(widgetId), settings.startOnMonday)
        val calendarIds = settings.calendarIds
        if (calendarIds == null) {
            editor.remove(calendarsKey(widgetId))
        } else {
            editor.putStringSet(calendarsKey(widgetId), calendarIds.map { it.toString() }.toSet())
        }
        editor.apply()
    }

    /** Forgets the settings of widgets removed from the home screen. */
    fun delete(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (id in widgetIds) {
            editor.remove(transparencyKey(id)).remove(dayCountKey(id)).remove(startOnMondayKey(id)).remove(calendarsKey(id))
        }
        editor.apply()
    }

    private fun transparencyKey(widgetId: Int) = "transparency_$widgetId"
    private fun dayCountKey(widgetId: Int) = "days_$widgetId"
    private fun startOnMondayKey(widgetId: Int) = "from_monday_$widgetId"
    private fun calendarsKey(widgetId: Int) = "calendars_$widgetId"

    companion object {
        const val FILE_NAME = "widget_settings"
    }
}
