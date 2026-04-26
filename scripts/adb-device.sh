#!/usr/bin/env bash
set -euo pipefail

adb start-server >/dev/null
adb devices -l

