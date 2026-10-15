package com.walktape.app.core.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.walktape.app.Graph
import com.walktape.app.core.db.DownloadError
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.net.OpenSubsonicClient
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based persistent download work (W08-T02): survives process death and reboot,
 * honours network constraints (Wi-Fi only by default, metered opt-in), exponential backoff
 * on transient failures. Work is unique per profile; the queue lives in Room so the worker
 * is stateless. No credentials travel in the work data: they are re-read from the secure
 * storage through the profile id.
 */
class DownloadEngine(private val context: Context) {

    /** Enqueues (or replaces) the unique queue-draining work for the profile. */
    suspend fun schedule(profileId: Long, replace: Boolean = false) {
        if (profileId <= 0) return
        val settings = settingsOf(profileId)
        if (Graph.database.downloadDao().downloadedPlaylistOrigins(profileId).isNotEmpty())
            DownloadedPlaylistWorker.schedule(context, profileId)
        if (settings?.offlineOnly == true || Graph.database.profileDao().get(profileId) == null) return
        val activeId = Graph.database.profileDao().active()?.id
        if (activeId != null && Graph.database.downloadDao().settings(activeId)?.offlineOnly == true) return
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(KEY_PROFILE to profileId))
            .addTag(TAG)
            .addTag(profileTag(profileId))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings?.wifiOnly != false) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(profileId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request)
    }

    /** User pause: rows become PAUSED and pending work is cancelled. */
    suspend fun pauseWork(profileId: Long) {
        Graph.downloadStore.pauseAll(profileId)
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(profileId))
    }

    /** Profile removal: no late worker may resurrect data for a deleted profile. */
    fun cancelProfile(profileId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(profileId))
        WorkManager.getInstance(context).cancelAllWorkByTag(profileTag(profileId))
    }

    private suspend fun settingsOf(profileId: Long): ProfileSettingsEntity? =
        Graph.database.downloadDao().settings(profileId)

    companion object {
        const val TAG = "walktape-downloads"
        const val KEY_PROFILE = "profileId"
        fun uniqueName(profileId: Long) = "walktape-downloads-$profileId"
        fun profileTag(profileId: Long) = "walktape-download-profile-$profileId"
    }
}

/**
 * Stateless queue drainer: claims queued rows one at a time and transfers them. Returns
 * retry so WorkManager backs off on transient errors; permanent failures mark the single
 * item and continue with the rest of the queue.
 */
class DownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        Graph.networkGate.awaitReady()
        val profileId = inputData.getLong(DownloadEngine.KEY_PROFILE, -1)
        if (profileId <= 0) return Result.failure()
        val store = Graph.downloadStore
        val dao = store.dao
        var transientFailure = false
        var spaceStop = false
        // Ordinary JobScheduler work has a bounded execution window. Yield before the
        // ten-minute CoroutineWorker deadline, preserving the HTTP partial for retry.
        val deadline = android.os.SystemClock.elapsedRealtime() + 8 * 60_000
        while (true) {
            if (isStopped) break
            if (android.os.SystemClock.elapsedRealtime() >= deadline) { transientFailure = true; break }
            if (dao.settings(profileId)?.offlineOnly == true ||
                Graph.database.profileDao().get(profileId) == null) break
            val activeId = Graph.database.profileDao().active()?.id
            if (activeId != null && dao.settings(activeId)?.offlineOnly == true) break
            val wifiOnly = dao.settings(profileId)?.wifiOnly != false
            if (wifiOnly && !DownloadNetworkPolicy.wifi(applicationContext)) return Result.retry()
            val item = dao.claimNext(profileId, System.currentTimeMillis()) ?: break
            val outcome = store.transfer(profileId, item.songId,
                OpenSubsonicClient.streamingHttpClient(), {
                    isStopped || android.os.SystemClock.elapsedRealtime() >= deadline ||
                        (wifiOnly && !DownloadNetworkPolicy.wifi(applicationContext))
                }) { bytes, total ->
                    setProgress(workDataOf("songId" to item.songId, "bytes" to bytes, "total" to total))
                }
            val row = dao.download(profileId, item.songId)
            when (outcome) {
                DownloadStore.Outcome.Completed -> { /* next item */ }
                DownloadStore.Outcome.Transient -> {
                    if (row != null) {
                        val attempts = row.attempts + 1
                        dao.updateExisting(if (attempts >= MAX_ATTEMPTS) {
                            row.copy(status = DownloadStatus.FAILED.name,
                                lastError = DownloadError.NETWORK.name,
                                attempts = attempts, updatedAt = System.currentTimeMillis())
                        } else {
                            row.copy(status = DownloadStatus.QUEUED.name,
                                attempts = attempts, updatedAt = System.currentTimeMillis())
                        })
                        if (attempts < MAX_ATTEMPTS) transientFailure = true
                    }
                    break // WorkManager, rather than a tight loop, applies retry/backoff
                }
                DownloadStore.Outcome.Space -> {
                    spaceStop = true
                    // this item is FAILED(space); the rest stays QUEUED without a retry
                    // cascade — the user frees space and resumes explicitly
                    break
                }
                DownloadStore.Outcome.Paused -> { /* this item paused; others may still be queued */ }
                DownloadStore.Outcome.Cancelled -> {
                    // row removed or system stop: restore QUEUED only if it is still RUNNING
                    val stored = dao.download(profileId, item.songId)
                    if (stored != null && stored.status == DownloadStatus.RUNNING.name) {
                        dao.updateExisting(stored.copy(status = DownloadStatus.QUEUED.name,
                            updatedAt = System.currentTimeMillis()))
                    }
                    if (isStopped) break
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) { transientFailure = true; break }
                    transientFailure = true
                    break
                }
                is DownloadStore.Outcome.Permanent -> { /* row already FAILED with its error */ }
            }
        }
        return when {
            spaceStop -> Result.success()
            transientFailure -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 6
    }
}
