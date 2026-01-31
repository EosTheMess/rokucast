# Integration Testing Guide - Roku Caster

This guide covers integration testing for the Roku Caster application, focusing on testing the critical foreground service persistence and casting functionality.

## Test Environment Setup

### Required Hardware
- Android device (physical preferred) running API 26+
- Roku device (any model with ECP support)
- WiFi router (both devices on same network)

### Optional Tools
- ADB (Android Debug Bridge)
- Wireshark or tcpdump (for network traffic analysis)
- Charles Proxy (for HTTP traffic inspection)

## Test Categories

### 1. Discovery Tests

#### Test 1.1: Basic SSDP Discovery
**Objective**: Verify SSDP multicast discovery works correctly

**Steps**:
1. Start the app
2. Tap "Discover Devices"
3. Wait 5-10 seconds

**Expected Results**:
- Progress indicator shows
- Roku device(s) appear in RecyclerView
- Device name, IP, and model are displayed correctly

**Verification**:
```bash
# Monitor SSDP traffic
adb logcat -s SsdpRokuDiscovery:D

# Look for:
# - "Sent SSDP discovery request"
# - "Found device at: http://..."
# - "Added device: [name] at [ip]"
```

#### Test 1.2: No Devices Found
**Objective**: Verify graceful handling when no devices are discovered

**Steps**:
1. Disconnect Roku from network
2. Start discovery

**Expected Results**:
- Discovery completes after timeout
- Toast message: "No Roku devices found..."
- RecyclerView remains empty

#### Test 1.3: Discovery Stop/Restart
**Objective**: Verify discovery can be stopped and restarted

**Steps**:
1. Start discovery
2. Tap "Stop Discovery" while in progress
3. Tap "Discover Devices" again

**Expected Results**:
- Discovery stops cleanly
- Socket closes properly (check logs)
- Second discovery starts successfully

**Verification**:
```bash
adb logcat -s SsdpRokuDiscovery:D | grep "Discovery stopped"
```

### 2. Casting Tests

#### Test 2.1: Successful Video Cast
**Objective**: Verify video casting works end-to-end

**Steps**:
1. Enter test video URL
2. Discover devices
3. Tap on a Roku device
4. Confirm casting in dialog

**Expected Results**:
- Notification appears within 5 seconds
- Video begins playing on Roku
- UI shows "casting" state (Stop Casting button enabled)

**Verification**:
```bash
# Check service logs
adb logcat -s CastingService:D

# Look for:
# - "Starting casting to [device]"
# - "Successfully started casting"
# - "Successfully cast video to [device]"
```

#### Test 2.2: Invalid URL Handling
**Objective**: Verify error handling for invalid URLs

**Test Cases**:
| URL | Expected Behavior |
|-----|-------------------|
| (empty) | Toast: "Please enter a video URL" |
| `example.com` | Toast: "URL must start with http://" |
| `ftp://example.com/video.mp4` | Toast: "URL must start with http://" |
| `http://invalid-domain-12345.com/video.mp4` | Casting fails, error logged |

#### Test 2.3: Multiple Cast Attempts
**Objective**: Verify only one active casting session allowed

**Steps**:
1. Start casting to Device A
2. Attempt to start casting to Device B

**Expected Results**:
- Discover button disabled during active cast
- Cannot start second cast without stopping first

### 3. Foreground Service Persistence Tests

#### Test 3.1: App Backgrounding (CRITICAL TEST)
**Objective**: Verify service maintains casting when app is minimized

**Steps**:
1. Start casting
2. Verify video playing on Roku
3. Press Home button (minimize app)
4. Wait 2 minutes
5. Open recent apps - app should be in background
6. Check Roku - video should still be playing
7. Check notification shade - notification should be visible
8. Reopen app

**Expected Results**:
- ✅ Video continues playing on Roku
- ✅ Notification remains visible
- ✅ Service continues running (check logs)
- ✅ UI reflects active casting state upon return

**Verification**:
```bash
# Check if service is running
adb shell dumpsys activity services | grep CastingService

# Monitor service logs while backgrounded
adb logcat -s CastingService:D

# Should see keep-alive pings every 30 seconds
```

**PASS CRITERIA**: Video plays continuously for at least 5 minutes while app is backgrounded.

#### Test 3.2: Screen Lock
**Objective**: Verify casting continues when screen is locked

