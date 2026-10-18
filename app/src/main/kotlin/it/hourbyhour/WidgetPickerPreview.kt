package it.hourbyhour

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The widget as the launcher's widget picker shows it, before it is added. Every launcher can show
 * the static picture in widget_preview.xml (previewLayout in widget_info.xml). From Android 15 the
 * app can also hand the picker the real widget views ("generated preview"), drawn here with
 * made-up events ([SampleAgenda]): never with the real calendar.
 */
object WidgetPickerPreview {
    private const val TAG = "HourByHour"

    /** The views of the widget with its default settings and the made-up events of [today]. Taps do nothing. */
    fun views(context: Context, today: LocalDate, zone: ZoneId): RemoteViews {
        val agenda = SampleAgenda.agenda(today, zone) { context.getString(it.stringId) }
        return WidgetUpdater.views(context, agenda, estimatedSize(minWidthDp = 0, maxHeightDp = 0), WidgetSettings(), clickable = false)
    }

    /**
     * Hands the picker a new preview when the one it has may be out of date: after an update of the
     * app, in another language or on another day (the preview shows dates). Android allows only a
     * few previews per hour, so it is not done on every redraw of the widgets.
     */
    fun publishIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val stamp = PickerPreviewStamp(
            installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime,
            language = Locale.getDefault().toLanguageTag(),
            date = today,
        )
        val store = PickerPreviewStore(context)
        if (store.isPublished(stamp)) return
        try {
            val published = AppWidgetManager.getInstance(context).setWidgetPreview(
                ComponentName(context, HourByHourWidget::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                views(context, today, zone),
            )
            // Refused when called too often: the next chance tries again.
            if (published) store.markPublished(stamp) else Log.w(TAG, "Widget picker preview not accepted")
        } catch (e: RuntimeException) {
            // The picker keeps showing previewLayout: not a reason to stop what called this.
            Log.e(TAG, "Widget picker preview failed", e)
        }
    }
}

/** What the published picker preview depends on, besides the made-up events: a change in any part needs a new one. */
data class PickerPreviewStamp(val installedAt: Long, val language: String, val date: LocalDate) {
    override fun toString() = "$installedAt|$language|$date"
}

/** Remembers which picker preview was last published. */
class PickerPreviewStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun isPublished(stamp: PickerPreviewStamp) = preferences.getString(KEY, null) == stamp.toString()

    fun markPublished(stamp: PickerPreviewStamp) {
        preferences.edit().putString(KEY, stamp.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "widget_picker_preview"
        const val KEY = "published"
    }
}
