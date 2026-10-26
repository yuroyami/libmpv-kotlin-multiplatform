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

# A static tree needs the library only. The dav1d tool also needs a resource compiler on Windows.
tools=$([ "$link_mode" == shared ] && echo true || echo false)
meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Denable_tests=false -Denable_tools=$tools -Db_lto=$([ "$link_mode" == shared ] && echo true || echo false) -Dstack_alignment=16

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install
