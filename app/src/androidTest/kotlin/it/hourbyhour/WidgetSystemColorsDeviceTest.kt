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
import it.hourbyhour.PaletteRole.DIVIDER
import it.hourbyhour.PaletteRole.ON_SURFACE
import it.hourbyhour.PaletteRole.ON_SURFACE_VARIANT
import it.hourbyhour.PaletteRole.PRIMARY
import it.hourbyhour.PaletteRole.SURFACE_CONTAINER
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget takes the colors of the system palette (Material You), in the light and the dark
 * theme. The colors are references to the palette resolved where the views are inflated.
 */
class WidgetSystemColorsDeviceTest {
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
        for ((theme, context) in bothThemes) {
            val settings = WidgetSettings(transparency = 50)
            val root = draw(WidgetViews(context, settings.backgroundAlpha, clicks = null).build(layout), context)
            val (todayHeader, otherHeader) = root.childrenOf(R.id.headers).map { it as TextView }
            val hourRow = root.childrenOf(R.id.hours).first() as ViewGroup
            val more = root.descendants().single { it.id == R.id.banner_more } as TextView
            val separator = (root.childrenOf(R.id.columns).first() as ViewGroup).getChildAt(0)

            val background = root.backgroundTintList!!.defaultColor
            assertEquals("Background, $theme", SURFACE_CONTAINER.color(context), opaque(background))
            assertEquals("Transparency is kept, $theme", settings.backgroundAlpha, Color.alpha(background))
            assertEquals("Today, $theme", PRIMARY.color(context), todayHeader.currentTextColor)
            assertEquals("Other days, $theme", ON_SURFACE.color(context), otherHeader.currentTextColor)
            assertEquals("Hour labels, $theme", ON_SURFACE_VARIANT.color(context), (hourRow.getChildAt(0) as TextView).currentTextColor)
            assertEquals("+N, $theme", ON_SURFACE_VARIANT.color(context), more.currentTextColor)
            assertEquals("Hour lines, $theme", DIVIDER.color(context), hourRow.getChildAt(1).fill)
            assertEquals("Day separators, $theme", DIVIDER.color(context), separator.fill)
        }
    }

    @Test
    fun theMessageTakesThePaletteOfEachTheme() {
        for ((theme, context) in bothThemes) {
            val root = draw(WidgetViews(context, 255, clicks = null).message(), context) as TextView

            assertEquals("Background, $theme", SURFACE_CONTAINER.color(context), root.backgroundTintList!!.defaultColor)
            assertEquals("Text, $theme", ON_SURFACE.color(context), root.currentTextColor)
        }
    }

    @Test
    fun thePickerPreviewTakesThePaletteOfEachTheme() {
        for ((theme, context) in bothThemes) {
            val root = draw(RemoteViews(targetContext.packageName, R.layout.widget_preview), context)
            val headers = root.childrenOf(R.id.preview_headers).map { it as TextView }

            assertEquals("Today, $theme", PRIMARY.color(context), headers.first().currentTextColor)
            assertEquals("Other days, $theme", ON_SURFACE.color(context), headers.last().currentTextColor)
        }
    }
}
