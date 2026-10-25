# Changelog

All notable changes to libmpv-kotlin-multiplatform (named libmpvKt up to 0.3.0) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The library is pre-1.0: a minor version may change the public API, and this file says so when it happens.

## [Unreleased]

- The library is renamed to libmpv-kotlin-multiplatform. Update the dependency lines, the imports, and any list of library files:

  | Before | After |
  |---|---|
  | repository `yuroyami/libmpvKt` | `yuroyami/libmpv-kotlin-multiplatform` |
  | package `io.github.yuroyami.libmpvkt` | `io.github.yuroyami.libmpv` |
  | artifacts `libmpvkt`, `libmpvkt-native`, `libmpvkt-view`, `libmpvkt-compose`, `libmpvkt-canvas` | `libmpv-kmp`, `libmpv-kmp-native`, `libmpv-kmp-view`, `libmpv-kmp-compose`, `libmpv-kmp-canvas` |
  | `libmpvkt_jni.so`, `libmpvkt_render.so` | `libmpv_kmp_jni.so`, `libmpv_kmp_render.so` |
  | `InternalLibmpvKtApi`, `BuildInfo.LIBMPVKT` | `InternalLibmpvKmpApi`, `BuildInfo.LIBMPV_KMP` |
  | logcat tag `libmpvKt` | `libmpv-kmp` |

  The group stays `io.github.yuroyami`. The other eight native libraries keep their names.
- Releases move to Maven Central. Add `mavenCentral()` and drop the `https://yuroyami.github.io/maven` repository. Versions 0.3.0 and older stay in that repository, under the old names.
- Two new artifacts come in by themselves: `libmpv-kmp-android-natives` holds the ten native libraries of `libmpv-kmp-native`, and `libmpv-kmp-canvas-android-natives` holds `libmpv_kmp_render.so`. They are plain jars with the libraries under `lib/<abi>/` and the keep rules under `META-INF/proguard/`.
- The AARs keep their classes in `libs/<artifact>-jvm.jar` and have no `aar-metadata.properties`. Gradle therefore no longer checks that the app's compile SDK is 35 or newer. The floor still applies, so check it yourself.
- The project builds with the Kotlin toolchain (the `./kotlin` script) instead of Gradle, on Kotlin 2.4.21. The device tests run in a separate Gradle build under `verification/`.
- Each module commits a dump of its Android API under `api/android/`, and a check fails when the public API changes without a new dump.
- `MpvNative.initAndroid`, `MpvNative.surfaceHandle` and `MpvNative.releaseSurfaceHandle` move to the new object `MpvAndroidNative` in the same package. `MpvNative` keeps every other function, and `libmpv-kmp-native` now also builds for the desktop JVM.
- `Mpv` moves to common code, and `libmpv-kmp` now also builds for the desktop JVM. `Mpv.create()` is new and takes no context. On Android, create the first core with `Mpv.create(context)`; until then, `Mpv.create()` throws an `IllegalStateException` that says so.
- These members of `Mpv` become extensions in the same package. Kotlin code in that package compiles as before. Code in another package adds one import per name, for example `import io.github.yuroyami.libmpv.create`:
  - `Mpv.create(context)`, `attachSurface`, `detachSurface` and `attachedSurface` (Android only).
  - `addStreamProtocol` (Android and the JVM).
- `screenshot` and `thumbnail` stay Android extensions, but their JVM class changes from `MpvExtrasKt` to `MpvBitmapsKt`. Java callers update the class name.
- `MpvNative.create` sets the C numeric locale before every `mpv_create`, not only once at `initAndroid`. mpv returns no core under any other numeric locale, and an app can change the locale at any time.

## [0.3.0] - 2026-09-26

