package it.hourbyhour

import android.content.Context

/**
 * The tone of the system palette (Material You) that each role of colors.xml takes in the light and
 * the dark theme, written out again so that the tests check it. The tones are compared with the
 * palette of the phone as it is now.
 */
enum class PaletteRole(private val light: Int, private val dark: Int) {
    SURFACE(android.R.color.system_neutral1_10, android.R.color.system_neutral1_900),
    SURFACE_CONTAINER(android.R.color.system_neutral1_50, android.R.color.system_neutral1_900),
    ON_SURFACE(android.R.color.system_neutral1_900, android.R.color.system_neutral1_100),
    ON_SURFACE_VARIANT(android.R.color.system_neutral2_700, android.R.color.system_neutral2_200),
    DIVIDER(android.R.color.system_neutral2_100, android.R.color.system_neutral2_700),
    PRIMARY(android.R.color.system_accent1_600, android.R.color.system_accent1_200),
    PRIMARY_CONTAINER(android.R.color.system_accent1_100, android.R.color.system_accent1_700),
    ;

    /** The color of this role in the theme of [context], which must be one of the app. */
    fun color(context: Context): Int {
        val dark = context.resources.configuration.isNightModeActive
        return context.getColor(if (dark) this.dark else light)
    }
}

/** Both themes of the app, with their names for the messages. */
val bothThemes: List<Pair<String, Context>> get() = listOf("light" to themedContext(dark = false), "dark" to themedContext(dark = true))
