# Building the natives

Nobody has to do this to use the library. It is here for changing a flag, bumping a version, or checking that the published bytes come from the published scripts.

## Prerequisites

- Linux or macOS. Windows is not supported, WSL included.
- Android NDK 29.0.14206865, and `ANDROID_NDK_HOME` pointing at it.
- macOS: `brew install automake autoconf libtool pkg-config coreutils gnu-sed meson ninja nasm`. If libplacebo's meson step fails naming `jinja2`: `python3 -m pip install --break-system-packages jinja2`.
- Ubuntu: `sudo apt-get install autoconf automake libtool pkg-config ninja-build meson nasm python3-jinja2`.
- A checkout path without a `#` in it. FFmpeg's configure cannot handle one.

## The commands

```bash
cd buildscripts
./download-deps.sh                  # every upstream at its pinned tag, into deps/
./buildall.sh --arch arm64          # dependencies, mpv, then the JNI library, for one ABI
```

Architectures: `arm64`, `armv7l`, `x86`, `x86_64`. Each takes between ten minutes and an hour the first time, depending on the machine; a CI runner is at the slow end. The output lands in `libmpv-kmp-native/native-libs/<abi>/`, ten files per ABI. Then:

```bash
LIBMPV_KMP_ABIS=arm64-v8a ./kotlin do checkAndroidNatives
./kotlin build -m libmpv-kmp-android-natives -m libmpv-kmp-canvas-android-natives
```

`LIBMPV_KMP_ABIS` is a comma-separated list of the ABIs to check. Without it, the check wants all four ABIs. The natives jars hold every ABI that has all of its files, and the build prints a warning for each ABI that is not complete.

To try the result in an app, publish it to the local Maven repository:

```bash
./kotlin publish mavenLocal -m libmpv-kmp-native -m libmpv-kmp-android-natives
```

Useful flags: `--clean` rebuilds a target's build directory, `-n` skips dependencies, `--deps-only` builds everything below mpv and stops, and a target name (`mpv`, `ffmpeg`, `libass`...) builds that one thing.

## Where the versions live

`buildscripts/include/depinfo.sh` is the only place. Every entry is a release tag. `download-deps.sh --refresh` re-fetches after a change. `BuildInfo` in the library is written from the same file: run `./kotlin do updateBuildInfo` after a pin change. The `buildInfo` check fails when `BuildInfo` and the pins disagree.

## What the scripts do

- `download-deps.sh` clones or downloads each source at its tag and records the commit in `.libmpv-kmp-commit`, which ends up in the release's `BUILD-INFO.txt`. A checkout counts as done only once that file exists, so an interrupted clone is fetched again.
- `buildall.sh` sets up one prefix per architecture under `buildscripts/prefix/`, writes a meson cross file, and builds each target's dependencies first. Everything below mpv is a static library; mpv and FFmpeg are shared.
- `scripts/jni.sh` runs `ndk-build` over `libmpv-kmp-native/native/jni/Android.mk`, which compiles `libmpv_kmp_jni.so` and copies the prebuilt shared libraries plus `libc++_shared.so` into `libmpv-kmp-native/native-libs/<abi>/`.
- Every shared library is linked with `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`, and `checkAndroidNatives` reads the ELF headers back to prove it.

## What CI does

`ci.yml` runs one job per ABI on Ubuntu. The prefix (everything below mpv) is cached under a key made of the pins, so a push that touches only Kotlin rebuilds mpv and the JNI library in a few minutes and nothing else. A pin change rebuilds the chain, about half an hour per ABI, in parallel.

`release-natives.yml` builds the same four ABIs on demand and attaches `libmpv-kmp-natives-<abi>.zip`, its `.sha256`, and the mpv and FFmpeg source tarballs to the GitHub release for the tag. `publish.yml` downloads those zips, verifies them, and assembles the natives jars from them. So the jars on Maven Central are made of the files anyone can inspect on the release page.

`.github/scripts/fetch-natives.sh <tag>` puts a release's libraries into `libmpv-kmp-native/native-libs/` on any machine, which is the quickest way to work on the Kotlin side without building anything. That works from `v0.4.0` on. Older releases ship `libmpvkt-natives-<abi>.zip` with JNI symbols for the old package, so `checkAndroidNatives` refuses them for the current tree.
