#!/usr/bin/env bash
#
# Verifies that every native library in the built APK has 16 KB aligned ELF LOAD
# segments, as Google Play requires for apps targeting Android 15 and above. The
# only native code Bocan ships is the FFmpeg audio decoder (org.jellyfin.media3),
# so this guards against a future artifact bump silently regressing alignment and
# getting the app bundle rejected at upload time.
#
# Usage: scripts/check-so-alignment.sh [path-to-apk-or-aab]
# With no argument it looks for the debug APK under app/build/outputs.

set -euo pipefail

MIN_ALIGN=16384 # 0x4000, one 16 KB page

apk="${1:-}"
if [ -z "$apk" ]; then
  apk="$(find app/build/outputs/apk -name '*.apk' 2>/dev/null | head -1 || true)"
fi
if [ -z "$apk" ] || [ ! -f "$apk" ]; then
  echo "check-so-alignment: no APK found (looked for '${1:-app/build/outputs/apk/**/*.apk}')" >&2
  exit 1
fi

# Pick an ELF reader: readelf on CI (binutils), llvm's objdump on macOS dev boxes.
reader=""
if command -v readelf >/dev/null 2>&1; then
  reader="readelf"
elif command -v objdump >/dev/null 2>&1; then
  reader="objdump"
else
  echo "check-so-alignment: need readelf or objdump on PATH" >&2
  exit 1
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
unzip -o -q "$apk" 'lib/*' -d "$workdir" || true

sofiles="$(find "$workdir/lib" -name '*.so' 2>/dev/null || true)"
if [ -z "$sofiles" ]; then
  echo "check-so-alignment: APK ships no native libraries, nothing to check"
  exit 0
fi

fail=0
while IFS= read -r so; do
  abi="$(basename "$(dirname "$so")")"
  name="$(basename "$so")"
  if [ "$reader" = "readelf" ]; then
    aligns="$(readelf -lW "$so" | awk '$1=="LOAD"{print $NF}')"
  else
    # objdump prints "align 2**N"; strip the "2**" and convert the exponent to bytes.
    aligns="$(objdump -p "$so" | awk '/LOAD/{for(i=1;i<=NF;i++) if($i=="align"){v=$(i+1); sub(/^2\*\*/,"",v); print 2^v}}')"
  fi
  for a in $aligns; do
    bytes=$((a))
    if [ "$bytes" -lt "$MIN_ALIGN" ]; then
      echo "FAIL  $abi/$name has a LOAD segment aligned to $bytes bytes (need >= $MIN_ALIGN)"
      fail=1
    fi
  done
  if [ "$fail" -eq 0 ]; then
    echo "ok    $abi/$name"
  fi
done <<< "$sofiles"

if [ "$fail" -ne 0 ]; then
  echo "check-so-alignment: one or more libraries are not 16 KB aligned; Play will reject this build" >&2
  exit 1
fi
echo "check-so-alignment: all native libraries are 16 KB aligned"
