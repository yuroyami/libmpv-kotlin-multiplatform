#!/usr/bin/env bash
# Packages the static mpv tree of one platform into dist/libmpv-kmp-tree-<platform>.tar.gz plus a .sha256.
# The tarball holds include/, lib/ (archives and pkgconfig/), BUILD-INFO.txt and MANIFEST.txt, the list of
# every file under lib/. The build files of the tree (toolchain.env, crossfile.txt) stay out: they hold
# paths of the machine that built it.
#
#   buildscripts/package-tree.sh macos-arm64
set -euo pipefail

platform="${1:?platform, e.g. macos-arm64}"
cd "$(dirname "$0")/.."
. buildscripts/include/depinfo.sh

tree="buildscripts/prefix/$platform"
[ -f "$tree/lib/pkgconfig/mpv.pc" ] || { echo "::error::$tree has no mpv; run buildscripts/buildall.sh --platform $platform first" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
out="$work/$platform"
mkdir -p "$out/lib/pkgconfig" dist
cp -R "$tree/include" "$out/"
cp "$tree"/lib/*.a "$out/lib/"
cp "$tree"/lib/pkgconfig/*.pc "$out/lib/pkgconfig/"
# The same flat layout as buildall.sh makes, so the prefix /usr/local in the .pc files resolves.
ln -s . "$out/usr"
ln -s . "$out/local"
# FFmpeg writes the absolute library folder of its dependencies (dav1d, zlib) into its .pc files. It names
# this machine only, and ${libdir} already points there.
for pc in "$out"/lib/pkgconfig/*.pc; do
  sed -i.bak "s|-L$PWD/$tree/usr/local/lib ||g" "$pc" && rm "$pc.bak"
done
if grep -rl "$PWD" "$out/lib/pkgconfig"; then
  echo "::error::a .pc file of $platform still names $PWD" >&2
  exit 1
fi

{
  echo "libmpv-kmp=$(sed -n 's/^    version:[[:space:]]*//p' library.module-template.yaml | tr -d '[:space:]')"
  echo "platform=$platform"
  echo "licence=LGPL-3.0-or-later"
  deps=(mpv ffmpeg libass libplacebo dav1d mbedtls harfbuzz freetype2 fribidi unibreak lua libxml2)
  [[ "$platform" == linux-* ]] && deps+=(fontconfig alsa)
  [ "$platform" == windows-x64 ] && deps+=(zlib)
  for dep in "${deps[@]}"; do
    ver_key="v_${dep/freetype2/freetype}"
    commit="$(cat "buildscripts/deps/$dep/.libmpv-kmp-commit" 2>/dev/null || echo unknown)"
    echo "$dep=${!ver_key} $commit"
  done
  # alsa-lib reads its configuration and plugins from these system folders at run time.
  if [[ "$platform" == linux-* ]]; then
    triple=$([ "$platform" == linux-x64 ] && echo x86_64-linux-gnu || echo aarch64-linux-gnu)
    echo "alsa-configdir=/usr/share/alsa"
    echo "alsa-plugindir=/usr/lib/$triple/alsa-lib"
  fi
} > "$out/BUILD-INFO.txt"
( cd "$out" && find lib -type f | LC_ALL=C sort > MANIFEST.txt )

find "$out" -exec touch -h -t 200001010000 {} +
name="libmpv-kmp-tree-$platform.tar.gz"
# Sorted entries, fixed owner and no gzip timestamp: the same tree gives the same bytes.
( cd "$work" && find "$platform" -print | LC_ALL=C sort | tar -cf - --no-recursion --uid 0 --gid 0 -T - | gzip -n ) > "dist/$name"
( cd dist && shasum -a 256 "$name" > "$name.sha256" )
ls -l "dist/$name"
