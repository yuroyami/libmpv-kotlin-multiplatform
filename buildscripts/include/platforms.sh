#!/bin/bash
# The static, LGPL trees of buildall.sh --platform: the desktop JVM and Apple targets. loadplatform sets
# the same names that loadarch sets for Android, and setup_platform_prefix writes the Meson cross file.

# A space-separated flag list as the items of a Meson array.
meson_array () {
	local out= flag
	for flag in $1; do out="$out'$flag', "; done
	echo "[${out%, }]"
}

loadplatform () {
	unset CC CXX CPATH LIBRARY_PATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH
	unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

	export platform=$1
	export prefix_dir="$PWD/prefix/$1"
	export dir_suffix=-$1
	export link_mode=static
	export licence=lgpl
	# Meson runs test programs only where the host can run them. An iOS binary does not run on a Mac.
	meson_needs_wrapper=false
	meson_windres= meson_dlltool=

	case "$1" in
	macos-arm64)
		export target_os=macos
		export host_triple=aarch64-apple-darwin
		# The floor of the desktop link and of Kotlin/Native's macosArm64. Without it clang takes the SDK's.
		export MACOSX_DEPLOYMENT_TARGET=12.0
		aim_flags="-arch arm64 -mmacosx-version-min=$MACOSX_DEPLOYMENT_TARGET"
		export CC=clang CXX=clang++ AR=ar RANLIB=ranlib NM=nm STRIP=strip
		meson_system=darwin meson_subsystem=macos meson_cpu_family=aarch64 meson_cpu=aarch64
		cxx_aim_flags=$aim_flags
		link_flags=$aim_flags
		# CoreText is the font provider here, not fontconfig.
		dep_libass=(freetype2 fribidi harfbuzz unibreak)
		;;
	ios-arm64|ios-simulator-arm64)
		local sdk min
		if [ "$1" == "ios-arm64" ]; then
			export target_os=ios
			sdk=iphoneos min=-mios-version-min=15.0 meson_subsystem=ios
		else
			export target_os=ios-simulator
			sdk=iphonesimulator min=-mios-simulator-version-min=15.0 meson_subsystem=ios-simulator
		fi
		export host_triple=aarch64-apple-darwin
		aim_flags="-arch arm64 -isysroot $(xcrun --sdk $sdk --show-sdk-path) $min"
		export CC=clang CXX=clang++ AR=ar RANLIB=ranlib NM=nm STRIP=strip
		meson_system=darwin meson_cpu_family=aarch64 meson_cpu=aarch64
		meson_needs_wrapper=true
		cxx_aim_flags=$aim_flags
		link_flags=$aim_flags
		# iOS has no system(), which Lua's os.execute needs.
		dep_mpv=(ffmpeg libass libplacebo)
		dep_libass=(freetype2 fribidi harfbuzz unibreak)
		;;
	linux-x64|linux-arm64)
		export target_os=linux
		# Kotlin/Native's clang and sysroot, written by ./kotlin do crossToolchains.
		if [ ! -f "$prefix_dir/toolchain.env" ]; then
			echo "No $prefix_dir/toolchain.env. Run ./kotlin do crossToolchains first." >&2
			exit 1
		fi
		. "$prefix_dir/toolchain.env"
		if [ "$1" == "linux-x64" ]; then
			export host_triple=x86_64-linux-gnu
			meson_cpu_family=x86_64 meson_cpu=x86_64
		else
			export host_triple=aarch64-linux-gnu
			meson_cpu_family=aarch64 meson_cpu=aarch64
		fi
		export CC=$CROSS_CC CXX=$CROSS_CXX AR=$CROSS_AR RANLIB=$CROSS_RANLIB NM=$CROSS_NM
		# Nothing strips a static archive, and Kotlin/Native's LLVM has no strip.
		export STRIP=/usr/bin/true
		# The archives end up inside a shared library, so every object is position independent.
		# The sysroot's glibc 2.19 makes strcspn and other string functions into macros, which break on a
		# braced argument (mpv 0.42.0's screenshot.c). __NO_STRING_INLINES turns those macros off.
		aim_flags="$CROSS_CFLAGS -fPIC -D__NO_STRING_INLINES"
		cxx_aim_flags="$CROSS_CXXFLAGS -fPIC -isystem $PWD/include/compat/libstdcxx8"
		link_flags="-target $CROSS_TRIPLE --sysroot=$CROSS_SYSROOT $CROSS_LDFLAGS"
		meson_system=linux meson_subsystem=linux
		meson_needs_wrapper=true
		dep_mpv=(ffmpeg libass lua libplacebo alsa)
		;;
	windows-x64)
		export target_os=windows
		# llvm-mingw, fetched by download-deps.sh. Its clang knows the target, links with lld and
		# brings the resource compiler (freetype, mpv) and dlltool (libplacebo) that Meson asks for.
		local mingw="$PWD/deps/llvm-mingw/bin"
		if [ ! -x "$mingw/x86_64-w64-mingw32-clang" ]; then
			echo "No llvm-mingw at $mingw. Run buildscripts/download-deps.sh first." >&2
			exit 1
		fi
		export host_triple=x86_64-w64-mingw32
		export CC=$mingw/x86_64-w64-mingw32-clang CXX=$mingw/x86_64-w64-mingw32-clang++
		export AR=$mingw/llvm-ar RANLIB=$mingw/llvm-ranlib NM=$mingw/llvm-nm
		export STRIP=/usr/bin/true
		aim_flags= cxx_aim_flags= link_flags=
		meson_system=windows meson_subsystem=windows meson_cpu_family=x86_64 meson_cpu=x86_64
		meson_needs_wrapper=true
		meson_windres=$mingw/x86_64-w64-mingw32-windres meson_dlltool=$mingw/x86_64-w64-mingw32-dlltool
		# llvm-mingw has no zlib, so the tree builds it before FFmpeg and FreeType.
		dep_ffmpeg=(zlib mbedtls dav1d libxml2)
		dep_freetype2=(zlib)
		# DirectWrite is the font provider here, not fontconfig.
		dep_libass=(freetype2 fribidi harfbuzz unibreak)
		;;
	*)
		echo "Invalid or not yet supported platform: $1" >&2
		exit 1
		;;
	esac

	export aim_flags cxx_aim_flags link_flags
	# autotools, make and FFmpeg read these. Meson reads the cross file instead.
	export CFLAGS="-O2 $aim_flags"
	export CXXFLAGS="-O2 $cxx_aim_flags"
	export LDFLAGS="$link_flags"
}

