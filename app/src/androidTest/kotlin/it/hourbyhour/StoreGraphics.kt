package it.hourbyhour

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Draws the pictures of the Google Play store listing with made-up events: the icon, the feature
 * graphic and the phone screenshots, in English and Italian. The widget is the real one, drawn as
 * a launcher would. Not a test: it runs only when asked, and saves into the files of the app (see
 * "Scheda dello store" in CLAUDE.md).
 */
class StoreGraphics {
    private val density = targetContext.resources.displayMetrics.density
    private val output = File(targetContext.filesDir, "store")
    private val zone = ZoneId.systemDefault()

    /** Tuesday morning, in a week with events every day. */
    private val now = LocalDate.of(2026, 10, 13).atTime(10, 40).atZone(zone)

    @Before
    fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeGraphics") == "true")
    }

    @Test
    fun icon() {
        for (language in Language.entries) save("${language.tag}/images/icon.png", onMain { icon(512) })
    }

    @Test
    fun featureGraphic() {
        for (language in Language.entries) save("${language.tag}/images/featureGraphic.png", onMain { featureGraphic(language) })
    }

    @Test
    fun screenshots() {
        for (language in Language.entries) {
            val shots = listOf(
                Shot(language.text("Your calendar, hour by hour", "Il tuo calendario, ora per ora"), dark = true, WidgetSettings()),
                Shot(
                    language.text("Up to 7 days, from today or from Monday", "Fino a 7 giorni, da oggi o da lunedì"),
                    dark = false,
                    WidgetSettings(dayCount = 7, startOnMonday = true),
                ),
                Shot(
                    language.text("As transparent as you like", "Trasparente quanto vuoi"),
                    dark = true,
                    WidgetSettings(transparency = 100, dayCount = 5),
                ),
                Shot(
                    language.text("Tap an event to open it in your calendar", "Tocca un evento per aprirlo nel calendario"),
                    dark = false,
                    WidgetSettings(transparency = 20, dayCount = 4),
                ),
            )
            shots.forEachIndexed { index, shot ->
                save("${language.tag}/images/phoneScreenshots/${index + 1}_${language.tag}.png", onMain { screenshot(shot, language) })
            }
        }
    }

    private enum class Language(val tag: String, val locale: Locale) {
        ENGLISH("en-US", Locale.US),
        ITALIAN("it-IT", Locale.ITALY),
        ;

        fun text(english: String, italian: String) = if (this == ENGLISH) english else italian
    }

    private class Shot(val caption: String, val dark: Boolean, val settings: WidgetSettings)

    private fun save(path: String, bitmap: Bitmap) {
        val file = File(output, path)
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * The launcher icon filling the whole square, as Google Play wants it: the store rounds the
     * corners. The layers are 108dp, of which launchers show the middle 72dp.
     */
    private fun icon(size: Int): Bitmap {
        val icon = targetContext.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val extra = size * 18 / 72
        for (layer in listOf(icon.background, icon.foreground)) {
            layer.setBounds(-extra, -extra, size + extra, size + extra)
            layer.draw(canvas)
        }
        return bitmap
    }

    private fun featureGraphic(language: Language): Bitmap {
        val bitmap = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(gradient(1024, 500, BLUE, DARK_BLUE))
        // Rounded like on a launcher.
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(icon(360), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        canvas.save()
        canvas.translate(70f, 70f)
        canvas.drawRoundRect(0f, 0f, 360f, 360f, 80f, 80f, icon)
        canvas.restore()
        val title = text(Color.WHITE, 76f, bold = true)
        canvas.drawText(targetContext.getString(R.string.app_name), 480f, 215f, title)
        val tagline = language.text("Your calendar, hour by hour, on the home screen", "Il tuo calendario, ora per ora, sulla schermata Home")
        draw(canvas, tagline, text(Color.WHITE, 38f, bold = false), x = 480f, y = 250f, width = 480)
        return bitmap
    }

    /** A phone screenshot in the 9:16 that Google Play shows best: a caption above the widget, on a wallpaper. */
    private fun screenshot(shot: Shot, language: Language): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(if (shot.dark) gradient(width, height, DARK_TOP, DARK_BOTTOM) else gradient(width, height, LIGHT_TOP, LIGHT_BOTTOM))
        val captionColor = if (shot.dark) Color.WHITE else DARK_TEXT
        draw(canvas, shot.caption, text(captionColor, 66f, bold = true), x = 70f, y = 150f, width = width - 140, center = true)
        drawWidget(canvas, shot, language, x = 40, y = 420, width = width - 80, height = 1420)
        return bitmap
    }

    private fun drawWidget(canvas: Canvas, shot: Shot, language: Language, x: Int, y: Int, width: Int, height: Int) {
        val context = themed(shot.dark)
        val snapshot = Agenda(events(language), now).snapshot(shot.settings)
        val layout = layoutWidget(snapshot, WidgetSize(width / density, height / density), fontScale = 1f, language.locale)
        val root = WidgetViews(context, shot.settings, clicks = null).build(layout).apply(context, FrameLayout(context))
        root.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        // Single-line texts are placed by their gravity just before being drawn in a window, which
        // these views never are: without this, centered and right-aligned labels end up off the view.
        root.descendants().filterIsInstance<TextView>().forEach { it.onPreDraw() }
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        root.draw(canvas)
        canvas.restore()
    }

    /** The app in the light or dark theme, whatever the theme of the phone. */
    private fun themed(dark: Boolean): Context {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return targetContext.createConfigurationContext(configuration)
    }

    /** A week of made-up events, from Monday 12 to Sunday 18 October 2026, in Google Calendar colors. */
    private fun events(language: Language): List<CalendarEvent> {
        fun at(day: Int, hour: Int, minute: Int) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

        fun event(english: String, italian: String, day: Int, from: Pair<Int, Int>, to: Pair<Int, Int>, color: Int) =
            CalendarEvent(language.text(english, italian), at(day, from.first, from.second), at(day, to.first, to.second), false, color)

        // Stored at UTC midnights, like the calendar provider does.
        fun allDay(english: String, italian: String, day: Int, days: Int, color: Int): CalendarEvent {
            val start = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return CalendarEvent(language.text(english, italian), start, start + days * 24 * 3_600_000L, true, color)
        }

        val standups = (12..16).map { event("Stand-up", "Stand-up", it, 9 to 0, 9 to 15, PEACOCK) }
        return standups + listOf(
            event("Gym", "Palestra", 12, 7 to 0, 8 to 0, SAGE),
            event("Project review", "Revisione del progetto", 12, 14 to 0, 15 to 30, PEACOCK),
            event("Call with Marco", "Chiamata con Marco", 12, 14 to 30, 15 to 0, LAVENDER),
            event("Dentist", "Dentista", 13, 11 to 0, 11 to 45, FLAMINGO),
            event("Lunch with Anna", "Pranzo con Anna", 13, 12 to 30, 13 to 30, GRAPE),
            event("Yoga", "Yoga", 13, 18 to 30, 19 to 30, SAGE),
            event("Gym", "Palestra", 14, 7 to 0, 8 to 0, SAGE),
            event("Design workshop", "Workshop di design", 14, 10 to 0, 12 to 0, PEACOCK),
            event("Pick up the kids", "Prendere i bambini", 14, 16 to 30, 17 to 0, TANGERINE),
            event("Client meeting", "Incontro con il cliente", 15, 10 to 30, 11 to 30, PEACOCK),
            event("Yoga", "Yoga", 15, 18 to 30, 19 to 30, SAGE),
            allDay("Mum's birthday", "Compleanno della mamma", 15, 1, BANANA),
            event("Gym", "Palestra", 16, 7 to 0, 8 to 0, SAGE),
            event("Sprint planning", "Pianificazione sprint", 16, 11 to 0, 12 to 0, PEACOCK),
            event("Dinner with friends", "Cena con amici", 16, 20 to 0, 22 to 30, GRAPE),
            allDay("Weekend in Rome", "Weekend a Roma", 17, 2, FLAMINGO),
            event("Football", "Calcetto", 17, 10 to 0, 11 to 30, SAGE),
            event("Groceries", "Spesa", 17, 17 to 0, 18 to 0, TANGERINE),
            event("Brunch", "Brunch", 18, 11 to 0, 12 to 30, GRAPE),
            event("Reading", "Lettura", 18, 21 to 0, 22 to 0, LAVENDER),
        )
    }

    private fun gradient(width: Int, height: Int, from: Int, to: Int) = Paint().apply {
        shader = LinearGradient(0f, 0f, width * 0.3f, height.toFloat(), from, to, Shader.TileMode.CLAMP)
    }

    private fun text(color: Int, size: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** [text] wrapped to [width], from its top left corner at [x], [y]. */
    private fun draw(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Int, center: Boolean = false) {
        val alignment = if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(alignment).build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private companion object {
        // Google Calendar colors, as the calendar provider gives them.
        const val PEACOCK = 0xFF039BE5.toInt()
        const val SAGE = 0xFF33B679.toInt()
        const val LAVENDER = 0xFF7986CB.toInt()
        const val FLAMINGO = 0xFFE67C73.toInt()
        const val GRAPE = 0xFF8E24AA.toInt()
        const val TANGERINE = 0xFFF4511E.toInt()
        const val BANANA = 0xFFF6BF26.toInt()

        const val BLUE = 0xFF1A73E8.toInt()
        const val DARK_BLUE = 0xFF0B47A1.toInt()
        const val DARK_TOP = 0xFF1E2A4A.toInt()
        const val DARK_BOTTOM = 0xFF3B1F4F.toInt()
        const val LIGHT_TOP = 0xFFDCE8FB.toInt()
        const val LIGHT_BOTTOM = 0xFFF6E3EC.toInt()
        const val DARK_TEXT = 0xFF1F1F1F.toInt()
    }
}
