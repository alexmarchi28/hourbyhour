package it.hourbyhour

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget takes the colors of the system palette (Material You), in the light and the dark
 * theme. The colors are references to the palette resolved where the views are inflated, so they
 * are compared with the palette of the phone as it is now.
 */
class WidgetSystemColorsDeviceTest {
    /** The colors of the widget in one theme, as palette resources. */
    private class Expected(val background: Int, val text: Int, val textSecondary: Int, val grid: Int, val today: Int)

    private val light = Expected(
        background = android.R.color.system_neutral1_50,
        text = android.R.color.system_neutral1_900,
        textSecondary = android.R.color.system_neutral2_700,
        grid = android.R.color.system_neutral2_100,
        today = android.R.color.system_accent1_600,
    )
    private val dark = Expected(
        background = android.R.color.system_neutral1_900,
        text = android.R.color.system_neutral1_100,
        textSecondary = android.R.color.system_neutral2_200,
        grid = android.R.color.system_neutral2_700,
        today = android.R.color.system_accent1_200,
    )

    private val tap = ClickTarget.Time(0)
    private val layout = WidgetLayout(
        headers = listOf(DayHeader("Lun 12", today = true, tap), DayHeader("Mar 13", today = false, tap)),
        bannerRows = listOf(listOf(BannerCell.More(2, tap), BannerCell.Empty(tap))),
        hourLabels = listOf("8", "9"),
        columns = listOf(DayColumn(emptyList(), nowLine = null, click = tap), DayColumn(emptyList(), nowLine = null, click = tap)),
    )

    private fun draw(views: RemoteViews, context: Context): View = onMain {
        views.apply(context, FrameLayout(context)).apply {
            measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(900, MeasureSpec.EXACTLY))
            layout(0, 0, 600, 900)
        }
    }

    private fun opaque(color: Int) = color or 0xFF000000.toInt()

    private val View.fill get() = (background as ColorDrawable).color

    @Test
    fun theWidgetTakesThePaletteOfEachTheme() {
        for ((isDark, expected) in listOf(false to light, true to dark)) {
            val context = themedContext(isDark)
            fun palette(id: Int) = context.getColor(id)
            val settings = WidgetSettings(transparency = 50)
            val root = draw(WidgetViews(context, settings.backgroundAlpha, clicks = null).build(layout), context)
            val (todayHeader, otherHeader) = root.childrenOf(R.id.headers).map { it as TextView }
            val hourRow = root.childrenOf(R.id.hours).first() as ViewGroup
            val more = root.descendants().single { it.id == R.id.banner_more } as TextView
            val separator = (root.childrenOf(R.id.columns).first() as ViewGroup).getChildAt(0)
            val theme = if (isDark) "dark" else "light"

            val background = root.backgroundTintList!!.defaultColor
            assertEquals("Background, $theme", palette(expected.background), opaque(background))
            assertEquals("Transparency is kept, $theme", settings.backgroundAlpha, Color.alpha(background))
            assertEquals("Today, $theme", palette(expected.today), todayHeader.currentTextColor)
            assertEquals("Other days, $theme", palette(expected.text), otherHeader.currentTextColor)
            assertEquals("Hour labels, $theme", palette(expected.textSecondary), (hourRow.getChildAt(0) as TextView).currentTextColor)
            assertEquals("+N, $theme", palette(expected.textSecondary), more.currentTextColor)
            assertEquals("Hour lines, $theme", palette(expected.grid), hourRow.getChildAt(1).fill)
            assertEquals("Day separators, $theme", palette(expected.grid), separator.fill)
        }
    }

    @Test
    fun theMessageTakesThePaletteOfEachTheme() {
        for ((isDark, expected) in listOf(false to light, true to dark)) {
            val context = themedContext(isDark)
            val root = draw(WidgetViews(context, 255, clicks = null).message(), context) as TextView

            assertEquals(context.getColor(expected.background), root.backgroundTintList!!.defaultColor)
            assertEquals(context.getColor(expected.text), root.currentTextColor)
        }
    }

    @Test
    fun thePickerPreviewTakesThePaletteOfEachTheme() {
        for ((isDark, expected) in listOf(false to light, true to dark)) {
            val context = themedContext(isDark)
            val root = draw(RemoteViews(targetContext.packageName, R.layout.widget_preview), context)
            val headers = root.childrenOf(R.id.preview_headers).map { it as TextView }

            assertEquals(context.getColor(expected.today), headers.first().currentTextColor)
            assertEquals(context.getColor(expected.text), headers.last().currentTextColor)
        }
    }
}
