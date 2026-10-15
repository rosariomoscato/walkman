package com.walktape.app.core.settings

import com.walktape.app.core.db.DownloadDao
import com.walktape.app.core.db.ProfileSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-profile offline-only gate (W08-T04): one in-process view of the profile_settings
 * table used by every network call site (covers, lyrics, suggestions, indexing, profile
 * checks, scrobbling, workers, streaming upstream). While offline-only is active the app
 * must issue no network request at all for that profile; pending work waits.
 */
class ProfileNetworkGate(
    dao: DownloadDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    activeProfile: Flow<Long?> = flowOf(null),
) {
    private val settings = ConcurrentHashMap<Long, ProfileSettingsEntity>()
    @Volatile private var loaded = false
    @Volatile private var activeId: Long? = null
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            combine(dao.observeAllSettings(), activeProfile) { rows, id -> rows to id }.collectLatest { (rows, id) ->
                val byProfile = rows.associateBy { it.profileId }
                settings.putAll(byProfile)
                settings.keys.retainAll(byProfile.keys)
                activeId = id
                loaded = true
                com.walktape.app.core.net.NetworkTraffic.setOffline(id?.let { byProfile[it]?.offlineOnly } == true)
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() { ready.await() }

    /** Default values apply when the profile has no stored row (wifi-only downloads). */
    fun offlineOnly(profileId: Long): Boolean =
        !loaded || activeId?.let { settings[it]?.offlineOnly } == true || settings[profileId]?.offlineOnly == true

    fun skipUnavailable(profileId: Long): Boolean =
        settings[profileId]?.skipUnavailable ?: true

    fun wifiOnly(profileId: Long): Boolean =
        settings[profileId]?.wifiOnly ?: true
}
