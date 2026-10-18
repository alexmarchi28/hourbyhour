package it.hourbyhour

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sizes from which WidgetLayout estimates the space of the widget (LayoutEstimates), against the
 * layouts: the exact ones against dimens.xml, the estimates against the views drawn on the phone.
 * The estimates must not be smaller than the views (titles would get lines that do not fit), nor
 * much larger (the grid would look emptier than it is).
 */
class LayoutEstimatesDeviceTest {
    /** The app with the default font size: WidgetLayout scales the estimates by the font size itself. */
    private val context: Context = targetContext.createConfigurationContext(
        Configuration(targetContext.resources.configuration).apply { fontScale = 1f },
    )
    private val density = context.resources.displayMetrics.density

    /** The size [id] as written in dimens.xml, which must be in [unit]. */
    private fun declared(id: Int, unit: Int): Float {
        val value = TypedValue()
        context.resources.getValue(id, value, true)
        assertEquals("Unit of ${context.resources.getResourceEntryName(id)}", unit, value.complexUnit)
        return TypedValue.complexToFloat(value.data)
    }

    private fun dp(id: Int) = declared(id, TypedValue.COMPLEX_UNIT_DIP)

    private fun sp(id: Int) = declared(id, TypedValue.COMPLEX_UNIT_SP)

    @Test
    fun exactSizesAreTheOnesOfTheLayouts() {
        assertEquals(2 * dp(R.dimen.widget_padding) + dp(R.dimen.widget_gutter), LayoutEstimates.HORIZONTAL_CHROME_DP, DELTA)
        assertEquals(
            dp(R.dimen.column_separator) + 2 * dp(R.dimen.event_margin) + 2 * dp(R.dimen.event_padding_horizontal),
            LayoutEstimates.EVENT_HORIZONTAL_SPACE_DP,
            DELTA,
        )
        assertEquals(dp(R.dimen.event_padding_top) + dp(R.dimen.event_margin_bottom), LayoutEstimates.EVENT_PADDING_DP, DELTA)
        assertEquals(sp(R.dimen.header_text_size), LayoutEstimates.HEADER_TEXT_SP, DELTA)
        assertEquals(sp(R.dimen.event_text_size), LayoutEstimates.EVENT_TEXT_SP, DELTA)
        assertEquals(sp(R.dimen.event_text_min_size), LayoutEstimates.MIN_TITLE_SP, DELTA)
    }

    /** A week of made-up events, with a row of all-day events, drawn as a launcher would. */
    private fun draw(): View = onMain {
        val settings = WidgetSettings(dayCount = 7)
        val agenda = SampleAgenda.agenda(LocalDate.of(2026, 10, 12), ZoneId.of("Europe/Rome")) { it.name }
        val layout = layoutWidget(agenda.snapshot(settings), WidgetSize(WIDTH_DP, HEIGHT_DP), fontScale = 1f, Locale.ITALY)
        val root = WidgetViews(context, settings, clicks = null).build(layout).apply(context, FrameLayout(context))
        root.measure(
            MeasureSpec.makeMeasureSpec((WIDTH_DP * density).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((HEIGHT_DP * density).toInt(), MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root
    }

    /**
     * The estimate is not smaller than the drawn view, give or take [ROUNDING_PX] pixels: padding and
     * text heights are rounded to whole pixels, which on a density like 2.3875 add up to about one.
     */
    private fun assertEstimate(what: String, estimateDp: Float, measuredDp: Float, slackDp: Float) {
        assertTrue("$what: estimated $estimateDp dp, drawn $measuredDp dp", measuredDp <= estimateDp + ROUNDING_PX / density)
        assertTrue("$what: estimated $estimateDp dp, drawn $measuredDp dp", measuredDp >= estimateDp - slackDp)
    }

    @Test
    fun chromeAroundTheGridIsAsEstimated() {
        val root = draw()
        val banners = root.findViewById<View>(R.id.banners)
        val grid = root.findViewById<View>(R.id.columns)

        assertEstimate("Chrome", LayoutEstimates.CHROME_HEIGHT_DP, (root.height - grid.height - banners.height) / density, slackDp = 6f)
    }

    @Test
    fun allDayRowIsAsEstimated() {
        val root = draw()
        val rows = root.childrenOf(R.id.banners)

        assertEquals(1, rows.size)
        assertEstimate("All-day row", LayoutEstimates.BANNER_ROW_HEIGHT_DP, root.findViewById<View>(R.id.banners).height / density, slackDp = 4f)
    }

    @Test
    fun titleLineIsAsEstimated() {
        val root = draw()
        val title = onMain {
            root.descendants().filterIsInstance<TextView>()
                .first { it.id == R.id.event_box && it.autoSizeTextType == TextView.AUTO_SIZE_TEXT_TYPE_NONE }
        }

        assertEstimate("Title line", LayoutEstimates.LINE_HEIGHT_SP, title.lineHeight / density, slackDp = 2f)
    }

    private companion object {
        const val WIDTH_DP = 400f
        const val HEIGHT_DP = 500f
        const val DELTA = 0.01f
        const val ROUNDING_PX = 2f
    }
}
