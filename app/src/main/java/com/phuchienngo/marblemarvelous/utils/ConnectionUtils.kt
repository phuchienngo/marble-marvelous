package com.phuchienngo.marblemarvelous.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object ConnectionUtils {
    @JvmStatic
    fun hasConnection(context: Context): Boolean {
        val connManager: ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network = connManager.activeNetwork ?: return false
        val caps: NetworkCapabilities = connManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