- `MpvView.prepare` starts a core without touching the view, so it can run off the main thread, and `MpvView.attach` then gives that core to the view on the main thread. `mpv_initialize` loads the configuration and starts the core's threads, which on a slow device froze the picture and every key. `initialize` stays, as `attach(prepare(...))`. (#76)
- Every release attaches a `libmpvkt-symbols-<abi>.zip` beside each natives zip: the unstripped copies of the same libraries, for a crash reporter or `ndk-stack`. The NDK keeps the unstripped `libc++_shared.so` itself, so the zip leaves it out. (#10)
- FFmpeg and mpv now link with a build id, so a crash in them can be matched to its symbol file. The packaging refuses a library whose packaged and unstripped copies differ in build id.
- The native versions are the same as in 0.2.0.

## [0.2.0] - 2026-09-17

The typed Kotlin API. `MPVLib` still works, so a 0.1.0 app moves by bumping the version.

- `Mpv`: one class per mpv core, as many cores per process as you make. Properties through `mpv[key]`, commands through `command`, events and logs as flows, hooks, and `close` that ends the event thread with the core.
- The catalogs: 399 properties and 71 commands with the type mpv gives each one, 55 enums for the options that take a fixed set of strings, and typed values for tracks, chapters, the playlist, audio and video parameters, the demuxer cache and the rest. A device test checks every name against mpv's own `property-list` and `command-list`.
- `playback`: the twelve properties a player screen watches, reduced to one state with a status of Idle, Loading, Playing, Paused, Buffering or Ended.
- Stream providers: an app can answer for a URI scheme itself, so a `content://` file plays through `ContentResolverStreamProvider` without a path mpv can open.
- The module split: `libmpvkt-native` holds the libraries and the raw binding, `libmpvkt` the API. An app still names `io.github.yuroyami:libmpvkt` and gets both.
- `MPVLib` is deprecated and calls `Mpv` underneath. It still behaves as in 0.1.0: callbacks on the core's event thread in mpv's order, `getPropertyInt` truncating a double, `terminal-default` logs, and the `eventProperty`, `event` and `logMessage` entry points.
- Every core starts with `vo=null`. Attaching a surface, or creating an `MpvRenderer`, sets the real output, so a file started before its surface keeps its video.
- An app needs compile SDK 35 or newer and Kotlin targeting JVM 11 or newer. The build itself uses SDK 37 and JDK 21.
- Fixed since 0.1.0: text crosses the JNI boundary as standard UTF-8, so a string from mpv that is not valid UTF-8 no longer aborts a debuggable app. 0.1.0's docs said every https URL fails without a CA bundle; mpv in fact checks no certificate unless `tls-verify=yes`, and the docs now say so.
- `libmpvkt-view`: `MpvView`, an Android View that carries mpv's output, with `SurfaceType` documenting the SurfaceView or TextureView choice where you make it, and `MpvOptions`, the startup options with a type each.
- `libmpvkt-compose`: `MpvSurface`, mpv in Compose with no View in between, and `MpvPlayer` for apps that want the view inside a composition. Compose is a floor, not a pin: Gradle raises it to whatever the app uses.
- `libmpvkt-canvas`, experimental: `MpvCanvas` draws mpv through mpv's own render API into a ring of hardware buffers, so the video is an ordinary Compose image that can be rotated, blurred or captured. Zero copies from API 29; a readback below it. Both paths are proven on Android 15 and Android 9; `gpu-next` does not drive the render API and the docs say so. The speed measurement that would end the experimental label needs a phone and has not run.
- The JNI symbols moved with the binding: they are `Java_io_github_yuroyami_libmpvkt_jni_MpvNative_*` now. Only an app that looked the old ones up by hand would notice.
- The native versions are the same as in 0.1.0: mpv 0.41.0, FFmpeg 9.0.1, libass 0.17.5, libplacebo 7.360.1, dav1d 1.5.4, Mbed TLS 3.6.7, HarfBuzz 14.4.0, FreeType 2.14.3, FriBidi 1.0.16, libunibreak 7.0 and Lua 5.2.4, compiled with NDK 29.0.14206865.

## [0.1.0] - 2026-09-06

First release, served from `https://yuroyami.github.io/maven`.

```kotlin
implementation("io.github.yuroyami:libmpvkt:0.1.0")
```

- `MPVLib`, the mpv-android JNI surface, under `io.github.yuroyami.libmpvkt`: one core per process, options, properties, commands, observers, log observers, thumbnails.
- `BuildInfo`, the versions inside, readable before the core exists.
- mpv 0.41.0, FFmpeg 9.0.1 built with `--enable-gpl --enable-version3`, libass 0.17.5, libplacebo 7.360.1, dav1d 1.5.4, Mbed TLS 3.6.7, HarfBuzz 14.4.0, FreeType 2.14.3, FriBidi 1.0.16, libunibreak 7.0, Lua 5.2.4. Compiled with NDK 29.0.14206865 for API 21.
- Four ABIs in one AAR, every library aligned to 16 KB pages, `libc++_shared.so` included.
- Consumer keep rules, so a shrunk build needs no ProGuard lines of its own.
