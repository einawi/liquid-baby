package com.liquid.liquidbaby

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build

/** Checks for a new build about twice a day, even when the app is closed. */
class UpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Updater.backgroundCheck(applicationContext, true)
            } catch (e: Exception) {
                Diag.e("Background update check failed", e)
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4201

        fun schedule(ctx: Context) {
            try {
                val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                if (Build.VERSION.SDK_INT >= 24 && js.getPendingJob(JOB_ID) != null) return
                val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(12L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build()
                js.schedule(job)
            } catch (e: Exception) {
                Diag.e("Could not schedule update job", e)
            }
        }
    }
}
