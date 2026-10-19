package it.hourbyhour

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The screens of the app take the colors of the system palette (Material You), like the widget, in
 * the light and the dark theme. The palette is the one of the phone as it is now.
 */
class ThemeSystemColorsDeviceTest {
    /** The colors of the screens in one theme, as palette resources. */
    private class Expected(val background: Int, val text: Int, val textSecondary: Int, val accent: Int, val button: Int)

    private val light = Expected(
        background = android.R.color.system_neutral1_10,
        text = android.R.color.system_neutral1_900,
        textSecondary = android.R.color.system_neutral2_700,
        accent = android.R.color.system_accent1_600,
        button = android.R.color.system_accent1_100,
    )
    private val dark = Expected(
        background = android.R.color.system_neutral1_900,
        text = android.R.color.system_neutral1_100,
        textSecondary = android.R.color.system_neutral2_200,
        accent = android.R.color.system_accent1_200,
        button = android.R.color.system_accent1_700,
    )

    private fun Context.color(attr: Int): Int = obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, 0) }

    private fun checkColors(theme: Int, opaqueWindow: Boolean) {
        for ((isDark, expected) in listOf(false to light, true to dark)) {
            val context = ContextThemeWrapper(themedContext(isDark), theme)
            fun palette(id: Int) = context.getColor(id)
            val name = "${context.resources.getResourceEntryName(theme)}, ${if (isDark) "dark" else "light"}"

            assertEquals("Background, $name", palette(expected.background), context.color(android.R.attr.colorBackground))
            assertEquals("Action bar, $name", palette(expected.background), context.color(android.R.attr.colorPrimary))
            assertEquals("Text, $name", palette(expected.text), context.color(android.R.attr.textColorPrimary))
            assertEquals("Secondary text, $name", palette(expected.textSecondary), context.color(android.R.attr.textColorSecondary))
            assertEquals("Controls, $name", palette(expected.accent), context.color(android.R.attr.colorAccent))
            assertEquals("Buttons, $name", palette(expected.button), context.color(android.R.attr.colorButtonNormal))
            if (opaqueWindow) {
                val window = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground)).use { it.getDrawable(0) }
                assertEquals("Window, $name", palette(expected.background), (window as ColorDrawable).color)
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
