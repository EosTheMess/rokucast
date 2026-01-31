# Roku Caster - Visual Architecture

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ANDROID SYSTEM                                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                        USER INTERFACE LAYER                             │ │
│  │                                                                          │ │
│  │  ┌──────────────┐         ┌──────────────┐        ┌──────────────┐    │ │
│  │  │              │◄────────┤              │        │              │    │ │
│  │  │  MainActivity│         │ MainViewModel│◄───────┤RokuDeviceAdap│    │ │
│  │  │              │────────►│              │        │      ter     │    │ │
│  │  │  - EditText  │         │ - StateFlows │        │              │    │ │
│  │  │  - RecyclerV │         │ - Discovery  │        │ - DiffUtil   │    │ │
│  │  │  - Buttons   │         │ - Casting    │        │ - ViewHolder │    │ │
│  │  └──────┬───────┘         └──────┬───────┘        └──────────────┘    │ │
│  │         │                        │                                      │ │
│  └─────────┼────────────────────────┼──────────────────────────────────────┘ │
│            │                        │                                        │
│            │ bindService()          │ Flow.collect()                         │
│            │                        │                                        │
│  ┌─────────▼────────────────────────▼──────────────────────────────────────┐ │
│  │                      BUSINESS LOGIC LAYER                                │ │
│  │                                                                           │ │
│  │  ┌──────────────────────────────────────────────────────────────┐       │ │
│  │  │             Repository (Interface)                           │       │ │
│  │  │  - discoverDevices() → Flow<List<RokuDevice>>               │       │ │
│  │  │  - castVideo(device, url) → Result<Boolean>                 │       │ │
│  │  │  - sendKeyPress(device, key) → Result<Boolean>              │       │ │
│  │  └────────────────────┬─────────────────────────────────────────┘       │ │
│  │                       │                                                  │ │
│  └───────────────────────┼──────────────────────────────────────────────────┘ │
│                          │ implements                                         │
│  ┌───────────────────────▼──────────────────────────────────────────────────┐ │
│  │                        DATA LAYER                                         │ │
│  │                                                                            │ │
│  │  ┌────────────────────────────────────────────────────────────┐          │ │
│  │  │      SsdpRokuDiscoveryRepository                           │          │ │
│  │  │                                                             │          │ │
│  │  │  SSDP Discovery:                                           │          │ │
│  │  │  1. Create UDP multicast socket                            │          │ │
│  │  │  2. Send M-SEARCH to 239.255.255.250:1900                  │          │ │
│  │  │  3. Listen for responses                                   │          │ │
│  │  │  4. Parse LOCATION header                                  │          │ │
│  │  │  5. Fetch device XML                                       │          │ │
│  │  │  6. Emit devices via Flow                                  │          │ │
│  │  │                                                             │          │ │
│  │  │  ECP Casting:                                              │          │ │
│  │  │  POST http://<roku-ip>:8060/launch/22507?contentId=...     │          │ │
│  │  │  POST http://<roku-ip>:8060/keypress/Play                  │          │ │
│  │  └────────────────────────────────────────────────────────────┘          │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                  SERVICE LAYER (CRITICAL!)                               │  │
│  │                                                                           │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐ │  │
│  │  │                   CastingService                                    │ │  │
│  │  │                   (Foreground Service)                              │ │  │
│  │  │                                                                      │ │  │
│  │  │  ┌─────────────────────────────────────────────────────────┐       │ │  │
│  │  │  │  WHY THIS IS CRITICAL FOR BACKGROUND PERSISTENCE:       │       │ │  │
│  │  │  │                                                          │       │ │  │
│  │  │  │  1. Runs as FOREGROUND service (HIGH priority)          │       │ │  │
│  │  │  │  2. startForeground() with notification                 │       │ │  │
│  │  │  │  3. Independent of Activity lifecycle                   │       │ │  │
│  │  │  │  4. START_STICKY for auto-restart                       │       │ │  │
│  │  │  │  5. Protected from OS memory reclaim                    │       │ │  │
│  │  │  └─────────────────────────────────────────────────────────┘       │ │  │
│  │  │                                                                      │ │  │
│  │  │  Components:                                                        │ │  │
│  │  │  - Notification with Play/Pause/Stop                               │ │  │
│  │  │  - Keep-alive coroutine (30s interval)                             │ │  │
│  │  │  - Service binder for Activity communication                       │ │  │
│  │  │  - ECP command execution                                           │ │  │
│  │  └────────────────────────────────────────────────────────────────────┘ │  │
│  │                                 ▲                                         │  │
│  │                                 │                                         │  │
│  │                                 │ Broadcast                               │  │
│  │                                 │                                         │  │
│  │  ┌──────────────────────────────┴──────────────────────────────────────┐ │  │
│  │  │       CastingNotificationReceiver                                    │ │  │
│  │  │       (BroadcastReceiver)                                            │ │  │
│  │  │                                                                       │ │  │
│  │  │  Handles notification actions:                                       │ │  │
│  │  │  - ACTION_PLAY_PAUSE → togglePlayPause()                            │ │  │
│  │  │  - ACTION_STOP → stopCasting()                                      │ │  │
│  │  └───────────────────────────────────────────────────────────────────────┘ │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

                                       ▼
                                       
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NETWORK LAYER                                      │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        SSDP Multicast                               │    │
│  │                    239.255.255.250:1900                             │    │
│  │                                                                      │    │
│  │        M-SEARCH * HTTP/1.1                                          │    │
│  │        HOST: 239.255.255.250:1900                                   │    │
│  │        ST: roku:ecp                                                 │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                   │                                         │
│                                   ▼                                         │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                      ROKU DEVICE                                    │    │
│  │                                                                      │    │
│  │  ┌────────────────────────────────────────────────────────┐        │    │
│  │  │   Roku External Control Protocol (ECP)                 │        │    │
│  │  │   Port 8060                                             │        │    │
│  │  │                                                          │        │    │
│  │  │   POST /launch/22507?contentId=<url>&mediaType=live    │        │    │
│  │  │   POST /keypress/Play                                   │        │    │
│  │  │   POST /keypress/Pause                                  │        │    │
│  │  │   POST /keypress/Home                                   │        │    │
│  │  │                                                          │        │    │
│  │  │   GET  /query/device-info  (device description XML)     │        │    │
│  │  └────────────────────────────────────────────────────────┘        │    │
│  │                                                                      │    │
│  │  Roku Media Player (Channel 22507)                                  │    │
│  │  - Plays video from URL                                             │    │
│  │  - Supports live streaming & video files                            │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Lifecycle Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CASTING SESSION LIFECYCLE                               │
└─────────────────────────────────────────────────────────────────────────────┘

