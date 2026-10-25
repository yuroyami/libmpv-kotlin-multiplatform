# libmpv-kotlin-multiplatform, for whoever works in this tree

libmpv for Android as one Maven artifact, with the same core API on the desktop JVM, macOS and iOS. `libmpv-kmp-native/` holds the binding, `libmpv-kmp-android-natives/` packs its
native libraries, `libmpv-kmp/` holds the typed API, `buildscripts/` compiles the natives, and `build-plugins/` holds the
checks. The build is the Kotlin toolchain (`./kotlin`); only `verification/` is Gradle. `CONTRIBUTING.md` has the ground
rules and the gate.
This file has only what reading the code would not teach you.

## How work happens here

- Work on `main`. Never create a branch without asking. Commit locally, never push. The owner
  pushes and runs the release workflows.
- Commit subject is one imperative sentence about the outcome. Short prose body. No trailers.
- Every change starts with an issue, and the commit that closes one says `Fixes #n` in its body.
- Plain words with the owner. No internal codes, no jargon walls.

## Gotchas

Each line is something that bit someone. Delete a line when it stops being true.

- The Kotlin package and the JNI library name are hardcoded in C: `mpv_kmp.cpp`'s `FN` macro builds
  the `Java_io_github_yuroyami_libmpv_jni_MpvNative_` symbol prefix, and `stream_cb.cpp` looks up
  `MpvStreamProvider` and `MpvStream` by name. Renaming the Kotlin package alone loads a library
  that crashes at the first call; `checkAndroidNatives` looks for one exported symbol to catch it.
- On Android, `MpvNative` loads the native libraries in its static initialiser, so an Android host
  test cannot touch it, nor `Mpv` and `MPVLib` above it. Anything that reaches an Android core is a
  device test. The core tests in `test@core` reach a real core on the JVM and macOS instead.
- `Mpv` allows several cores per process; `MPVLib` keeps 0.1.0's rule of one. A freed handle is
  still a crash, not an exception, which is why every call goes through `Mpv`'s call gate and
  `close()` waits for the calls already inside native code.
- libmpv needs the NDK r29 `libc++_shared.so`. An older one lacks `__from_chars_floating_point`
  and crashes at load; `checkAndroidNatives` greps for the symbol. Two dependencies both shipping
  libc++ make AGP refuse the merge, which is the right outcome.
- Kotlin block comments nest, so a `/*` inside one (`native-libs/<abi>/*.so` written in a build
  file comment) opens a second comment and swallows the rest of the file. The error lands far
  from the cause, on the first `*/` in a string.
- Meson's prefix wants a flat layout: `buildall.sh` symlinks `usr` and `local` to `.` inside each
  prefix. Delete `buildscripts/prefix/<arch>` whole rather than parts of it.
- `lua.sh` and `mbedtls.sh` always clean and rebuild; that is upstream's makefile, not a bug.
- On macOS the scripts need GNU `install` and `sed` (`ginstall`, `gsed` from coreutils and
  gnu-sed); `path.sh` exports them.
- FFmpeg's configure fails on a path with `#` in it. The checkout must not live under
  `StudioProjects/#Kite`.
- `LIBMPV_KMP_ABIS=arm64-v8a` is for laptops with one architecture. The publish path never
  sets it, so a publish with a missing ABI fails, on purpose.
- `verifyExoOnlyApk` and the exoOnly exclusion list live in Synkplay, not here, but they name
  the ten files of `libmpv-kmp-android-natives`. Adding or renaming a library here means updating
  that list there.
- The `mpv-version` property answers `mpv v0.41.0`; `BuildInfo.MPV` is `0.41.0`. The device
  test compares by containment for that reason.
- `vo=gpu-next` does not work through the render API: mpv ends with no video output and no
  frames. The canvas has the `gpu` renderer only, measured on an Android 15 phone and on Android
  15 and 9 emulators.
