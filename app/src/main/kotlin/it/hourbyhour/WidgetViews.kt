package it.hourbyhour

import android.app.PendingIntent
import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews

/**
 * Turns a [WidgetLayout] into RemoteViews. The layouts are placed by weights (see event.xml), so
 * they fill the real size of the widget whatever size the launcher reports. The colors of the
 * [WidgetPalette] are set as resources, which the launcher resolves: they follow the system theme
 * and palette as soon as they change, without a redraw. Event colors are set for both themes, so
 * the launcher can switch them as soon as the theme changes.
 *
 * All decisions about what to show and where belong in [layoutWidget], which is unit tested.
 * Taps send the pending intents made by [clicks]; without it (in previews) the views do nothing.
 */
class WidgetViews(
    context: Context,
    /** The settings of this widget: transparency of the background and colors. */
    private val settings: WidgetSettings,
    private val clicks: ((ClickTarget) -> PendingIntent)?,
) {
    private val packageName = context.packageName
    private val palette = WidgetPalette.of(settings)

    /** The message shown instead of the events when calendar access is missing. */
    fun message() = RemoteViews(packageName, R.layout.widget_message).apply {
        paintBackground()
        setColorStateList(R.id.message, "setTextColor", palette.text)
    }

    fun build(layout: WidgetLayout): RemoteViews {
        val root = RemoteViews(packageName, R.layout.widget)
        root.paintBackground()
        root.setColor(R.id.grid_bottom, "setBackgroundColor", palette.grid)
        // The launcher may apply these views on top of the previous ones instead of inflating the
        // layout again: without this, every update would add another copy of the content.
        for (container in intArrayOf(R.id.headers, R.id.banners, R.id.hours, R.id.columns)) {
            root.removeAllViews(container)
        }

        for (header in layout.headers) {
            val views = RemoteViews(packageName, if (header.today) R.layout.day_header_today else R.layout.day_header)
            views.setTextViewText(R.id.day_header, header.label)
            views.setColorStateList(R.id.day_header, "setTextColor", if (header.today) palette.today else palette.text)
            views.setClick(R.id.day_header, header.click)
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
            row.setColorStateList(R.id.hour_label, "setTextColor", palette.textSecondary)
            row.setColor(R.id.hour_line, "setBackgroundColor", palette.grid)
            root.addView(R.id.hours, row)
        }

        for (column in layout.columns) {
            val columnViews = RemoteViews(packageName, R.layout.day_column)
            columnViews.setColor(R.id.column_separator, "setBackgroundColor", palette.grid)
            // Events are clickable views on top of the column: a tap elsewhere reaches the column.
            columnViews.setClick(R.id.day_column, column.click)
            for (box in column.events) columnViews.addView(R.id.day_column, event(box))
            column.nowLine?.let { columnViews.addView(R.id.day_column, nowLine(it)) }
            root.addView(R.id.columns, columnViews)
        }
        return root
    }

    /** Every day gets the same cell, see [BannerCell]. */
    private fun bannerCell(cell: BannerCell) = RemoteViews(packageName, R.layout.banner_cell).apply {
        setClick(R.id.banner_cell, cell.click)
        when (cell) {
            is BannerCell.Event -> addView(
                R.id.banner_cell,
                RemoteViews(packageName, R.layout.banner_chip).apply {
                    setTextViewText(R.id.banner_chip, cell.event.title)
                    setEventColors(R.id.banner_chip, cell.event.color, past = false)
                    setClick(R.id.banner_chip, cell.event.clickTarget())
                },
            )
            is BannerCell.More -> addView(
                R.id.banner_cell,
                RemoteViews(packageName, R.layout.banner_more).apply {
                    setTextViewText(R.id.banner_more, cell.label)
                    setColorStateList(R.id.banner_more, "setTextColor", palette.textSecondary)
                },
            )
            is BannerCell.Empty -> Unit
        }
    }

    private fun event(box: EventBox) = RemoteViews(packageName, R.layout.event).apply {
        setWeightSums(R.id.event_v1, R.id.event_v2, box.top, box.bottom)
        setWeightSums(R.id.event_h1, R.id.event_h2, box.left, box.right)
        addView(R.id.event_h2, eventBox(box))
    }

    private fun eventBox(box: EventBox): RemoteViews {
        val layout = if (box.title == TitleSize.NORMAL) R.layout.event_box else R.layout.event_box_compact
        return RemoteViews(packageName, layout).apply {
            if (box.title != TitleSize.NONE) setTextViewText(R.id.event_box, box.event.title)
            if (box.title == TitleSize.NORMAL) setInt(R.id.event_box, "setMaxLines", box.maxLines)
            setEventColors(R.id.event_box, box.event.color, box.past)
            setClick(R.id.event_box, box.event.clickTarget())
        }
    }

    private fun RemoteViews.setClick(viewId: Int, target: ClickTarget) {
        clicks?.let { setOnClickPendingIntent(viewId, it(target)) }
    }

    /** The line sits on the top edge of a view that spans from the current time down, like an event. */
    private fun nowLine(top: Float) = RemoteViews(packageName, R.layout.now_line).apply {
        setFloat(R.id.now_position, "setWeightSum", spanWeightSums(top, 1f).first)
    }

    /**
     * The background layer takes the color of the palette, resolved by the launcher, and the
     * transparency of this widget. The layer is the rounded background shape, so its corners stay.
     */
    private fun RemoteViews.paintBackground() {
        setColorStateList(android.R.id.background, "setImageTintList", palette.background)
        setInt(android.R.id.background, "setImageAlpha", settings.backgroundAlpha)
    }

    /** One color per theme: RemoteViews applies the one matching the launcher theme. */
    private fun RemoteViews.setEventColors(viewId: Int, providerColor: Int, past: Boolean) {
        val colors = eventColors(providerColor, past)
        setColorStateList(
            viewId,
            "setBackgroundTintList",
            ColorStateList.valueOf(colors.lightFill),
            ColorStateList.valueOf(colors.darkFill),
        )
        setColorInt(viewId, "setTextColor", colors.lightText, colors.darkText)
    }

    /** Makes the content of [outer] span the fraction [from, to] of it, see [spanWeightSums]. */
    private fun RemoteViews.setWeightSums(outer: Int, inner: Int, from: Float, to: Float) {
        val (outerSum, innerSum) = spanWeightSums(from, to)
        setFloat(outer, "setWeightSum", outerSum)
        setFloat(inner, "setWeightSum", innerSum)
    }
}