User Action: Tap "Cast to Roku"
        │
        ▼
┌───────────────────────┐
│  MainActivity         │
│  startCasting()       │
└───────┬───────────────┘
        │
        │ startForegroundService(intent)
        ▼
┌───────────────────────────────────────────────────────────────┐
│  CastingService                                                │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ onStartCommand()                                         │ │
│  │  - Extract device info & URL from intent                │ │
│  │  - Call startForeground(notification)  ◄─── CRITICAL!   │ │
│  │  - Return START_STICKY                                  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ startCasting()                                           │ │
│  │  - Launch coroutine                                     │ │
│  │  - Call repository.castVideo()                          │ │
│  │  - Start keep-alive job                                 │ │
│  └─────────────────────────────────────────────────────────┘ │
└────────┬──────────────────────────────────────────────────────┘
         │
         │ HTTP POST
         ▼
┌─────────────────────┐
│  Roku Device         │
│  - Receives command  │
│  - Launches channel  │
│  - Plays video      │
└─────────────────────┘

[Notification appears in status bar]

User Action: Press Home (minimize app)
        │
        ▼
┌───────────────────────┐
│  MainActivity         │
│  onPause()           │
│  onStop()            │
│  [onDestroy()?]      │  ◄─── Activity MAY be destroyed
└───────────────────────┘
        │
        │ BUT...
        │
        ▼
┌───────────────────────────────────────────────────────────────┐
│  CastingService                                                │
│  ✅ CONTINUES RUNNING                                          │
│                                                                │
│  - Foreground service priority protects it                    │
│  - Notification keeps it visible to user & OS                 │
│  - Keep-alive coroutine maintains session                     │
│  - Independent lifecycle from Activity                        │
│                                                                │
│  Every 30 seconds:                                            │
│  keepAliveJob {                                               │
│    while(isActive) {                                          │
│      delay(30_000)                                            │
│      // Optional: ping Roku to keep session alive            │
│    }                                                          │
│  }                                                            │
└────────┬──────────────────────────────────────────────────────┘
         │
         │ Maintains connection
         ▼
┌─────────────────────┐
│  Roku Device         │
│  ✅ Video continues  │
│     playing          │
└─────────────────────┘

User Action: Tap notification (or reopen app)
        │
        ▼
┌───────────────────────┐
│  MainActivity         │
│  onCreate()          │  ◄─── May be a NEW instance
│  - ViewModel survives │
│  - Rebind to service │
└───────┬───────────────┘
        │
        │ bindService()
        ▼
┌───────────────────────────────────────────────────────────────┐
│  CastingService                                                │
│  onServiceConnected()                                         │
│  - Activity gets service reference                            │
│  - UI shows active casting state                              │
└───────────────────────────────────────────────────────────────┘

User Action: Tap "Stop" in notification
        │
        ▼
