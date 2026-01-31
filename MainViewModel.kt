package com.example.rokucaster.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rokucaster.data.repository.SsdpRokuDiscoveryRepository
import com.example.rokucaster.domain.model.RokuDevice
import com.example.rokucaster.domain.repository.RokuDiscoveryRepository
import com.example.rokucaster.service.CastingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the MainActivity.
 * 
 * Responsibilities:
 * - Manage device discovery state
 * - Handle user interactions (start/stop casting)
 * - Communicate with the CastingService
 * - Survive configuration changes (screen rotation, etc.)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    // Repository for device discovery
    private val repository: RokuDiscoveryRepository = SsdpRokuDiscoveryRepository()
    
    // UI State flows
    private val _discoveredDevices = MutableStateFlow<List<RokuDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RokuDevice>> = _discoveredDevices.asStateFlow()
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Service connection
    private var castingService: CastingService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CastingService.CastingBinder
            castingService = binder.getService()
            isServiceBound = true
            Log.d(TAG, "Connected to CastingService")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            castingService = null
            isServiceBound = false
            _isCasting.value = false
            Log.d(TAG, "Disconnected from CastingService")
        }
    }
    
    /**
     * Starts device discovery.
     */
    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already in progress")
            return
        }
        
        _isDiscovering.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                repository.discoverDevices()
                    .collect { devices ->
                        _discoveredDevices.value = devices
                        Log.d(TAG, "Found ${devices.size} devices")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error: ${e.message}", e)
                _errorMessage.value = "Discovery failed: ${e.message}"
                _isDiscovering.value = false
            }
        }
    }
    
    /**
     * Stops device discovery.
     */
    fun stopDiscovery() {
        repository.stopDiscovery()
        _isDiscovering.value = false
        Log.d(TAG, "Discovery stopped")
    }
    
    /**
     * Starts casting to the selected Roku device.
     * 
     * This method:
     * 1. Validates the video URL
     * 2. Starts the CastingService (foreground service)
     * 3. Binds to the service for communication
     */
    fun startCasting(context: Context, device: RokuDevice, videoUrl: String, mediaType: String = "live") {
        if (videoUrl.isBlank()) {
            _errorMessage.value = "Please enter a video URL"
            return
        }
        
        // Validate URL format
        if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
            _errorMessage.value = "URL must start with http:// or https://"
            return
        }
        
        Log.d(TAG, "Starting casting to ${device.name}")
        
        // Start the foreground service
        val serviceIntent = Intent(context, CastingService::class.java).apply {
            action = CastingService.ACTION_START_CASTING
            putExtra(CastingService.EXTRA_DEVICE_NAME, device.name)
            putExtra(CastingService.EXTRA_DEVICE_IP, device.ipAddress)
            putExtra(CastingService.EXTRA_VIDEO_URL, videoUrl)
            putExtra(CastingService.EXTRA_MEDIA_TYPE, mediaType)
        }
        
        context.startForegroundService(serviceIntent)
        
        // Bind to the service
        val bindIntent = Intent(context, CastingService::class.java)
        context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        _isCasting.value = true
        _errorMessage.value = null
    }
    
    /**
     * Stops the active casting session.
     */
    fun stopCasting(context: Context) {
        castingService?.stopCasting()
        
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
        
        _isCasting.value = false
        Log.d(TAG, "Casting stopped")
    }
    
    /**
     * Toggles play/pause on the active casting session.
     */
    fun togglePlayPause() {
        castingService?.togglePlayPause()
    }
    
    /**
     * Clears the error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
        Log.d(TAG, "ViewModel cleared")
    }
}
