package it.hourbyhour

import android.app.PendingIntent
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
 * Taps send the pending intents made by [clicks]; without it (in previews) the views do nothing.
 */
class WidgetViews(
    private val context: Context,
    private val settings: WidgetSettings,
    private val clicks: ((ClickTarget) -> PendingIntent)?,
) {
    private val packageName = context.packageName
    private val lightBackground = themedColor(R.color.widget_background, night = false)
    private val darkBackground = themedColor(R.color.widget_background, night = true)

    /** The message shown instead of the events when calendar access is missing. */
    fun message() = RemoteViews(packageName, R.layout.widget_message).apply { tintBackground() }

    fun build(layout: WidgetLayout): RemoteViews {
        val root = RemoteViews(packageName, R.layout.widget)
        root.tintBackground()
        // The launcher may apply these views on top of the previous ones instead of inflating the
        // layout again: without this, every update would add another copy of the content.
        for (container in intArrayOf(R.id.headers, R.id.banners, R.id.hours, R.id.columns)) {
            root.removeAllViews(container)
        }

        for (header in layout.headers) {
            val views = RemoteViews(packageName, if (header.today) R.layout.day_header_today else R.layout.day_header)
            views.setTextViewText(R.id.day_header, header.label)
            views.setClick(R.id.day_header, header.click)
            root.addView(R.id.headers, views)
        }

        for (row in layout.bannerRows) {
            val rowViews = RemoteViews(packageName, R.layout.banner_row)
            row.forEachIndexed { day, cell -> rowViews.addView(R.id.banner_row, bannerCell(cell, layout.columns[day].click)) }
            root.addView(R.id.banners, rowViews)
        }

        for (label in layout.hourLabels) {
            val row = RemoteViews(packageName, R.layout.hour_row)
            row.setTextViewText(R.id.hour_label, label)
            root.addView(R.id.hours, row)
        }

        for (column in layout.columns) {
            val columnViews = RemoteViews(packageName, R.layout.day_column)
            // Events are clickable views on top of the column: a tap elsewhere reaches the column.
            columnViews.setClick(R.id.day_column, column.click)
            for (box in column.events) columnViews.addView(R.id.day_column, event(box))
            column.nowLine?.let { columnViews.addView(R.id.day_column, nowLine(it)) }
            root.addView(R.id.columns, columnViews)
        }
        return root
    }

    /**
     * Every day gets the same cell, so that cells line up with the grid columns below. A tap on the
     * cell (empty, or with the "+N" text) opens the day; a tap on an event chip opens the event.
     */
    private fun bannerCell(cell: BannerCell, dayClick: ClickTarget) = RemoteViews(packageName, R.layout.banner_cell).apply {
        setClick(R.id.banner_cell, dayClick)
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
                    setTextViewText(R.id.banner_more, "+${cell.count}")
                },
            )
            BannerCell.Empty -> Unit
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
     * The background takes the transparency of this widget. Tinting the rounded background shape,
     * rather than replacing it, keeps its corners; one color per theme as for the events.
     */
    private fun RemoteViews.tintBackground() {
        setColorStateList(
            android.R.id.background,
            "setBackgroundTintList",
            ColorStateList.valueOf(withAlpha(lightBackground, settings.backgroundAlpha)),
            ColorStateList.valueOf(withAlpha(darkBackground, settings.backgroundAlpha)),
        )
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

    /** Makes the content of [outer] span the fraction [from, to] of it, see [spanWeightSums]. */
    private fun RemoteViews.setWeightSums(outer: Int, inner: Int, from: Float, to: Float) {
        val (outerSum, innerSum) = spanWeightSums(from, to)
        setFloat(outer, "setWeightSum", outerSum)
        setFloat(inner, "setWeightSum", innerSum)
    }

    /** A color resource as it is in the light or dark theme, whatever the current theme is. */
    private fun themedColor(id: Int, night: Boolean): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(configuration).getColor(id)
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