┌───────────────────────────────────┐
│  CastingNotificationReceiver      │
│  onReceive(ACTION_STOP)          │
└───────┬───────────────────────────┘
        │
        │ Send intent to service
        ▼
┌───────────────────────────────────────────────────────────────┐
│  CastingService                                                │
│  stopCasting()                                                │
│  - Send "Home" keypress to Roku                              │
│  - stopForeground(REMOVE_NOTIFICATION)                       │
│  - stopSelf()                                                 │
└───────┬───────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────┐
│  Roku Device         │
│  Returns to home     │
│  screen              │
└─────────────────────┘

[Service destroyed, notification removed]
```

## Data Flow Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                         DISCOVERY DATA FLOW                             │
└────────────────────────────────────────────────────────────────────────┘

User taps "Discover Devices"
        │
        ▼
    MainViewModel
    startDiscovery()
        │
        ▼
    Repository.discoverDevices()
        │
        ▼
    SsdpRokuDiscoveryRepository
    ┌─────────────────────────────┐
    │ 1. Create UDP socket         │
    │ 2. Send M-SEARCH multicast   │───────► 239.255.255.250:1900
    │ 3. Listen for responses      │◄───────  SSDP Response
    │ 4. Parse LOCATION header     │          LOCATION: http://192.168.1.100:8060/
    │ 5. HTTP GET device XML       │───────► 192.168.1.100:8060
    │ 6. Parse friendlyName, model │◄───────  <device><friendlyName>Living Room Roku
    └─────────────┬───────────────┘
                  │
                  │ emit(RokuDevice(...))
                  ▼
            MutableStateFlow<List<RokuDevice>>
                  │
                  │ Flow.collect()
                  ▼
              ViewModel
    _discoveredDevices.value = devices
                  │
                  │ StateFlow observation
                  ▼
              MainActivity
    deviceAdapter.submitList(devices)
                  │
                  ▼
          RecyclerView updates UI
    ┌─────────────────────────┐
    │ Roku Living Room         │
    │ Roku Streaming Stick+    │
    │ 192.168.1.100            │
    └─────────────────────────┘


┌────────────────────────────────────────────────────────────────────────┐
│                          CASTING DATA FLOW                              │
└────────────────────────────────────────────────────────────────────────┘

User taps on device in RecyclerView
        │
        ▼
    MainActivity
    onDeviceSelected(device)
    Show confirmation dialog
        │
        │ User confirms
        ▼
    ViewModel.startCasting(device, url)
        │
        │ startForegroundService()
        ▼
    CastingService starts
        │
        ▼
    Service calls Repository.castVideo(device, url)
        │
        ▼
    SsdpRokuDiscoveryRepository.castVideo()
    ┌────────────────────────────────────────────┐
    │ val url = "http://192.168.1.100:8060       │
    │     /launch/22507?contentId=<video-url>"   │
    │                                             │
    │ HTTP POST to Roku                           │───────► Roku ECP API
    └────────────────┬───────────────────────────┘          :8060
                     │
                     │ Result<Boolean>
                     ▼
              CastingService
    onSuccess: startKeepAlive()
    onFailure: stopSelf()
                     │
                     │
                     ▼
        Notification appears
    ┌─────────────────────────┐
    │ Casting to Living Room   │
    │ http://example.com/...   │
    │ [▶️ Pause]  [⏹️ Stop]    │
    └─────────────────────────┘
```

## Process Priority Diagram

```
┌─────────────────────────────────────────────────────────────┐
│         ANDROID PROCESS PRIORITY LEVELS                      │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Level 0: FOREGROUND (Active Activity)              │    │
│  │ - Never killed unless critical memory shortage     │    │
│  │ - User is actively interacting                     │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Level 1: VISIBLE (Visible but not focused)         │    │
│  │ - Rarely killed                                     │    │
│  │ - User can see the activity                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Level 2: SERVICE (Foreground Service) ◄── OUR APP  │    │
│  │ - Low kill likelihood                               │    │
│  │ - Has persistent notification                       │    │
│  │ - Doing important user-initiated work              │    │
│  │                                                      │    │
│  │ ✅ CastingService runs here                         │    │
│  │ ✅ Protected from normal memory cleanup             │    │
│  │ ✅ Only killed in extreme low-memory situations     │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Level 3: SERVICE (Background Service)              │    │
│  │ - Medium-High kill likelihood                       │    │
│  │ - No notification                                   │    │
│  │ - Can be killed for memory                         │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Level 4: CACHED (Background App)                   │    │
│  │ - High kill likelihood                              │    │
│  │ - First to be killed when memory needed            │    │
│  │ - User switched away from the app                  │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘

When user minimizes our app:
  - MainActivity drops to Level 4 (CACHED) ❌ Can be killed
  - CastingService stays at Level 2 (SERVICE) ✅ Protected

This is why the service continues casting even when the Activity
is destroyed!
```
