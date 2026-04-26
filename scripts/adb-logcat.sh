#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.himgang.piconnect"

adb start-server >/dev/null

pid="$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$pid" ]]; then
  echo "$PACKAGE_NAME is not running. Start it with scripts/adb-run-debug.sh first."
  exit 1
fi

adb logcat --pid="$pid"

