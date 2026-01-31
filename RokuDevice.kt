package com.example.rokucaster.domain.model

/**
 * Domain model representing a discovered Roku device.
 * This is part of the Clean Architecture domain layer.
 */
data class RokuDevice(
    val name: String,
    val ipAddress: String,
    val port: Int = 8060,
    val modelName: String? = null,
    val serialNumber: String? = null
) {
    /**
     * Returns the base URL for ECP (External Control Protocol) commands
     */
    fun getBaseUrl(): String = "http://$ipAddress:$port"
    
    /**
     * Returns a unique identifier for this device
     */
    fun getDeviceId(): String = serialNumber ?: ipAddress
}
