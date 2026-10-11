package it.tregiorni

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** What the widget shows: the days with their events, as seen at [now]. */
data class Snapshot(val days: List<Day>, val now: ZonedDateTime)

private class Palette(
    val background: Int,
    val text: Int,
    val secondaryText: Int,
    val grid: Int,
    val today: Int,
    val nowLine: Int,
) {
    companion object {
        val LIGHT = Palette(
            background = 0xF2FFFFFF.toInt(),
            text = 0xFF1F1F1F.toInt(),
            secondaryText = 0xFF5F6368.toInt(),
            grid = 0xFFE3E3E3.toInt(),
            today = 0xFF1A73E8.toInt(),
            nowLine = 0xFFD93025.toInt(),
        )
        val DARK = Palette(
            background = 0xF2202124.toInt(),
            text = 0xFFE8EAED.toInt(),
            secondaryText = 0xFF9AA0A6.toInt(),
            grid = 0xFF3C4043.toInt(),
            today = 0xFF8AB4F8.toInt(),
            nowLine = 0xFFF28B82.toInt(),
        )
    }
}

/** Draws the widget on a bitmap: day headers, all-day banners, hour grid, events and the current time. */
class WidgetRenderer(context: Context) {
    private val metrics = context.resources.displayMetrics
    private val locale = Locale.getDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d", locale)
    private val cornerRadius = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    private val palette = if (
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    ) Palette.DARK else Palette.LIGHT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint().apply { color = palette.grid; strokeWidth = dp(1f) }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.nowLine; strokeWidth = dp(2f) }
    private val headerPaint = textPaint(12f, palette.text).apply { textAlign = Paint.Align.CENTER }
    private val todayHeaderPaint = textPaint(12f, palette.today, Typeface.DEFAULT_BOLD).apply { textAlign = Paint.Align.CENTER }
    private val hourPaint = textPaint(10f, palette.secondaryText).apply { textAlign = Paint.Align.RIGHT }
    private val chipPaint = textPaint(10f, palette.text, MEDIUM)
    private val morePaint = textPaint(10f, palette.secondaryText)
    private val eventPaint = textPaint(11f, palette.text, MEDIUM)
    private val messagePaint = textPaint(14f, palette.text)

    fun render(width: Int, height: Int, snapshot: Snapshot): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, height)

        val days = snapshot.days
        val today = snapshot.now.toLocalDate()
        val padding = dp(8f)
        val top = padding
        val right = width - padding
        val bottom = height - padding
        val gridLeft = padding + hourPaint.measureText("24") + dp(6f)
        val columnWidth = (right - gridLeft) / days.size

        // Day headers.
        val headerHeight = headerPaint.fontSpacing + dp(4f)
        days.forEachIndexed { i, day ->
            val paint = if (day.date == today) todayHeaderPaint else headerPaint
            val label = dayFormat.format(day.date).replaceFirstChar { it.titlecase(locale) }
            val text = TextUtils.ellipsize(label, paint, columnWidth - dp(2f), TextUtils.TruncateAt.END)
            canvas.drawText(text, 0, text.length, gridLeft + columnWidth * (i + 0.5f), centerBaseline(top + headerHeight / 2, paint), paint)
        }

        // All-day banners. When a day has more events than rows, the last row says how many are hidden.
        var y = top + headerHeight
        val bannerRows = minOf(days.maxOf { it.allDay.size }, MAX_BANNER_ROWS)
        if (bannerRows > 0) {
            val chipHeight = chipPaint.fontSpacing + dp(4f)
            val rowHeight = chipHeight + dp(2f)
            days.forEachIndexed { i, day ->
                val left = gridLeft + columnWidth * i + dp(1f)
                val chipRight = left + columnWidth - dp(2f)
                val overflow = day.allDay.size > bannerRows
                val shown = if (overflow) day.allDay.take(bannerRows - 1) else day.allDay
                shown.forEachIndexed { row, event ->
                    val chipTop = y + rowHeight * row
                    drawChip(canvas, RectF(left, chipTop, chipRight, chipTop + chipHeight), event)
                }
                if (overflow) {
                    val more = "+${day.allDay.size - shown.size}"
                    canvas.drawText(more, left + dp(3f), centerBaseline(y + rowHeight * shown.size + chipHeight / 2, morePaint), morePaint)
                }
            }
            y += rowHeight * bannerRows + dp(2f)
        }

        // Hour grid.
        val labelHalf = hourPaint.fontSpacing / 2
        val gridTop = y + labelHalf
        val gridBottom = bottom - labelHalf
        for (i in 1 until days.size) {
            val x = gridLeft + columnWidth * i
            canvas.drawLine(x, top + headerHeight, x, gridBottom, gridPaint)
        }
        if (gridBottom - gridTop < dp(12f)) return bitmap

        val range = hourRange(days)
        val rangeStart = range.start * 60
        val rangeEnd = range.end * 60
        val minuteHeight = (gridBottom - gridTop) / (rangeEnd - rangeStart)
        fun yOf(minute: Int) = gridTop + (minute - rangeStart) * minuteHeight

        val labelStep = LABEL_STEPS.firstOrNull { it * 60 * minuteHeight >= hourPaint.fontSpacing * 1.2f } ?: 24
        for (hour in range.start..range.end) {
            val lineY = yOf(hour * 60)
            canvas.drawLine(gridLeft, lineY, right, lineY, gridPaint)
            if ((hour - range.start) % labelStep == 0) {
                canvas.drawText(hour.toString(), gridLeft - dp(4f), centerBaseline(lineY, hourPaint), hourPaint)
            }
        }

        // Events. Short events are drawn at least one text line tall.
        val minEventHeight = eventPaint.fontSpacing + dp(2f)
        val minDuration = ceil(minEventHeight / minuteHeight).toInt()
        val nowMillis = snapshot.now.toInstant().toEpochMilli()
        days.forEachIndexed { i, day ->
            val columnLeft = gridLeft + columnWidth * i + dp(1f)
            for (placed in placeSegments(day.timed, minDuration)) {
                val slotWidth = (columnWidth - dp(2f)) / placed.columns
                val left = columnLeft + slotWidth * placed.column
                val segment = placed.segment
                val end = maxOf(segment.endMinute, segment.startMinute + minDuration).coerceAtMost(rangeEnd)
                val eventBottom = yOf(end)
                val eventTop = minOf(yOf(segment.startMinute), eventBottom - minEventHeight)
                val rect = RectF(left, eventTop + dp(0.5f), left + slotWidth - dp(1f), eventBottom - dp(0.5f))
                drawEvent(canvas, rect, segment.event, past = segment.event.end <= nowMillis)
            }
        }

        // Current time, on today's column.
        val todayIndex = days.indexOfFirst { it.date == today }
        val nowMinute = snapshot.now.hour * 60 + snapshot.now.minute
        if (todayIndex >= 0 && nowMinute in rangeStart..rangeEnd) {
            val lineY = yOf(nowMinute)
            val left = gridLeft + columnWidth * todayIndex
            canvas.drawLine(left, lineY, left + columnWidth, lineY, nowPaint)
            canvas.drawCircle(left, lineY, dp(4f), nowPaint)
        }
        return bitmap
    }

    /** A centered message on the widget background, e.g. when calendar access is missing. */
    fun renderMessage(width: Int, height: Int, message: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, height)
        val margin = dp(16f)
        val layout = StaticLayout.Builder
            .obtain(message, 0, message.length, messagePaint, (width - 2 * margin).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.translate(margin, (height - layout.height) / 2f)
        layout.draw(canvas)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        fillPaint.color = palette.background
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, fillPaint)
    }

    private fun drawChip(canvas: Canvas, rect: RectF, event: CalendarEvent) {
        fillPaint.color = event.color
        canvas.drawRoundRect(rect, dp(4f), dp(4f), fillPaint)
        chipPaint.color = contrastingText(event.color)
        val text = TextUtils.ellipsize(event.title, chipPaint, rect.width() - dp(6f), TextUtils.TruncateAt.END)
        canvas.drawText(text, 0, text.length, rect.left + dp(3f), centerBaseline(rect.centerY(), chipPaint), chipPaint)
    }

    /** Past events are faded towards the background so the upcoming ones stand out. */
    private fun drawEvent(canvas: Canvas, rect: RectF, event: CalendarEvent, past: Boolean) {
        val color = if (past) blend(event.color, palette.background, 0.5f) else event.color
        fillPaint.color = color
        canvas.drawRoundRect(rect, dp(4f), dp(4f), fillPaint)

        val textWidth = rect.width() - dp(6f)
        if (textWidth < dp(8f)) return
        eventPaint.color = contrastingText(color)
        val lineHeight = eventPaint.fontSpacing
        val verticalPadding = if (rect.height() >= lineHeight + dp(4f)) dp(2f) else maxOf(0f, (rect.height() - lineHeight) / 2)
        val maxLines = maxOf(1, ((rect.height() - 2 * verticalPadding) / lineHeight).toInt())
        val layout = StaticLayout.Builder
            .obtain(event.title, 0, event.title.length, eventPaint, textWidth.toInt())
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.clipRect(rect)
        canvas.translate(rect.left + dp(3f), rect.top + verticalPadding)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun textPaint(sizeSp: Float, color: Int, typeface: Typeface = Typeface.DEFAULT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            this.color = color
            this.typeface = typeface
        }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)

    private fun centerBaseline(centerY: Float, paint: Paint) = centerY - (paint.ascent() + paint.descent()) / 2

    private companion object {
        const val MAX_BANNER_ROWS = 2
        val LABEL_STEPS = listOf(1, 2, 3, 4, 6, 12)
        val MEDIUM: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)

        fun contrastingText(background: Int) = if (Color.luminance(background) > 0.5f) 0xFF202124.toInt() else Color.WHITE

        /** Mixes the RGB channels of [from] towards [to]; the result is opaque. */
        fun blend(from: Int, to: Int, ratio: Float): Int {
            fun channel(shift: Int) =
                (((from shr shift) and 0xFF) * (1 - ratio) + ((to shr shift) and 0xFF) * ratio).toInt()
            return Color.rgb(channel(16), channel(8), channel(0))
        }
    }
}
