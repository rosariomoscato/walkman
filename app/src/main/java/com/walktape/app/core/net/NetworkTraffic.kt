package com.walktape.app.core.net

import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Process-local traffic gate. No URLs, credentials or request data are logged or persisted. */
object NetworkTraffic {
    @Volatile private var offline = false
    private val active = ConcurrentHashMap.newKeySet<Call>()

    fun setOffline(value: Boolean) {
        offline = value
        if (value) active.forEach { it.cancel() }
    }

    val listener = object : EventListener() {
        override fun callStart(call: Call) {
            active.add(call)
            if (offline) call.cancel()
        }
        override fun callEnd(call: Call) { active.remove(call) }
        override fun callFailed(call: Call, ioe: IOException) { active.remove(call) }
    }
}
