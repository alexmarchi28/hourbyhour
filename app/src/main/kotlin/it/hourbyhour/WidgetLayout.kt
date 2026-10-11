package it.hourbyhour

import java.time.LocalDate
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
 * of its width, as fractions between 0 and 1. The box always matches the real start and end time
 * (only events shorter than a thin bar are drawn a little longer), and the title adapts to it.
 */
data class EventBox(
    val event: CalendarEvent,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val title: TitleSize,
    /** How many lines of the title fit, estimated from the widget height. Used with [TitleSize.NORMAL]. */
    val maxLines: Int,
    val past: Boolean,
)

enum class TitleSize {
    /** Regular text, on as many lines as fit. */
    NORMAL,

    /** One line of text that shrinks to fit the real height of the box. */
    COMPACT,

    /** No title: the box is just a colored bar. */
    NONE,
}

/**
 * The size of the widget as reported by the launcher, in dp. Only an estimate: some launchers get it
 * wrong (the Motorola one reports about 3/4 of the height; its width is about right).
 */
data class WidgetSize(val widthDp: Float, val heightDp: Float)

/** [nowLine] is the position of the current time as a fraction of the height, null when not shown. */
data class DayColumn(val events: List<EventBox>, val nowLine: Float?)

/**
 * Lays out [snapshot] for a widget of about [size]. The size is only an estimate: it decides how
 * dense the hour labels are, how the day headers and the titles fit narrow columns and when a box
 * is too short for a title. [fontScale] is the system font size setting.
 */
fun layoutWidget(snapshot: Snapshot, size: WidgetSize, fontScale: Float, locale: Locale): WidgetLayout {
    val days = snapshot.days
    require(days.isNotEmpty()) { "The widget needs at least one day" }
    val today = snapshot.now.toLocalDate()
    val columnWidthDp = ((size.widthDp - HORIZONTAL_CHROME_DP) / days.size).coerceAtLeast(1f)
    val headers = days.map { day ->
        DayHeader(dayLabel(day.date, columnWidthDp - HEADER_MARGIN_DP, fontScale, locale), day.date == today)
    }

    val rowCount = minOf(days.maxOf { it.allDay.size }, MAX_BANNER_ROWS)
    val bannerRows = (0 until rowCount).map { row -> days.map { bannerCell(it, row, rowCount) } }

    val range = hourRange(days)
    val rangeStart = range.start * 60
    val rangeEnd = range.end * 60
    val gridHeightDp = (size.heightDp - CHROME_HEIGHT_DP - rowCount * BANNER_ROW_HEIGHT_DP).coerceAtLeast(1f)
    val dpPerMinute = gridHeightDp / (rangeEnd - rangeStart)

    val labelStep = LABEL_STEPS.firstOrNull { it * 60 * dpPerMinute >= LABEL_SPACING_SP * fontScale } ?: 24
    val hourLabels = (range.start until range.end).map { hour ->
        if ((hour - range.start) % labelStep == 0) hour.toString() else ""
    }

    val lineHeightDp = LINE_HEIGHT_SP * fontScale
    val minBar = ceil(MIN_BAR_DP / dpPerMinute).toInt().coerceAtLeast(1)
    fun fraction(minute: Int) = (minute - rangeStart).toFloat() / (rangeEnd - rangeStart)

    val nowMillis = snapshot.now.toInstant().toEpochMilli()
    val nowMinute = snapshot.now.hour * 60 + snapshot.now.minute
    val columns = days.map { day ->
        // Columns follow the real times, so that events that do not overlap never sit side by side.
        // Events without duration count as one minute.
        val placed = placeSegments(day.timed, minDuration = 1)
        val events = placed.map { box ->
            val segment = box.segment
            val (start, end) = visibleSpan(box, placed, minBar, rangeStart, rangeEnd)
            val boxHeightDp = (end - start) * dpPerMinute
            val textWidthDp = columnWidthDp * (box.right - box.left) - EVENT_HORIZONTAL_SPACE_DP
            EventBox(
                event = segment.event,
                top = fraction(start),
                bottom = fraction(end),
                left = box.left,
                right = box.right,
                title = when {
                    boxHeightDp >= lineHeightDp + EVENT_PADDING_DP -> TitleSize.NORMAL
                    boxHeightDp >= MIN_TITLE_SP * fontScale -> TitleSize.COMPACT
                    else -> TitleSize.NONE
                },
                maxLines = if (fitsWordByWord(segment.event.title, textWidthDp, fontScale)) {
                    ((boxHeightDp - EVENT_PADDING_DP) / lineHeightDp).toInt().coerceAtLeast(1)
                } else {
                    1
                },
                past = segment.event.end <= nowMillis,
            )
        }
        val nowLine = if (day.date == today && nowMinute in rangeStart..rangeEnd) fraction(nowMinute) else null
        DayColumn(events, nowLine)
    }
    return WidgetLayout(headers, bannerRows, hourLabels, columns)
}

