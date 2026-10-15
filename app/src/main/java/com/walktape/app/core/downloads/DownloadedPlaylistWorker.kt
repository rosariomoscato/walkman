package com.walktape.app.core.downloads

import android.content.Context
import androidx.work.*
import com.walktape.app.Graph
import com.walktape.app.core.library.PlaylistIndex
import java.util.concurrent.TimeUnit

/** Wi-Fi-only periodic refresh of already downloaded playlists; never carries credentials. */
class DownloadedPlaylistWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        Graph.networkGate.awaitReady()
        val profileId = inputData.getLong("profileId", -1)
        if (Graph.database.profileDao().get(profileId) == null) return Result.success()
        if (Graph.networkGate.offlineOnly(profileId)) return Result.success()
        if (!DownloadNetworkPolicy.wifi(applicationContext)) return Result.retry()
        val client = Graph.profiles.clientFor(profileId) ?: return Result.retry()
        if (Graph.playlistIndex().refresh(profileId, client) != null) return Result.retry()
        Graph.downloadStore.reconcileDownloadedPlaylists(profileId)
        Graph.downloadEngine.schedule(profileId)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, profileId: Long) {
            val work = PeriodicWorkRequestBuilder<DownloadedPlaylistWorker>(6, TimeUnit.HOURS)
                .setInputData(workDataOf("profileId" to profileId))
                .addTag(DownloadEngine.profileTag(profileId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("walktape-playlists-$profileId",
                ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
