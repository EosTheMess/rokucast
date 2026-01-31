# Quick Start Guide - Roku Caster

## Prerequisites

- Android Studio Arctic Fox or later
- Android device/emulator running API 26+ (Android 8.0+)
- Roku device on the same WiFi network
- Test video URL (HTTP/HTTPS accessible)

## Setup Steps

### 1. Import the Project

```bash
# Clone or copy the project
cd RokuCaster

# Open in Android Studio
# File → Open → Select RokuCaster folder
```

### 2. Sync Gradle

```bash
# Android Studio will prompt to sync
# Or manually: File → Sync Project with Gradle Files
```

### 3. Enable External Control on Roku

On your Roku device:
1. Settings → System → Advanced system settings
2. External control → Enable
3. Network access → Network control by applications → Default (enabled)

### 4. Run the Application

```bash
# Connect Android device or start emulator
# Click Run button or Shift+F10
```

## Using the App

### Step 1: Enter Video URL

Pre-filled with a sample video:
```
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
```

Or use your own HTTP/HTTPS video URL.

### Step 2: Discover Devices

1. Tap **"Discover Devices"** button
2. Wait 5-10 seconds for SSDP discovery
3. Found Roku devices appear in the RecyclerView

**Note**: If no devices found:
- Check WiFi connection (same network as Roku)
- Verify External Control is enabled on Roku
- Check router firewall settings (SSDP uses port 1900)

### Step 3: Cast Video

1. Tap on a discovered Roku device in the list
2. Confirm casting in the dialog
3. Video begins playing on Roku
4. Notification appears with media controls

### Step 4: Background Persistence Test

1. Press Home button to minimize app
2. Verify notification remains visible
3. Verify video continues playing on Roku
4. Tap notification to reopen app
5. UI reflects active casting state

### Step 5: Notification Controls

Pull down notification shade:
- **Play/Pause**: Toggle playback
- **Stop**: End casting session

### Step 6: Stop Casting

Either:
- Tap **"Stop Casting"** button in app
- Tap **"Stop"** in notification
- Roku returns to home screen

## Architecture Overview

```
MainActivity (UI)
    ↓
MainViewModel (Business Logic)
    ↓
SsdpRokuDiscoveryRepository (Discovery & ECP Commands)
    ↓
CastingService (Foreground Service - Maintains Session)
```

## Key Files

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Main UI and user interaction |
| `MainViewModel.kt` | State management and business logic |
| `CastingService.kt` | **Foreground service for background persistence** |
| `SsdpRokuDiscoveryRepository.kt` | SSDP discovery and Roku ECP commands |
| `RokuDevice.kt` | Domain model |
| `RokuDeviceAdapter.kt` | RecyclerView adapter |
| `AndroidManifest.xml` | Permissions and component declarations |

## Common Issues & Solutions

### Issue: No Devices Found

**Possible Causes:**
1. Not on same WiFi network as Roku
2. Router blocking SSDP multicast (port 1900)
3. External Control disabled on Roku

**Solutions:**
- Verify WiFi connection
- Check Roku settings (Settings → System → Advanced → External control)
- Try different WiFi network
- Check router firewall settings

### Issue: Casting Starts But Immediately Stops

**Possible Causes:**
1. Invalid video URL
2. Roku cannot access the URL (network restrictions)
3. Unsupported video format

**Solutions:**
- Verify URL is HTTP/HTTPS
- Test URL in browser first
- Try a different video URL
- Check Roku's network connectivity

### Issue: Service Gets Killed

**Possible Causes:**
1. Aggressive battery optimization (manufacturer-specific)
2. User denied POST_NOTIFICATIONS permission
3. App not whitelisted in battery settings

**Solutions:**
- Settings → Apps → Roku Caster → Battery → Unrestricted
- Grant notification permission
- Add to autostart whitelist (Xiaomi, Huawei, etc.)

### Issue: "Permission Denied" Error

**Cause:** Missing or denied permissions

**Solution:**
- Check AndroidManifest.xml for all required permissions
- Grant runtime permissions (POST_NOTIFICATIONS on Android 13+)
- Reinstall app if permissions are stuck

## Testing Checklist

- [ ] Discovery finds Roku devices
- [ ] Selecting device shows confirmation dialog
- [ ] Casting starts and notification appears
- [ ] Video plays on Roku
- [ ] App can be minimized (casting continues)
- [ ] Screen rotation preserves state
- [ ] Notification Play/Pause works
- [ ] Notification Stop works
- [ ] Reopening app shows active casting state
- [ ] Stop Casting button ends session

## Advanced: ADB Testing

### Force Stop App (Test Service Persistence)

```bash
# Kill the app process
adb shell am force-stop com.example.rokucaster

# Service should continue running in separate process
# Check running services
adb shell dumpsys activity services | grep CastingService
```

### Simulate Doze Mode

```bash
# Unplug device (simulate on battery)
adb shell dumpsys battery unplug

# Force device into idle (Doze)
adb shell dumpsys deviceidle force-idle

# Service should maintain casting
# Exit Doze mode
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

### Monitor Service Logs

```bash
# Filter logs by tag
adb logcat -s CastingService:D SsdpRokuDiscovery:D MainViewModel:D

# Or filter by package
adb logcat | grep "com.example.rokucaster"
```

## Next Steps

1. **Customize UI**: Modify layouts in `res/layout/`
2. **Add Features**: 
   - Volume control
   - Seek bar
   - Media info display
   - Multiple device casting
3. **Improve Discovery**: Add manual IP entry option
4. **Error Handling**: More robust error messages and retry logic
5. **Testing**: Add unit tests and instrumentation tests

## Resources

- [Roku ECP Documentation](https://developer.roku.com/docs/developer-program/debugging/external-control-api.md)
- [Android Foreground Services](https://developer.android.com/develop/background-work/services/foreground-services)
- [SSDP Protocol](https://tools.ietf.org/html/draft-cai-ssdp-v1-03)

## Support

For issues or questions:
1. Check `TECHNICAL_ARCHITECTURE.md` for in-depth explanations
2. Review `README.md` for troubleshooting
3. Check Android Studio Logcat for error messages
