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

unset CC CXX # meson wants these unset

# libass's system font provider on Android and Linux. macOS, iOS and Windows have their own
# (CoreText, DirectWrite), so only those two build it.
if [ "$target_os" == "android" ]; then
	# Android has no fonts.conf. MpvAndroidFonts writes one at startup and names it in FONTCONFIG_FILE.
	# These folders are the fallback when that file is missing.
	paths=(-Ddefault-fonts-dirs=/system/fonts,/product/fonts -Dadditional-fonts-dirs=)
else
	# The system's own configuration and cache, so the library sees the fonts the desktop sees.
	paths=(-Dbaseconfig-dir=/etc/fonts -Dcache-dir=/var/cache/fontconfig)
fi

meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Ddoc=disabled -Dnls=disabled -Dtests=disabled -Dtools=disabled -Dcache-build=disabled \
	-Diconv=disabled -Dfontations=disabled -Dxml-backend=libxml2 \
	"${paths[@]}"

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install
