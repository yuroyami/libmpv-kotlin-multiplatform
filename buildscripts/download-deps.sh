#!/bin/bash -e
# Fetches every upstream source at the tag depinfo.sh pins, into buildscripts/deps/. --tools-only fetches
# the build toolchains alone.
# Idempotent: a finished checkout at the pinned tag is left alone, and one at another tag is
# fetched again. --refresh deletes deps/ first. Each checkout records its commit in
# .libmpv-kmp-commit, for BUILD-INFO.txt, and its tag in .libmpv-kmp-tag.
set -eo pipefail

cd "$( dirname "${BASH_SOURCE[0]}" )"
. ./include/depinfo.sh

[ "${1:-}" == "--refresh" ] && rm -rf deps
mkdir -p deps && cd deps

# A checkout counts as done only once .libmpv-kmp-commit exists and .libmpv-kmp-tag names the pin,
# so an interrupted clone, a failed submodule or an old version is fetched again.
clone () { # <dir> <url> <tag> [extra git args]
	local dir=$1 url=$2 tag=$3
	shift 3
	[ -f "$dir/.libmpv-kmp-commit" ] && [ "$(cat "$dir/.libmpv-kmp-tag" 2>/dev/null)" = "$tag" ] && return 0
	rm -rf "$dir"
	git clone --depth 1 --branch "$tag" "$@" "$url" "$dir"
	git -C "$dir" submodule update --init --recursive --depth 1 3rdparty 2>/dev/null || true
	echo "$tag" > "$dir/.libmpv-kmp-tag"
	git -C "$dir" rev-parse HEAD > "$dir/.libmpv-kmp-commit"
}

# The URL carries the version, so the recorded URL is what tells an old unpack from a current one.
fetch_tar () { # <dir> <url>
	local dir=$1 url=$2
	[ -f "$dir/.libmpv-kmp-commit" ] && [ "$(cat "$dir/.libmpv-kmp-commit")" = "$url" ] && return 0
	local unpack=-z
	[[ "$url" == *.xz ]] && unpack=-J
	rm -rf "$dir"
	mkdir "$dir"
	curl -fL --retry 3 "$url" | tar -x $unpack -C "$dir" --strip-components=1
	echo "$url" > "$dir/.libmpv-kmp-commit"
}

# Only a Mac builds the trees and links the Windows library, so only a Mac needs the Windows toolchain.
if [ "$(uname -s)" = Darwin ]; then
	fetch_tar llvm-mingw "https://github.com/mstorsjo/llvm-mingw/releases/download/$tool_llvm_mingw/llvm-mingw-$tool_llvm_mingw-ucrt-macos-universal.tar.xz"
fi
# --tools-only: the toolchains alone, for a job that links from release trees and compiles no source.
[ "${1:-}" == "--tools-only" ] && { echo "tools ready"; exit 0; }

clone mbedtls    https://github.com/Mbed-TLS/mbedtls.git             "v$v_mbedtls" --recurse-submodules --shallow-submodules
clone dav1d      https://code.videolan.org/videolan/dav1d.git         "$v_dav1d"
clone ffmpeg     https://github.com/FFmpeg/FFmpeg.git                 "n$v_ffmpeg"
clone freetype2  https://gitlab.freedesktop.org/freetype/freetype.git "VER-${v_freetype//./-}" --recurse-submodules --shallow-submodules
clone fribidi    https://github.com/fribidi/fribidi.git               "v$v_fribidi"
clone harfbuzz   https://github.com/harfbuzz/harfbuzz.git             "$v_harfbuzz"
fetch_tar unibreak "https://github.com/adah1972/libunibreak/releases/download/libunibreak_${v_unibreak//./_}/libunibreak-${v_unibreak}.tar.gz"
clone libxml2    https://gitlab.gnome.org/GNOME/libxml2.git           "v$v_libxml2"
clone fontconfig https://gitlab.freedesktop.org/fontconfig/fontconfig.git "$v_fontconfig"
clone libass     https://github.com/libass/libass.git                 "$v_libass"
fetch_tar lua    "https://www.lua.org/ftp/lua-$v_lua.tar.gz"
clone libplacebo https://code.videolan.org/videolan/libplacebo.git    "v$v_libplacebo" --recurse-submodules --shallow-submodules
clone alsa       https://github.com/alsa-project/alsa-lib.git         "v$v_alsa"
clone zlib       https://github.com/madler/zlib.git                   "v$v_zlib"
clone mpv        https://github.com/mpv-player/mpv.git                "v$v_mpv"

cd ..
echo "deps ready"