/**
 * The colors of the widget other than the events and the current time, as color resources. The
 * Google Calendar look is the default; with [WidgetSettings.systemColors] the widget takes the
 * system palette (Material You), which follows the wallpaper.
 */
enum class WidgetPalette(val background: Int, val text: Int, val textSecondary: Int, val grid: Int, val today: Int) {
    GOOGLE(R.color.widget_background, R.color.widget_text, R.color.widget_text_secondary, R.color.widget_grid, R.color.widget_today),
    SYSTEM(
        R.color.widget_system_background,
        R.color.widget_system_text,
        R.color.widget_system_text_secondary,
        R.color.widget_system_grid,
        R.color.widget_system_today,
    ),
    ;

    companion object {
        fun of(settings: WidgetSettings) = if (settings.systemColors) SYSTEM else GOOGLE
    }
}

/**
 * The weight sums of two nested layouts that make their content span the fraction [from, to] (see
 * event.xml): the outer one keeps its single child on the far side and gives it 1 - from, then the
 * inner one gives its child the share for [from, to]. The span is kept inside the layout and never
 * empty, so that the sums stay finite.
 */
fun spanWeightSums(from: Float, to: Float): Pair<Float, Float> {
    val start = from.coerceIn(0f, MAX_FRACTION)
    val end = to.coerceIn(start + MIN_FRACTION, 1f)
    return 1f / (1f - start) to (1f - start) / (end - start)
}

private const val MAX_FRACTION = 0.999f
private const val MIN_FRACTION = 0.001f
