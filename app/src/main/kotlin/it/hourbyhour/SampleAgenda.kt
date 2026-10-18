package it.hourbyhour

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The titles of the made-up events; their texts in each language are the sample_* strings. */
enum class SampleTitle {
    STAND_UP,
    GYM,
    PROJECT_REVIEW,
    CALL_WITH_MARCO,
    DENTIST,
    LUNCH_WITH_ANNA,
    YOGA,
    DESIGN_WORKSHOP,
    PICK_UP_THE_KIDS,
    CLIENT_MEETING,
    MUMS_BIRTHDAY,
    SPRINT_PLANNING,
    DINNER_WITH_FRIENDS,
    WEEKEND_IN_ROME,
    FOOTBALL,
    GROCERIES,
    BRUNCH,
    READING,
}

/** The string resource with this title. R ids are plain numbers: this needs no Android. */
val SampleTitle.stringId: Int
    get() = when (this) {
        SampleTitle.STAND_UP -> R.string.sample_stand_up
        SampleTitle.GYM -> R.string.sample_gym
        SampleTitle.PROJECT_REVIEW -> R.string.sample_project_review
        SampleTitle.CALL_WITH_MARCO -> R.string.sample_call_with_marco
        SampleTitle.DENTIST -> R.string.sample_dentist
        SampleTitle.LUNCH_WITH_ANNA -> R.string.sample_lunch_with_anna
        SampleTitle.YOGA -> R.string.sample_yoga
        SampleTitle.DESIGN_WORKSHOP -> R.string.sample_design_workshop
        SampleTitle.PICK_UP_THE_KIDS -> R.string.sample_pick_up_the_kids
        SampleTitle.CLIENT_MEETING -> R.string.sample_client_meeting
        SampleTitle.MUMS_BIRTHDAY -> R.string.sample_mums_birthday
        SampleTitle.SPRINT_PLANNING -> R.string.sample_sprint_planning
        SampleTitle.DINNER_WITH_FRIENDS -> R.string.sample_dinner_with_friends
        SampleTitle.WEEKEND_IN_ROME -> R.string.sample_weekend_in_rome
        SampleTitle.FOOTBALL -> R.string.sample_football
        SampleTitle.GROCERIES -> R.string.sample_groceries
        SampleTitle.BRUNCH -> R.string.sample_brunch
        SampleTitle.READING -> R.string.sample_reading
    }

/**
 * A busy week of made-up events, in Google Calendar colors: what the widget shows in the launcher's
 * widget picker and in the pictures of the store listing. Never the real calendar, which is private.
 */
object SampleAgenda {
    /** The time of day at which [agenda] is seen: the morning is over, the afternoon is still to come. */
    val NOW: LocalTime = LocalTime.of(10, 40)

    /** The week of events seen on [today] at [NOW], from today on. */
    fun agenda(today: LocalDate, zone: ZoneId, title: (SampleTitle) -> String) =
        Agenda(events(today, zone, title), today.atTime(NOW).atZone(zone))

    /** Seven days of events from [firstDay] in [zone], with their titles from [title]. */
    fun events(firstDay: LocalDate, zone: ZoneId, title: (SampleTitle) -> String): List<CalendarEvent> {
        fun at(day: Int, time: Pair<Int, Int>) = firstDay.plusDays(day.toLong()).atTime(time.first, time.second).atZone(zone).toInstant().toEpochMilli()

        fun event(name: SampleTitle, day: Int, from: Pair<Int, Int>, to: Pair<Int, Int>, color: Int) =
            CalendarEvent(title(name), at(day, from), at(day, to), allDay = false, color)

        // Stored at UTC midnights, like the calendar provider does.
        fun allDay(name: SampleTitle, day: Int, days: Int, color: Int): CalendarEvent {
            val start = firstDay.plusDays(day.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return CalendarEvent(title(name), start, start + days * MILLIS_PER_DAY, allDay = true, color)
        }

        val standUps = (0..4).map { event(SampleTitle.STAND_UP, it, 9 to 0, 9 to 15, PEACOCK) }
        return standUps + listOf(
            event(SampleTitle.GYM, 0, 7 to 0, 8 to 0, SAGE),
            event(SampleTitle.PROJECT_REVIEW, 0, 14 to 0, 15 to 30, PEACOCK),
            event(SampleTitle.CALL_WITH_MARCO, 0, 14 to 30, 15 to 0, LAVENDER),
            event(SampleTitle.DENTIST, 1, 11 to 0, 11 to 45, FLAMINGO),
            event(SampleTitle.LUNCH_WITH_ANNA, 1, 12 to 30, 13 to 30, GRAPE),
            event(SampleTitle.YOGA, 1, 18 to 30, 19 to 30, SAGE),
            event(SampleTitle.GYM, 2, 7 to 0, 8 to 0, SAGE),
            event(SampleTitle.DESIGN_WORKSHOP, 2, 10 to 0, 12 to 0, PEACOCK),
            event(SampleTitle.PICK_UP_THE_KIDS, 2, 16 to 30, 17 to 0, TANGERINE),
            event(SampleTitle.CLIENT_MEETING, 3, 10 to 30, 11 to 30, PEACOCK),
            event(SampleTitle.YOGA, 3, 18 to 30, 19 to 30, SAGE),
            allDay(SampleTitle.MUMS_BIRTHDAY, 3, 1, BANANA),
            event(SampleTitle.GYM, 4, 7 to 0, 8 to 0, SAGE),
            event(SampleTitle.SPRINT_PLANNING, 4, 11 to 0, 12 to 0, PEACOCK),
            event(SampleTitle.DINNER_WITH_FRIENDS, 4, 20 to 0, 22 to 30, GRAPE),
            allDay(SampleTitle.WEEKEND_IN_ROME, 5, 2, FLAMINGO),
            event(SampleTitle.FOOTBALL, 5, 10 to 0, 11 to 30, SAGE),
            event(SampleTitle.GROCERIES, 5, 17 to 0, 18 to 0, TANGERINE),
            event(SampleTitle.BRUNCH, 6, 11 to 0, 12 to 30, GRAPE),
            event(SampleTitle.READING, 6, 21 to 0, 22 to 0, LAVENDER),
        )
    }

    // Google Calendar colors, as the calendar provider gives them.
    const val PEACOCK = 0xFF039BE5.toInt()
    const val SAGE = 0xFF33B679.toInt()
    const val LAVENDER = 0xFF7986CB.toInt()
    const val FLAMINGO = 0xFFE67C73.toInt()
    const val GRAPE = 0xFF8E24AA.toInt()
    const val TANGERINE = 0xFFF4511E.toInt()
    const val BANANA = 0xFFF6BF26.toInt()

    private const val MILLIS_PER_DAY = 24 * 3_600_000L
}
