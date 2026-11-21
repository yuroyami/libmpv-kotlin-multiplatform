# Building the natives

Nobody has to do this to use the library. It is here for changing a flag, bumping a version, or checking that the published bytes come from the published scripts.

The scripts build the Android libraries, and the static mpv trees that the desktop JVM and Apple libraries link. The section [The desktop and Apple libraries](#the-desktop-and-apple-libraries) describes the trees.

## Prerequisites

- Linux or macOS for the Android libraries. Windows is not supported, WSL included.
- An arm64 Mac with Xcode for the static trees, the Linux and Windows trees included.
- Android NDK 30.0.16248370, and `ANDROID_NDK_HOME` pointing at it, for the Android libraries.
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

`release-natives.yml` builds the same four ABIs and the six trees on demand. It attaches `libmpv-kmp-natives-<abi>.zip`, `libmpv-kmp-tree-<platform>.tar.gz`, their `.sha256` files, and the mpv, FFmpeg and alsa-lib source tarballs to the GitHub release for the tag. `publish.yml` downloads the zips and the trees, verifies them, assembles the natives jars from the zips and links the desktop and Apple libraries from the trees. So the jars on Maven Central are made of the files anyone can inspect on the release page.

`.github/scripts/fetch-natives.sh <tag>` puts a release's libraries into `libmpv-kmp-native/native-libs/` on any machine, which is the quickest way to work on the Kotlin side without building anything. That works from `v0.4.0` on. Older releases ship `libmpvkt-natives-<abi>.zip` with JNI symbols for the old package, so `checkAndroidNatives` refuses them for the current tree.

## The desktop and Apple libraries

The desktop JVM library is `mpv_kmp_jni`, built from the same C++ sources as Android, with mpv linked in statically. The Apple klibs bind mpv through cinterop. Both take mpv from a static mpv tree.

### The static mpv tree

A static mpv tree is one folder per platform at `buildscripts/prefix/<platform>/`. It holds mpv and every library below it as static archives, built as LGPL ([Licensing](licensing.md)). A tree has:

- the static archives (`.a`) under `lib/`, and the headers under `include/`.
- `lib/pkgconfig/mpv.pc`. The link line is the output of `pkg-config --static --libs mpv` for that tree.
- mpv at the version that `v_mpv` in `buildscripts/include/depinfo.sh` names, and the other libraries at their pins.

### Building a tree

The trees build on an arm64 Mac with Xcode and the Homebrew packages of [Prerequisites](#prerequisites). Linux and Windows are cross builds from the same Mac.

```bash
cd buildscripts
./download-deps.sh                          # the sources, and llvm-mingw for the Windows tree
../kotlin do crossToolchains                # the Linux toolchain files, once
./buildall.sh --platform macos-arm64        # dependencies, then mpv
```

The platforms are `macos-arm64`, `ios-arm64`, `ios-simulator-arm64`, `linux-x64`, `linux-arm64` and `windows-x64`. Build one tree at a time; each takes between ten minutes and an hour the first time.

- **macOS and iOS** use Xcode's clang. The iOS trees have no Lua, because iOS has no `system()`.
- **Linux** uses the clang and the glibc sysroots of Kotlin/Native. `./kotlin do crossToolchains` downloads them when they are missing and writes `buildscripts/prefix/linux-*/toolchain.env`, which `buildall.sh` reads.
- **Linux audio** goes through a static alsa-lib. It reads the configuration from `/usr/share/alsa` and the plugins from `/usr/lib/<multiarch>/alsa-lib` at run time, the Debian and Ubuntu layout, so PulseAudio and PipeWire play through their ALSA plugins.
- **Windows** uses llvm-mingw (`tool_llvm_mingw` in `depinfo.sh`), which `download-deps.sh` fetches into `buildscripts/deps/llvm-mingw`. The Windows tree also builds zlib, because llvm-mingw has none.

`buildscripts/package-tree.sh <platform>` packs a tree into `dist/libmpv-kmp-tree-<platform>.tar.gz`, with a `BUILD-INFO.txt` and a `MANIFEST.txt` that lists every file under `lib/`. `.github/scripts/fetch-natives.sh <tag> trees` unpacks a release's six trees into `buildscripts/prefix/`, and `./kotlin do checkTrees` checks them against their manifests.

### The tasks

- `jniMacosArm64`, `jniLinuxX64`, `jniLinuxArm64` and `jniWindowsX64` link the JNI library of one platform. The output goes into the JVM jar under `libmpv-kmp-native/<platform>/`, beside a `manifest.txt`. The macOS link uses `xcrun clang++`, the Linux links the clang of Kotlin/Native, and the Windows link the llvm-mingw that built its tree.
- `mpvInteropMacosArm64`, `mpvInteropIosArm64` and `mpvInteropIosSimulatorArm64` write the cinterop definition of one Apple target.
- A platform without a tree still builds. Its JNI library is missing, and its klib has the headers only, so an app that uses that klib does not link.

The C++ compiles against the vendored headers in `libmpv-kmp-native/native/include/mpv/`, never against an installed mpv. When `v_mpv` changes, copy `client.h` and `stream_cb.h` again from `buildscripts/deps/mpv/include/mpv/`. The `mpvHeaders` check fails until the copies match.

### Dev mode

On an arm64 Mac without a tree, the macOS JNI library and the macOS klib link Homebrew's shared mpv instead. Run `brew install mpv` once. The manifest then starts with `# dev`. Homebrew's mpv can be older than the pin, and the catalog tests skip themselves when the versions differ.

### The release checks

- `./kotlin do checkTrees` fails unless all six trees are present and each matches its `MANIFEST.txt`, with no shared library under `lib/`.
- `./kotlin do checkDesktopJni` fails unless all four desktop platforms have a library linked from a tree. It also fails when a Linux or macOS library exports a symbol beside the JNI functions.
- `./kotlin do checkNativeInterop` fails unless every Apple definition binds a tree.

Dev mode and a missing tree fail these checks, on purpose.
