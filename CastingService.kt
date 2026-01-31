package com.example.rokucaster.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rokucaster.R
import com.example.rokucaster.data.repository.SsdpRokuDiscoveryRepository
import com.example.rokucaster.domain.model.RokuDevice
import com.example.rokucaster.domain.repository.RokuDiscoveryRepository
import com.example.rokucaster.ui.MainActivity
import kotlinx.coroutines.*

/**
 * CastingService - A Foreground Service for maintaining Roku casting sessions.
 * 
 * WHY THIS SERVICE IS CRITICAL FOR BACKGROUND PERSISTENCE:
 * 
 * 1. FOREGROUND SERVICE PRIORITY:
 *    - By running as a foreground service with a persistent notification, Android
 *      treats this service with HIGH priority and will NOT kill it aggressively.
 *    - Normal services can be killed by the OS when memory is low or the app goes
 *      to background. Foreground services are protected from this behavior.
 * 
 * 2. ACTIVITY LIFECYCLE INDEPENDENCE:
 *    - When the user minimizes the app or the Activity is destroyed (configuration
 *      change, low memory), this Service continues running independently.
 *    - The casting session state is maintained in the Service, not the Activity.
 * 
 * 3. NOTIFICATION WITH MEDIA CONTROLS:
 *    - The persistent notification serves two purposes:
 *      a) Signals to Android that the service is doing important foreground work
 *      b) Provides user controls (Play/Pause, Stop) accessible from notification tray
 * 
 * 4. WAKE LOCKS & NETWORK PERSISTENCE:
 *    - The service can maintain network connections and periodic keep-alive pings
 *      without being interrupted by device sleep or app backgrounding.
 * 
 * 5. PROCESS SURVIVAL:
 *    - Even if the app's UI process is killed, the service process can continue
 *      running, maintaining the casting session until explicitly stopped.
 */
class CastingService : Service() {
    
    companion object {
        private const val TAG = "CastingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "roku_casting_channel"
        
        const val ACTION_START_CASTING = "com.example.rokucaster.START_CASTING"
        const val ACTION_STOP_CASTING = "com.example.rokucaster.STOP_CASTING"
        
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DEVICE_IP = "device_ip"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_MEDIA_TYPE = "media_type"
    }
    
    // Binder for clients to interact with the service
    private val binder = CastingBinder()
    
    // Repository for Roku communication
    private val repository: RokuDiscoveryRepository = SsdpRokuDiscoveryRepository()
    
    // Current casting session state
    private var currentDevice: RokuDevice? = null
    private var currentVideoUrl: String? = null
    private var isPlaying: Boolean = false
    
    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Keep-alive job to maintain session
    private var keepAliveJob: Job? = null
    
    inner class CastingBinder : Binder() {
        fun getService(): CastingService = this@CastingService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CastingService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CASTING -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Roku Device"
                val deviceIp = intent.getStringExtra(EXTRA_DEVICE_IP) ?: return START_NOT_STICKY
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "live"
                
                startCasting(deviceName, deviceIp, videoUrl, mediaType)
            }
            ACTION_STOP_CASTING -> {
                stopCasting()
            }
            "TOGGLE_PLAY_PAUSE" -> {
                togglePlayPause()
            }
        }
        
        // START_STICKY ensures the service is restarted if killed by the system
        // This is important for maintaining the casting session
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * Creates the notification channel required for foreground services on Android O+.
     * This channel categorizes our notification as a media playback notification.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Roku Casting",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            ).apply {
                description = "Controls for active Roku casting session"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Starts a casting session and promotes the service to foreground.
     * 
     * CRITICAL: startForeground() must be called within 5 seconds of service start
     * on Android 8.0+, otherwise the service will be killed by the system.
     */
    private fun startCasting(deviceName: String, deviceIp: String, videoUrl: String, mediaType: String) {
        Log.d(TAG, "Starting casting to $deviceName ($deviceIp)")
        
        currentDevice = RokuDevice(name = deviceName, ipAddress = deviceIp)
        currentVideoUrl = videoUrl
        isPlaying = true
        
        // Promote to foreground service with notification
        // This is THE KEY to preventing the OS from killing the service
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Launch the video on the Roku device
        serviceScope.launch {
            currentDevice?.let { device ->
                val result = repository.castVideo(device, videoUrl, mediaType)
                
                result.onSuccess {
                    Log.d(TAG, "Successfully started casting")
                    startKeepAlive()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to start casting: ${error.message}")
                    stopSelf() // Stop service if casting fails
                }
            }
        }
    }
    
    /**
     * Builds the persistent notification with media controls.
     * 
     * This notification:
     * - Keeps the service in foreground state (prevents OS from killing it)
     * - Provides user-visible indication of active casting
     * - Offers Play/Pause and Stop controls via PendingIntents
     */
    private fun buildNotification(): Notification {
        val deviceName = currentDevice?.name ?: "Roku Device"
        
        // Intent to open the main activity when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Play/Pause action
        val playPauseIntent = Intent(this, CastingNotificationReceiver::class.java).apply {
            action = CastingNotificationReceiver.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop action
        val stopIntent = Intent(this, CastingNotificationReceiver::class.java).apply {
            action = CastingNotificationReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Casting to $deviceName")
            .setContentText(currentVideoUrl)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use your own icon
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true) // Cannot be dismissed by user
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Media controls
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1))
            .build()
    }
    
    /**
     * Updates the notification (e.g., when play state changes).
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
    
    /**
     * Starts a keep-alive mechanism to maintain the session.
     * 
     * This periodically checks the connection or sends lightweight commands
     * to prevent the Roku from timing out the session.
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                delay(30_000) // Every 30 seconds
                
                // You could send a query command here to check device status
                // For now, we just log to show the service is alive
                Log.d(TAG, "Keep-alive ping")
            }
        }
    }
    
    /**
     * Handles Play/Pause toggle.
     * Sends the appropriate keypress to the Roku device.
     */
    fun togglePlayPause() {
        serviceScope.launch {
            currentDevice?.let { device ->
                val key = if (isPlaying) "Pause" else "Play"
                
                repository.sendKeyPress(device, key).onSuccess {
                    isPlaying = !isPlaying
                    updateNotification()
                    Log.d(TAG, "Toggled play/pause to: ${if (isPlaying) "playing" else "paused"}")
                }
            }
        }
    }
    
    /**
     * Stops the casting session and the service.
     */
    fun stopCasting() {
        Log.d(TAG, "Stopping casting session")
        
        // Send Home keypress to return Roku to home screen
        serviceScope.launch {
            currentDevice?.let { device ->
                repository.sendKeyPress(device, "Home")
            }
            
            // Clean up and stop service
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    
    /**
     * Cleans up resources when the service is destroyed.
     */
    private fun cleanup() {
        keepAliveJob?.cancel()
        serviceScope.cancel()
        currentDevice = null
        currentVideoUrl = null
        isPlaying = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CastingService destroyed")
        cleanup()
    }
}
