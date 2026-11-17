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

[ -f configure ] || ./autogen.sh

# Android and Linux read the system fonts through fontconfig. macOS and iOS use CoreText, Windows
# DirectWrite. Each platform must find its provider, so a missing one fails the build.
case "$target_os" in
	android|linux) provider=(--enable-fontconfig --disable-coretext --disable-directwrite) ;;
	macos|ios*) provider=(--disable-fontconfig --enable-coretext --disable-directwrite) ;;
	windows) provider=(--disable-fontconfig --disable-coretext --enable-directwrite) ;;
esac

mkdir -p _build$dir_suffix
cd _build$dir_suffix

../configure \
	--host=$host_triple --with-pic \
	--enable-static --disable-shared \
	--enable-libunibreak --enable-require-system-font-provider \
	"${provider[@]}"

make -j$cores
make DESTDIR="$prefix_dir" install
