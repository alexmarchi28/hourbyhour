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

    /** Colors that stay fixed on purpose: the current time is red as in Google Calendar, events keep their colors. */
    private fun isFixed(name: String) = name == "widget_now" || name.startsWith("preview_") || name == "ic_launcher_background"

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
    fun theWidgetTakesItsColorsFromTheRoles() {
        val widget = day.filterKeys { it.startsWith("widget_") && !isFixed(it) }
        assertTrue("No widget colors", widget.isNotEmpty())
        for ((name, value) in widget) {
            assertTrue("$name is $value", value.removePrefix("@color/") in roles)
        }
        // They follow the theme through the roles: the night theme does not redefine them.
        assertTrue(night.keys.none { it.startsWith("widget_") && !isFixed(it) })
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
