package it.hourbyhour

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainActivityDeviceTest {
    @Test
    fun withCalendarAccessShowsAPreviewThatCannotBeTapped() {
        assumeTrue("Calendar access not granted", CalendarSource.hasPermission(targetContext))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            onMain {
                assertEquals(activity.getString(R.string.status_ready), activity.findViewById<TextView>(R.id.status).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.grant).visibility)
            }
            val preview = onMain { activity.findViewById<View>(R.id.preview) }
            waitFor("the preview") { preview.findViewById<View>(R.id.headers) != null }
            // A tap on the preview would leave the app.
            assertTrue(onMain { preview.descendants().none { it.hasOnClickListeners() } })
        }
    }
}
