package it.hourbyhour

import android.content.Context
import android.content.SharedPreferences

/**
 * Keeps the [WidgetSettings] of each widget on the home screen, by widget id. The file and key names
 * are the saved format: renaming them loses the settings of the widgets already on the home screen.
 */
class WidgetSettingsStore(private val preferences: SharedPreferences) : SettingsRepository {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun load(widgetId: Int) = WidgetSettings(
        transparency = preferences.getInt(transparencyKey(widgetId), WidgetSettings.DEFAULT_TRANSPARENCY)
            .coerceIn(WidgetSettings.TRANSPARENCY_RANGE),
        dayCount = preferences.getInt(dayCountKey(widgetId), WidgetSettings.DEFAULT_DAY_COUNT).coerceIn(WidgetSettings.DAY_COUNT_RANGE),
        startOnMonday = preferences.getBoolean(startOnMondayKey(widgetId), false),
        // No saved set means all calendars. The set from preferences must not be modified: copy it.
        calendarIds = preferences.getStringSet(calendarsKey(widgetId), null)?.mapNotNull { it.toLongOrNull() }?.toSet(),
        // Widgets saved before birthdays could be chosen show them, like every calendar at first.
        showBirthdays = preferences.getBoolean(birthdaysKey(widgetId), true),
        // Widgets saved before hours could be chosen fit them to the events.
        customHours = preferences.getBoolean(customHoursKey(widgetId), false),
        hours = loadHours(widgetId),
    )

    /** The saved hours brought back within a day, with the end after the start as when choosing them. */
    private fun loadHours(widgetId: Int): HourRange {
        val day = WidgetSettings.HOURS_RANGE
        val start = preferences.getInt(hoursFromKey(widgetId), WidgetSettings.DEFAULT_HOURS.start).coerceIn(day.first, day.last - 1)
        val end = preferences.getInt(hoursToKey(widgetId), WidgetSettings.DEFAULT_HOURS.end).coerceIn(day.first + 1, day.last)
        return HourRange(start, end).startingAt(start)
    }

    override fun save(widgetId: Int, settings: WidgetSettings) {
        val editor = preferences.edit()
            .putInt(transparencyKey(widgetId), settings.transparency)
            .putInt(dayCountKey(widgetId), settings.dayCount)
            .putBoolean(startOnMondayKey(widgetId), settings.startOnMonday)
            .putBoolean(birthdaysKey(widgetId), settings.showBirthdays)
            .putBoolean(customHoursKey(widgetId), settings.customHours)
            .putInt(hoursFromKey(widgetId), settings.hours.start)
            .putInt(hoursToKey(widgetId), settings.hours.end)
        val calendarIds = settings.calendarIds
        if (calendarIds == null) {
            editor.remove(calendarsKey(widgetId))
        } else {
            editor.putStringSet(calendarsKey(widgetId), calendarIds.map { it.toString() }.toSet())
        }
        editor.apply()
    }

    /**
     * Forgets the settings of widgets removed from the home screen: every key ending with `_<id>`,
     * including keys this version no longer writes.
     */
    override fun delete(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (key in preferences.all.keys) {
            val id = key.substringAfterLast('_').toIntOrNull() ?: continue
            if (id in widgetIds) editor.remove(key)
        }
        editor.apply()
    }

    private fun transparencyKey(widgetId: Int) = "transparency_$widgetId"
    private fun dayCountKey(widgetId: Int) = "days_$widgetId"
    private fun startOnMondayKey(widgetId: Int) = "from_monday_$widgetId"
    private fun calendarsKey(widgetId: Int) = "calendars_$widgetId"
    private fun birthdaysKey(widgetId: Int) = "birthdays_$widgetId"
    private fun customHoursKey(widgetId: Int) = "custom_hours_$widgetId"
    private fun hoursFromKey(widgetId: Int) = "hours_from_$widgetId"
    private fun hoursToKey(widgetId: Int) = "hours_to_$widgetId"

    companion object {
        const val FILE_NAME = "widget_settings"
    }
}
