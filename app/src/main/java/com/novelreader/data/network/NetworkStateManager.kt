package com.novelreader.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surveille l'état du réseau et expose des Flow réactifs.
 *
 * - [isOnline] : true si une connexion réseau est disponible
 * - [isOnWifi] : true si la connexion active est en WiFi (implique aussi isOnline = true)
 * - [networkType] : valeur combinée [NetworkType]
 *
 * Un SEUL NetworkCallback est enregistré et partagé (shareIn) entre tous les
 * collecteurs — les 3 flows dérivent du même flux de capabilities.
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Flux partagé des capabilities du réseau actif (null = hors-ligne). */
    private val capabilitiesFlow: Flow<NetworkCapabilities?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(getActiveCapabilities()) }
            override fun onLost(network: Network) { trySend(getActiveCapabilities()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(caps) }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Valeur initiale
        trySend(getActiveCapabilities())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(1000), replay = 1)

    /** True si une connexion réseau est active (WiFi ou données mobiles). */
    val isOnline: Flow<Boolean> = capabilitiesFlow
        .map { it?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
        .distinctUntilChanged()

    /** True si la connexion active est en WiFi. False si mobile, hors-ligne ou inconnu. */
    val isOnWifi: Flow<Boolean> = capabilitiesFlow
        .map { it?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        .distinctUntilChanged()

    /** État réseau combiné. */
    val networkType: Flow<NetworkType> = capabilitiesFlow
        .map { resolveNetworkType(it) }
        .distinctUntilChanged()

    // ── Fonctions de requête ponctuelle ──

    fun isCurrentlyOnline(): Boolean = getActiveCapabilities()
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    fun isCurrentlyOnWifi(): Boolean = getActiveCapabilities()
        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    fun getCurrentNetworkType(): NetworkType = resolveNetworkType(getActiveCapabilities())

    // ── Internes ──

    private fun getActiveCapabilities(): NetworkCapabilities? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(activeNetwork)
    }

    private fun resolveNetworkType(caps: NetworkCapabilities?): NetworkType {
        if (caps == null) return NetworkType.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            else -> NetworkType.OFFLINE
        }
    }
}

enum class NetworkType { WIFI, MOBILE, OFFLINE }