private val PlacedSegment.left get() = column.toFloat() / columns
private val PlacedSegment.right get() = (column + 1f) / columns

/**
 * The minutes [box] covers on the grid: its real start and end, except that an event shorter than
 * [minBar] grows to a visible bar. It grows only into free time, first after the event and then
 * before it, never over another event in the same horizontal space: overlapping boxes would suggest
 * overlapping times.
 */
private fun visibleSpan(box: PlacedSegment, all: List<PlacedSegment>, minBar: Int, rangeStart: Int, rangeEnd: Int): Pair<Int, Int> {
    val segment = box.segment
    val neighbors = all.filter { it !== box && it.left < box.right && it.right > box.left }
    val nextStart = neighbors.map { it.segment.startMinute }.filter { it >= segment.startMinute }.minOrNull() ?: rangeEnd
    val previousEnd = neighbors.map { it.segment.endMinute }.filter { it <= segment.startMinute }.maxOrNull() ?: rangeStart
    val end = maxOf(segment.endMinute, minOf(segment.startMinute + minBar, nextStart, rangeEnd))
    val start = minOf(segment.startMinute, maxOf(end - minBar, previousEnd, rangeStart))
    return start to end
}

/**
 * "Lun 12" when it fits [widthDp], else "L 12" (one-letter weekday, unambiguous next to the date),
 * else just "12".
 */
private fun dayLabel(date: LocalDate, widthDp: Float, fontScale: Float, locale: Locale): String =
    listOf("EEE d", "EEEEE d", "d")
        .map { DateTimeFormatter.ofPattern(it, locale).format(date).replaceFirstChar { c -> c.titlecase(locale) } }
        .firstOrNull { estimatedWidthDp(it, HEADER_TEXT_SP * fontScale) <= widthDp }
        ?: date.dayOfMonth.toString()

/**
 * Whether [title] can wrap on more lines without breaking a word: Android cuts words longer than
 * the box anywhere ("Arrampica / ta"), so such titles stay on one line, cut with an ellipsis.
 */
private fun fitsWordByWord(title: String, widthDp: Float, fontScale: Float): Boolean {
    val longestWord = title.split(Regex("\\s+")).maxOfOrNull { it.length } ?: 0
    return longestWord * CHAR_WIDTH_EM * EVENT_TEXT_SP * fontScale <= widthDp
}

/** A rough width of [text]: real glyph widths are not known outside the launcher. */
private fun estimatedWidthDp(text: String, textSizeDp: Float) = text.length * CHAR_WIDTH_EM * textSizeDp

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

// Widths in dp/sp matching the layouts, used with the estimated width.
private const val HORIZONTAL_CHROME_DP = 34f // widget padding (2 x 6) and hour labels gutter (22)
private const val HEADER_MARGIN_DP = 2f
private const val HEADER_TEXT_SP = 12f
private const val EVENT_TEXT_SP = 11f
private const val EVENT_HORIZONTAL_SPACE_DP = 9f // separator, margins and padding of an event box

/** Average width of a character in em: a little wide, so that estimates err on the safe side. */
private const val CHAR_WIDTH_EM = 0.6f

/** The thinnest an event is drawn, so that it stays visible. */
private const val MIN_BAR_DP = 3f

/**
 * Below this height even the smallest compact title (8sp, see event_box_compact.xml) does not fit.
 * Autosize does not go lower: long titles are cut with an ellipsis instead of becoming unreadable.
 */
private const val MIN_TITLE_SP = 8f
