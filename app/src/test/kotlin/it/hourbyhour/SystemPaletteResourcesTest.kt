package it.hourbyhour

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The colors of the widget and of the screens come from the system palette (Material You), through
 * the palette roles (palette_*) of colors.xml: one place says which tone of the palette each role
 * takes in each theme. The tests on the phone check the colors drawn; these run in the CI too.
 */
class SystemPaletteResourcesTest {
    /** The colors defined in a colors.xml of the app, by name. */
    private fun colors(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/colors.xml"))
        val nodes = document.getElementsByTagName("color")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private val day = colors("values")
    private val night = colors("values-night")
    private val roles = day.filterKeys { it.startsWith("palette_") }

    /**
     * Colors that stay fixed on purpose: the default look of the widget is Google Calendar's (widget_*),
     * the current time is red as in Google Calendar, events keep their colors.
     */
    private fun isFixed(name: String) =
        (name.startsWith("widget_") && !isSystem(name)) || name.startsWith("preview_") || name == "ic_launcher_background"

    /** The colors of a widget with system colors on (WidgetSettings.systemColors). */
    private fun isSystem(name: String) = name.startsWith("widget_system_")

    @Test
    fun everyRoleIsAToneOfTheSystemPalette() {
        assertTrue("No palette roles", roles.isNotEmpty())
        for ((name, value) in roles + night.filterKeys { it.startsWith("palette_") }) {
            assertTrue("$name is $value", value.matches(Regex("@android:color/system_(accent[123]|neutral[12])_\\d+")))
        }
    }

    @Test
    fun theNightThemeRedefinesEveryRole() {
        assertEquals(roles.keys, night.keys.filter { it.startsWith("palette_") }.toSet())
        for (name in roles.keys) assertNotEquals("$name is the same in both themes", roles[name], night[name])
    }

    @Test
    fun theWidgetWithSystemColorsTakesThemFromTheRoles() {
        val widget = day.filterKeys(::isSystem)
        assertTrue("No system colors for the widget", widget.isNotEmpty())
        for ((name, value) in widget) {
            assertTrue("$name is $value", value.removePrefix("@color/") in roles)
        }
        // They follow the theme through the roles: the night theme does not redefine them.
        assertTrue(night.keys.none(::isSystem))
    }

    @Test
    fun theWidgetHasASystemColorForEachOfItsOwnColors() {
        // WidgetViews sets one or the other for each part of the widget.
        val own = day.keys.filter { it.startsWith("widget_") && !isSystem(it) && it != "widget_now" }
        assertEquals(own.map { it.replace("widget_", "widget_system_") }.toSet(), day.keys.filter(::isSystem).toSet())
    }

    @Test
    fun nothingElseOutsideTheRolesIsAFixedColor() {
        for ((name, value) in day + night) {
            if (name.startsWith("palette_") || isFixed(name)) continue
            assertTrue("$name is $value", value.startsWith("@color/palette_"))
        }
    }

    @Test
    fun theScreensTakeTheirColorsFromTheRoles() {
        val theme = File("src/main/res/values/themes.xml").readText()
        val colors = Regex("@color/(\\w+)").findAll(theme).map { it.groupValues[1] }.toSet()
        assertTrue("No colors in the themes", colors.isNotEmpty())
        for (name in colors) {
            val selector = File("src/main/res/color/$name.xml")
            val references = if (selector.exists()) {
                Regex("@color/(\\w+)").findAll(selector.readText()).map { it.groupValues[1] }.toList()
            } else {
                listOf(name)
            }
            for (reference in references) assertTrue("$name refers to $reference", reference in roles)
        }
    }
}
