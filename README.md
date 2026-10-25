# libmpv-kotlin-multiplatform

Prebuilt [mpv](https://mpv.io) for Android. One dependency gives you libmpv, FFmpeg, libass, dav1d and the rest, compiled for all four Android ABIs, with a Kotlin API where every property, command and event has a type. The same API also builds for the desktop JVM, macOS and iOS.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yuroyami/libmpv-kmp?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.yuroyami/libmpv-kmp)
[![CI](https://img.shields.io/github/actions/workflow/status/yuroyami/libmpv-kotlin-multiplatform/ci.yml?label=CI)](https://github.com/yuroyami/libmpv-kotlin-multiplatform/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0%20wrapper%2C%20GPL%20binaries-blue)](NOTICE)

**[Documentation](https://yuroyami.github.io/libmpv-kotlin-multiplatform/)** · [API reference](https://yuroyami.github.io/libmpv-kotlin-multiplatform/api/) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)

## What you get

- mpv 0.41.0 and FFmpeg 9.0.1, built for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`, aligned to 16 KB pages.
- Subtitles through libass, AV1 through dav1d, https through Mbed TLS, scripts through Lua 5.2, and hardware decoding through MediaCodec.
- A Kotlin API with a type for every property, command and event. Errors come back as values, and events arrive as flows.
- Three ways to show the video: an Android View that also works in XML layouts, Compose surfaces, and an experimental Compose canvas.
- `Mpv` and the typed API in common Kotlin code, for Android, the desktop JVM, macOS and iOS.

The native libraries are built in public CI from pinned upstream versions. Each GitHub release carries the exact zips that the Maven Central artifacts are made from.

## Platforms

| Platform | What carries mpv | State |
|---|---|---|
| Android 5.0 and newer | `libmpv-kmp-android-natives`: ten libraries per ABI | Ready |
| Desktop JVM: macOS arm64, Linux x64 and arm64, Windows x64 | `mpv_kmp_jni` inside the `libmpv-kmp-native` jar, with mpv linked in | The API is ready. No release carries mpv yet. |
| macOS arm64, iOS arm64, iOS simulator arm64 | the `libmpv-kmp-native` klibs, with mpv linked in | The API is ready. No release carries mpv yet. |

The static builds of mpv for the desktop and Apple platforms do not exist yet, and the release workflow refuses to publish until they do. In a checkout on an arm64 Mac, the JVM and macOS targets already run against Homebrew's mpv (`brew install mpv`). The tests run that way.

## Install

The artifacts are on Maven Central. Add the artifact that matches how you show video. Each one pulls in `libmpv-kmp` and the native libraries by itself.

With Gradle, in the app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.yuroyami:libmpv-kmp-compose:0.4.0")
}
```

With the Kotlin toolchain, in the app's `module.yaml`:

```yaml
dependencies:
  - io.github.yuroyami:libmpv-kmp-compose:0.4.0
```

In a Kotlin Multiplatform module, `libmpv-kmp` goes in the common dependencies (`commonMain`, or `dependencies:` in a toolchain module). The view, Compose and canvas artifacts are Android only, so they go in the Android dependencies (`androidMain`, or `dependencies@android:`).

Versions 0.3.0 and older are not on Maven Central. They stay in the old repository at `https://yuroyami.github.io/maven`.

| Artifact | What it gives you | Min SDK |
|---|---|---|
| `libmpv-kmp-view` | `MpvView`, an Android View, and `MpvOptions` | 21 |
| `libmpv-kmp-compose` | `MpvSurface` and `MpvPlayer`, for Compose | 21 |
| `libmpv-kmp-canvas` | `MpvCanvas`, the experimental Compose canvas | 26 |
| `libmpv-kmp` | `Mpv` on its own, for apps that handle the surface themselves | 21 |

Your app needs compile SDK 35 or newer and Kotlin targeting JVM 11 or newer. Gradle does not check the compile SDK for you. You need no ProGuard rules on Android: the artifacts bring their own.

## Show a video

### Android View

`MpvView` is a normal View. It has the `(Context, AttributeSet)` constructor, so you can also put it in an XML layout. Either way, call `initialize` from code.

```kotlin
val view = MpvView(context)
view.initialize(MpvOptions())
view.playFile("https://example.com/video.mkv")

// When the screen goes away:
view.destroy()
```

`initialize` starts the core on the main thread, and on a slow device that takes long enough to freeze the picture and every key. To keep the start off the main thread, split it:

```kotlin
val prepared = withContext(Dispatchers.IO) { view.prepare(MpvOptions()) }
view.attach(prepared) // on the main thread
```

### Compose

`MpvSurface` uses Compose's own surface composables, with no Android View in between. `rememberMpv` closes the core when it leaves the composition.

```kotlin
val mpv = rememberMpv()
MpvSurface(mpv, Modifier.fillMaxSize())
LaunchedEffect(mpv) { mpv.command(MpvCommands.loadFile(url)) }
```

If you want the View's helpers (`paused`, `seek`, `tracks`) in Compose, `MpvPlayer` wraps `MpvView`:

```kotlin
MpvPlayer(Modifier.fillMaxSize()) { view -> view.playFile(url) }
```

### Compose canvas (experimental)

`MpvCanvas` draws the video as a plain Compose image. You can rotate, blur, clip or capture it like any other drawing. It costs one extra GPU pass per frame, plus a CPU copy on API 26 to 28.

```kotlin
val mpv = rememberMpv(MpvOptions.forCanvas())   // rememberMpv comes from libmpv-kmp-compose
val renderer = rememberMpvRenderer(mpv)
LaunchedEffect(mpv) { mpv.command(MpvCommands.loadFile(url)) }
MpvCanvas(renderer, Modifier.fillMaxSize().graphicsLayer { rotationZ = 15f })
```

It is experimental because nobody has measured it on a real phone yet. [The Compose canvas](docs/compose-canvas.md) has what has been measured so far.

### Which one to pick

| You want | Use |
|---|---|
| A full-screen player with the lowest battery use | `MpvView` or `MpvSurface` as they are (`SurfaceType.Surface`) |
| Video in a scrolling list, with rounded corners, animated, or under a blur | `MpvView` or `MpvSurface` with `SurfaceType.Texture` |
| Video that Compose transforms like any image | `MpvCanvas` |

[Choosing a surface](docs/choosing-a-surface.md) compares them in detail.

## The Kotlin API

`Mpv` is one mpv core. The views drive it for you, and you can also use it directly:

```kotlin
mpv[MpvProperties.Pause] = true
val duration = mpv[MpvProperties.Duration].getOrNull()
mpv.command(MpvCommands.seek(10.0))
mpv.playback.collect { state -> showStatus(state.status, state.positionSeconds) }
```

| What you want | Call |
|---|---|
| Start and stop a core | `Mpv.create(context)` on Android, `Mpv.create()` elsewhere, then `initialize()`, `close()`, or `use { }` |
| Give mpv a window (Android) | `attachSurface(surface)`, `detachSurface()` |
| Read or write a property | `mpv[MpvProperties.Duration].getOrNull()`, `mpv[MpvProperties.Pause] = true` |
| Set an option before init | `setOption(MpvProperties.Hwdec, HwdecMode.Auto)` |
| Run a command | `command(MpvCommands.seek(10.0))`, `commandAsync(...)` |
| Watch one property | `observe(MpvProperties.TimePos).collect { }` |
| Receive events | `events.filterIsInstance<MpvEvent.EndFile>().collect { }` |
| Follow playback | `playback.collect { it.status }` |
| Receive mpv's log | `requestLogMessages(MpvLogLevel.Info)`, then `logs.collect { }` |
| Rewrite a URL before it opens | `hook("on_load") { }` |
| Feed mpv your own bytes (Android and the JVM) | `addStreamProtocol("content", ContentResolverStreamProvider(context))` |
| Ask what is inside | `BuildInfo.MPV`, `BuildInfo.FFMPEG`, `BuildInfo.ABIS` |

`Mpv.create(context)`, `attachSurface`, `detachSurface`, `attachedSurface` and `addStreamProtocol` are extension functions. Outside the package `io.github.yuroyami.libmpv`, each one needs an import, for example `import io.github.yuroyami.libmpv.create`.

The catalogs cover 399 properties and 71 commands. Anything missing from them still works by name: `mpv.getNode("some-property")`, `mpv.command(MpvCommand.of("some-command", "arg"))`. The [mpv manual](https://mpv.io/manual/stable/) documents every property, option and command. [The typed API](docs/typed-api.md) covers hooks, streams and the rest.

### Coming from mpv-android or 0.1.0

`MPVLib`, the mpv-android style API, is still here. It is deprecated and calls `Mpv` underneath, but it behaves as it did in 0.1.0. A 0.1.0 app updates by changing the version number.

## What your app has to add

- The `INTERNET` permission, to play network streams. The library declares no permissions.
- A CA bundle, to check https certificates. mpv starts with `tls-verify=no`, so https plays with no certificate check, and Mbed TLS cannot read Android's trust store. Ship a PEM bundle and pass its path with `MpvOptions(tlsCaFile = ...)`, which also turns `tls-verify` on.
- A subtitle font. This libass has no system font provider, so it draws no subtitles until it finds a font. Put a TrueType font at `<config-dir>/subfont.ttf`, and start mpv with `config=yes` and `config-dir` set to that folder.

[Getting started](docs/getting-started.md) explains each one, and the order of calls when you drive `Mpv` yourself.

## Limits

- The native libraries are GPL, so an app that ships them falls under GPL-3.0-or-later as a whole. [Licensing](docs/licensing.md) explains what that means.
- The native libraries are about 59 MB. Each ABI adds 31 to 41 MB to an APK before compression, so ship an app bundle: each phone then downloads only its own ABI.
- `libc++_shared.so` comes inside. If another dependency ships one too, the build stops with a merge error. That error protects you, because libmpv crashes at load with an older libc++. If you must choose one, `packaging { jniLibs { pickFirsts += "**/libc++_shared.so" } }` does it, and the copy that wins must come from NDK r29 or newer.
- Rotation restarts playback, unless the activity declares `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` or the core lives somewhere that outlives the activity.
- The deprecated `MPVLib` allows one core per process. `Mpv` has no such limit.
- Video shows on Android only for now. On the desktop JVM, macOS and iOS, start the core with `vo=null`: audio, properties, commands, events and the playback state work as on Android.
- Stream providers (`addStreamProtocol`) work on Android and the desktop JVM, not on macOS or iOS.
- No release carries mpv for the desktop or Apple platforms yet. See [Platforms](#platforms).

## What is inside

| Library | Version | License |
|---|---|---|
| mpv | 0.41.0 | GPL-2.0-or-later |
| FFmpeg | 9.0.1, built with `--enable-gpl --enable-version3` | GPL-3.0-or-later as configured |
| libass | 0.17.5 | ISC |
| libplacebo | 7.360.1 | LGPL-2.1-or-later |
| dav1d | 1.5.4 | BSD-2-Clause |
| Mbed TLS | 3.6.7 | Apache-2.0 |
| HarfBuzz | 14.4.0 | MIT |
| FreeType | 2.14.3 | FTL |
| FriBidi | 1.0.16 | LGPL-2.1-or-later |
| libunibreak | 7.0 | zlib |
| Lua | 5.2.4 | MIT |
| Android NDK | 29.0.14206865, API 21 | |

FFmpeg has its decoders, demuxers and MediaCodec hardware decoding. It has no encoders or muxers, except the few that mpv's screenshots and cache dumps use.

The libraries in the published artifacts are stripped. Every release page also has a `libmpv-kmp-symbols-<abi>.zip` with the unstripped copies (`libmpvkt-symbols-<abi>.zip` for 0.3.0), which carry the same build ids. Give them to your crash reporter, or read a crash with `ndk-stack -sym <unzipped>/<abi>`. The NDK keeps the unstripped `libc++_shared.so` itself.

## Building the natives yourself

You do not have to. Every release page has the native zips and the mpv and FFmpeg source code they were built from. To change a build flag or a version, follow [Building the natives](docs/building-natives.md). You need an NDK, meson, ninja and autotools, and between ten minutes and an hour per ABI.

## License

The Kotlin code, the JNI code and the build scripts are Apache-2.0. The native libraries keep their own licenses, and together they make the published artifacts GPL-3.0-or-later. `MPVLib.kt` and the JNI code come from [mpv-android](https://github.com/mpv-android/mpv-android), which is MIT. [NOTICE](NOTICE) lists everything.
