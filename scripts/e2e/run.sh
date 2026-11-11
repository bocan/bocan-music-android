#!/usr/bin/env bash
# Run the end-to-end suite (phase 17) and collect its artifacts under build/e2e/.
#
# Usage: scripts/e2e/run.sh [E2ESuite|SmokeSuite]   (normally through make end-to-end)
#
# Device choice: the first attached device in the "device" state is used, a phone
# included, and the script says which one it is. With nothing attached it boots the
# AVD named by E2E_AVD (default bocan-api36) headless, waits for boot, and shuts it
# down afterwards unless E2E_KEEP_EMULATOR=1. Only the debug build (applicationId
# suffix .debug) is installed, so a release install on the same phone is untouched.
set -euo pipefail

suite="${1:-E2ESuite}"
repo="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo"

sdk="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
if [ -z "$sdk" ]; then
  echo "e2e: local.properties has no sdk.dir; run make doctor" >&2
  exit 2
fi
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"
avd="${E2E_AVD:-bocan-api36}"
out="build/e2e"

rm -rf "$out"
mkdir -p "$out"
# Gradle leaves the last run's results in place when a build fails before the tests run;
# clear them so the summary below can never report an earlier run as this one.
rm -rf app/build/outputs/androidTest-results/connected app/build/reports/androidTests/connected \
  app/build/outputs/connected_android_test_additional_output

serial="$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
booted_here=0
if [ -z "$serial" ]; then
  if ! "$emulator" -list-avds 2>/dev/null | grep -qx "$avd"; then
    echo "e2e: no device is attached and there is no AVD named $avd." >&2
    echo "e2e: create it in Android Studio, or boot another with: make emulator-headless AVD=<name>" >&2
    exit 2
  fi
  echo "e2e: no device attached; booting $avd headless (log: $out/emulator.log)"
  "$emulator" -avd "$avd" -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect > "$out/emulator.log" 2>&1 &
  booted_here=1
  scripts/e2e/wait-for-boot.sh 300
  serial="$("$adb" devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi
export ANDROID_SERIAL="$serial"

if [ "$("$adb" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" = "1" ] || [[ "$serial" == emulator-* ]]; then
  kind="emulator"
else
  kind="PHYSICAL DEVICE"
fi
model="$("$adb" shell getprop ro.product.model | tr -d '\r')"
api="$("$adb" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "e2e: running $suite on $serial ($kind: $model, API $api)"

started=$(date +%s)
set +e
./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=io.cloudcauldron.bocan.app.$suite" \
  2>&1 | tee "$out/gradle.log"
status=${PIPESTATUS[0]}
set -e
elapsed=$(( $(date +%s) - started ))

# Gradle's outputs: JUnit XML, the HTML report, and the Test Storage files (screenshots).
if [ -d app/build/outputs/androidTest-results/connected/debug ]; then
  mkdir -p "$out/junit"
  find app/build/outputs/androidTest-results/connected/debug -name '*.xml' -exec cp {} "$out/junit/" \;
fi
if [ -d app/build/reports/androidTests/connected/debug ]; then
  cp -R app/build/reports/androidTests/connected/debug "$out/report"
fi
if [ -d app/build/outputs/connected_android_test_additional_output ]; then
  mkdir -p "$out/screenshots"
  find app/build/outputs/connected_android_test_additional_output -name '*.png' -exec cp {} "$out/screenshots/" \;
fi

if [ "$booted_here" = "1" ] && [ "${E2E_KEEP_EMULATOR:-0}" != "1" ]; then
  "$adb" emu kill > /dev/null 2>&1 || true
fi

count() { # sum one attribute over every <testsuite> in the JUnit XML
  find "$out/junit" -name '*.xml' -exec grep -ho "<testsuite [^>]*" {} + 2>/dev/null |
    grep -o "$1=\"[0-9]*\"" | grep -o '[0-9]*' | awk '{ s += $1 } END { print s + 0 }'
}
if [ -d "$out/junit" ]; then
  tests=$(count tests); failures=$(count failures); errors=$(count errors); skipped=$(count skipped)
  passed=$(( tests - failures - errors - skipped ))
  echo "e2e: $suite on $serial in ${elapsed}s: $passed passed, $(( failures + errors )) failed, $skipped skipped"
else
  echo "e2e: $suite on $serial in ${elapsed}s: no JUnit results (see $out/gradle.log)"
fi
echo "e2e: report $out/report/index.html, screenshots $out/screenshots/, Gradle log $out/gradle.log"
exit "$status"
