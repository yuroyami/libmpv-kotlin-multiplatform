#!/usr/bin/env bash
#
# Runs the Android device tests of the libmpvKt libraries on one connected device or emulator.
#
# The Kotlin Toolchain has no instrumented tests. verification/device-tests is a Gradle build that
# compiles test/ and android-device-test/kotlin/ of each library against the staged artifacts of
# this checkout, then runs the test APKs on the device that adb sees.
# With more than one device connected, set ANDROID_SERIAL to the serial of the one to use. Without
# it, the script refuses to start, because Gradle would install the tests on every device.
# The natives must be in place first: buildscripts/buildall.sh, or .github/scripts/fetch-natives.sh.
#
#   scripts/run-device-tests.sh                          # stage, then run every device test set
#   scripts/run-device-tests.sh :canvas:connectedAndroidDeviceTest
#   scripts/run-device-tests.sh --no-stage TASK...       # reuse the staged artifacts
#
# Gradle arguments pass through, for example
# -Pandroid.testInstrumentationRunnerArguments.class=io.github.yuroyami.libmpvkt.MpvTest.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
stage=true
if [ "${1:-}" = "--no-stage" ]; then stage=false; shift; fi
# Without a task path, Gradle runs this task in every project that has it.
[ $# -gt 0 ] || set -- connectedAndroidDeviceTest
version="$(sed -n 's/^    version:[[:space:]]*//p' "$ROOT/library.module-template.yaml" | tr -d '\r')"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" 2>/dev/null || true)}}"
[ -n "$sdk" ] || { echo "Set ANDROID_HOME, or sdk.dir in local.properties." >&2; exit 2; }
export ANDROID_HOME="$sdk"
adb="$sdk/platform-tools/adb"
[ -x "$adb" ] || adb=adb

# Checked before the staging and the build, not after them.
if [ -z "${ANDROID_SERIAL:-}" ]; then
    attached="$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    count="$(printf '%s' "$attached" | grep -c . || true)"
    if [ "$count" -ne 1 ]; then
        echo "The device tests need exactly one device, or ANDROID_SERIAL set to one. Attached: ${attached:-none}" >&2
        exit 1
    fi
fi

if [ "$stage" = true ]; then
    "$ROOT/scripts/stage-verification-maven.sh" \
        libmpvkt-native libmpvkt-android-natives libmpvkt libmpvkt-view libmpvkt-compose \
        libmpvkt-canvas libmpvkt-canvas-android-natives
fi
[ -d "$ROOT/build/verification-maven" ] || { echo "Nothing staged. Run without --no-stage first." >&2; exit 2; }

"$ROOT/verification/gradlew" -p "$ROOT/verification/device-tests" --no-daemon --console=plain \
    "-PlibmpvktVersion=$version" "-PverificationRepository=$ROOT/build/verification-maven" "$@"
