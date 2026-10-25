# Contributing to libmpvKt

libmpvKt packages a prebuilt libmpv for Android. The Kotlin side is small; most of the work is in the build scripts and in keeping what ships true to what the docs say.

Open work lives in GitHub Issues. There is no private planning file.

## Ground rules

- **Red first.** A new check or test is seen failing before it is seen passing. The native library check was proved by removing a library and watching the build refuse it; keep that habit.
- **A claim carries the strength of its evidence.** A green host test says nothing about a device. The device tests and the sample on a real phone are the proof that the libraries load and play.
- **No em dashes and no en dashes in any file.** The gate scans for them.
- **No new dependency without asking first**: not a library, not a toolchain plugin, not a GitHub Action, not a change of an upstream pin.
- **Explicit API mode is on.** Every public declaration has KDoc. Each module commits a dump of its Android API in `api/android/`. A public API change updates the dump in the same commit: run `./kotlin build -p android`, then `./kotlin do updateAndroidAbi`. Review a change to `libmpvkt/api/android/libmpvkt.api` with care: `MPVLib` keeps the 0.1.0 signatures.
- **The pins are the truth.** `buildscripts/include/depinfo.sh` is the only place a version is written. README tables, `BuildInfo` and CHANGELOG follow it. After a pin change, `./kotlin do updateBuildInfo` rewrites `BuildInfo`.
- **When the tree contradicts an issue or a document, stop and say so.** Do not improvise the document back into truth.

## Getting a build

The repository builds with the Kotlin toolchain. The `./kotlin` script (`kotlin.bat` on Windows) downloads the toolchain and its JDK on the first run.

- An Android SDK, in `ANDROID_HOME` or in `sdk.dir` in `local.properties`.
- JDK 21 for the device tests only. They run in a separate Gradle build under `verification/`.
- For the natives: NDK 29.0.14206865 in `ANDROID_NDK_HOME`, plus the tools in `docs/building-natives.md`.
- To work on the Kotlin side without compiling anything native, download a release's libraries into place: `.github/scripts/fetch-natives.sh v0.3.0`.

The commands you need most:

```bash
./kotlin build -p android                     # every module
./kotlin test -p android                      # the host tests
./kotlin test -m build-support                # the tests of the build logic
./kotlin check androidAbi buildInfo           # the API dumps and BuildInfo
./kotlin do checkAndroidNatives               # the native libraries in place
./kotlin build -m sample --variant release    # the sample app, shrunk by R8
./kotlin show modules                         # every module and its platforms
```

`LIBMPVKT_ABIS=arm64-v8a` before `./kotlin do checkAndroidNatives` limits the check to the ABIs in the list. Use it on a machine that built one ABI. A release never sets it, so a release with a missing ABI fails.

## The gate before every commit

Two tiers, selected by the paths that changed, never by confidence.

**Tier 1, every change, a few minutes:** `scripts/check-gate.sh tier1`. The host tests, the build, the API dump and `BuildInfo` checks, the build-logic tests and the dash scan.

**Tier 2, about an hour:** `scripts/check-gate.sh tier2`. Run it for any change under `buildscripts/`, `libmpvkt-native/native/`, the canvas C++ sources or `build-plugins/`, and for a change to `project.yaml`, a `module.yaml`, a `*.module-template.yaml` or `libs.versions.toml`. Tier 2 runs tier 1, then builds the arm64 natives, checks them, builds the release sample, and runs every module's device tests.

The device tests run on every attached device. The script refuses to start with more than one device attached, unless `ANDROID_SERIAL` names the device to use.

To run the device tests alone, run `scripts/run-device-tests.sh`. It stages the libraries of this checkout, then runs the tests on the device. On an x86_64 emulator, add `LIBMPVKT_ABIS=x86_64` in front.

`./kotlin ... | tail` reports the exit code of `tail`, not of the build. Write the output to a file and read it.

## Pull requests

- One change per pull request, with a test where the change is testable.
- Say which gate tier ran.
- A commit that fixes an issue says `Fixes #n` in its body.
