package it.hourbyhour

import android.app.job.JobParameters
import android.app.job.JobService
import kotlin.concurrent.thread

/**
 * Redraws the widgets when the calendar changes, e.g. after Google Calendar syncs a new event.
 * [RefreshScheduler] schedules it, and again after each run: a content URI job fires only once.
 */
class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            val hasWidgets = WidgetUpdater.updateAll(applicationContext)
            jobFinished(params, false)
            if (hasWidgets) RefreshScheduler(applicationContext).afterRedraw(fromCalendarJob = true)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}
