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

# The library only. The sample programs need generated test headers, and their generator needs Python's jinja2.
make -j$cores "${win[@]}" lib
# What `make install` copies, without the programs it would build first.
mkdir -p "$prefix_dir/include" "$prefix_dir/lib"
cp -rp include/mbedtls include/psa "$prefix_dir/include/"
cp -RP library/libmbedtls.* library/libmbedx509.* library/libmbedcrypto.* "$prefix_dir/lib/"
