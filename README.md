# Hue Stream TV

Real-time Philips Hue Entertainment screen synchronization for Android TV and Google TV (tested on Nvidia Shield TV Pro).

Hue Stream TV captures the screen in real-time, extracts dominant ambient colors around the display edges mapped to your entertainment area light positions, and streams RGB frames directly to the Philips Hue Bridge over DTLS.

## Features

- **Native DTLS 1.2 PSK Streaming**: Directly communicates with the Hue Bridge on UDP port 2100 using Bouncy Castle DTLS 1.2 (`TLS_PSK_WITH_AES_128_GCM_SHA256`).
- **Hardware-aligned virtual display**: Low overhead downsampling (160x90 RGBA) with direct stride unpacking tuned for Nvidia Tegra X1 (768-byte alignment) to avoid drops during 4K/HDR video playback.
- **App Whitelist & Standby**: Pick which installed media apps (e.g. Plex, SmartTube, Kodi) activate the sync. When started, the service remains in standby until one of the selected apps comes to the foreground.
- **Auto-Release**: Detects when you leave a whitelisted app or go back to the Android TV Home launcher, automatically pausing the stream and releasing the Hue lights back to normal room lighting.
- **Sync Dynamics & Brightness**:
  - Three dynamics profiles: Subtle (smooth cinematic transitions), Balanced, and Intense (low-latency action).
  - Adjustable brightness multiplier (20% to 150%).
- **TV-First UI**: Dark interface built with Jetpack Compose for TV with full D-pad remote navigation, pairing wizard, and an offline demo mode.

## How It Works

1. **Discovery & Pairing**: Discovers the Hue Bridge via mDNS/SSDP (with N-UPnP fallback) and initiates standard Pushlink pairing to obtain the API username and PSK client key.
2. **Entertainment Area**: Fetches configured entertainment groups and maps each bulb's 3D coordinates $(x, y, z)$ to corresponding 2D screen sampling zones.
3. **Capture & Streaming**: MediaProjection captures the virtual display, extracts smoothed edge colors, and packages them into Hue Entertainment v1 binary packets streamed at ~30-60 fps over UDP.

## Building from Source

### Requirements
- JDK 17+
- Android SDK (API 36 / Build Tools 36)

```bash
./gradlew assembleDebug
```

The resulting APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Sideloading via ADB

```bash
adb connect <your_android_tv_ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For foreground app detection, grant the secure settings / usage stats permission:
```bash
adb shell pm grant org.openhue.streamtv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set org.openhue.streamtv GET_USAGE_STATS allow
```

## License

MIT
