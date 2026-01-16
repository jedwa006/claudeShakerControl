# Claude Code Project Instructions

## Environment Setup

### Java/Gradle
This project requires Java 17+ for Hilt/Dagger. Use the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Always use this JAVA_HOME when running Gradle commands:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>
```

Or with full environment:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH:$HOME/Library/Android/sdk/platform-tools"
./gradlew <task>
```

### Android SDK
```bash
export ANDROID_HOME=~/Library/Android/sdk
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

### Common Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lintDebug

# Run unit tests
./gradlew testDebugUnitTest

# Check connected devices
adb devices

# View BLE logs
adb logcat -s BleManager:D BleMachineRepository:D SessionManager:D

# Take screenshot
adb exec-out screencap -p > screenshot.png
```

## Project Structure
- Android app: Kotlin + Jetpack Compose + Hilt
- BLE communication with ESP32-S3 firmware
- Wire protocol: CRC-16/CCITT-FALSE framed messages

## UI Test Coordinates (Lenovo TB351FU, landscape 2000x1200)

Use `adb shell uiautomator dump` to get fresh coordinates if layout changes.

### Home Screen
- Hamburger menu: tap 60 107
- "Disconnected" status: tap 862 108 → navigates to Devices
- "Connected" status: tap 862 108 → navigates to Devices

### Devices Screen
- Reconnect button: tap 1863 242
- Disconnect button: tap 1303 168 (same location as Reconnect)
- Scan button: tap 1325 271
- Forget button: tap 1195 168

### Navigation Drawer
- Open: swipe from x=0 to x=300 at y=400
- Devices item: tap 100 270

## Related Repositories
- Firmware: `/tmp/NuNuCryoShaker` (ESP32-S3 BLE GATT server)
- Handoff doc: `docs/FIRMWARE_AGENT_PROMPT.md`
