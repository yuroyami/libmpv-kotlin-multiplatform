#!/usr/bin/env bash
# Writes MozillaCaBundle.kt, the CA list that macOS and iOS cores trust, from curl's copy of Mozilla's list.
# Run it before a release. curl publishes the list at https://curl.se/docs/caextract.html (MPL-2.0).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

out=libmpv-kmp/src@native/io/github/yuroyami/libmpv/MozillaCaBundle.kt
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

curl -fsSL --retry 3 -o "$work/cacert.pem" https://curl.se/ca/cacert.pem
curl -fsSL --retry 3 -o "$work/cacert.pem.sha256" https://curl.se/ca/cacert.pem.sha256
( cd "$work" && shasum -a 256 -c cacert.pem.sha256 )

# The date line of the file names the Mozilla list it was made from.
date="$(sed -n 's/^## Certificate data from Mozilla as of: //p' "$work/cacert.pem" | head -1)"
[ -n "$date" ] || { echo "no date line in cacert.pem" >&2; exit 1; }
# Only the certificate blocks: no names, so the Kotlin raw string holds no character it would read.
certs="$(awk '/-----BEGIN CERTIFICATE-----/{p=1} p{print} /-----END CERTIFICATE-----/{p=0}' "$work/cacert.pem")"
count="$(grep -c -- '-----BEGIN CERTIFICATE-----' <<<"$certs")"
[ "$count" -gt 100 ] || { echo "only $count certificates in cacert.pem" >&2; exit 1; }
if grep -q '[$"]' <<<"$certs"; then echo "cacert.pem has a character a raw string cannot hold" >&2; exit 1; fi

{
  echo "// Written by scripts/update-ca-bundle.sh from https://curl.se/ca/cacert.pem. Do not edit."
  echo "// The certificate list is Mozilla's, under MPL-2.0: https://www.mozilla.org/MPL/2.0/"
  echo "package io.github.yuroyami.libmpv"
  echo
  echo "/** Mozilla's trusted CAs as of $date, $count certificates. macOS and iOS cores verify https with this list. */"
  echo "internal object MozillaCaBundle {"
  echo "    const val DATE: String = \"$date\""
  echo
  echo "    val PEM: String = \"\"\""
  printf '%s\n' "$certs"
  echo "\"\"\""
  echo "}"
} > "$out"
echo "wrote $out: $count certificates, Mozilla list of $date"
