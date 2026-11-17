# Getting started

## The dependency

The artifacts are on Maven Central. With Gradle, in the app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.yuroyami:libmpv-kmp:0.4.0")
    // Only what the app draws with:
    implementation("io.github.yuroyami:libmpv-kmp-view:0.4.0")      // MpvView, MpvOptions
    implementation("io.github.yuroyami:libmpv-kmp-compose:0.4.0")   // MpvSurface, MpvPlayer
}
```

With the Kotlin toolchain, in the app's `module.yaml`:

```yaml
dependencies:
  - io.github.yuroyami:libmpv-kmp:0.4.0
  - io.github.yuroyami:libmpv-kmp-view:0.4.0
```

Versions 0.3.0 and older are not on Maven Central. They stay in the old repository at `https://yuroyami.github.io/maven`.

Minimum SDK 21, and 26 for `libmpv-kmp-canvas`. The app needs compile SDK 35 or newer, and Kotlin targeting JVM 11 or newer. Gradle does not check the compile SDK for you. The view, Compose and canvas artifacts are Android only. In a Kotlin Multiplatform project, declare them in `androidMain.dependencies`, or under `dependencies@android:` in a toolchain module. `libmpv-kmp` itself also builds for the desktop JVM, macOS and iOS: see the last section.

An app that plays network streams declares `<uses-permission android:name="android.permission.INTERNET" />`; the library declares no permission of its own. Rotation recreates an activity, and a recreated activity closes its core, so playback starts over. Declare `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` on the player activity, as the sample does, or keep the core somewhere that outlives the activity.

## The call order

`MpvView` and `MpvSurface` do the surface steps for you (see [Choosing a surface](choosing-a-surface.md)). This is what they do:

1. `val mpv = Mpv.create(applicationContext)`.
2. Options through `mpv.setOption(...)`, `vo=null` among them: `null` always opens, so a file loaded before the surface keeps its video. Options are read at `initialize`; after that, change behaviour through properties. `MpvOptions(...).applyTo(mpv)` sets the ones below in one call.
3. `mpv.initialize().getOrThrow()`.
4. When a `Surface` exists: `mpv.attachSurface(surface)`, which sets `wid`, `force-window=yes` and `vo=gpu`, then the size through `mpv[MpvProperties.AndroidSurfaceSize] = "${w}x$h"`.
5. `mpv.command(MpvCommands.loadFile(pathOrUrl))`. This works before the surface exists too.
6. Before the surface is destroyed: `mpv.detachSurface()`.
7. `mpv.close()`. It waits for calls still running on other threads, then for mpv to stop, which a stuck stream can make slow, so call it off the main thread.

The deprecated `MPVLib` follows the same order with its own calls; `sample/src/main/kotlin/io/github/yuroyami/libmpv/sample/MainActivity.kt` is that list as code.

## Options a phone player sets

`MpvOptions` sets all of these by default.

| Option | Value | Why |
|---|---|---|
| `vo` | `null` at start, `gpu` once a surface exists | `null` always opens. `gpu` is the OpenGL ES renderer; `gpu-next` also works and uses libplacebo's newer path. |
| `gpu-context` | `android` | Render into an Android surface. |
| `opengl-es` | `yes` | The context is GLES. |
| `hwdec` | `auto` | MediaCodec when the codec allows it, software otherwise. |
| `ao` | `audiotrack,opensles` | AudioTrack first, OpenSL ES as the fallback. |
| `force-window` | `no` until a surface exists | mpv aborts if asked for a window it cannot create. |

## Certificates and fonts

You do not supply either. Every core does both by itself, on every platform.

**https certificates.** Every new core starts with `tls-verify=yes`, so an https stream with a bad certificate does not play. Mbed TLS, mpv's TLS library here, cannot read the platform's trust store, so the core gets the trusted certificate authorities as a PEM file in `tls-ca-file`:

| Platform | The certificate authorities |
|---|---|
| Android | The app's own: its network security config, or the system store when it has none. Written to `noBackupFilesDir`. |
| Desktop JVM | The JVM's default trust store (`cacerts`). Written to a temporary file that only your user can read. |
| macOS, iOS | Mozilla's list, from curl, built into the library. `scripts/update-ca-bundle.sh` refreshes it before a release. |

To trust other authorities, set `tls-ca-file` to your own PEM file before `initialize` (`MpvOptions(tlsCaFile = ...)`). To accept any server, set `tls-verify=no` (`MpvOptions(tlsVerify = false)`). An `mpv.conf` read at `initialize` wins over both. Warning: for an https URL with an IP address instead of a host name, only the certificate chain is checked, not the name. FFmpeg gives Mbed TLS no name for an IP address, so any certificate from a trusted authority passes. Use host names for https.

