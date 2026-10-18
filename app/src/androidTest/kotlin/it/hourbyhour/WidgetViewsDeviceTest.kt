package it.hourbyhour

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The views of the widget, built from a layout and drawn by Android as a launcher would. */
class WidgetViewsDeviceTest {
    private fun event(title: String, id: Long, allDay: Boolean = false) =
        CalendarEvent(title, begin = id * 1_000, end = id * 1_000 + 500, allDay, color = 0xFF039BE5.toInt(), eventId = id)

    private fun box(event: CalendarEvent, top: Float, bottom: Float, left: Float = 0f, right: Float = 1f, title: TitleSize, maxLines: Int = 1) =
        EventBox(event, top, bottom, left, right, title, maxLines, past = false)

    private val monday = ClickTarget.Time(1_000_000)
    private val tuesday = ClickTarget.Time(2_000_000)
    private val holiday = event("Holiday", 9, allDay = true)
    private val meeting = event("Long meeting", 1)
    private val call = event("Call", 2)
    private val reminder = event("Reminder", 3)

    private val layout = WidgetLayout(
        headers = listOf(DayHeader("Lun 12", today = true, monday), DayHeader("Mar 13", today = false, tuesday)),
        bannerRows = listOf(listOf(BannerCell.Event(holiday, monday), BannerCell.More(3, tuesday))),
        hourLabels = listOf("8", "", "10"),
        columns = listOf(
            DayColumn(
                listOf(
                    box(meeting, 0.25f, 0.5f, title = TitleSize.NORMAL, maxLines = 3),
                    box(call, 0.5f, 0.55f, left = 0.5f, title = TitleSize.COMPACT),
                    box(reminder, 0.9f, 0.91f, title = TitleSize.NONE),
                ),
                nowLine = 0.6f,
                click = monday,
            ),
            DayColumn(emptyList(), nowLine = null, click = tuesday),
        ),
    )

