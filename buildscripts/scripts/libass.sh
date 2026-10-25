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

mkdir -p _build$dir_suffix
cd _build$dir_suffix

../configure \
	--host=$host_triple --with-pic \
	--enable-static --disable-shared \
	--enable-libunibreak --disable-require-system-font-provider

make -j$cores
make DESTDIR="$prefix_dir" install
