#!/bin/bash -e
set -eo pipefail  # also when run as "bash script.sh", which ignores the flags in the shebang

. ../../include/path.sh

build=_build$dir_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf $build
	exit 0
else
	exit 255
fi

cc=$CC # kept for the macOS helper below
unset CC CXX # meson wants these unset

if [ "$target_os" == "android" ]; then
	opts=(
		--default-library shared
		-Diconv=disabled -Dlua=enabled
		-Dlibmpv=true -Dcplayer=false
		-Dmanpage-build=disabled
	)
else
	# Every optional feature is off unless named here, so a library this Mac has never reaches the tree.
	# No video output yet: no gl, vulkan or d3d11.
	opts=(
		--default-library static
		--auto-features=disabled
		-Dgpl=false -Dlibmpv=true -Dcplayer=false
		-Dzlib=enabled -Dvector=enabled
		# gl is the one feature that defaults to enabled rather than auto.
		-Dgl=disabled
	)
	case "$target_os" in
		macos) opts+=(-Dlua=enabled -Dcoreaudio=enabled) ;;
		ios*) opts+=(-Daudiounit=enabled) ;;
		linux) opts+=(-Dlua=enabled -Dalsa=enabled) ;;
		windows) opts+=(-Dlua=enabled -Dwasapi=enabled -Dwin32-threads=enabled) ;;
	esac
fi

meson setup $build --cross-file "$prefix_dir"/crossfile.txt "${opts[@]}"

ninja -C $build -j$cores
if [ "$link_mode" == "shared" ] && [ -f $build/libmpv.a ]; then
	echo >&2 "Meson fucked up, forcing rebuild."
	$0 clean
	exec $0 build
fi
# ao_coreaudio calls cfstr_from_cstr, which mpv compiles only with cocoa, and cocoa needs Swift.
# The helper is plain CoreFoundation C, so it goes into libmpv.a here instead.
if [ "$target_os" == "macos" ]; then
	$cc $CFLAGS -I$build -I. -c osdep/utils-mac.c -o $build/utils-mac.o
	$AR rs $build/libmpv.a $build/utils-mac.o
fi
DESTDIR="$prefix_dir" ninja -C $build install

# ao_audiounit uses AVAudioSession, but mpv's audiounit dependency names no AVFoundation, so a static
# link outside Xcode fails. The framework goes into mpv.pc, as libplacebo.sh adds its C++ library.
if [[ "$target_os" == ios* ]]; then
	${SED:-sed} '/^Libs:/ s|$| -framework AVFoundation|' "$prefix_dir/lib/pkgconfig/mpv.pc" -i
fi
