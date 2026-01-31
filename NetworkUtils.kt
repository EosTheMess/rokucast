package com.example.rokucaster.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Utility class for network-related operations.
 * Provides helper methods for checking network connectivity and WiFi status.
 */
object NetworkUtils {
    
    /**
     * Checks if the device is connected to a WiFi network.
     * 
     * @param context Application context
     * @return true if connected to WiFi, false otherwise
     */
    fun isConnectedToWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
    
    /**
     * Gets the current WiFi SSID (network name).
     * 
     * @param context Application context
     * @return WiFi SSID or null if not connected
     */
    fun getWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+, SSID requires location permission
            // For simplicity, return null here
            null
        } else {
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo?.ssid?.replace("\"", "")
        }
    }
    
    /**
     * Checks if the device has any active network connection.
     * 
     * @param context Application context
     * @return true if connected to any network, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Gets the local IP address of the device.
     * Useful for debugging SSDP discovery issues.
     * 
     * @param context Application context
     * @return Local IP address or null if not available
     */
    fun getLocalIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        @Suppress("DEPRECATION")
        val ipAddress = wifiManager.connectionInfo.ipAddress
        
        return if (ipAddress == 0) {
            null
        } else {
            String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        }
    }
}
