# Roku Caster - Android Application

A production-ready Android application for discovering and casting video content to Roku devices using the Roku External Control Protocol (ECP).

## 🎯 Core Features

- **SSDP Device Discovery**: Automatically discovers Roku devices on the local network
- **Video Casting**: Cast any HTTP/HTTPS video URL to your Roku
- **Background Persistence**: Uses a Foreground Service to maintain casting even when the app is minimized
- **Notification Controls**: Play/Pause and Stop controls accessible from the notification tray
- **MVVM Architecture**: Clean separation of concerns with ViewModel, Repository, and Domain layers

## 🏗️ Architecture

### Clean Architecture + MVVM

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ MainActivity │──│  ViewModel   │──│   Adapter    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                     Domain Layer                             │
│  ┌────────────────┐  ┌──────────────────────────────┐      │
│  │  RokuDevice    │  │ RokuDiscoveryRepository      │      │
│  │  (Model)       │  │ (Interface)                  │      │
│  └────────────────┘  └──────────────────────────────┘      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │  SsdpRokuDiscoveryRepository (Implementation)    │       │
│  │  - SSDP Multicast Discovery                      │       │
│  │  - Device XML Parsing                            │       │
│  │  - ECP HTTP Commands                             │       │
│  └──────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                   Service Layer                              │
│  ┌────────────────────────────────────────────────┐         │
│  │  CastingService (Foreground Service)           │         │
│  │  - Maintains casting session                   │         │
│  │  - Notification with media controls            │         │
│  │  - Keep-alive mechanism                        │         │
│  └────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## 🔑 Why the Foreground Service is Critical

### The Problem Without a Foreground Service

When you minimize an Android app or the screen turns off, the OS may kill background processes to save resources. For a casting app, this means:
- The network connection to the Roku could be lost
- The casting session would terminate unexpectedly
- User would have to restart playback when reopening the app

### How CastingService Solves This

1. **Foreground Service Priority**
   - Android treats foreground services as HIGH priority
   - Protected from aggressive battery optimization
   - Will NOT be killed when app is backgrounded

2. **Persistent Notification**
   - Required for foreground services (Android O+)
   - Signals to the OS that the service is doing important work
   - Provides user-visible controls (Play/Pause, Stop)

3. **Activity Lifecycle Independence**
   - Service runs in its own lifecycle, separate from MainActivity
   - Configuration changes (rotation) don't affect the service
   - Activity can be destroyed and recreated without interrupting casting

4. **Process Survival**
   - Even if the app's UI process is killed, the service can continue
   - Maintains the casting session until explicitly stopped

## 📡 How SSDP Discovery Works

### Discovery Process

1. **M-SEARCH Multicast**
   ```
   M-SEARCH * HTTP/1.1
   HOST: 239.255.255.250:1900
   MAN: "ssdp:discover"
   MX: 3
   ST: roku:ecp
   ```

2. **Device Response**
   - Roku devices respond with a LOCATION header
   - LOCATION points to device description XML

3. **Device Info Extraction**
   - Fetch XML from LOCATION URL
   - Parse device name, model, IP address, serial number

4. **Flow Updates**
   - Emit discovered devices through Kotlin Flow
   - RecyclerView updates automatically via ListAdapter

## 🎬 How Roku Casting Works (ECP)

### Roku External Control Protocol (ECP)

The Roku ECP is a simple HTTP-based API for controlling Roku devices:

```kotlin
// Cast a video
POST http://<roku-ip>:8060/launch/22507?contentId=<video-url>&mediaType=live

// Send keypress commands
POST http://<roku-ip>:8060/keypress/Play
POST http://<roku-ip>:8060/keypress/Pause
POST http://<roku-ip>:8060/keypress/Home
```

### Channel 22507 - Roku Media Player

- Built-in channel on all Roku devices
- Supports direct URL playback
- Accepts `contentId` and `mediaType` parameters

### Media Types
- `live`: Live streaming content
- `video`: Standard video files
- `song`: Audio content

## 🚀 Usage

### 1. Discovery

```kotlin
// In MainActivity
viewModel.startDiscovery()

// Devices appear in RecyclerView as they're found
```

### 2. Start Casting

```kotlin
// User selects device from RecyclerView
viewModel.startCasting(context, device, videoUrl, "live")

// CastingService starts as foreground service
// Notification appears with media controls
```

### 3. Background Persistence

- **Minimize App**: Service continues running, notification stays visible
- **Screen Off**: Service maintains connection
- **Low Memory**: OS preserves foreground service

### 4. Stop Casting

```kotlin
// From UI
viewModel.stopCasting(context)

// From notification
// User taps "Stop" button → CastingNotificationReceiver → Service stops
```

## 📱 Notification Controls

The persistent notification includes:
- **Title**: "Casting to [Device Name]"
- **Content**: Current video URL
- **Play/Pause Button**: Toggles playback via ECP keypress
- **Stop Button**: Ends session and returns Roku to home screen

## 🔐 Permissions

### Required Permissions (AndroidManifest.xml)

```xml
<!-- Network discovery and communication -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- Notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Runtime Permission Handling

The app requests `POST_NOTIFICATIONS` permission on Android 13+ for showing the foreground service notification with media controls.

## 🛠️ Build Configuration

### Requirements
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9+
- **Gradle**: 8.0+

### Dependencies
- AndroidX Core, AppCompat, Material
- Lifecycle & ViewModel KTX
- Kotlin Coroutines
- OkHttp (for HTTP requests)
- RecyclerView & CardView

## 🧪 Testing the App

### Test Video URLs

```
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4
https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4
```

### Testing Scenarios

1. **Normal Casting**
   - Enter URL, discover devices, select device, cast starts
   - Verify notification appears
   - Verify video plays on Roku

2. **Background Persistence**
   - Start casting
   - Press home button (minimize app)
   - Verify notification remains
   - Verify playback continues
   - Reopen app - verify UI reflects active casting

3. **Screen Rotation**
   - Start casting
   - Rotate device
   - Verify ViewModel survives configuration change
   - Verify casting continues uninterrupted

4. **Notification Controls**
   - While casting, pull down notification shade
   - Tap Play/Pause - verify Roku responds
   - Tap Stop - verify playback stops and notification disappears

## 🐛 Troubleshooting

### No Devices Found

1. **Check WiFi**: Ensure device is on same WiFi network as Roku
2. **Firewall**: Some routers block SSDP multicast (port 1900)
3. **Roku Settings**: Ensure External Control is enabled on Roku
   - Settings → System → Advanced system settings → External control

### Casting Fails

1. **URL Format**: Must start with `http://` or `https://`
2. **CORS**: Some URLs may have CORS restrictions
3. **Roku Channel**: Ensure Roku Media Player (22507) is installed

### Service Killed

1. **Battery Optimization**: Check if app is battery optimized
   - Settings → Apps → Roku Caster → Battery → Unrestricted
2. **Manufacturer Restrictions**: Some manufacturers (Xiaomi, Huawei) aggressively kill services
   - Add app to autostart/whitelist

## 📄 License

This is a demonstration/educational project. Roku and Roku ECP are trademarks of Roku, Inc.

## 🔗 References

- [Roku External Control Protocol Documentation](https://developer.roku.com/docs/developer-program/debugging/external-control-api.md)
- [Android Foreground Services](https://developer.android.com/develop/background-work/services/foreground-services)
- [SSDP Specification](https://tools.ietf.org/html/draft-cai-ssdp-v1-03)
