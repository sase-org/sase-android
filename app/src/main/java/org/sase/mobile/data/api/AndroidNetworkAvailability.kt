package org.sase.mobile.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkAvailability(
    context: Context,
) : NetworkAvailability {
    private val connectivityManager = context
        .applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
