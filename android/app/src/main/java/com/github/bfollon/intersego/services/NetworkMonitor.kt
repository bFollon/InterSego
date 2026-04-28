/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network monitoring utility to check online/offline state
 * Simple wrapper around Android's ConnectivityManager
 */
object NetworkMonitor {

    private var connectivityManager: ConnectivityManager? = null

    /**
     * Initialize the network monitor with application context
     * Should be called once from MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        DebugConfig.debugPrint("NetworkMonitor: Initialized")
    }

    /**
     * Check if the device has an active internet connection
     *
     * @return true if device has validated internet access, false otherwise
     */
    fun isOnline(): Boolean {
        val cm = connectivityManager
        if (cm == null) {
            DebugConfig.debugWarn("NetworkMonitor: Not initialized, assuming offline")
            return false
        }

        return try {
            val network = cm.activeNetwork
            if (network == null) {
                DebugConfig.debugPrint("NetworkMonitor: No active network")
                return false
            }

            val capabilities = cm.getNetworkCapabilities(network)
            if (capabilities == null) {
                DebugConfig.debugPrint("NetworkMonitor: No network capabilities")
                return false
            }

            // Check for actual internet capability (not just network connection)
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            val online = hasInternet && isValidated

            DebugConfig.debugPrint("NetworkMonitor: Online status = $online (internet=$hasInternet, validated=$isValidated)")
            online

        } catch (e: Exception) {
            DebugConfig.debugError("NetworkMonitor: Error checking network state", e)
            false
        }
    }

    /**
     * Get a human-readable description of the current network state
     * Useful for debugging and UI display
     */
    fun getNetworkStateDescription(): String {
        val cm = connectivityManager ?: return "No inicializado"

        return try {
            val network = cm.activeNetwork ?: return "Sin conexión"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "Sin capacidades de red"

            val transportType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Desconocido"
            }

            val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                "conectado"
            } else {
                "sin internet"
            }

            "$transportType ($validated)"

        } catch (e: Exception) {
            DebugConfig.debugError("NetworkMonitor: Error getting network description", e)
            "Error al comprobar red"
        }
    }
}
