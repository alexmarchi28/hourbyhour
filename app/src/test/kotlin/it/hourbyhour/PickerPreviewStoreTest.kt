package it.hourbyhour

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerPreviewStoreTest {
    private val store = PickerPreviewStore(FakeSharedPreferences())
    private val stamp = PickerPreviewStamp(installedAt = 1_000, language = "it-IT", date = LocalDate.of(2026, 10, 18))

    @Test
    fun nothingIsPublishedAtFirst() {
        assertFalse(store.isPublished(stamp))
    }

    @Test
    fun thePublishedPreviewIsRemembered() {
        store.markPublished(stamp)

        assertTrue(store.isPublished(stamp))
    }

    @Test
    fun anUpdateOfTheAppNeedsANewPreview() {
        store.markPublished(stamp)

        assertFalse(store.isPublished(stamp.copy(installedAt = 2_000)))
    }

    @Test
    fun anotherLanguageNeedsANewPreview() {
        store.markPublished(stamp)

        assertFalse(store.isPublished(stamp.copy(language = "en-US")))
    }

    @Test
    fun aNewDayNeedsANewPreview() {
        // The preview shows the dates of the days.
        store.markPublished(stamp)

        assertFalse(store.isPublished(stamp.copy(date = stamp.date.plusDays(1))))
    }

    @Test
    fun stampsDifferInEveryPart() {
        val stamps = listOf(stamp, stamp.copy(installedAt = 2_000), stamp.copy(language = "en-US"), stamp.copy(date = stamp.date.plusDays(1)))

        assertEquals(stamps.size, stamps.map { it.toString() }.toSet().size)
    }
}
