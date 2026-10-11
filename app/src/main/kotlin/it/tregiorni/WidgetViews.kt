package it.tregiorni

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.RemoteViews
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** What the widget shows: the days with their events, as seen at [now]. */
data class Snapshot(val days: List<Day>, val now: ZonedDateTime)

/**
 * Builds the widget out of layouts placed by weights (see event.xml), so it fills the real size of
 * the widget whatever size the launcher reports. Static colors come from resources and follow the
 * system theme; event colors are set here.
 *
 * [heightDp] is only an estimate of the widget height, used to decide how dense the hour labels
 * can be and how tall the shortest events must be drawn to stay readable.
 */
class WidgetViews(private val context: Context, private val heightDp: Float) {
    private val packageName = context.packageName
    private val locale = Locale.getDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d", locale)
    private val fontScale = context.resources.configuration.fontScale
    private val background = context.getColor(R.color.widget_background)

    fun build(snapshot: Snapshot): RemoteViews {
        val days = snapshot.days
        val today = snapshot.now.toLocalDate()
        val root = RemoteViews(packageName, R.layout.widget)
        // The launcher may apply these views on top of the previous ones instead of inflating the
        // layout again: without this, every update would add another copy of the content.
        for (container in intArrayOf(R.id.headers, R.id.banners, R.id.hours, R.id.columns)) {
            root.removeAllViews(container)
        }

        for (day in days) {
            val header = RemoteViews(packageName, if (day.date == today) R.layout.day_header_today else R.layout.day_header)
            header.setTextViewText(R.id.day_header, dayFormat.format(day.date).replaceFirstChar { it.titlecase(locale) })
            root.addView(R.id.headers, header)
        }

        val bannerRows = minOf(days.maxOf { it.allDay.size }, MAX_BANNER_ROWS)
        for (row in 0 until bannerRows) {
            val rowViews = RemoteViews(packageName, R.layout.banner_row)
            for (day in days) rowViews.addView(R.id.banner_row, bannerCell(day, row, bannerRows))
            root.addView(R.id.banners, rowViews)
        }

        val range = hourRange(days)
        val gridHeightDp = (heightDp - CHROME_HEIGHT_DP - bannerRows * BANNER_ROW_HEIGHT_DP).coerceAtLeast(1f)
        val dpPerMinute = gridHeightDp / (range.hours * 60)

        val labelStep = LABEL_STEPS.firstOrNull { it * 60 * dpPerMinute >= LABEL_SPACING_SP * fontScale } ?: 24
        for (hour in range.start until range.end) {
            val row = RemoteViews(packageName, R.layout.hour_row)
            row.setTextViewText(R.id.hour_label, if ((hour - range.start) % labelStep == 0) hour.toString() else "")
            root.addView(R.id.hours, row)
        }

        // Short events are drawn at least one text line tall.
        val minEventHeightDp = LINE_HEIGHT_SP * fontScale + EVENT_PADDING_DP
        val minDuration = ceil(minEventHeightDp / dpPerMinute).toInt()
        val rangeStart = range.start * 60
        val rangeEnd = range.end * 60
        fun fraction(minute: Int) = (minute - rangeStart).toFloat() / (rangeEnd - rangeStart)

        val nowMillis = snapshot.now.toInstant().toEpochMilli()
        val nowMinute = snapshot.now.hour * 60 + snapshot.now.minute
        for (day in days) {
            val column = RemoteViews(packageName, R.layout.day_column)
            for (placed in placeSegments(day.timed, minDuration)) {
                val segment = placed.segment
                val end = maxOf(segment.endMinute, segment.startMinute + minDuration).coerceAtMost(rangeEnd)
                val start = minOf(segment.startMinute, end - minDuration).coerceAtLeast(rangeStart)
                val past = segment.event.end <= nowMillis
                column.addView(R.id.day_column, event(placed, fraction(start), fraction(end), (end - start) * dpPerMinute, past))
            }
            if (day.date == today && nowMinute in rangeStart..rangeEnd) {
                column.addView(R.id.day_column, nowLine(fraction(nowMinute)))
            }
            root.addView(R.id.columns, column)
        }
        return root
    }

    /** When a day has more all-day events than rows, its last row says how many are not shown. */
    private fun bannerCell(day: Day, row: Int, rows: Int): RemoteViews {
        val overflow = day.allDay.size > rows
        val shown = if (overflow) rows - 1 else day.allDay.size
        return when {
            row < shown -> RemoteViews(packageName, R.layout.banner_chip).apply {
                val event = day.allDay[row]
                setTextViewText(R.id.banner_chip, event.title)
                setTextColor(R.id.banner_chip, contrastingText(event.color))
                setColorStateList(R.id.banner_chip, "setBackgroundTintList", ColorStateList.valueOf(event.color))
            }
            overflow && row == rows - 1 -> RemoteViews(packageName, R.layout.banner_more).apply {
                setTextViewText(R.id.banner_more, "+${day.allDay.size - shown}")
            }
            else -> RemoteViews(packageName, R.layout.banner_empty)
        }
    }

    /** Past events are faded towards the background so the upcoming ones stand out. */
    private fun event(placed: PlacedSegment, top: Float, bottom: Float, eventHeightDp: Float, past: Boolean): RemoteViews {
        val left = placed.column.toFloat() / placed.columns
        val right = (placed.column + 1f) / placed.columns
        val event = placed.segment.event
        val color = if (past) blend(event.color, background, 0.5f) else event.color
        val lines = ((eventHeightDp - EVENT_PADDING_DP) / (LINE_HEIGHT_SP * fontScale)).toInt().coerceAtLeast(1)
        return RemoteViews(packageName, R.layout.event).apply {
            setWeightSums(R.id.event_v1, R.id.event_v2, top, bottom)
            setWeightSums(R.id.event_h1, R.id.event_h2, left, right)
            setTextViewText(R.id.event_text, event.title)
            setTextColor(R.id.event_text, contrastingText(color))
            setColorStateList(R.id.event_text, "setBackgroundTintList", ColorStateList.valueOf(color))
            setInt(R.id.event_text, "setMaxLines", lines)
        }
    }

    private fun nowLine(top: Float) = RemoteViews(packageName, R.layout.now_line).apply {
        setFloat(R.id.now_position, "setWeightSum", 1f / (1f - top.coerceAtMost(MAX_FRACTION)))
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

    private companion object {
        const val MAX_BANNER_ROWS = 2
        val LABEL_STEPS = listOf(1, 2, 3, 4, 6, 12)

        // Estimates in dp/sp matching the layouts, used only with the estimated height.
        const val CHROME_HEIGHT_DP = 38f
        const val BANNER_ROW_HEIGHT_DP = 18f
        const val LABEL_SPACING_SP = 14f
        const val LINE_HEIGHT_SP = 13f
        const val EVENT_PADDING_DP = 3f

        const val MAX_FRACTION = 0.999f
        const val MIN_FRACTION = 0.001f

        fun contrastingText(background: Int) = if (Color.luminance(background) > 0.5f) 0xFF202124.toInt() else Color.WHITE

        /** Mixes the RGB channels of [from] towards [to]; the result is opaque. */
        fun blend(from: Int, to: Int, ratio: Float): Int {
            fun channel(shift: Int) =
                (((from shr shift) and 0xFF) * (1 - ratio) + ((to shr shift) and 0xFF) * ratio).toInt()
            return Color.rgb(channel(16), channel(8), channel(0))
        }
    }
}
