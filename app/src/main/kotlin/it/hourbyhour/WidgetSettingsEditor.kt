package it.hourbyhour

/**
 * The settings screen of one widget without Android: where each control stands for [settings] and
 * what each change does to them. WidgetSettingsActivity only translates, as WidgetViews does.
 */
data class WidgetSettingsEditor(val settings: WidgetSettings) {
    val transparencyProgress: Int get() = settings.transparency / WidgetSettings.TRANSPARENCY_STEP
    val dayCountProgress: Int get() = settings.dayCount - WidgetSettings.DAY_COUNT_RANGE.first
    val hoursFromProgress: Int get() = settings.hours.start
    val hoursToProgress: Int get() = settings.hours.end

    /** The choice is kept below five days, but has no effect there. */
    val startOnMondayEnabled: Boolean get() = settings.canStartOnMonday

    /** The chosen hours are kept while custom hours are off. */
    val hoursEnabled: Boolean get() = settings.customHours

    fun isChecked(calendarId: Long): Boolean = settings.showsCalendar(calendarId)

    fun transparencyMoved(progress: Int) = edit { copy(transparency = progress * WidgetSettings.TRANSPARENCY_STEP) }

    fun dayCountMoved(progress: Int) = edit { copy(dayCount = progress + WidgetSettings.DAY_COUNT_RANGE.first) }

    fun startOnMondayChecked(checked: Boolean) = edit { copy(startOnMonday = checked) }

    fun customHoursChecked(checked: Boolean) = edit { copy(customHours = checked) }

    /** Each bar keeps the other one on its side: the hours span at least one hour. */
    fun hoursFromMoved(hour: Int) = edit { copy(hours = hours.startingAt(hour)) }

    fun hoursToMoved(hour: Int) = edit { copy(hours = hours.endingAt(hour)) }

    /** [calendarId] checked or not among the [available] calendars; checking them all means all calendars, also later ones. */
    fun calendarChecked(calendarId: Long, checked: Boolean, available: Collection<Long>): WidgetSettingsEditor {
        val shown = available.filter { isChecked(it) }.toSet()
        return edit { copy(calendarIds = WidgetSettings.calendarSelection(if (checked) shown + calendarId else shown - calendarId, available)) }
    }

    fun birthdaysChecked(checked: Boolean) = edit { copy(showBirthdays = checked) }

    private fun edit(change: WidgetSettings.() -> WidgetSettings) = WidgetSettingsEditor(settings.change())

    companion object {
        val TRANSPARENCY_MAX = WidgetSettings.TRANSPARENCY_RANGE.last / WidgetSettings.TRANSPARENCY_STEP
        val DAY_COUNT_MAX = WidgetSettings.DAY_COUNT_RANGE.last - WidgetSettings.DAY_COUNT_RANGE.first
        val HOURS_FROM_MAX = WidgetSettings.HOURS_RANGE.last - 1
        val HOURS_TO_MIN = WidgetSettings.HOURS_RANGE.first + 1
        val HOURS_TO_MAX = WidgetSettings.HOURS_RANGE.last
    }
}
