package it.hourbyhour

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** What the launcher's widget picker shows before the widget is added. */
class WidgetPickerPreviewDeviceTest {
    private val density = targetContext.resources.displayMetrics.density
    private val provider = ComponentName(targetContext, HourByHourWidget::class.java)

    /** [views] applied and laid out as a launcher would, in [context]. */
    private fun show(views: RemoteViews, context: Context = targetContext): View = onMain {
        val root = views.apply(context, FrameLayout(context))
        val width = (300 * density).toInt()
        val height = (220 * density).toInt()
        root.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        root
    }

    private fun View.texts() = descendants().filterIsInstance<TextView>().map { it.text.toString() }

    private fun string(id: Int) = targetContext.getString(id)

    @Test
    fun thePickerGetsThePreviewLayout() {
        val info = AppWidgetManager.getInstance(targetContext).getInstalledProvidersForPackage(targetContext.packageName, null)
            .single { it.provider == provider }

        assertEquals(R.layout.widget_preview, info.previewLayout)
    }

    @Test
    fun thePreviewLayoutIsAWidgetWithMadeUpEvents() {
        // RemoteViews refuses the classes a launcher could not inflate.
        val root = show(RemoteViews(targetContext.packageName, R.layout.widget_preview))

        val texts = root.texts()
        for (title in listOf(R.string.sample_project_review, R.string.sample_dentist, R.string.sample_lunch_with_anna, R.string.sample_design_workshop)) {
            assertTrue("${string(title)} in $texts", string(title) in texts)
        }
        assertEquals(3, onMain { root.childrenOf(R.id.preview_headers).size })
    }

    @Test
    fun thePreviewLayoutPlacesEventsByTheirTimes() {
        val root = show(RemoteViews(targetContext.packageName, R.layout.widget_preview))

        fun top(title: Int) = onMain {
            val view = root.descendants().filterIsInstance<TextView>().single { it.text == string(title) }
            IntArray(2).also { view.getLocationInWindow(it) }[1]
        }
        val hours = onMain { root.childrenOf(R.id.preview_hours).map { IntArray(2).also { row -> it.getLocationInWindow(row) }[1] } }
        // The grid starts at 9: the dentist at 11 and the lunch at 12:30.
        assertEquals(hours[2].toFloat(), top(R.string.sample_dentist).toFloat(), 2 * density)
        assertEquals((hours[3] + hours[4]) / 2f, top(R.string.sample_lunch_with_anna).toFloat(), 2 * density)
    }

    @Test
    fun thePreviewLayoutHasTheEventColorsOfEachTheme() {
        for (dark in listOf(false, true)) {
            val context = themedContext(dark)
            val root = show(RemoteViews(targetContext.packageName, R.layout.widget_preview), context)
            val background = context.getColor(R.color.widget_background)
            for ((title, color) in listOf(R.string.sample_dentist to SampleAgenda.FLAMINGO, R.string.sample_design_workshop to SampleAgenda.PEACOCK)) {
                val expected = eventColors(color, past = false, background, background)
                val box = onMain { root.descendants().filterIsInstance<TextView>().single { it.text == string(title) } }
                assertEquals("Fill of ${string(title)}, dark $dark", if (dark) expected.darkFill else expected.lightFill, box.backgroundTintList!!.defaultColor)
                assertEquals("Text of ${string(title)}, dark $dark", if (dark) expected.darkText else expected.lightText, box.currentTextColor)
            }
        }
    }

    @Test
    fun theGeneratedPreviewShowsTheMadeUpEventsOfToday() {
        val today = LocalDate.now()
        val root = show(WidgetPickerPreview.views(targetContext, today, ZoneId.systemDefault()))

        val texts = root.texts()
        for (title in listOf(R.string.sample_project_review, R.string.sample_dentist, R.string.sample_design_workshop)) {
            assertTrue("${string(title)} in $texts", string(title) in texts)
        }
        assertEquals(WidgetSettings.DEFAULT_DAY_COUNT, onMain { root.childrenOf(R.id.headers).size })
        // A tap in the picker must not open the calendar.
        assertTrue(onMain { root.descendants().none { it.hasOnClickListeners() } })
    }

    @Test
    fun theGeneratedPreviewIsPublished() {
        assumeTrue("Generated previews need Android 15", Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)

        WidgetPickerPreview.publishIfNeeded(targetContext)

        val manager = AppWidgetManager.getInstance(targetContext)
        assertNotNull(manager.getWidgetPreview(provider, null, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
    }
}
