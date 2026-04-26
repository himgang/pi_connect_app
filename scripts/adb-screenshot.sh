#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CAPTURE_DIR="$ROOT_DIR/captures"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_PATH="$CAPTURE_DIR/pi-connect-$TIMESTAMP.png"

mkdir -p "$CAPTURE_DIR"
adb exec-out screencap -p > "$OUTPUT_PATH"
echo "$OUTPUT_PATH"

