package ca.thebikemechanic.thruspark.session

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WORK_TAG = "thruspark_session_end"

object SessionScheduler {

    fun scheduleEnd(context: Context, durationHours: Int) {
        val request = OneTimeWorkRequestBuilder<SessionEndWorker>()
            .setInitialDelay(durationHours.toLong(), TimeUnit.HOURS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelEnd(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }
}
