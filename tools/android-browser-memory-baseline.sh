#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="${PACKAGE:-org.kaspa.browser}"
SERIAL=""
LABEL="unspecified"
SAMPLES=6
INTERVAL=10

usage() {
  cat <<'USAGE'
Usage: android-browser-memory-baseline.sh [-s SERIAL] -l LABEL [-n SAMPLES] [-i INTERVAL_SECONDS]
Captures host and visible app-owned WebView renderer PSS using adb.
Run against a release build on a physical device. Keep the same device, page set,
network, app build, and scenario when comparing runs.
USAGE
}

while getopts ":s:l:n:i:h" opt; do
  case "$opt" in
    s) SERIAL="$OPTARG" ;;
    l) LABEL="$OPTARG" ;;
    n) SAMPLES="$OPTARG" ;;
    i) INTERVAL="$OPTARG" ;;
    h) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ -n "$LABEL" ]] || { echo "A scenario label is required (-l)." >&2; exit 2; }
[[ "$SAMPLES" =~ ^[1-9][0-9]*$ ]] || { echo "Samples must be a positive integer." >&2; exit 2; }
[[ "$INTERVAL" =~ ^[0-9]+$ ]] || { echo "Interval must be a non-negative integer." >&2; exit 2; }
command -v adb >/dev/null || { echo "adb is required on PATH." >&2; exit 127; }

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )
"${ADB[@]}" get-state >/dev/null

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -cs 'A-Za-z0-9._-' '_')"
OUT="browser-memory-${SAFE_LABEL}-${STAMP}"
mkdir -p "$OUT"

{
  echo "utc_start=$STAMP"
  echo "package=$PACKAGE"
  echo "label=$LABEL"
  echo "samples=$SAMPLES"
  echo "interval_seconds=$INTERVAL"
  "${ADB[@]}" shell getprop ro.build.version.release
  "${ADB[@]}" shell getprop ro.build.version.sdk
  "${ADB[@]}" shell getprop ro.product.manufacturer
  "${ADB[@]}" shell getprop ro.product.model
  "${ADB[@]}" shell dumpsys webviewupdate
  "${ADB[@]}" shell dumpsys package "$PACKAGE" | grep -E 'versionName=|versionCode=' || true
} > "$OUT/device.txt" 2>&1

for ((sample=1; sample<=SAMPLES; sample++)); do
  FILE="$OUT/sample-$(printf '%02d' "$sample").txt"
  {
    echo "sample=$sample"
    date -u
    echo
    echo "===== APP HOST MEMINFO ====="
    "${ADB[@]}" shell dumpsys meminfo "$PACKAGE"
    echo
    echo "===== APP / WEBVIEW RENDERER PROCESS LIST ====="
    "${ADB[@]}" shell ps -A -o PID,NAME
    echo
    echo "===== FULL APP PROCESS CONNECTIONS (FIND ISOLATED RENDERER PID) ====="
    "${ADB[@]}" shell dumpsys activity processes "$PACKAGE"
    echo
    echo "===== HOST PROCESS MEMINFO ====="
    "${ADB[@]}" shell dumpsys meminfo "$PACKAGE"
    echo
    echo "Renderer PIDs must be identified from the Isolated/SandboxedProcessService connection records above."
    echo "Run: adb shell dumpsys meminfo RENDERER_PID for each app-owned renderer PID and include all in the total."
  } > "$FILE" 2>&1
  if (( sample < SAMPLES )); then sleep "$INTERVAL"; fi
done

echo "Captured $SAMPLES samples in $OUT"
echo "Important: renderer processes may be isolated and hidden from package-name filtering on some Android builds."
echo "Use the activity-process connection section and device process list to verify every renderer PID before summing PSS."
