# Technical Deep Dive: Foreground Service for Casting Persistence

## The Critical Challenge: Maintaining State Across Activity Lifecycle

### Android's Activity Lifecycle Problem

Android activities have a volatile lifecycle. They can be destroyed and recreated for various reasons:

1. **Configuration Changes** (screen rotation, language change)
2. **Low Memory** (OS kills background activities)
3. **User Navigation** (pressing home, switching apps)
4. **Process Death** (system needs resources)

For a casting application, losing the activity means:
- ❌ Network socket to Roku is closed
- ❌ Casting session state is lost
- ❌ User must restart playback manually
- ❌ Poor user experience

## The Solution: Foreground Service Architecture

### What is a Foreground Service?

A foreground service is a service that:
1. **Must display a persistent notification** (required by Android O+)
2. **Runs with HIGH priority** (OS will not kill it easily)
3. **Continues running when app is backgrounded**
4. **Survives activity destruction** (independent lifecycle)

### How CastingService Achieves Persistence

```kotlin
class CastingService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground IMMEDIATELY (within 5 seconds)
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Return START_STICKY for automatic restart if killed
        return START_STICKY
    }
}
```

#### Key Implementation Details

**1. START_STICKY Flag**
```kotlin
return START_STICKY
```
- If the OS kills the service due to low memory, it will be restarted when resources become available
- Intent is null on restart, so the service should maintain internal state
- Alternative: `START_NOT_STICKY` (don't restart) or `START_REDELIVER_INTENT` (restart with original intent)

**2. startForeground() Timing**
```kotlin
startForeground(NOTIFICATION_ID, buildNotification())
```
- MUST be called within 5 seconds of service start on Android 8.0+
- Failure to do so results in `ForegroundServiceStartNotAllowedException`
- This is enforced to prevent services from hiding their activity from users

**3. Notification Requirement**
```kotlin
private fun buildNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setOngoing(true)  // Cannot be dismissed
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentTitle("Casting to $deviceName")
        .addAction(...) // Media controls
        .build()
}
```
- The notification serves dual purposes:
  - **User Transparency**: User knows the service is running
  - **OS Signal**: Tells Android this service is doing important foreground work

**4. Foreground Service Type**
```xml
<service
    android:name=".service.CastingService"
    android:foregroundServiceType="mediaPlayback">
</service>
```
- Android 10+ requires specifying the type of foreground work
- `mediaPlayback` type grants appropriate permissions and battery optimization exemptions
- Other types: `location`, `camera`, `microphone`, `connectedDevice`, etc.

## Process Priority Levels

Android manages processes with different priority levels:

| Priority | Level | Example | Kill Likelihood |
|----------|-------|---------|-----------------|
| Foreground | 0 | Activity in focus | Never (unless critical memory) |
| Visible | 1 | Activity visible but not focused | Very Low |
| Service (Foreground) | 2 | Our CastingService | Low |
| Service (Background) | 3 | Normal services | Medium-High |
| Cached | 4 | Background apps | High |

**Our CastingService operates at Level 2**, meaning:
- ✅ Higher priority than background services
- ✅ Protected from normal memory cleanup
- ✅ Only killed in extreme low-memory situations
- ✅ Restarted automatically if killed (START_STICKY)

## Activity ↔ Service Communication

### Binding Pattern

```kotlin
// In MainActivity
private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as CastingService.CastingBinder
        castingService = binder.getService()
        // Now we can call methods on the service
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
        castingService = null
    }
}

// Start the service
context.startForegroundService(intent)

// Bind to the service for communication
context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
```

### Why Both Start AND Bind?

1. **startForegroundService()**: 
   - Ensures service continues running even if activity unbinds
   - Service lifecycle is independent

2. **bindService()**:
   - Allows activity to communicate with service
   - Get service reference to call methods
   - Optional - service runs without binding

### Lifecycle Timeline

```
User taps "Cast" button
    ↓
MainActivity.startCasting() called
    ↓
startForegroundService(intent)  ← Service starts
    ↓
Service.onCreate()
    ↓
Service.onStartCommand()
    ↓
startForeground() called  ← Service promoted to foreground
    ↓
Notification appears
    ↓
bindService() called  ← Activity binds to service
    ↓
onServiceConnected() callback
    ↓
Activity has service reference
    ↓
[User minimizes app]
    ↓
Activity.onPause()
    ↓
Activity.onStop()
    ↓
[System may destroy activity]
    ↓
Activity.onDestroy()
    ↓
Service CONTINUES RUNNING ✅  ← This is the key!
    ↓
[User reopens app]
    ↓
Activity.onCreate()
    ↓
ViewModel survives (if using ViewModelStoreOwner)
    ↓
Activity rebinds to existing service
    ↓
Casting session restored ✅
```

## ViewModel Persistence

The ViewModel provides an additional layer of persistence:

```kotlin
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // State survives configuration changes
    private val _isCasting = MutableStateFlow(false)
    
    // ViewModel is scoped to Activity's lifecycle, not individual instances
    // Survives rotation, but not process death
}
```

**ViewModel Scope**:
- ✅ Survives configuration changes (rotation)
- ✅ Cleared only when activity is finished
- ❌ Does NOT survive process death

**Service Scope**:
- ✅ Survives configuration changes
- ✅ Survives activity destruction
- ✅ Survives process death (if START_STICKY)
- ❌ Only stops when explicitly stopped

## Keep-Alive Mechanism

```kotlin
private fun startKeepAlive() {
    keepAliveJob = serviceScope.launch {
        while (isActive) {
            delay(30_000) // Every 30 seconds
            
            // Option 1: Query device status
            repository.queryStatus(device)
            
            // Option 2: Send lightweight command
            repository.sendKeyPress(device, "InstantReplay")
            
            // Prevents Roku from timing out the session
        }
    }
}
```

### Why Keep-Alive?

1. **Network Socket Timeout**: TCP connections may timeout if idle
2. **Roku Session Timeout**: Roku may close inactive channels
3. **Router NAT Table**: Some routers clear NAT entries for idle connections

The keep-alive ensures the session stays active even if no user interaction occurs.

## Notification Media Controls

```kotlin
private fun buildNotification(): Notification {
    // Play/Pause action
    val playPauseIntent = Intent(this, CastingNotificationReceiver::class.java).apply {
        action = CastingNotificationReceiver.ACTION_PLAY_PAUSE
    }
    val playPausePendingIntent = PendingIntent.getBroadcast(
        this, 1, playPauseIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .addAction(R.drawable.ic_pause, "Pause", playPausePendingIntent)
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1))
        .build()
}
```

**Notification → BroadcastReceiver → Service** flow:

```
User taps "Pause" in notification
    ↓
Android sends broadcast
    ↓
CastingNotificationReceiver.onReceive()
    ↓
Receiver sends intent to service
    ↓
Service receives intent in onStartCommand()
    ↓
Service calls repository.sendKeyPress("Pause")
    ↓
HTTP POST to http://roku-ip:8060/keypress/Pause
    ↓
Roku pauses playback
    ↓
Service updates notification (Pause → Play button)
```

## Battery Optimization Challenges

### Modern Android Restrictions

Android 6.0+ introduced Doze mode and App Standby:
- **Doze**: Device enters low-power state when idle
- **App Standby**: Unused apps have network access restricted

### How Foreground Services Bypass This

1. **Foreground Service Exemption**
   - Foreground services are exempt from Doze mode restrictions
   - Can maintain network connections even in Doze
   - Can use wake locks if needed

2. **Notification Requirement**
   - The visible notification signals user-initiated work
   - Android assumes user wants this service to continue
   - Service gets higher priority for resources

3. **Manufacturer Challenges**
   - Some manufacturers (Xiaomi, Huawei, OnePlus) add aggressive battery optimization
   - May kill foreground services despite Android standards
   - Solution: Educate users to whitelist the app

## Testing Service Persistence

### Test Case 1: App Backgrounding
```
1. Start casting
2. Press Home button
3. Wait 5 minutes
4. Verify: Notification still visible
5. Verify: Roku still playing
6. Reopen app
7. Verify: UI shows active casting state
```

### Test Case 2: Screen Rotation
```
1. Start casting
2. Rotate device
3. Verify: Casting continues
4. Verify: ViewModel state preserved
5. Verify: Service reference maintained
```

### Test Case 3: Low Memory Simulation
```
1. Start casting
2. adb shell am kill <package-name>  (kill app process)
3. Verify: Service continues (separate process)
4. Reopen app
5. Verify: Rebinds to existing service
```

### Test Case 4: Doze Mode
```
1. Start casting
2. adb shell dumpsys battery unplug
3. adb shell dumpsys deviceidle force-idle
4. Wait 1 minute
5. Verify: Casting continues
6. adb shell dumpsys deviceidle unforce
```

## Conclusion

The CastingService architecture ensures robust, reliable casting by:

1. ✅ **Operating as a foreground service** with notification
2. ✅ **Running independently** of activity lifecycle
3. ✅ **Using START_STICKY** for automatic restart
4. ✅ **Maintaining keep-alive** to prevent timeouts
5. ✅ **Providing notification controls** for user convenience
6. ✅ **Surviving configuration changes** and process death

This architecture is production-ready and follows Android best practices for long-running media playback and network streaming applications.
