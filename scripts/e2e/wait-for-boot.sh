#!/usr/bin/env bash
# Wait until a device or emulator is attached and has finished booting (phase 17).
#
# Usage: scripts/e2e/wait-for-boot.sh [timeout seconds, default 300]
# Honours ANDROID_SERIAL when set. Exits non-zero on timeout.
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
sdk="$(sed -n 's/^sdk.dir=//p' "$repo/local.properties" 2>/dev/null || true)"
adb="${sdk:+$sdk/platform-tools/}adb"
timeout="${1:-300}"

if ! timeout_cmd="$(command -v timeout || command -v gtimeout)"; then
  timeout_cmd=""
fi
if [ -n "$timeout_cmd" ]; then
  "$timeout_cmd" "$timeout" "$adb" wait-for-device
else
  "$adb" wait-for-device
fi

deadline=$(( $(date +%s) + timeout ))
until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "wait-for-boot: the device did not finish booting within ${timeout}s" >&2
    exit 1
  fi
  sleep 2
done
echo "wait-for-boot: boot completed"
