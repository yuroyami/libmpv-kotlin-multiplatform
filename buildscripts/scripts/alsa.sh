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

[ -f configure ] || autoreconf -fi

mkdir -p $build
cd $build

# The configuration and the plugins (the PulseAudio and PipeWire bridges among them) come from the
# system at run time. These are the Debian and Ubuntu paths. -fPIC comes from CFLAGS: configure refuses
# --with-pic for a static library, and symbol versions belong to a shared one. CPP carries the target
# flags: configure reads the symbol prefix from a bare $CC -E, which answers for the Mac ("_"), and the
# aliases such as snd_pcm_forward then point at names that do not exist.
../configure \
	CPP="$CC $CFLAGS -E" \
	--host=$host_triple \
	--enable-static --disable-shared \
	--disable-python --disable-alsatopology --disable-ucm \
	--without-versioned \
	--with-configdir=/usr/share/alsa \
	--with-plugindir=/usr/lib/$host_triple/alsa-lib

make -j$cores
make DESTDIR="$prefix_dir" install
