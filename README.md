# Pi Connect Android

Android wrapper for Raspberry Pi Connect, focused on making `connect.raspberrypi.com` easier to use from a phone than a regular mobile browser.

## Current MVP

- Fullscreen WebView for Raspberry Pi Connect.
- Persistent cookies and DOM storage for sign-in.
- WebRTC permission bridge for trusted Raspberry Pi domains.
- Bottom controls for back, forward, home, reload, Android keyboard, remote key bar, and fullscreen.
- Browser fallback, clipboard paste, offline-aware retry, and web session reset controls.
- Remote key bar for Escape, Tab, sticky/locked modifiers, terminal shortcuts, arrows, paging, and Enter.
- GitHub Actions debug APK build, so the Raspberry Pi does not need Android Studio or the Android SDK.

## Build

The intended build path is GitHub Actions:

1. Push to `main`.
2. Open the repository on GitHub.
3. Go to **Actions**.
4. Open the latest **Android CI** run.
5. Download the `pi-connect-debug-apk` artifact.
6. Install `app-debug.apk` on an Android phone.

Local builds are optional and require Java 17 and Android SDK platform 35/build-tools 35.0.0.
This repository includes a Gradle wrapper pinned to the same Gradle version used by CI:

```bash
./gradlew --no-daemon assembleDebug
```

## ADB Development

Local ADB testing requires an Android phone with Developer options and USB debugging enabled.
After plugging in the phone, accept the RSA authorization prompt and check the connection:

```bash
scripts/adb-device.sh
```

Build, install, and launch the debug app:

```bash
scripts/adb-run-debug.sh
```

View app logs for the running debug build:

```bash
scripts/adb-logcat.sh
```

Capture the current device screen to `captures/`:

```bash
scripts/adb-screenshot.sh
```

## Raspberry Pi Workflow

This repository is designed so development can happen from a Raspberry Pi 5 over Raspberry Pi Connect. The Pi only needs git for normal work. GitHub Actions performs the Android build independently after each push, so the build continues even if the Pi Connect browser session disconnects.

## Notes

This app does not reverse engineer the private Raspberry Pi Connect session protocol. It uses the official browser endpoint inside Android WebView and adds native mobile controls around it.