    // Taps send broadcasts naming their target, instead of opening the calendar app.
    private val taps = LinkedBlockingQueue<String>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            taps += intent.getStringExtra(TARGET)!!
        }
    }

    @Before
    fun listenForTaps() {
        targetContext.registerReceiver(receiver, IntentFilter(TAP), Context.RECEIVER_NOT_EXPORTED)
    }

    @After
    fun stopListening() {
        targetContext.unregisterReceiver(receiver)
    }

    private fun tapIntent(target: ClickTarget): PendingIntent = PendingIntent.getBroadcast(
        targetContext,
        0,
        Intent(TAP).setPackage(targetContext.packageName).putExtra(TARGET, target.toString()).setIdentifier(target.toString()),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** The widget drawn [width] by [height] pixels. */
    private fun draw(
        settings: WidgetSettings = WidgetSettings(),
        clicks: ((ClickTarget) -> PendingIntent)? = ::tapIntent,
        width: Int = 600,
        height: Int = 900,
    ): View = onMain {
        val root = WidgetViews(targetContext, settings.backgroundAlpha, clicks).build(layout).apply(targetContext, FrameLayout(targetContext))
        root.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        root
    }

    /** What a tap on [view] opens. */
    private fun tap(view: View): String {
        assertTrue("$view has no tap action", onMain { view.performClick() })
        return taps.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("The tap on $view sent nothing")
    }

    private val View.text get() = (this as TextView).text.toString()

    private fun View.columns() = childrenOf(R.id.columns)

    /** The events of a day column, after its separator, without the current-time line. */
    private fun eventsOf(column: View) = (column as ViewGroup).let { group ->
        (0 until group.childCount).map { group.getChildAt(it) }.filter { it.id == R.id.event_v1 }
    }

    private fun View.eventBox() = findViewById<TextView>(R.id.event_box)

    @Test
    fun headersShowTheDaysAndTodayStandsOut() {
        val headers = draw().childrenOf(R.id.headers)

        assertEquals(listOf("Lun 12", "Mar 13"), headers.map { it.text })
        assertTrue((headers[0] as TextView).typeface.isBold)
        assertFalse((headers[1] as TextView).typeface?.isBold ?: false)
    }

    @Test
    fun bannerShowsAllDayEventsAndHowManyMore() {
        val rows = draw().childrenOf(R.id.banners)
        val cells = rows.single().childrenOf(R.id.banner_row)

        assertEquals(2, cells.size)
        assertEquals("Holiday", cells[0].findViewById<View>(R.id.banner_chip).text)
        assertEquals("+3", cells[1].findViewById<View>(R.id.banner_more).text)
    }

    @Test
    fun everyHourHasItsRow() {
        assertEquals(listOf("8", "", "10"), draw().childrenOf(R.id.hours).map { it.findViewById<View>(R.id.hour_label).text })
    }

    @Test
    fun titlesFitAsTheLayoutSays() {
        val root = draw()
        val (normal, compact, none) = eventsOf(root.columns()[0]).map { it.eventBox() }

        assertEquals("Long meeting", normal.text.toString())
        assertEquals(3, normal.maxLines)
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, normal.autoSizeTextType)
        assertEquals("Call", compact.text.toString())
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, compact.autoSizeTextType)
        assertEquals("", none.text.toString())
    }

    @Test
    fun eventsSitAtTheirTimesAndColumns() {
        val root = draw()
        val column = root.columns()[0]
        val height = column.height
        val separator = (targetContext.resources.displayMetrics.density * 1).toInt()
        val width = column.width - separator
        val (meetingBox, callBox) = eventsOf(column).map { rectIn(column, it.findViewById(R.id.event_h2)) }

        assertEquals(0.25f * height, meetingBox.top.toFloat(), 1.5f)
        assertEquals(0.5f * height, meetingBox.bottom.toFloat(), 1.5f)
        assertEquals(separator.toFloat(), meetingBox.left.toFloat(), 1.5f)
        assertEquals(column.width.toFloat(), meetingBox.right.toFloat(), 1.5f)
        assertEquals(0.5f * height, callBox.top.toFloat(), 1.5f)
        assertEquals(0.55f * height, callBox.bottom.toFloat(), 1.5f)
        assertEquals(separator + 0.5f * width, callBox.left.toFloat(), 1.5f)
    }

    @Test
    fun currentTimeLineSitsAtTheCurrentTime() {
        val root = draw()
        val column = root.columns()[0]
        val line = (column.findViewById<ViewGroup>(R.id.now_position)).getChildAt(0)

        assertEquals(0.6f * column.height, rectIn(column, line).top.toFloat(), 1.5f)
        assertEquals(null, root.columns()[1].findViewById<View>(R.id.now_position))
    }

    @Test
    fun backgroundTakesTheTransparencyOfTheWidget() {
        val root = draw(WidgetSettings(transparency = 50))

        assertEquals(WidgetSettings(transparency = 50).backgroundAlpha, Color.alpha(root.backgroundTintList!!.defaultColor))
    }

    @Test
    fun tapsOpenWhatTheLayoutSays() {
        val root = draw()
        val (mondayCell, tuesdayCell) = root.childrenOf(R.id.banners).single().childrenOf(R.id.banner_row)
        val (mondayColumn, tuesdayColumn) = root.columns()

        assertEquals(monday.toString(), tap(root.childrenOf(R.id.headers)[0]))
        assertEquals(tuesday.toString(), tap(root.childrenOf(R.id.headers)[1]))
        assertEquals(holiday.clickTarget().toString(), tap(mondayCell.findViewById(R.id.banner_chip)))
        assertEquals(monday.toString(), tap(mondayCell))
        // The "+N" text lets taps through to its cell.
        assertFalse(onMain { tuesdayCell.findViewById<View>(R.id.banner_more).hasOnClickListeners() })
        assertEquals(tuesday.toString(), tap(tuesdayCell))
        assertEquals(meeting.clickTarget().toString(), tap(eventsOf(mondayColumn)[0].eventBox()))
        assertEquals(reminder.clickTarget().toString(), tap(eventsOf(mondayColumn)[2].eventBox()))
        assertEquals(monday.toString(), tap(mondayColumn))
        assertEquals(tuesday.toString(), tap(tuesdayColumn))
    }

    @Test
    fun hourLabelsAreNotTappable() {
        assertTrue(draw().childrenOf(R.id.hours).flatMap { it.descendants() }.none { it.hasOnClickListeners() })
    }

    @Test
    fun withoutClicksNothingIsTappable() {
        assertTrue(draw(clicks = null).descendants().none { it.hasOnClickListeners() })
    }

    @Test
    fun drawingAgainOnTheSameViewsDoesNotDuplicateThem() {
        // Launchers may apply new views on top of the previous ones.
        val views = WidgetViews(targetContext, WidgetSettings().backgroundAlpha, null).build(layout)
        val root = onMain { views.apply(targetContext, FrameLayout(targetContext)).also { views.reapply(targetContext, it) } }

        assertEquals(2, root.childrenOf(R.id.headers).size)
        assertEquals(1, root.childrenOf(R.id.banners).size)
        assertEquals(3, root.childrenOf(R.id.hours).size)
        assertEquals(2, root.columns().size)
    }

    private companion object {
        const val TAP = "it.hourbyhour.test.TAP"
        const val TARGET = "target"
    }
}
