package it.tregiorni

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * What the widget shows and where, without Android types so it can be tested on the JVM.
 * Positions are fractions of a day column, because the real size of the widget is unknown
 * (see WidgetViews).
 */
data class WidgetLayout(
    val headers: List<DayHeader>,
    /** Rows of the all-day banner, each with one cell per day. */
    val bannerRows: List<List<BannerCell>>,
    /** One label per hour of the grid, from the top; empty when skipped to avoid crowding. */
    val hourLabels: List<String>,
    /** One column per day. */
    val columns: List<DayColumn>,
)

data class DayHeader(val label: String, val today: Boolean)

sealed interface BannerCell {
    data class Event(val event: CalendarEvent) : BannerCell

    /** Stands for [count] all-day events that do not fit. */
    data class More(val count: Int) : BannerCell

    data object Empty : BannerCell
}

/**
 * An event drawn in a day column: it covers [top, bottom] of the column height and [left, right]
 * of its width, as fractions between 0 and 1.
 */
data class EventBox(
    val event: CalendarEvent,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    /** How many lines of the title fit, estimated from the widget height. */
    val maxLines: Int,
    val past: Boolean,
)

/** [nowLine] is the position of the current time as a fraction of the height, null when not shown. */
data class DayColumn(val events: List<EventBox>, val nowLine: Float?)

/**
 * Lays out [snapshot] for a widget about [heightDp] tall. The height is only an estimate (launchers
 * may report a wrong size): it decides how dense the hour labels are and how tall the shortest
 * events must be to stay readable. [fontScale] is the system font size setting.
 */
fun layoutWidget(snapshot: Snapshot, heightDp: Float, fontScale: Float, locale: Locale): WidgetLayout {
    val days = snapshot.days
    require(days.isNotEmpty()) { "The widget needs at least one day" }
    val today = snapshot.now.toLocalDate()
    val dayFormat = DateTimeFormatter.ofPattern("EEE d", locale)
    val headers = days.map { day ->
        DayHeader(dayFormat.format(day.date).replaceFirstChar { it.titlecase(locale) }, day.date == today)
    }

    val rowCount = minOf(days.maxOf { it.allDay.size }, MAX_BANNER_ROWS)
    val bannerRows = (0 until rowCount).map { row -> days.map { bannerCell(it, row, rowCount) } }

    val range = hourRange(days)
    val rangeStart = range.start * 60
    val rangeEnd = range.end * 60
    val gridHeightDp = (heightDp - CHROME_HEIGHT_DP - rowCount * BANNER_ROW_HEIGHT_DP).coerceAtLeast(1f)
    val dpPerMinute = gridHeightDp / (rangeEnd - rangeStart)

    val labelStep = LABEL_STEPS.firstOrNull { it * 60 * dpPerMinute >= LABEL_SPACING_SP * fontScale } ?: 24
    val hourLabels = (range.start until range.end).map { hour ->
        if ((hour - range.start) % labelStep == 0) hour.toString() else ""
    }

    // Short events are drawn at least one text line tall.
    val lineHeightDp = LINE_HEIGHT_SP * fontScale
    val minDuration = ceil((lineHeightDp + EVENT_PADDING_DP) / dpPerMinute).toInt()
    fun fraction(minute: Int) = (minute - rangeStart).toFloat() / (rangeEnd - rangeStart)

    val nowMillis = snapshot.now.toInstant().toEpochMilli()
    val nowMinute = snapshot.now.hour * 60 + snapshot.now.minute
    val columns = days.map { day ->
        val events = placeSegments(day.timed, minDuration).map { placed ->
            val segment = placed.segment
            // Near the end of the range a short event grows upwards instead of overflowing.
            val end = maxOf(segment.endMinute, segment.startMinute + minDuration).coerceAtMost(rangeEnd)
            val start = minOf(segment.startMinute, end - minDuration).coerceAtLeast(rangeStart)
            EventBox(
                event = segment.event,
                top = fraction(start),
                bottom = fraction(end),
                left = placed.column.toFloat() / placed.columns,
                right = (placed.column + 1f) / placed.columns,
                maxLines = (((end - start) * dpPerMinute - EVENT_PADDING_DP) / lineHeightDp).toInt().coerceAtLeast(1),
                past = segment.event.end <= nowMillis,
            )
        }
        val nowLine = if (day.date == today && nowMinute in rangeStart..rangeEnd) fraction(nowMinute) else null
        DayColumn(events, nowLine)
    }
    return WidgetLayout(headers, bannerRows, hourLabels, columns)
}

/** When a day has more all-day events than rows, its last row says how many are not shown. */
private fun bannerCell(day: Day, row: Int, rows: Int): BannerCell {
    val overflow = day.allDay.size > rows
    val shown = if (overflow) rows - 1 else day.allDay.size
    return when {
        row < shown -> BannerCell.Event(day.allDay[row])
        overflow && row == rows - 1 -> BannerCell.More(day.allDay.size - shown)
        else -> BannerCell.Empty
    }
}

private const val MAX_BANNER_ROWS = 2
private val LABEL_STEPS = listOf(1, 2, 3, 4, 6, 12)

// Estimates in dp/sp matching the layouts in res/layout, used only with the estimated height.
private const val CHROME_HEIGHT_DP = 38f
private const val BANNER_ROW_HEIGHT_DP = 18f
private const val LABEL_SPACING_SP = 14f
private const val LINE_HEIGHT_SP = 13f
private const val EVENT_PADDING_DP = 3f