**Steps**:
1. Start casting
2. Lock screen (power button)
3. Wait 1 minute
4. Unlock screen

**Expected Results**:
- Video continues playing
- Notification visible on lock screen
- Service maintains session

#### Test 3.3: Configuration Changes (Screen Rotation)
**Objective**: Verify activity recreation doesn't affect casting

**Steps**:
1. Start casting
2. Rotate device (portrait ↔ landscape)
3. Repeat 3-4 times

**Expected Results**:
- Video continues playing (no interruption)
- UI state preserved (ViewModel)
- Service reference maintained
- RecyclerView shows same devices

**Verification**:
```bash
# Monitor activity lifecycle
adb logcat -s MainActivity:D

# Look for:
# - onCreate()
# - onDestroy()
# But CastingService logs should show no interruption
```

#### Test 3.4: Low Memory Simulation
**Objective**: Verify service survives when app process is killed

**Steps**:
1. Start casting
2. Open Developer Options → Background process limit → Set to "No background processes"
3. Press Home
4. Open several other apps (to pressure memory)
5. Check if video still playing
6. Reopen app

**Expected Results**:
- Service continues running (separate process)
- Video still playing
- App rebinds to existing service

**Verification**:
```bash
# Force kill app process
adb shell am force-stop com.example.rokucaster

# Check if service is still running
adb shell dumpsys activity services | grep CastingService

# Should show service running despite app being killed
```

#### Test 3.5: Doze Mode (Advanced)
**Objective**: Verify service maintains casting in Doze mode

**Steps**:
```bash
# Start casting first

# Simulate device on battery
adb shell dumpsys battery unplug

# Force Doze mode
adb shell dumpsys deviceidle force-idle

# Wait 30 seconds

# Check service
adb shell dumpsys activity services | grep CastingService

# Exit Doze
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

**Expected Results**:
- Service continues running
- Keep-alive may be delayed but resumes after Doze
- Video playback may pause briefly then resume

### 4. Notification Control Tests

#### Test 4.1: Play/Pause Toggle
**Objective**: Verify Play/Pause button in notification works

**Steps**:
1. Start casting
2. Pull down notification shade
3. Tap "Pause" button
4. Verify video pauses on Roku
5. Tap "Play" button
6. Verify video resumes

**Expected Results**:
- Roku responds to keypress commands
- Notification button updates (Pause ↔ Play)

**Verification**:
```bash
# Monitor keypress commands
adb logcat -s SsdpRokuDiscovery:D | grep "keypress"

# Should see:
# - "Sent keypress 'Pause' to [device]"
# - "Sent keypress 'Play' to [device]"
```

#### Test 4.2: Stop Button
**Objective**: Verify Stop button ends casting session

**Steps**:
1. Start casting
2. Pull down notification shade
3. Tap "Stop" button

**Expected Results**:
- Video stops on Roku
- Roku returns to home screen
- Notification disappears
- Service stops
- UI returns to initial state

**Verification**:
```bash
# Should see service destruction
adb logcat -s CastingService:D | grep "destroyed"
```

#### Test 4.3: Notification Tap (Open App)
**Objective**: Verify tapping notification opens app

**Steps**:
1. Start casting
2. Press Home to minimize app
3. Tap on the casting notification

**Expected Results**:
- App comes to foreground
- MainActivity displayed with active casting state

### 5. Edge Case Tests

#### Test 5.1: Network Disconnection
**Objective**: Verify behavior when WiFi disconnects during casting

**Steps**:
1. Start casting
2. Turn off WiFi on phone
3. Wait 10 seconds
4. Turn WiFi back on

**Expected Results**:
- Service detects disconnection (optional: show error notification)
- Service attempts to reconnect when WiFi returns
- Or gracefully stops with user notification

#### Test 5.2: Roku Power Off
**Objective**: Verify behavior when Roku is turned off during casting

**Steps**:
1. Start casting
2. Turn off Roku device
3. Check app state

**Expected Results**:
- Keep-alive fails
- Service eventually times out or detects error
- Optional: Notify user that device is unavailable

#### Test 5.3: Multiple Rapid Start/Stop
**Objective**: Verify no resource leaks or crashes

**Steps**:
1. Start casting
2. Immediately stop
3. Repeat 10 times rapidly

**Expected Results**:
- No crashes
- No memory leaks
- Service starts/stops cleanly each time

**Verification**:
```bash
# Monitor for errors
adb logcat *:E

