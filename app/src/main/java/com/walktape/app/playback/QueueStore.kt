package com.walktape.app.playback

import com.walktape.app.core.db.QueueDao
import com.walktape.app.core.db.toState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One process-wide writer. Load/flush are FIFO barriers after already submitted saves. */
class QueueStore(private val dao: QueueDao) {
    private sealed interface Request {
        data class Save(val queue: SavedQueue) : Request
        data class Load(val id: Long, val result: CompletableDeferred<SavedQueue?>) : Request
        data class Flush(val result: CompletableDeferred<Unit>) : Request
    }
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val failures = MutableStateFlow<Set<Long>>(emptySet())
    val failedProfiles = failures.asStateFlow()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        val written = mutableMapOf<Long, SavedQueue>()
        for (request in requests) {
            when (request) {
                is Request.Save -> {
                    val queue = request.queue
                    try {
                        require(queue.isValid())
                        val prior = written[queue.profileId]
                        val sameLayout = prior != null && prior.items == queue.items && prior.traversal == queue.traversal
                        val saved = if (sameLayout && dao.updateState(queue.toState()) > 0) true else dao.replace(queue)
                        if (saved) written[queue.profileId] = queue else written.remove(queue.profileId)
                        failures.value = failures.value - queue.profileId
                    } catch (_: Exception) { failures.value = failures.value + queue.profileId }
                }
                is Request.Load -> {
                    try {
                        val queue = dao.load(request.id)
                        if (queue != null) written[request.id] = queue else written.remove(request.id)
                        failures.value = failures.value - request.id
                        request.result.complete(queue)
                    } catch (_: Exception) {
                        failures.value = failures.value + request.id
                        request.result.complete(null)
                    }
                }
                is Request.Flush -> request.result.complete(Unit)
            }
        }
    }

    fun enqueue(queue: SavedQueue) { check(requests.trySend(Request.Save(queue)).isSuccess) }
    suspend fun load(profileId: Long): SavedQueue? {
        val result = CompletableDeferred<SavedQueue?>()
        requests.send(Request.Load(profileId, result))
        return result.await()
    }
    suspend fun flush() {
        val result = CompletableDeferred<Unit>()
        requests.send(Request.Flush(result)); result.await()
    }
    suspend fun close() { requests.close(); worker.join() }
}
