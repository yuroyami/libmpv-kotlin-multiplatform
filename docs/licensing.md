# Licensing

Two licences apply to what this repository publishes, and they are not the same.

## The repository: Apache-2.0

The Kotlin sources, `BuildInfo`, the JNI sources under `libmpv-kmp-native/native/jni` and `libmpv-kmp-canvas/native`, the build scripts and the sample are licensed under the Apache License 2.0. `MPVLib.kt` and the JNI sources derive from mpv-android, which is MIT; the notice is in `NOTICE`.

## The AAR: GPL-3.0-or-later

The published AAR contains compiled mpv (GPL-2.0-or-later) and an FFmpeg configured with `--enable-gpl --enable-version3` (GPL-3.0-or-later), beside libass (ISC), libplacebo (LGPL-2.1-or-later), dav1d (BSD-2-Clause), Mbed TLS (Apache-2.0), HarfBuzz (MIT), FreeType (FTL), FriBidi (LGPL-2.1-or-later), libunibreak (zlib), Lua (MIT) and the NDK's libc++. The combination can only be redistributed under GPL-3.0-or-later.

For an app this means: shipping the AAR makes the app subject to the GPL, and the app has to make its own source available under GPL-compatible terms. If that is not acceptable, this artifact is not the one to use; an LGPL build of libmpv (`-Dgpl=false` in mpv, no `--enable-gpl` in FFmpeg) is possible and is listed as future work.

## The desktop JVM and Apple libraries

The desktop JNI library and the Apple klibs link mpv and its libraries statically. The same GPL-3.0-or-later applies to them, and to an app that ships them.

The App Store terms conflict with the GPL. An iOS app in the App Store needs an LGPL build of libmpv, and no such build exists yet.

## The source offer

Every GitHub release carries the mpv and FFmpeg source tarballs for the exact tags that were built, and `SOURCES.txt` names the tag and commit of every other library. The POM of every published version declares both licences.
