package ca.thebikemechanic.thruspark.session

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.thebikemechanic.thruspark.engine.ProfileEngine

private const val TAG = "SessionEndWorker"

/**
 * Fired by WorkManager when a timed session (e.g. Long Flight) expires.
 * Deactivates the current profile and restores system settings.
 */
class SessionEndWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Session timer fired — deactivating profile")
        return runCatching {
            ProfileEngine.deactivateSuspend(applicationContext)
            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "Deactivation failed in SessionEndWorker", e)
            Result.retry()
        }
    }
}
