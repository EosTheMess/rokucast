package com.example.rokucaster.domain.repository

import com.example.rokucaster.domain.model.RokuDevice
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Roku device discovery.
 * This abstraction allows us to swap implementations (NSD vs SSDP).
 */
interface RokuDiscoveryRepository {
    /**
     * Starts discovery and emits found devices as a Flow.
     * The Flow continues emitting as new devices are discovered.
     */
    fun discoverDevices(): Flow<List<RokuDevice>>
    
    /**
     * Stops the discovery process and releases resources.
     */
    fun stopDiscovery()
    
    /**
     * Sends a cast command to the specified Roku device.
     * @param device The target Roku device
     * @param videoUrl The URL of the video to cast
     * @param mediaType The type of media (live, video, etc.)
     * @return true if the command was sent successfully
     */
    suspend fun castVideo(device: RokuDevice, videoUrl: String, mediaType: String = "live"): Result<Boolean>
    
    /**
     * Sends a keypress command to the Roku device.
     * Common keys: Play, Pause, Home, Back, etc.
     */
    suspend fun sendKeyPress(device: RokuDevice, key: String): Result<Boolean>
}
