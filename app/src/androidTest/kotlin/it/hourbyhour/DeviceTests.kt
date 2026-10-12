package it.hourbyhour

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry

// Helpers for the tests that run on the phone, in the process of the app.

val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()

/** The context of the app under test. */
val targetContext: Context get() = instrumentation.targetContext

/**
 * Waits until the widget settings saved with apply() are on disk. The test process ends as soon as
 * the last test does, and would lose them. A commit waits for the writes queued before it.
 */
fun flushWidgetSettings() {
    targetContext.getSharedPreferences(WidgetSettingsStore.FILE_NAME, Context.MODE_PRIVATE).edit().commit()
}

/** Runs [block] on the main thread, where views live, and returns its result. */
fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    instrumentation.runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}

/** Waits until [condition], checked on the main thread, holds. */
fun waitFor(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (!onMain(condition)) {
        if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
        Thread.sleep(50)
    }
}

/** [this] and all the views inside it. */
fun View.descendants(): List<View> =
    listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

/** The children of the view group [id] inside [this]; none while it is not there. */
fun View.childrenOf(id: Int): List<View> {
    val group = findViewById<ViewGroup>(id) ?: return emptyList()
    return (0 until group.childCount).map { group.getChildAt(it) }
}
