package com.walktape.app.sync

import android.content.Context
import androidx.work.*
import com.walktape.app.Graph
import java.util.concurrent.TimeUnit

/** Durable reconnect trigger; only the profile identifier is persisted in WorkManager. */
class OfflineSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        Graph.networkGate.awaitReady()
        val profileId = inputData.getLong("profileId", -1)
        if (profileId <= 0 || Graph.database.profileDao().get(profileId) == null) return Result.success()
        val active = Graph.database.profileDao().active()?.id
        if (Graph.database.downloadDao().settings(profileId)?.offlineOnly == true ||
            active?.let { Graph.database.downloadDao().settings(it)?.offlineOnly } == true) return Result.success()
        Graph.offlineOps.drain(profileId)
        return if (Graph.database.downloadDao().pendingCount(profileId) > 0) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context, profileId: Long) {
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setInputData(workDataOf("profileId" to profileId))
                .addTag("walktape-sync-profile-$profileId")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("walktape-sync-$profileId",
                ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context, profileId: Long) {
            WorkManager.getInstance(context).cancelAllWorkByTag("walktape-sync-profile-$profileId")
        }
    }
}
