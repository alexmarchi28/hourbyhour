package it.hourbyhour

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
 * With system colors on, the widget takes the colors of the system palette (Material You), in the
 * light and the dark theme; by default it keeps the Google Calendar ones. The colors are resources
 * resolved where the views are applied, as the launcher does.
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

    private val View.fill get() = (background as ColorDrawable).color

    /** The colors a widget drawn with [settings] in [context] shows, by part. */
    private fun colorsOf(settings: WidgetSettings, context: Context): Map<String, Int> {
        val root = draw(WidgetViews(context, settings, clicks = null).build(layout), context)
        val (todayHeader, otherHeader) = root.childrenOf(R.id.headers).map { it as TextView }
        val hourRow = root.childrenOf(R.id.hours).first() as ViewGroup
        val more = root.descendants().single { it.id == R.id.banner_more } as TextView
        val separator = (root.childrenOf(R.id.columns).first() as ViewGroup).getChildAt(0)
        return mapOf(
            "Background" to root.findViewById<ImageView>(android.R.id.background).imageTintList!!.defaultColor,
            "Today" to todayHeader.currentTextColor,
            "Other days" to otherHeader.currentTextColor,
            "Hour labels" to (hourRow.getChildAt(0) as TextView).currentTextColor,
            "+N" to more.currentTextColor,
            "Hour lines" to hourRow.getChildAt(1).fill,
            "Grid bottom" to root.findViewById<View>(R.id.grid_bottom).fill,
            "Day separators" to separator.fill,
        )
    }

    @Test
    fun withSystemColorsTheWidgetTakesThePaletteOfEachTheme() {
        for ((theme, context) in bothThemes) {
            val expected = mapOf(
                "Background" to SURFACE_CONTAINER,
                "Today" to PRIMARY,
                "Other days" to ON_SURFACE,
                "Hour labels" to ON_SURFACE_VARIANT,
                "+N" to ON_SURFACE_VARIANT,
                "Hour lines" to DIVIDER,
                "Grid bottom" to DIVIDER,
                "Day separators" to DIVIDER,
            ).mapValues { it.value.color(context) }

            assertEquals(theme, expected, colorsOf(WidgetSettings(systemColors = true), context))
        }
    }

    @Test
    fun byDefaultTheWidgetKeepsTheGoogleCalendarColors() {
        for ((theme, context) in bothThemes) {
            val expected = mapOf(
                "Background" to R.color.widget_background,
                "Today" to R.color.widget_today,
                "Other days" to R.color.widget_text,
                "Hour labels" to R.color.widget_text_secondary,
                "+N" to R.color.widget_text_secondary,
                "Hour lines" to R.color.widget_grid,
                "Grid bottom" to R.color.widget_grid,
                "Day separators" to R.color.widget_grid,
            ).mapValues { context.getColor(it.value) }

            assertEquals(theme, expected, colorsOf(WidgetSettings(), context))
        }
    }

    @Test
    fun theMessageTakesThePaletteOfTheWidget() {
        for ((theme, context) in bothThemes) {
            val system = draw(WidgetViews(context, WidgetSettings(systemColors = true), clicks = null).message(), context)
            val google = draw(WidgetViews(context, WidgetSettings(), clicks = null).message(), context)

            assertEquals("Background, $theme", SURFACE_CONTAINER.color(context), system.findViewById<ImageView>(android.R.id.background).imageTintList!!.defaultColor)
            assertEquals("Text, $theme", ON_SURFACE.color(context), system.findViewById<TextView>(R.id.message).currentTextColor)
            assertEquals("Default background, $theme", context.getColor(R.color.widget_background), google.findViewById<ImageView>(android.R.id.background).imageTintList!!.defaultColor)
            assertEquals("Default text, $theme", context.getColor(R.color.widget_text), google.findViewById<TextView>(R.id.message).currentTextColor)
        }
    }

    @Test
    fun thePickerPreviewShowsTheDefaultColors() {
        // The picker shows a widget with the default settings, so in the Google Calendar colors.
        for ((theme, context) in bothThemes) {
            val root = draw(RemoteViews(targetContext.packageName, R.layout.widget_preview), context)
            val headers = root.childrenOf(R.id.preview_headers).map { it as TextView }

            assertEquals("Today, $theme", context.getColor(R.color.widget_today), headers.first().currentTextColor)
            assertEquals("Other days, $theme", context.getColor(R.color.widget_text), headers.last().currentTextColor)
        }
    }
}
