package it.tregiorni

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.widget.RemoteViews

/**
 * Turns a [WidgetLayout] into RemoteViews. The layouts are placed by weights (see event.xml), so
 * they fill the real size of the widget whatever size the launcher reports. Static colors come from
 * resources and follow the system theme. Event colors are set for both themes, so the launcher can
 * switch them as soon as the theme changes.
 *
 * All decisions about what to show and where belong in [layoutWidget], which is unit tested.
 */
class WidgetViews(private val context: Context) {
    private val packageName = context.packageName
    private val lightBackground = themedColor(R.color.widget_background, night = false)
    private val darkBackground = themedColor(R.color.widget_background, night = true)

    fun build(layout: WidgetLayout): RemoteViews {
        val root = RemoteViews(packageName, R.layout.widget)
        // The launcher may apply these views on top of the previous ones instead of inflating the
        // layout again: without this, every update would add another copy of the content.
        for (container in intArrayOf(R.id.headers, R.id.banners, R.id.hours, R.id.columns)) {
            root.removeAllViews(container)
        }

        for (header in layout.headers) {
            val views = RemoteViews(packageName, if (header.today) R.layout.day_header_today else R.layout.day_header)
            views.setTextViewText(R.id.day_header, header.label)
            root.addView(R.id.headers, views)
        }

        for (row in layout.bannerRows) {
            val rowViews = RemoteViews(packageName, R.layout.banner_row)
            for (cell in row) rowViews.addView(R.id.banner_row, bannerCell(cell))
            root.addView(R.id.banners, rowViews)
        }

        for (label in layout.hourLabels) {
            val row = RemoteViews(packageName, R.layout.hour_row)
            row.setTextViewText(R.id.hour_label, label)
            root.addView(R.id.hours, row)
        }

        for (column in layout.columns) {
            val columnViews = RemoteViews(packageName, R.layout.day_column)
            for (box in column.events) columnViews.addView(R.id.day_column, event(box))
            column.nowLine?.let { columnViews.addView(R.id.day_column, nowLine(it)) }
            root.addView(R.id.columns, columnViews)
        }
        return root
    }

    private fun bannerCell(cell: BannerCell): RemoteViews = when (cell) {
        is BannerCell.Event -> RemoteViews(packageName, R.layout.banner_chip).apply {
            setTextViewText(R.id.banner_chip, cell.event.title)
            setEventColors(R.id.banner_chip, cell.event.color, past = false)
        }
        is BannerCell.More -> RemoteViews(packageName, R.layout.banner_more).apply {
            setTextViewText(R.id.banner_more, "+${cell.count}")
        }
        BannerCell.Empty -> RemoteViews(packageName, R.layout.banner_empty)
    }

    private fun event(box: EventBox) = RemoteViews(packageName, R.layout.event).apply {
        setWeightSums(R.id.event_v1, R.id.event_v2, box.top, box.bottom)
        setWeightSums(R.id.event_h1, R.id.event_h2, box.left, box.right)
        setTextViewText(R.id.event_text, box.event.title)
        setEventColors(R.id.event_text, box.event.color, box.past)
        setInt(R.id.event_text, "setMaxLines", box.maxLines)
    }

    private fun nowLine(top: Float) = RemoteViews(packageName, R.layout.now_line).apply {
        setFloat(R.id.now_position, "setWeightSum", 1f / (1f - top.coerceAtMost(MAX_FRACTION)))
    }

    /** One color per theme: RemoteViews applies the one matching the launcher theme. */
    private fun RemoteViews.setEventColors(viewId: Int, providerColor: Int, past: Boolean) {
        val colors = eventColors(providerColor, past, lightBackground, darkBackground)
        setColorStateList(
            viewId,
            "setBackgroundTintList",
            ColorStateList.valueOf(colors.lightFill),
            ColorStateList.valueOf(colors.darkFill),
        )
        setColorInt(viewId, "setTextColor", colors.lightText, colors.darkText)
    }

    /**
     * Makes the content of [outer] span the fraction [from, to] of it: [outer] keeps its single child
     * on the far side and gives it 1 - from, then [inner] gives its child the share for [from, to].
     */
    private fun RemoteViews.setWeightSums(outer: Int, inner: Int, from: Float, to: Float) {
        val start = from.coerceIn(0f, MAX_FRACTION)
        val end = to.coerceIn(start + MIN_FRACTION, 1f)
        setFloat(outer, "setWeightSum", 1f / (1f - start))
        setFloat(inner, "setWeightSum", (1f - start) / (end - start))
    }

    /** A color resource as it is in the light or dark theme, whatever the current theme is. */
    private fun themedColor(id: Int, night: Boolean): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(configuration).getColor(id)
    }

    private companion object {
        const val MAX_FRACTION = 0.999f
        const val MIN_FRACTION = 0.001f
    }
}
