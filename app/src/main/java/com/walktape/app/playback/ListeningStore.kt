package com.walktape.app.playback

import com.walktape.app.core.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** FIFO barrier on load: a new service sees the previous service's final sample. */
class ListeningStore(private val dao: ScrobbleDao, private val delivered: () -> Unit) {
    private sealed interface Request {
        data class Save(val state: ListeningStateEntity, val event: ScrobbleEventEntity?) : Request
        data class Load(val profileId: Long, val result: CompletableDeferred<ListeningStateEntity?>) : Request
    }
    private val requests = Channel<Request>(Channel.UNLIMITED)
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (request in requests) {
                when (request) {
                    is Request.Save -> try {
                        dao.record(request.state, request.event)
                        if (request.event != null) delivered()
                    } catch (_: Exception) { /* Profile removal may race a final sample. */ }
                    is Request.Load -> try {
                        request.result.complete(dao.listeningState(request.profileId))
                    } catch (e: Exception) { request.result.completeExceptionally(e) }
                }
            }
        }
    }
    fun save(state: ListeningStateEntity, event: ScrobbleEventEntity?) {
        check(requests.trySend(Request.Save(state, event)).isSuccess)
    }
    suspend fun load(profileId: Long): ListeningStateEntity? {
        val result = CompletableDeferred<ListeningStateEntity?>()
        requests.send(Request.Load(profileId, result))
        return result.await()
    }
}
