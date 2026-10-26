#!/bin/bash -e
set -eo pipefail  # also when run as "bash script.sh", which ignores the flags in the shebang

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

$0 clean # separate building not supported, always clean
if [[ "$ndk_triple" == "i686"* ]]; then
	./scripts/config.py unset MBEDTLS_AESNI_C
else
	./scripts/config.py set MBEDTLS_AESNI_C
fi

# A cross build for Windows names itself: the sample programs then link bcrypt and ws2_32.
win=()
[ "$target_os" == "windows" ] && win=(WINDOWS_BUILD=1)

make -j$cores "${win[@]}" no_test
make "${win[@]}" DESTDIR="$prefix_dir" install
