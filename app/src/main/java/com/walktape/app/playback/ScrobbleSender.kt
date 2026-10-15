package com.walktape.app.playback

import com.walktape.app.core.db.ScrobbleDao
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivers pending listens using each event's own profile, one at a time. Only a server
 * ACK marks an event sent; rejected events remain pending. Navidrome has no idempotency
 * key: an ACK lost after server acceptance can cause a duplicate on retry.
 */
class ScrobbleSender(
    private val dao: ScrobbleDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val scope: CoroutineScope,
    private val offlineOnly: (Long) -> Boolean = { false },
) {
    private val mutex = Mutex()

    fun trigger() {
        scope.launch {
            try { deliverPending() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Keep unacknowledged rows pending for the next trigger. */ }
        }
    }

    suspend fun deliverPending() = mutex.withLock {
        while (true) {
            val profiles = dao.profilesWithPending()
            if (profiles.isEmpty()) return@withLock
            var deliveredAny = false
            for (profileId in profiles) {
                if (offlineOnly(profileId)) continue // offline-only: listen events stay pending
                val batch = dao.pending(profileId, BATCH)
                if (batch.isEmpty()) continue
                val client = clientProvider(profileId)
                if (client == null) continue // profile credentials unavailable: retry later
                for (event in batch) when (client.scrobble(listOf(event.songId to event.playedAtMs))) {
                    is ApiResult.Success -> {
                        dao.markSent(profileId, listOf(event.eventId), System.currentTimeMillis())
                        dao.pruneSent(profileId, KEEP_DELIVERED)
                        deliveredAny = true
                    }
                    is ApiResult.Failure -> break // No ACK: retain the event, including permission/auth errors.
                }
            }
            if (!deliveredAny) return@withLock
        }
    }

    companion object {
        private const val BATCH = 50
        private const val KEEP_DELIVERED = 500
    }
}
