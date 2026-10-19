package it.hourbyhour

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import it.hourbyhour.PaletteRole.ON_SURFACE
import it.hourbyhour.PaletteRole.ON_SURFACE_VARIANT
import it.hourbyhour.PaletteRole.PRIMARY
import it.hourbyhour.PaletteRole.PRIMARY_CONTAINER
import it.hourbyhour.PaletteRole.SURFACE
import org.junit.Assert.assertEquals
import org.junit.Test

/** The screens of the app take the colors of the system palette (Material You), like the widget, in both themes. */
class ThemeSystemColorsDeviceTest {
    private fun Context.color(attr: Int): Int = obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, 0) }

    private fun checkColors(theme: Int, opaqueWindow: Boolean) {
        for ((name, themed) in bothThemes) {
            val context = ContextThemeWrapper(themed, theme)
            val where = "${context.resources.getResourceEntryName(theme)}, $name"

            assertEquals("Background, $where", SURFACE.color(context), context.color(android.R.attr.colorBackground))
            assertEquals("Action bar, $where", SURFACE.color(context), context.color(android.R.attr.colorPrimary))
            assertEquals("Text, $where", ON_SURFACE.color(context), context.color(android.R.attr.textColorPrimary))
            assertEquals("Secondary text, $where", ON_SURFACE_VARIANT.color(context), context.color(android.R.attr.textColorSecondary))
            assertEquals("Controls, $where", PRIMARY.color(context), context.color(android.R.attr.colorAccent))
            assertEquals("Buttons, $where", PRIMARY_CONTAINER.color(context), context.color(android.R.attr.colorButtonNormal))
            if (opaqueWindow) {
                val window = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground)).use { it.getDrawable(0) }
                assertEquals("Window, $where", SURFACE.color(context), (window as ColorDrawable).color)
            }
        }
    }

    @Test
    fun theAppThemeTakesThePaletteOfEachTheme() = checkColors(R.style.Theme_HourByHour, opaqueWindow = true)

    @Test
    fun theSettingsThemeTakesThePaletteOfEachTheme() = checkColors(R.style.Theme_HourByHour_WidgetSettings, opaqueWindow = false)

    @Test
    fun theScreensUseTheseThemes() {
        val packageManager = targetContext.packageManager
        fun themeOf(activity: Class<*>) = packageManager.getActivityInfo(ComponentName(targetContext, activity), 0).themeResource

        assertEquals(R.style.Theme_HourByHour, targetContext.applicationInfo.theme)
        assertEquals(R.style.Theme_HourByHour, themeOf(MainActivity::class.java))
        assertEquals(R.style.Theme_HourByHour_WidgetSettings, themeOf(WidgetSettingsActivity::class.java))
    }
}
