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

unset CC CXX
meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Dvulkan=disabled -Ddemos=false

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install

# add missing library for static linking
# this isn't "-lstdc++" due to a meson bug: https://github.com/mesonbuild/meson/issues/11300
# Linux and Windows link GNU libstdc++, the C++ library of the Kotlin/Native sysroots.
cxxlib=-lc++
[[ "$target_os" == "linux" || "$target_os" == "windows" ]] && cxxlib=-lstdc++
${SED:-sed} "/^Libs:/ s|\$| $cxxlib|" "$prefix_dir/lib/pkgconfig/libplacebo.pc" -i
