#!/bin/bash -e

## Upstream versions. Every entry is a release tag; nothing tracks a branch.
## To bump: edit here, run ./download-deps.sh --refresh, run a full build, then CHANGELOG.md.
## GenerateBuildInfoTask reads the v_* lines, so the names below are part of the build contract.

v_ndk=29.0.14206865

v_mpv=0.41.0
v_ffmpeg=9.0.1
v_libass=0.17.5
v_libplacebo=7.360.1
v_dav1d=1.5.4
v_mbedtls=3.6.7
v_harfbuzz=14.4.0
v_freetype=2.14.3
v_fribidi=1.0.16
v_unibreak=7.0
v_lua=5.2.4
# FFmpeg's DASH demuxer and fontconfig's config parser.
v_libxml2=2.15.4
# Android and Linux only: libass's system font provider there.
v_fontconfig=2.18.3
# Linux trees only (buildall.sh --platform linux-*): the audio output of mpv there.
v_alsa=1.2.16.1
# Windows tree only: llvm-mingw has no zlib, which FFmpeg and mpv link.
v_zlib=1.3.2

## Build tools. Not part of BuildInfo.
# The Windows tree and its JNI library compile with llvm-mingw. Kotlin/Native's MinGW sysroot is
# from 2019 and lacks import libraries that mpv links, pathcch among them.
tool_llvm_mingw=20261020

## Dependency tree. buildall.sh builds a target's dependencies first, in this order.

dep_mbedtls=()
dep_dav1d=()
dep_libxml2=()
dep_ffmpeg=(mbedtls dav1d libxml2)
dep_freetype2=()
dep_fribidi=()
dep_harfbuzz=()
dep_unibreak=()
dep_fontconfig=(freetype2 libxml2)
# platforms.sh drops fontconfig for macOS, iOS and Windows, which have their own font provider.
dep_libass=(freetype2 fribidi harfbuzz unibreak fontconfig)
dep_lua=()
dep_alsa=()
dep_zlib=()
dep_libplacebo=()
dep_mpv=(ffmpeg libass lua libplacebo)
dep_jni=(mpv)

## The cache identity of everything below mpv. CI keys its prefix cache on this string, so a
## bump of any dependency invalidates it, and a bump of mpv alone does not.
prefix_id="ndk${v_ndk}-ffmpeg${v_ffmpeg}-libass${v_libass}-placebo${v_libplacebo}-dav1d${v_dav1d}-mbedtls${v_mbedtls}-harfbuzz${v_harfbuzz}-freetype${v_freetype}-fribidi${v_fribidi}-unibreak${v_unibreak}-lua${v_lua}-libxml2${v_libxml2}-fontconfig${v_fontconfig}"

## The cache identity of everything below mpv in a static platform tree (buildall.sh --platform), as
## prefix_id is for Android: the pins and every build tool pin, without the NDK.
tree_id="ffmpeg${v_ffmpeg}-libass${v_libass}-placebo${v_libplacebo}-dav1d${v_dav1d}-mbedtls${v_mbedtls}-harfbuzz${v_harfbuzz}-freetype${v_freetype}-fribidi${v_fribidi}-unibreak${v_unibreak}-lua${v_lua}-libxml2${v_libxml2}-fontconfig${v_fontconfig}-alsa${v_alsa}-zlib${v_zlib}-llvmmingw${tool_llvm_mingw}"
