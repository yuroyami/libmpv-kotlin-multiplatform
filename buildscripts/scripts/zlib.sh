#!/bin/bash -e
set -eo pipefail  # also when run as "bash script.sh", which ignores the flags in the shebang

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make -f win32/Makefile.gcc clean
	exit 0
else
	exit 255
fi

# The Windows tree only: every other platform takes zlib from its system or SDK. zlib builds inside its
# source tree, so the last build is cleaned first.
[ "$target_os" == "windows" ] || { echo "zlib.sh builds for the Windows tree only" >&2; exit 1; }
$0 clean

# The static library also carries zlib's version resource, so it needs llvm-mingw's resource compiler.
tools=(CC="$CC" AR="$AR" RC="$(dirname "$CC")/x86_64-w64-mingw32-windres" CFLAGS="$CFLAGS -Wall")
make -f win32/Makefile.gcc -j$cores "${tools[@]}" libz.a
make -f win32/Makefile.gcc "${tools[@]}" install DESTDIR="$prefix_dir" SHARED_MODE=0 \
	INCLUDE_PATH=/usr/local/include LIBRARY_PATH=/usr/local/lib BINARY_PATH=/usr/local/bin
