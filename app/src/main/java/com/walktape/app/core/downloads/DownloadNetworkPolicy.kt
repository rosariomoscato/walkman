package com.walktape.app.core.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** UNMETERED alone can also match a mobile plan; Wi-Fi-only requires the actual transport. */
object DownloadNetworkPolicy {
    fun wifi(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
