package it.tregiorni

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import kotlin.concurrent.thread

/**
 * Redraws the widgets when the calendar changes, e.g. after Google Calendar syncs a new event.
 * A content URI job fires only once, so it schedules itself again after each run.
 */
class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            val hasWidgets = WidgetUpdater.updateAll(applicationContext)
            jobFinished(params, false)
            if (hasWidgets) schedule(applicationContext)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = false

    companion object {
        private const val JOB_ID = 1

        /** Schedules the job unless it is already waiting for changes. */
        fun ensureScheduled(context: Context) {
            if (scheduler(context).getPendingJob(JOB_ID) == null) schedule(context)
        }

        fun cancel(context: Context) = scheduler(context).cancel(JOB_ID)

        private fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
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
            scheduler(context).schedule(job)
        }

        private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)
    }
}