- Through the render API mpv picks `mediacodec-copy`, not the zero-copy MediaCodec path.
- An emulator has no hardware decoder. Its software stand-in returns no frame through the render
  API, so `CanvasCapabilitiesTest` skips that run on goldfish and ranchu; it passes on an Android
  15 phone (#64).
- An emulator from API 29 on never finishes creating mpv's GL context on a `SurfaceTexture` while a
  file plays, so `aFileStartedBeforeItsSurfaceKeepsItsVideo` skips itself there. It passes on a phone
  and on the API 21 and 28 emulators (#72).
- Every canvas and view device test carries a 120 second limit, so a stall fails with the stack of
  the stuck thread instead of eating the CI job's hour (#71).
- `MpvOptions` defaults `keep-open` to Yes, which holds the last frame and never ends the file. A
  test that waits for the end has to ask for No.
- `libmpv_kmp_render.so` belongs to `libmpv-kmp-canvas` alone. `jni.sh` moves it there after
  ndk-build, and `fetch-natives.sh` does the same after unpacking a release, because the same
  file in two artifacts makes AGP refuse to merge an app that uses both. The canvas packs it into
  `libmpv-kmp-canvas-android-natives`.
- `libmpv_kmp_render.so` links `libmpv.so` at load time, so the canvas artifacts and the core
  artifacts must be the same version.
- Every render function runs on `MpvRenderer`'s own thread, and the C++ checks it: an EGL context
  belongs to one thread.
- `AHardwareBuffer_toHardwareBuffer` is in `libandroid`, not `libnativewindow`, so the render
  library links both.
- A frame is published only after `glFinish`. HWUI samples the buffer with no fence, so publishing
  earlier tears, invisibly on a fast phone and constantly on a slow one.
- Below API 29 the canvas allocates heap Bitmaps, and a failed one throws `OutOfMemoryError`, not an
  `Exception`. `MpvRenderer`'s loop catches `Throwable` so a bad size stops the renderer, not the app (#67).
- The surface handshake lives once, in `SurfaceHandshake`. A second copy of `wid`, `force-window`,
  `vo` and `android-surface-size` drifts from the first and shows as a black picture, not an error.
- The sample's screens each run in their own process, because each holds a core.
- The sample is minSdk 26 while the library is 21: its canvas screen needs `AHardwareBuffer`, and
  `activity-compose` alone would already ask for 23.
- The toolchain's `compose: enabled` also adds `org.jetbrains.compose` runtime and `components-resources`
  to the POM, which puts a content provider into every app. The Compose modules apply only the Compose
  compiler, through `compose.module-template.yaml`, and depend on androidx Compose themselves.
- `Mpv.events` has no replay: subscribe before the command that produces the event, or it is gone.
- The catalog test is the truth about names. When it fails, fix the table in the catalog, never the
  test: it reads mpv's own `property-list` and `command-list`.
- mpv reads a choice back as `Int64` when `strtol` accepts its whole name, and the empty name passes:
  `cscale`'s "same as scale" arrives as 0, not "" (#65).
- Closing a `FileChannel` does not wake a read blocked on a pipe on the API 21 emulator, so
  `DescriptorStream` waits in `poll()` beside a wake-up pipe that `cancel()` writes to (#66).
- The binding's package is `io.github.yuroyami.libmpv.jni`, not `.native`: `native` is a Java
  keyword, and AGP rejects the generated test package `...native.test` outright.
- A property named after a type it uses (`DemuxerCacheState`, `AudioParams`, `GpuApi`) shadows that
  type inside `MpvProperties`, so those entries name their codec in full.
- Every core starts with `vo=null`. A window-less `vo` at start fails when a file opens before its
  surface, and mpv drops the video track for good; the surface's attach and `MpvRenderer` set the
  real `vo` (#36).
- `mpv_render_context_free` under `vo=libmpv` drops the video track, so `MpvRenderer.close()` sets
  `vo=null` first, while its render thread still serves mpv (#42).
- `mpv_terminate_destroy` waits for every client handle of the core, so `Mpv.close()` closes the
  clients `createClient` made before it ends the core (#29).
- Releases go to Maven Central, which never replaces or deletes a version. `publishingMode: manual`
  stops each upload on the Central Portal until the owner presses Publish there.
- A source alias that names every platform of a module merges into `src/`, and its folder then cannot see
  `src/`. `jvmAndAndroid`, `core` and `appleCore` work only because each leaves a platform out. Never write an
  alias of one platform.
- Every `./kotlin test` names its platforms (`-p jvm`, `-p macosArm64`, `-p iosSimulatorArm64`). `iosArm64` has
  no test runner on a Mac, and the Android tests run in `verification/`.
- The copied `abi-check` plugin does the JVM and klib dumps only. `android-abi-check` does the Android dump, because
  `abi-check` needs JVM classes and cannot run on an Android-only module.
- Android's `jni.h` declares `AttachCurrentThread(JNIEnv **, ...)`, the desktop JDK's `(void **, ...)`. The
  JNI sources that both compile keep that one call behind `#ifdef __ANDROID__`.
- `do` is a shell keyword, so shellcheck (inside actionlint) flags `./kotlin do ...` in a workflow.
  Put `# shellcheck disable=SC1010` on the line before it.
- `scripts/run-device-tests.sh` runs on every attached device, a phone included. Pin one with
  `ANDROID_SERIAL`; the script and the tier 2 gate refuse to start otherwise.
- Everything above `MPVLib` is Phase 2 (`PLAN-2-typed-api.md`, `PLAN-3-surfaces.md`,
  `PLAN-4-canvas.md`). Do not grow `MPVLib` into a player API; it is the compatibility surface
  and the typed `Mpv` class is where new capability goes.
- Without a static tree in `buildscripts/prefix/<platform>`, the JVM and macOS builds link Homebrew's
  mpv (dev mode, `# dev` in the manifest). Homebrew has 0.40.0 while the pin is 0.41.0, so the catalog
  tests skip themselves there; they run fully only on Android or against a tree.
- The vendored headers in `libmpv-kmp-native/native/include/mpv/` move with `v_mpv`. The `mpvHeaders`
  check compares them with `buildscripts/deps/mpv` when that checkout exists.
- `Android.mk` compiles the JNI sources as C++20, the desktop link as C++17. The four shared sources
  stay within C++17, or only Android builds them.
- The Linux and macOS JNI libraries export only the JNI functions (`exports.map`, `exports.macos`).
  The Windows DLL also exports mpv's own dllexport functions, which is harmless there.
- `MpvPlaybackState.Status.Ended` needs `keep-open=yes`: `eof-reached` is true only while the file stays loaded.
  With `keep-open=no` the state falls back to Idle.
