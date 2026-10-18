package it.hourbyhour

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
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Draws the pictures of the Google Play store listing with made-up events: the icon, the feature
 * graphic and the phone screenshots, in English and Italian, in the sizes Google Play asks for. The
 * widget is the real one, drawn as a launcher would, with the events of [SampleAgenda]. The pictures
 * stay in the files of the app, from where they are copied into the repository when they change
 * ("Scheda dello store" in CLAUDE.md).
 */
class StoreGraphics {
    private val density = targetContext.resources.displayMetrics.density
    private val output = File(targetContext.filesDir, "store")
    private val zone = ZoneId.systemDefault()

    /** Tuesday morning, in a week with events every day. */
    private val now = LocalDate.of(2026, 10, 13).atTime(10, 40).atZone(zone)

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
        val (width, height) = when {
            path.endsWith("icon.png") -> 512 to 512
            path.endsWith("featureGraphic.png") -> 1024 to 500
            else -> 1080 to 1920
        }
        assertEquals("Size of $path", width to height, bitmap.width to bitmap.height)
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
        val context = themedContext(shot.dark)
        val snapshot = Agenda(events(language), now).snapshot(shot.settings)
        val layout = layoutWidget(snapshot, WidgetSize(width / density, height / density), fontScale = 1f, language.locale)
        val root = WidgetViews(context, shot.settings.backgroundAlpha, clicks = null).build(layout).apply(context, FrameLayout(context))
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

    /** The made-up week from Monday 12 to Sunday 18 October 2026, titled in [language]. */
    private fun events(language: Language): List<CalendarEvent> {
        val configuration = Configuration(targetContext.resources.configuration).apply { setLocale(language.locale) }
        val resources = targetContext.createConfigurationContext(configuration).resources
        return SampleAgenda.events(LocalDate.of(2026, 10, 12), zone) { resources.getString(it.stringId) }
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
        const val BLUE = 0xFF1A73E8.toInt()
        const val DARK_BLUE = 0xFF0B47A1.toInt()
        const val DARK_TOP = 0xFF1E2A4A.toInt()
        const val DARK_BOTTOM = 0xFF3B1F4F.toInt()
        const val LIGHT_TOP = 0xFFDCE8FB.toInt()
        const val LIGHT_BOTTOM = 0xFFF6E3EC.toInt()
        const val DARK_TEXT = 0xFF1F1F1F.toInt()
    }
}
