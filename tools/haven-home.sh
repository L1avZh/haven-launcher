#!/bin/bash
# Bring Haven to the front on the TV from your Mac (wireless ADB must already be connected).
# Usage: tools/haven-home.sh [ip:port]
# Use this when the TV remote cannot reach Haven (e.g. the LG Home button goes to the LG TV, not to this box).
set -e
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"; [ -x "$ADB" ] || ADB=adb
DEV="${1:-$("$ADB" devices | awk '/:[0-9]+\tdevice/{print $1; exit}')}"
[ -n "$DEV" ] || { echo "No wireless ADB device connected. Run: adb connect <ip>:<port>"; exit 1; }
"$ADB" -s "$DEV" shell am start -n dev.l1avzh.haven/.MainActivity