setup_platform_prefix () {
	# enforce flat structure (/usr/local -> /). The folder can exist already: crossToolchains writes
	# toolchain.env into it first.
	mkdir -p "$prefix_dir"
	[ -e "$prefix_dir/usr" ] || ln -s . "$prefix_dir/usr"
	[ -e "$prefix_dir/local" ] || ln -s . "$prefix_dir/local"

	cat >"$prefix_dir/crossfile.tmp" <<CROSSFILE
[built-in options]
buildtype = 'release'
default_library = 'static'
wrap_mode = 'nodownload'
prefix = '/usr/local'
c_args = $(meson_array "$aim_flags")
cpp_args = $(meson_array "$cxx_aim_flags")
objc_args = $(meson_array "$aim_flags")
objcpp_args = $(meson_array "$cxx_aim_flags")
c_link_args = $(meson_array "$link_flags")
cpp_link_args = $(meson_array "$link_flags")
objc_link_args = $(meson_array "$link_flags")
objcpp_link_args = $(meson_array "$link_flags")
[binaries]
c = '$CC'
cpp = '$CXX'
objc = '$CC'
objcpp = '$CXX'
ar = '$AR'
nm = '$NM'
strip = '$STRIP'
pkgconfig = 'pkg-config'
pkg-config = 'pkg-config'
${meson_windres:+windres = '$meson_windres'}
${meson_dlltool:+dlltool = '$meson_dlltool'}
[properties]
needs_exe_wrapper = $meson_needs_wrapper
[host_machine]
system = '$meson_system'
subsystem = '$meson_subsystem'
cpu_family = '$meson_cpu_family'
cpu = '$meson_cpu'
endian = 'little'
CROSSFILE
	if cmp -s "$prefix_dir"/crossfile.{tmp,txt}; then
		rm "$prefix_dir/crossfile.tmp"
	else
		mv "$prefix_dir"/crossfile.{tmp,txt}
	fi
}
