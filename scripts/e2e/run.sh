#!/usr/bin/env bash
# Run the end-to-end suite (phase 17) and collect its artifacts under build/e2e/.
#
# Usage: scripts/e2e/run.sh [E2ESuite|SmokeSuite|Persistence|<class>]   (normally through make end-to-end)
# E2ESuite also runs the two persistence phases around a process kill; Persistence runs only those.
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

# local.properties on a development Mac; ANDROID_HOME (or ANDROID_SDK_ROOT) on a CI runner.
sdk="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
sdk="${sdk:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
if [ -z "$sdk" ]; then
  echo "e2e: no Android SDK: local.properties has no sdk.dir and ANDROID_HOME is unset; run make doctor" >&2
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

app_id="io.cloudcauldron.bocan.android.debug"
runner="$app_id.test/androidx.test.runner.AndroidJUnitRunner"

# One persistence phase through a bare am instrument: no Gradle, no Orchestrator, so the app's
# data is not cleared before it. Passes only on "OK (1 test)" with no failure or skip in the
# status codes (-2 a failure, -4 a skipped assumption: a phase that skipped tested nothing).
run_phase() {
  local log="$out/persistence-$1.log"
  "$adb" shell am instrument -w -r -e e2ePersistence true \
    -e class "io.cloudcauldron.bocan.app.persistence.$1" "$runner" > "$log" 2>&1 || true
  grep -q '^OK (1 test)' "$log" && ! grep -Eq 'INSTRUMENTATION_STATUS_CODE: -(2|4)|INSTRUMENTATION_FAILED|Process crashed' "$log"
}

# Settings and the queue across a real process kill (docs/e2e.md, "Persistence"): phase 1 on
# a cleared install, am force-stop, phase 2 in a new process on the same data. Sets
# persistence_passed and persistence_failed; the APKs Gradle uninstalled are installed again
# for the two phases and removed afterwards.
persistence() {
  persistence_passed=0
  persistence_failed=0
  echo "e2e: persistence: write, force-stop, read (logs: $out/persistence-*.log)"
  if ! ./gradlew -q :app:installDebug :app:installDebugAndroidTest >> "$out/gradle.log" 2>&1; then
    echo "e2e: persistence: could not install the APKs (see $out/gradle.log)" >&2
    persistence_failed=2
    return
  fi
  "$adb" shell pm clear "$app_id" > /dev/null
  local phase
  for phase in PersistenceWritePhase PersistenceReadPhase; do
    if [ "$phase" = PersistenceReadPhase ]; then
      "$adb" shell am force-stop "$app_id"
    fi
    if run_phase "$phase"; then
      echo "e2e: persistence: $phase passed"
      persistence_passed=$(( persistence_passed + 1 ))
    else
      echo "e2e: persistence: $phase FAILED (see $out/persistence-$phase.log)"
      persistence_failed=$(( persistence_failed + 1 ))
      # The read phase means nothing after a failed write.
      [ "$phase" = PersistenceWritePhase ] && persistence_failed=2 && break
    fi
  done
  "$adb" uninstall "$app_id.test" > /dev/null 2>&1 || true
  "$adb" uninstall "$app_id" > /dev/null 2>&1 || true
}

started=$(date +%s)
status=0
persistence_passed=0
persistence_failed=0
if [ "$suite" != "Persistence" ]; then
  set +e
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=io.cloudcauldron.bocan.app.$suite" \
    2>&1 | tee "$out/gradle.log"
  status=${PIPESTATUS[0]}
  set -e
fi
# The full suite, or the persistence phases alone (make end-to-end-persistence).
if [ "$suite" = "E2ESuite" ] || [ "$suite" = "Persistence" ]; then
  persistence
  [ "$persistence_failed" -gt 0 ] && status=1
fi
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
[ "$suite" = "Persistence" ] && mkdir -p "$out/junit" # no Gradle results: count from an empty folder
if [ -d "$out/junit" ]; then
  tests=$(count tests); failures=$(count failures); errors=$(count errors); skipped=$(count skipped)
  passed=$(( tests - failures - errors - skipped + persistence_passed ))
  failed=$(( failures + errors + persistence_failed ))
  summary="e2e: $suite on $serial in ${elapsed}s: $passed passed, $failed failed, $skipped skipped"
else
  summary="e2e: $suite on $serial in ${elapsed}s: no JUnit results (see $out/gradle.log)"
fi
echo "$summary"
# The same line for tools: the nightly workflow quotes it in the issue it opens on failure.
echo "$summary" > "$out/summary.txt"
echo "e2e: report $out/report/index.html, screenshots $out/screenshots/, Gradle log $out/gradle.log"
exit "$status"
