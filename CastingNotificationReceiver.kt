package com.example.rokucaster.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action buttons.
 * 
 * When the user taps Play/Pause or Stop in the notification,
 * this receiver intercepts the broadcast and communicates with the CastingService.
 */
class CastingNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CastingNotificationRx"
        const val ACTION_PLAY_PAUSE = "com.example.rokucaster.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.example.rokucaster.ACTION_STOP"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                // Send intent to service to toggle play/pause
                val serviceIntent = Intent(context, CastingService::class.java).apply {
                    action = "TOGGLE_PLAY_PAUSE"
                }
                context.startService(serviceIntent)
            }
            
            ACTION_STOP -> {
                // Stop the casting service
                val serviceIntent = Intent(context, CastingService::class.java).apply {
                    action = CastingService.ACTION_STOP_CASTING
                }
                context.startService(serviceIntent)
            }
        }
    }
}