# Check for any exception stack traces
```

#### Test 5.4: Permission Denial
**Objective**: Verify app handles denied notification permission gracefully

**Steps**:
1. Install app
2. Deny POST_NOTIFICATIONS permission
3. Start casting

**Expected Results**:
- Service still starts (notification is mandatory)
- User sees notification despite denied permission
- Toast warns user about degraded experience

### 6. Performance Tests

#### Test 6.1: Memory Usage
**Objective**: Verify no memory leaks during extended casting

**Steps**:
1. Start casting
2. Let run for 30 minutes
3. Monitor memory usage

**Verification**:
```bash
# Monitor memory usage
adb shell dumpsys meminfo com.example.rokucaster

# Run multiple times, check for steady increase (leak indicator)
```

**Expected Results**:
- Memory usage stable
- No continuous growth

#### Test 6.2: Battery Impact
**Objective**: Measure battery consumption during casting

**Steps**:
1. Fully charge device
2. Start casting
3. Monitor battery level for 1 hour

**Expected Results**:
- Battery drain comparable to other media apps
- Device doesn't heat up excessively

#### Test 6.3: Network Traffic
**Objective**: Verify minimal traffic from app (Roku does streaming)

**Steps**:
1. Use network monitoring tool
2. Start casting
3. Monitor app's data usage

**Expected Results**:
- Initial ECP commands (< 1 KB)
- Periodic keep-alive (minimal, every 30s)
- No large data transfer (video streams to Roku, not through phone)

## Regression Test Checklist

Before each release, run this checklist:

- [ ] Discovery finds devices on first attempt
- [ ] Casting starts and video plays on Roku
- [ ] Notification appears with controls
- [ ] App can be minimized - casting continues
- [ ] Screen rotation doesn't interrupt casting
- [ ] Play/Pause notification button works
- [ ] Stop notification button works
- [ ] Stop Casting button in app works
- [ ] Service survives app force-stop
- [ ] No crashes on rapid start/stop
- [ ] Memory usage stable over 30min cast
- [ ] All permissions properly requested
- [ ] Error messages clear and helpful

## Automated Testing

### Example Instrumented Test (CastingServiceTest.kt)

```kotlin
@RunWith(AndroidJUnit4::class)
class CastingServiceTest {
    
    @get:Rule
    val serviceRule = ServiceTestRule()
    
    @Test
    fun testServiceStartsAsForeground() {
        val intent = Intent(ApplicationProvider.getApplicationContext<Context>(), 
            CastingService::class.java).apply {
            action = CastingService.ACTION_START_CASTING
            putExtra(CastingService.EXTRA_DEVICE_NAME, "Test Roku")
            putExtra(CastingService.EXTRA_DEVICE_IP, "192.168.1.100")
            putExtra(CastingService.EXTRA_VIDEO_URL, "http://test.com/video.mp4")
        }
        
        serviceRule.startService(intent)
        
        // Verify service is running
        val binder = serviceRule.bindService(Intent(
            ApplicationProvider.getApplicationContext(),
            CastingService::class.java
        ))
        
        assertNotNull(binder)
    }
}
```

## Test Results Template

```markdown
# Test Execution Report

**Date**: YYYY-MM-DD
**Tester**: [Name]
**Build**: [Version]
**Device**: [Model / API Level]

## Test Results

| Test ID | Test Name | Status | Notes |
|---------|-----------|--------|-------|
| 1.1 | Basic SSDP Discovery | ✅ PASS | Found 2 devices in 8s |
| 1.2 | No Devices Found | ✅ PASS | Proper timeout message |
| 3.1 | App Backgrounding | ✅ PASS | 10min continuous playback |
| 3.4 | Low Memory Simulation | ❌ FAIL | Service killed after 2min |

## Issues Found

1. **Issue #001**: Service killed in low memory on Samsung Galaxy S10
   - Severity: High
   - Workaround: Add battery optimization whitelist instruction
```

## Continuous Integration

For CI/CD pipelines, create automated test suites:

```yaml
# .github/workflows/android-test.yml
name: Android CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run Unit Tests
        run: ./gradlew test
      - name: Run Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 29
          script: ./gradlew connectedCheck
```

## Conclusion

Thorough testing of the foreground service is critical to ensure reliable background casting. Focus especially on Tests 3.1-3.4, as these verify the core value proposition of the app: uninterrupted casting even when the app is not in focus.
