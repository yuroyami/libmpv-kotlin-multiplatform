# Licensing

Three licences apply to what this repository publishes. The repository itself is Apache-2.0. Each native build has its own licence: the Android build is GPL, and the builds for the other platforms are LGPL.

## The repository: Apache-2.0

The Kotlin sources, `BuildInfo`, the JNI sources under `libmpv-kmp-native/native/jni` and `libmpv-kmp-canvas/native`, the build scripts and the samples are licensed under the Apache License 2.0. `MPVLib.kt` and the JNI sources derive from mpv-android, which is MIT; the notice is in `NOTICE`.

## Android: GPL-3.0-or-later

The Android natives contain compiled mpv (GPL-2.0-or-later) and an FFmpeg configured with `--enable-gpl --enable-version3` (GPL-3.0-or-later), beside libass (ISC), libplacebo (LGPL-2.1-or-later), dav1d (BSD-2-Clause), Mbed TLS (Apache-2.0), HarfBuzz (MIT), FreeType (FTL), FriBidi (LGPL-2.1-or-later), libunibreak (zlib), Lua (MIT), libxml2 (MIT), fontconfig (MIT) and the NDK's libc++. The combination can only be redistributed under GPL-3.0-or-later.

For an app this means: shipping the Android natives makes the app subject to the GPL, and the app has to make its own source available under GPL-compatible terms. An LGPL build for Android is possible with the same switches as the other platforms, and is listed as future work.

## The desktop JVM, macOS and iOS: LGPL-3.0-or-later

The desktop JNI libraries (macOS, Linux and Windows) and the Apple klibs link a static mpv tree. A static tree is a folder of static libraries for one platform, built by `buildscripts/buildall.sh --platform`. Every tree is an LGPL build:

- mpv is built with `-Dgpl=false`, which leaves out its GPL-only parts.
- FFmpeg is built without `--enable-gpl`, and with `--enable-version3` because Mbed TLS needs it. That makes FFmpeg LGPL-3.0-or-later.
- The other libraries are the ones of the Android build, without the NDK's libc++. The Linux trees leave the NDK's libc++ out and add fontconfig (MIT) and alsa-lib (LGPL-2.1-or-later). The macOS and iOS trees leave fontconfig out, because CoreText is their font provider. The Windows tree adds zlib (zlib licence) and the runtime of llvm-mingw (libc++ and libunwind under Apache-2.0 with the LLVM exception, the MinGW-w64 runtime and winpthreads under permissive licences).

- The macOS and iOS klibs also carry Mozilla's list of trusted certificate authorities, as curl publishes it, under MPL-2.0.

The combination can be redistributed under LGPL-3.0-or-later. An app may keep its own code under any licence. Because the libraries are linked statically, the LGPL asks one thing more: the app must let its users relink it against a changed version of the library, for example by offering its object files.

## The source offer

Every GitHub release carries the mpv, FFmpeg and alsa-lib source tarballs for the exact tags that were built. `SOURCES.txt` names the tag of every other library, and `BUILD-INFO.txt` inside each zip and each tree names its commit. The POM of every published version declares the licences.
