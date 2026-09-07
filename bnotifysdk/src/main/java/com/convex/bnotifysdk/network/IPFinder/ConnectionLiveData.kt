package com.techionic.customnotifcationapp.network.IPFinder

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.*
import android.os.Build
import androidx.lifecycle.LiveData

internal class ConnectionLiveData(private val context: Context) : LiveData<Boolean>() {

    private val connectivityManager =
        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private lateinit var connectivityManagerCallback: ConnectivityManager.NetworkCallback

    private val networkRequestBuilder: NetworkRequest.Builder = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

    override fun onActive() {
        super.onActive()
        updateConnection()
        registerNetworkCallback()
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(connectivityManagerCallback)
    }

    private fun registerNetworkCallback() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                connectivityManager.registerDefaultNetworkCallback(
                    getMarshmallowNetworkCallback()
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                registerMarshmallowNetworkRequest()
            }
            else -> {
                registerLollipopNetworkRequest()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun registerLollipopNetworkRequest() {
        connectivityManager.registerNetworkCallback(
            networkRequestBuilder.build(),
            getLollipopNetworkCallback()
        )
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.M)
    private fun registerMarshmallowNetworkRequest() {
        connectivityManager.registerNetworkCallback(
            networkRequestBuilder.build(),
            getMarshmallowNetworkCallback()
        )
    }

    private fun getLollipopNetworkCallback(): ConnectivityManager.NetworkCallback {
        connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                postValue(true)
            }

            override fun onLost(network: Network) {
                postValue(false)
            }
        }
        return connectivityManagerCallback
    }

    private fun getMarshmallowNetworkCallback(): ConnectivityManager.NetworkCallback {
        connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && isValidated) {
                    postValue(true)
                }
            }

            override fun onLost(network: Network) {
                postValue(false)
            }
        }
        return connectivityManagerCallback
    }

    @SuppressLint("MissingPermission")
    private fun updateConnection() {
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        postValue(activeNetwork?.isConnected == true)
    }
}
