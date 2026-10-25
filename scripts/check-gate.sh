#!/usr/bin/env bash
# The local commit gate. Usage: scripts/check-gate.sh tier1|tier2
#   tier1: every change, minutes. Host tests, the Android build, the ABI dump and BuildInfo checks,
#          the build-logic tests, the dash scan.
#   tier2: any change under buildscripts/, the C++ sources, build-plugins/, verification/ or a
#          module.yaml or template file, about an hour. tier1, then the arm64 natives, the packaging
#          check, the release sample, and every module's device tests on one connected device.
set -euo pipefail

TIER="${1:-}"
case "$TIER" in tier1|tier2) ;; *) echo "usage: $0 tier1|tier2" >&2; exit 2 ;; esac
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# The toolchain reads ANDROID_HOME and ignores local.properties, so the gate passes sdk.dir on.
if [ -z "${ANDROID_HOME:-}" ]; then
    sdk="${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)}"
    if [ -n "$sdk" ]; then export ANDROID_HOME="$sdk"; fi
fi
export KOTLIN_CLI_NO_WELCOME_BANNER=1

run() {
    printf 'gate:'
    printf ' %q' "$@"
    printf '\n'
    "$@"
}

scan_dashes() {
    # Em dash and en dash, in every tracked text file. git grep exits 1 on no match, which is the pass.
    if git grep -n -I -e "$(printf '\342\200\224')" -e "$(printf '\342\200\223')" -- . ; then
        echo 'An em dash or an en dash is in a tracked file.' >&2
        return 1
    fi
}

if [ "$TIER" = tier2 ]; then
    # The device tests install on every attached device, so with more than one, ANDROID_SERIAL picks
    # the one. Checked before the hour of building, not after it.
    if [ -z "${ANDROID_SERIAL:-}" ]; then
        attached=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
        count=$(printf '%s' "$attached" | grep -c . || true)
        if [ "$count" -ne 1 ]; then
            echo "tier2 needs exactly one device, or ANDROID_SERIAL set to one; attached: ${attached:-none}" >&2
            exit 1
        fi
    fi
fi

run ./kotlin test -p android
run ./kotlin build -p android
run ./kotlin check androidAbi buildInfo
run ./kotlin check abi -m libmpv-kmp-native -m libmpv-kmp
run ./kotlin test -m build-support
run scan_dashes

if [ "$TIER" = tier2 ]; then
    run buildscripts/download-deps.sh
    run buildscripts/buildall.sh --arch arm64
    run env LIBMPV_KMP_ABIS=arm64-v8a ./kotlin "do" checkAndroidNatives
    run ./kotlin build -m sample --variant release
    run scripts/run-device-tests.sh
fi

echo "gate $TIER passed"
