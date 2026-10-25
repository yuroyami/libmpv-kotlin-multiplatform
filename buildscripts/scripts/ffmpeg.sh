#!/bin/bash -e
set -eo pipefail  # also when run as "bash script.sh", which ignores the flags in the shebang

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$dir_suffix
	exit 0
else
	exit 255
fi

mkdir -p _build$dir_suffix
cd _build$dir_suffix

# The size and component flags every build shares.
lean=(
	# disable unneeded parts
	--disable-{stripping,doc,programs}
	# to keep the build lean we disable some feature quite aggressively:
	# - muxers, encoders: mpv-android does not have any way to use these
	# - devices: no practical use on Android
	--disable-{muxers,encoders,devices}
	# useful to taking screenshots
	--enable-encoder=mjpeg,png
	# useful for the `dump-cache` command
	--enable-muxer=mov,matroska,mpegts
)

if [ "$target_os" == "android" ]; then
	cpu=armv7-a
	[[ "$ndk_triple" == "aarch64"* ]] && cpu=armv8-a
	[[ "$ndk_triple" == "x86_64"* ]] && cpu=generic
	[[ "$ndk_triple" == "i686"* ]] && cpu="i686 --disable-asm"

	cpuflags=
	[[ "$ndk_triple" == "arm"* ]] && cpuflags="$cpuflags -mfpu=neon -mcpu=cortex-a8"

	args=(
		--target-os=android --enable-cross-compile
		--cross-prefix=$ndk_triple- --cc=$CC --pkg-config=pkg-config --nm=llvm-nm
		--arch=${ndk_triple%%-*} --cpu=$cpu
		--extra-cflags="-I$prefix_dir/include $cpuflags"
		# A build id ties each library to its unstripped copy in the symbols zip of a release.
		--extra-ldflags="-L$prefix_dir/lib -Wl,--build-id=sha1"

		--enable-{jni,mediacodec,mbedtls,libdav1d} --disable-vulkan
		--disable-static --enable-shared --enable-{gpl,version3}
		"${lean[@]}"
	)
else
	case "$target_os" in
		macos|ios*) ffmpeg_os=darwin ;;
		linux) ffmpeg_os=linux ;;
		windows) ffmpeg_os=mingw32 ;;
	esac
	args=(
		--target-os=$ffmpeg_os --arch=${host_triple%%-*} --enable-cross-compile
		--cc="$CC" --cxx="$CXX" --ar="$AR" --nm="$NM" --ranlib="$RANLIB"
		# configure runs a few tools it builds here, so they need the host compiler.
		--host-cc=/usr/bin/clang
		--pkg-config=pkg-config --pkg-config-flags=--static
		--extra-cflags="$aim_flags -I$prefix_dir/include"
		--extra-ldflags="$link_flags -L$prefix_dir/lib"

		# Only what is asked for: a library found on this Mac by chance never reaches the tree.
		--disable-autodetect --enable-zlib
		--enable-{mbedtls,libdav1d} --disable-{vulkan,vaapi,vdpau}
		# LGPL: mbedtls is in FFmpeg's version3 list, so the result is LGPL-3.0-or-later.
		--enable-static --disable-shared --enable-version3
		"${lean[@]}"
	)
	case "$target_os" in
		macos|ios*) args+=(--enable-{videotoolbox,audiotoolbox}) ;;
		windows) args+=(--disable-pthreads --enable-w32threads) ;;
	esac
fi
../configure "${args[@]}"

make -j$cores
make DESTDIR="$prefix_dir" install
