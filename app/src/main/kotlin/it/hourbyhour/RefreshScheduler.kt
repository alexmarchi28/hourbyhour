package it.hourbyhour

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Clock

/**
 * What follows a redraw of the widgets, in one place: the alarm for the next quarter hour, the job
 * that waits for calendar changes ([CalendarChangeJob]) and the picker preview. Drawing the widgets
 * ([WidgetUpdater]) knows nothing of it: the widget provider and the job call it after drawing.
 */
class RefreshScheduler(private val context: Context, private val clock: Clock = Clock.systemDefaultZone()) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val jobs get() = context.getSystemService(JobScheduler::class.java)

    /**
     * After the widgets have been redrawn. [fromCalendarJob] after a run of the job, which has
     * finished: a content URI job fires only once, so it is scheduled again for the next change.
     */
    fun afterRedraw(fromCalendarJob: Boolean = false) {
        scheduleAlarm()
        if (fromCalendarJob || jobs.getPendingJob(CALENDAR_JOB_ID) == null) scheduleCalendarJob()
        // The picker preview shows dates: a new one on the first redraw of the day.
        WidgetPickerPreview.publishIfNeeded(context, clock)
    }

    /** When the last widget leaves the home screen. */
    fun cancel() {
        alarms.cancel(alarmIntent())
        jobs.cancel(CALENDAR_JOB_ID)
    }

    /**
     * Redraws at the next quarter hour, so the current-time line moves and the days roll over at
     * midnight. RTC (not RTC_WAKEUP) never wakes the phone: a sleeping phone redraws when it wakes up.
     */
    private fun scheduleAlarm() {
        alarms.set(AlarmManager.RTC, nextRefreshTime(clock.millis()), alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HourByHourWidget::class.java).setAction(HourByHourWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleCalendarJob() {
        val job = JobInfo.Builder(CALENDAR_JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    CalendarContract.CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                ),
            )
            // A sync writes many rows in a burst: wait for it to settle before redrawing.
            .setTriggerContentUpdateDelay(2_000)
            .setTriggerContentMaxDelay(10_000)
            .build()
        jobs.schedule(job)
    }

    companion object {
        /** The id of [CalendarChangeJob] (`adb shell cmd jobscheduler run -f it.hourbyhour 1`). */
        const val CALENDAR_JOB_ID = 1
    }
}

private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L

/**
 * The next quarter hour after [nowMillis], plus a few seconds so the clock is surely past it.
 * Quarter hours of epoch time are also local quarter hours (and midnight) in every time zone whose
 * offset is a multiple of 15 minutes, which covers all current ones.
 */
fun nextRefreshTime(nowMillis: Long): Long = (nowMillis / REFRESH_INTERVAL_MILLIS + 1) * REFRESH_INTERVAL_MILLIS + 5_000
