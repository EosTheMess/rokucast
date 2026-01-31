# Roku Caster - Project Summary

## 📋 Project Overview

**Roku Caster** is a production-ready Android application that discovers Roku devices on the local network and casts video content to them using the Roku External Control Protocol (ECP). The application is built with Clean Architecture principles and MVVM pattern, featuring a critical **Foreground Service** that maintains the casting session even when the app is minimized or destroyed.

## 🎯 Core Technical Requirements Met

### ✅ Architecture
- **MVVM Pattern**: Separation of UI (Activity), Business Logic (ViewModel), and Data (Repository)
- **Clean Architecture**: Domain layer with models and repository interfaces
- **Foreground Service**: CastingService maintains session independently of Activity lifecycle

### ✅ Device Discovery
- **SSDP Implementation**: Manual SSDP (Simple Service Discovery Protocol) using UDP multicast
- **Service Type**: Discovers devices with `ST: roku:ecp`
- **Device Information**: Parses XML to extract device name, model, IP, serial number

### ✅ Casting Logic
- **ECP Commands**: POST requests to Roku's External Control Protocol API
- **Endpoint**: `http://<roku-ip>:8060/launch/22507?contentId=<url>&mediaType=live`
- **Channel 22507**: Roku Media Player for direct URL playback
- **Keypress Support**: Play, Pause, Home commands

### ✅ UI Components
- **MainActivity**: EditText for URL input, RecyclerView for device list
- **Material Design**: Modern UI with CardView, Material buttons, dialogs
- **Real-time Updates**: Kotlin Flow for reactive device list updates

### ✅ Background Persistence (Critical Feature)
- **Foreground Service**: Runs with HIGH priority, protected from OS termination
- **Persistent Notification**: Required by Android, provides media controls
- **Activity Independence**: Service survives Activity destruction/recreation
- **Keep-Alive**: Periodic pings to maintain session

### ✅ Notification Controls
- **Play/Pause Button**: Toggle playback via ECP keypress
- **Stop Button**: Ends session, returns Roku to home screen
- **Media Style**: Proper notification styling for media controls

### ✅ Permissions & Manifest
- Network: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE
- Service: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK
- Notifications: POST_NOTIFICATIONS (Android 13+)

## 📁 Project Structure

```
RokuCaster/
├── app/
│   ├── build.gradle                    # App-level dependencies
│   └── src/main/
│       ├── AndroidManifest.xml         # Permissions & components
│       ├── java/com/example/rokucaster/
│       │   ├── data/
│       │   │   └── repository/
│       │   │       └── SsdpRokuDiscoveryRepository.kt  # SSDP implementation
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   └── RokuDevice.kt                   # Domain model
│       │   │   └── repository/
│       │   │       └── RokuDiscoveryRepository.kt      # Repository interface
│       │   ├── service/
│       │   │   ├── CastingService.kt                   # Foreground service ⭐
│       │   │   └── CastingNotificationReceiver.kt      # Notification actions
│       │   ├── ui/
│       │   │   ├── MainActivity.kt                     # Main UI
│       │   │   ├── adapter/
│       │   │   │   └── RokuDeviceAdapter.kt            # RecyclerView adapter
│       │   │   └── viewmodel/
│       │   │       └── MainViewModel.kt                # Business logic
│       │   └── util/
│       │       └── NetworkUtils.kt                     # Network helpers
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml                   # Main layout
│           │   └── item_roku_device.xml                # RecyclerView item
│           └── values/
│               └── strings.xml                         # String resources
├── build.gradle                        # Project-level build config
├── settings.gradle                     # Project settings
├── README.md                          # User documentation
├── QUICKSTART.md                      # Quick start guide
├── TECHNICAL_ARCHITECTURE.md          # Deep dive on service architecture
└── TESTING_GUIDE.md                   # Comprehensive testing guide
```

## 🔑 Key Files Explained

### 1. CastingService.kt (THE CRITICAL COMPONENT)
**Why it's critical**: This foreground service is what enables background persistence.

**Key features**:
- `startForeground()`: Promotes service to foreground with notification
- `START_STICKY`: Ensures service is restarted if killed by OS
- `foregroundServiceType="mediaPlayback"`: Proper Android categorization
- Keep-alive mechanism: Maintains session with periodic pings
- Independent lifecycle: Survives Activity destruction

**How it prevents OS from killing the stream**:
1. Foreground services have HIGH priority (Level 2 vs Level 4 for cached apps)
2. Persistent notification signals to Android that user wants this to continue
3. OS only kills foreground services in extreme low-memory situations
4. START_STICKY ensures automatic restart if killed

### 2. SsdpRokuDiscoveryRepository.kt
**SSDP Discovery Process**:
1. Creates UDP multicast socket
2. Sends M-SEARCH request to 239.255.255.250:1900
3. Listens for responses containing LOCATION header
4. Fetches device description XML from LOCATION
5. Parses XML to extract device information
6. Emits discovered devices via Kotlin Flow

**ECP Casting Implementation**:
```kotlin
POST http://192.168.1.100:8060/launch/22507?contentId=<url>&mediaType=live
```
Uses OkHttp for HTTP requests, handles errors gracefully.

