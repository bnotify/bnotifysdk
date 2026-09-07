package com.convex.bnotifysdk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.also
import kotlin.collections.forEach
import kotlin.run

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class NetworkMonitor private constructor(context: Context) : ConnectivityManager.NetworkCallback() {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isRegistered = false

    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private var listeners = mutableSetOf<NetworkStateListener>()

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        connectivityManager.registerNetworkCallback(networkRequest, this)
        isRegistered = true
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        connectivityManager.unregisterNetworkCallback(this)
        isRegistered = false
    }

    fun addListener(listener: NetworkStateListener) {
        listeners.add(listener)
        // Notify immediately with current state
        checkCurrentNetworkState()
    }

    fun removeListener(listener: NetworkStateListener) {
        listeners.remove(listener)
    }

    private fun checkCurrentNetworkState() {
        val network = connectivityManager.activeNetwork ?: run {
            notifyListeners(NetworkState.Disconnected)
            return
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: run {
            notifyListeners(NetworkState.Disconnected)
            return
        }

        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                notifyListeners(NetworkState.Connected(NetworkType.WIFI))
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                notifyListeners(NetworkState.Connected(NetworkType.CELLULAR))
            }
            else -> {
                notifyListeners(NetworkState.Disconnected)
            }
        }
    }

    private fun notifyListeners(state: NetworkState) {
        listeners.forEach { it.onNetworkStateChanged(state) }
    }

    // NetworkCallback overrides
    override fun onAvailable(network: Network) {
        checkCurrentNetworkState()
    }

    override fun onLost(network: Network) {
        notifyListeners(NetworkState.Disconnected)
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
    ) {
        checkCurrentNetworkState()
    }
}

interface NetworkStateListener {
    fun onNetworkStateChanged(state: NetworkState)
}

sealed class NetworkState {
    object Disconnected : NetworkState()
    data class Connected(val type: NetworkType) : NetworkState()
}

enum class NetworkType {
    WIFI, CELLULAR
}