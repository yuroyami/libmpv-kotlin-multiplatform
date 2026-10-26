#!/usr/bin/env bash
# Downloads the native library zips of a libmpv-kotlin-multiplatform GitHub release into libmpv-kmp-native/native-libs/,
# verifying each against its .sha256. Default: all four ABIs. The word trees fetches the six static mpv trees
# instead, into buildscripts/prefix/<platform>/; `./kotlin do checkTrees` then checks them.
#
#   .github/scripts/fetch-natives.sh v0.1.0
#   .github/scripts/fetch-natives.sh v0.1.0 x86_64
#   .github/scripts/fetch-natives.sh v0.1.0 trees
#
# LIBMPV_KMP_NATIVES_BASE overrides where the files come from; a file:// URL works, which is how
# CI feeds its own artifacts through the same path as a release.
set -euo pipefail

tag="${1:?release tag, e.g. v0.1.0}"
shift
abis=("$@")
[ ${#abis[@]} -eq 0 ] && abis=(arm64-v8a armeabi-v7a x86 x86_64)

cd "$(dirname "$0")/../.."
base="${LIBMPV_KMP_NATIVES_BASE:-https://github.com/yuroyami/libmpv-kotlin-multiplatform/releases/download/$tag}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Downloads <asset> and its .sha256 into $work and checks one against the other.
fetch () {
  curl -fL --retry 3 -o "$work/$1" "$base/$1"
  curl -fL --retry 3 -o "$work/$1.sha256" "$base/$1.sha256"
  ( cd "$work" && shasum -a 256 -c "$1.sha256" )
}

if [ "${abis[*]}" = trees ]; then
  for platform in macos-arm64 ios-arm64 ios-simulator-arm64 linux-x64 linux-arm64 windows-x64; do
    asset="libmpv-kmp-tree-$platform.tar.gz"
    fetch "$asset"
    rm -rf "buildscripts/prefix/$platform"
    mkdir -p buildscripts/prefix
    # tar keeps the usr and local links of the flat layout.
    tar -xzf "$work/$asset" -C buildscripts/prefix
    test -f "buildscripts/prefix/$platform/MANIFEST.txt"
    echo "fetched the $platform tree"
  done
  exit 0
fi

for abi in "${abis[@]}"; do
  asset="libmpv-kmp-natives-$abi.zip"
  fetch "$asset"
  mkdir -p "$work/$abi.unpacked"
  unzip -q "$work/$asset" -d "$work/$abi.unpacked"
  rm -rf "libmpv-kmp-native/native-libs/$abi"
  mkdir -p libmpv-kmp-native/native-libs
  mv "$work/$abi.unpacked/$abi" "libmpv-kmp-native/native-libs/$abi"
  test -f "libmpv-kmp-native/native-libs/$abi/libmpv.so"
  # The render library publishes from libmpv-kmp-canvas, not from the core module.
  if [ -f "libmpv-kmp-native/native-libs/$abi/libmpv_kmp_render.so" ]; then
    mkdir -p "libmpv-kmp-canvas/native-libs/$abi"
    mv "libmpv-kmp-native/native-libs/$abi/libmpv_kmp_render.so" "libmpv-kmp-canvas/native-libs/$abi/"
  fi
  echo "fetched $abi: $(ls "libmpv-kmp-native/native-libs/$abi" | wc -l | tr -d ' ') files"
done