### 3. MainViewModel.kt
**State Management**:
- `StateFlow` for reactive UI updates
- Survives configuration changes (screen rotation)
- Communicates with CastingService via binding
- Manages discovery lifecycle

### 4. MainActivity.kt
**UI Features**:
- Permission handling (POST_NOTIFICATIONS on Android 13+)
- RecyclerView with discovered devices
- Confirmation dialog before casting
- Binds to CastingService for communication
- Observes ViewModel state via Kotlin Flow

## 🚀 How to Build & Run

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK 34
- Roku device on same WiFi network

### Build Steps
```bash
# 1. Open project in Android Studio
# 2. Sync Gradle files
# 3. Run on device or emulator (API 26+)
# 4. Grant notification permission when prompted
# 5. Tap "Discover Devices"
# 6. Select Roku from list
# 7. Watch video cast to Roku
# 8. Minimize app - casting continues!
```

## 🧪 Testing the Background Persistence

**Critical Test**: App Backgrounding
1. Start casting a video
2. Press Home button to minimize app
3. Wait 5 minutes
4. Verify video still playing on Roku
5. Verify notification still visible
6. Reopen app - UI should reflect active casting

**Expected Result**: ✅ Video continues playing without interruption

See `TESTING_GUIDE.md` for comprehensive test scenarios.

## 📚 Documentation Files

### README.md
- User-facing documentation
- Architecture diagrams
- Usage instructions
- Troubleshooting guide

### QUICKSTART.md
- Setup steps for developers
- Common issues and solutions
- ADB testing commands
- File structure overview

### TECHNICAL_ARCHITECTURE.md
- Deep dive into foreground service mechanism
- Process priority levels
- Activity ↔ Service communication
- Keep-alive implementation
- Lifecycle timelines

### TESTING_GUIDE.md
- Integration test scenarios
- Service persistence tests
- Edge case testing
- Performance testing
- Automated test examples

## 🔧 Dependencies

```gradle
// Core Android
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
com.google.android.material:material:1.11.0

// Architecture Components
androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0

// Coroutines
kotlinx-coroutines-core:1.7.3
kotlinx-coroutines-android:1.7.3

// Network
com.squareup.okhttp3:okhttp:4.12.0

// UI
androidx.recyclerview:recyclerview:1.3.2
androidx.constraintlayout:constraintlayout:2.1.4
```

## 🎓 Learning Points

This project demonstrates:

1. **Foreground Services**: How to implement and why they're critical for background tasks
2. **SSDP Protocol**: Manual implementation of service discovery
3. **Clean Architecture**: Separation of concerns across layers
4. **MVVM Pattern**: ViewModel for business logic, Repository for data
5. **Kotlin Coroutines**: Async operations with Flow for reactive updates
6. **Service Binding**: Activity ↔ Service communication
7. **Notification Management**: MediaStyle notifications with controls
8. **Permission Handling**: Runtime permissions (POST_NOTIFICATIONS)
9. **Lifecycle Awareness**: Surviving configuration changes

## ⚠️ Important Notes

### Why Foreground Service is Essential

**Without Foreground Service**:
- ❌ Android kills the app when backgrounded
- ❌ Network connection to Roku is lost
- ❌ User must restart playback when returning to app

**With Foreground Service**:
- ✅ Service runs at HIGH priority
- ✅ Protected from OS termination
- ✅ Casting continues when app is minimized
- ✅ Survives Activity destruction (rotation, low memory)
- ✅ User can control playback from notification

### Roku ECP Limitations

- Requires same WiFi network (no internet casting)
- Some URLs may have CORS restrictions
- Roku Media Player (22507) must be installed
- Limited metadata support compared to commercial SDKs

### Battery Optimization

Some manufacturers (Xiaomi, Huawei, OnePlus) may still kill foreground services despite Android standards. Users may need to:
- Add app to battery optimization whitelist
- Enable autostart permission
- Disable aggressive battery saving modes

## 📈 Future Enhancements

Potential improvements:
- Volume control via ECP
- Seek/scrub support
- Playlist/queue management
- Multiple device casting
- Local video file support
- Chromecast support alongside Roku
- Better error recovery
- Network quality detection

## 🔗 References

- [Roku ECP API Documentation](https://developer.roku.com/docs/developer-program/debugging/external-control-api.md)
- [Android Foreground Services Guide](https://developer.android.com/develop/background-work/services/foreground-services)
- [SSDP Protocol Specification](https://tools.ietf.org/html/draft-cai-ssdp-v1-03)
- [Android Architecture Components](https://developer.android.com/topic/architecture)

## ✨ Conclusion

This is a **production-ready implementation** of a Roku casting application with proper architecture, comprehensive error handling, and robust background persistence through a foreground service. The code is well-documented, follows Android best practices, and includes extensive testing guides.

The **CastingService** is the star of this implementation - it's what makes the app actually usable by maintaining the casting session even when the user minimizes the app, rotates the screen, or the OS tries to reclaim resources.

**Total Files**: 20+ Kotlin/XML/Gradle/Markdown files
**Lines of Code**: ~3000+ (including comments)
**Documentation**: 4 comprehensive guides