**Subtitle fonts.** libass reads the system fonts: fontconfig on Android and Linux, CoreText on macOS and iOS, DirectWrite on Windows. A character that the subtitle's own font lacks comes from another system font that has it. On Android, `Mpv.create(context)` writes a `fonts.conf` that names `/system/fonts` and sets `FONTCONFIG_FILE` to it, unless your app set that variable first. The first file with subtitles builds fontconfig's cache in the app's cache folder, which takes a moment once.

## Reading state

`mpv.observe(MpvProperties.TimePos)` is a flow of every change of `time-pos`, and `mpv.events` is a flow of file loaded, end of file, seek and the rest; [The typed API](typed-api.md) shows both. `mpv.playback` reduces twelve properties to one state for a player screen. Collect the flows on whatever dispatcher suits you. `MPVLib`'s observers get their callbacks on the core's event thread, in mpv's order, as in 0.1.0.

`mpv.getString("mpv-version")` and `mpv.getString("ffmpeg-version")` say what is running. `BuildInfo` says the same before `create`.

## The desktop JVM, macOS and iOS

`libmpv-kmp` and `libmpv-kmp-native` also build for the desktop JVM and for `macosArm64`, `iosArm64` and `iosSimulatorArm64`. In a Kotlin Multiplatform project, declare `libmpv-kmp` in `commonMain.dependencies`, or under `dependencies:` in a toolchain module.

From the next release on, the JVM jar and the Apple klibs carry mpv, linked from LGPL static trees ([Licensing](licensing.md)). Releases up to 0.3.0 carry none. The README's Platforms table says which platforms CI tests.

Create a core with `Mpv.create()`, which takes no context. On Android, call `Mpv.create(context)` once first. Until then, `Mpv.create()` throws an `IllegalStateException` that says so.

These platforms have no video output yet, so set `vo=null` before `initialize`. Audio, properties, commands, events and the playback state work as on Android. `addStreamProtocol` works on the JVM, not on macOS or iOS.

```kotlin
val mpv = Mpv.create()
mpv.setOption("vo", "null").getOrThrow()
mpv.initialize().getOrThrow()
mpv.command(MpvCommands.loadFile("/path/to/music.flac")).getOrThrow()
```

`sample-desktop` is a complete program: `./kotlin run -m sample-desktop -- <file>` plays a file and prints every change of the playback state.

### Where the JVM finds the native library

The JNI library is `mpv_kmp_jni`. On the JVM, the library tries three places, in this order:

1. The file that the system property `libmpv-kmp.jni.path` names, for example `-Dlibmpv-kmp.jni.path=/opt/app/libmpv_kmp_jni.so`.
2. `System.loadLibrary("mpv_kmp_jni")`, which searches `java.library.path`. Use this when a packager ships its own copy.
3. The copy in the `libmpv-kmp-native` jar, under `libmpv-kmp-native/<platform>/`. The library extracts it to a temporary folder first.

The jar has a folder for `macos-arm64`, `linux-x64`, `linux-arm64` and `windows-x64`. On any other machine, an Intel Mac for example, the error names the platform and both overrides.

On Linux, the library needs `libatomic.so.1`, `libz.so.1` and `libstdc++.so.6` from the system; a minimal Debian or Ubuntu install may need the `libatomic1` package. Audio goes through ALSA, with the system's ALSA configuration, so PulseAudio and PipeWire play through their ALSA plugins. On Windows, the library needs only system DLLs, and the Universal C Runtime that Windows 10 and newer include.

### A shrunk desktop release build

The C++ code finds `MpvNative` and the stream types by name. Compose Desktop shrinks a release build with ProGuard by default, so keep them with these rules. They are the rules that the Android artifacts bring by themselves:

```
-keepclasseswithmembernames,includedescriptorclasses class io.github.yuroyami.libmpv.jni.MpvNative { native <methods>; }
-keep interface io.github.yuroyami.libmpv.stream.MpvStreamProvider { *; }
-keep interface io.github.yuroyami.libmpv.stream.MpvStream { *; }
-keep class * implements io.github.yuroyami.libmpv.stream.MpvStream { public <methods>; }
-keep class * implements io.github.yuroyami.libmpv.stream.MpvStreamProvider { public <methods>; }
```

### The numeric locale

mpv refuses to create a core unless the C numeric locale is `C`. A desktop JVM starts with the user's locale, which writes numbers with a decimal comma in German or French, for example. `Mpv.create()` therefore sets the numeric locale to `C` for the whole process before every core. Native code of your own that formats numbers through C sees that change.
