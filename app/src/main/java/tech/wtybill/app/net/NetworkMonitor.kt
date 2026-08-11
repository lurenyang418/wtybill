package tech.wtybill.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class NetworkMonitor(context: Context, private val onAvailable: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onAvailable()
    }

    fun start() = manager.registerDefaultNetworkCallback(callback)
    fun stop() = runCatching { manager.unregisterNetworkCallback(callback) }
}
