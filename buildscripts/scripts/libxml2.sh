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

# FFmpeg's DASH demuxer and fontconfig's config parser are the only users. Nothing that pulls in a
# library of its own: no iconv (API 21 has none), ICU, zlib, HTTP or module loading.
meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Ddocs=disabled -Dpython=disabled -Diconv=disabled -Dicu=disabled -Dzlib=disabled \
	-Dhttp=disabled -Dreadline=disabled -Dhistory=disabled -Dmodules=disabled -Dcatalog=disabled \
	-Ddebugging=disabled

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install
