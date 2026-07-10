package com.novelreader.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surveille l'état du réseau et expose des Flow réactifs.
 *
 * - [isOnline] : true si une connexion réseau est disponible
 * - [isOnWifi] : true si la connexion active est en WiFi (implique aussi isOnline = true)
 * - [networkType] : valeur combinée [NetworkType]
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** True si une connexion réseau est active (WiFi ou données mobiles). */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Valeur initiale
        trySend(getCurrentOnlineStatus())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** True si la connexion active est en WiFi. False si mobile, hors-ligne ou inconnu. */
    val isOnWifi: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isCurrentNetworkWifi()) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        trySend(isCurrentNetworkWifi())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** État réseau combiné. */
    val networkType: Flow<NetworkType> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(resolveNetworkType()) }
            override fun onLost(network: Network) { trySend(NetworkType.OFFLINE) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(resolveNetworkType(caps))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        trySend(resolveNetworkType())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    // ── Fonctions de requête ponctuelle ──

    fun isCurrentlyOnline(): Boolean = getCurrentOnlineStatus()

    fun isCurrentlyOnWifi(): Boolean = isCurrentNetworkWifi()

    fun getCurrentNetworkType(): NetworkType = resolveNetworkType()

    // ── Internes ──

    private fun getCurrentOnlineStatus(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isCurrentNetworkWifi(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun resolveNetworkType(caps: NetworkCapabilities? = null): NetworkType {
        val capabilities = caps ?: let {
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.OFFLINE
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.OFFLINE
        }
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            else -> NetworkType.OFFLINE
        }
    }
}

enum class NetworkType { WIFI, MOBILE, OFFLINE }
