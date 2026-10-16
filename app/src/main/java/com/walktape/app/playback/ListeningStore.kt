package com.walktape.app.playback

import com.walktape.app.core.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** FIFO barrier on load: a new service sees the previous service's final sample. */
class ListeningStore(private val dao: ScrobbleDao, private val delivered: () -> Unit) {
    private sealed interface Request {
        data class Save(val state: ListeningStateEntity, val event: ScrobbleEventEntity?) : Request
        data class Load(val profileId: Long, val result: CompletableDeferred<ListeningStateEntity?>) : Request
        data class SaveOverlap(val state: ListeningStateEntity, val event: ScrobbleEventEntity?) : Request
        data class LoadOverlap(val profileId: Long, val result: CompletableDeferred<OverlapListeningStateEntity?>) : Request
        data class Promote(val state: ListeningStateEntity, val outgoing: ListeningStateEntity?) : Request
        data class ClearOverlap(val profileId: Long) : Request
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
                    is Request.SaveOverlap -> try {
                        dao.recordOverlap(request.state, request.event)
                        if (request.event != null) delivered()
                    } catch (_: Exception) { /* Deleted profile: FK prevents resurrection. */ }
                    is Request.LoadOverlap -> try {
                        request.result.complete(dao.overlapState(request.profileId))
                    } catch (e: Exception) { request.result.completeExceptionally(e) }
                    is Request.Promote -> try {
                        dao.promoteOverlap(request.state, request.outgoing?.let { com.google.gson.Gson().toJson(it) })
                    } catch (_: Exception) {}
                    is Request.ClearOverlap -> try { dao.deleteOverlap(request.profileId) } catch (_: Exception) {}
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

    fun saveOverlap(state: ListeningStateEntity, event: ScrobbleEventEntity?) {
        check(requests.trySend(Request.SaveOverlap(state, event)).isSuccess)
    }

    suspend fun loadOverlap(profileId: Long): OverlapListeningStateEntity? {
        val result = CompletableDeferred<OverlapListeningStateEntity?>()
        requests.send(Request.LoadOverlap(profileId, result))
        return result.await()
    }

    fun promoteOverlap(state: ListeningStateEntity, outgoing: ListeningStateEntity?) {
        check(requests.trySend(Request.Promote(state, outgoing)).isSuccess)
    }

    fun clearOverlap(profileId: Long) {
        check(requests.trySend(Request.ClearOverlap(profileId)).isSuccess)
    }
}
