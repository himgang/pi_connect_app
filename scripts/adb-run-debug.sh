#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="com.himgang.piconnect"
ACTIVITY_NAME=".MainActivity"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$ROOT_DIR"

adb start-server >/dev/null

device_count="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$device_count" -eq 0 ]]; then
  echo "No authorized Android device found."
  echo "Connect a phone with USB debugging enabled, accept the RSA prompt, then run:"
  echo "  scripts/adb-device.sh"
  exit 1
fi

./gradlew --no-daemon assembleDebug
adb install -r "$APK_PATH"
adb shell am force-stop "$PACKAGE_NAME" >/dev/null
adb shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"

