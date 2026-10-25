#!/usr/bin/env bash
#
# Stages the artifacts of this checkout for the Gradle build under verification/device-tests.
#
# It publishes the named modules to Maven Local with one `./kotlin publish mavenLocal`. Then it copies
# only this version of those modules into build/verification-maven, and removes that version from
# Maven Local again, so a local build of a release version never stays there. The device-test build
# resolves libmpv-kotlin-multiplatform from build/verification-maven only.
#
#   scripts/stage-verification-maven.sh libmpv-kmp-native libmpv-kmp-android-natives
#   scripts/stage-verification-maven.sh --with-dependencies libmpv-kmp-canvas
#
# --with-dependencies adds every module that the named modules depend on, read from their
# module.yaml files.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
closure=false
if [ "${1:-}" = "--with-dependencies" ]; then closure=true; shift; fi
[ $# -gt 0 ] || { echo "usage: $0 [--with-dependencies] MODULE..." >&2; exit 2; }
if [ "$closure" = true ]; then
    # Every `- //libmpv-kmp<name>` line of a module.yaml, followed until no new module appears.
    # shellcheck disable=SC2046 # one module name per word
    set -- $(python3 - "$ROOT" "$@" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
todo, seen = list(sys.argv[2:]), set()
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    text = (root / module / "module.yaml").read_text()
    todo += re.findall(r"^\s*-\s*//(libmpv-kmp[a-z0-9-]*)", text, re.M)
print(" ".join(sorted(seen)))
PY
)
fi
version="$(sed -n 's/^    version:[[:space:]]*//p' "$ROOT/library.module-template.yaml" | tr -d '\r')"
[ -n "$version" ] && [[ "$version" != *$'\n'* ]] || { echo "Expected one publishing version in library.module-template.yaml" >&2; exit 2; }
group="$HOME/.m2/repository/io/github/yuroyami"
repository="$ROOT/build/verification-maven"
all_modules="$(awk '/^modules:/ { inside = 1; next } /^[^[:space:]-]/ { inside = 0 } inside && /^  - / { print $2 }' "$ROOT/project.yaml")"

# The artifact folders of this version that belong to a named module. A folder belongs to the module
# with the longest matching name: libmpv-kmp-canvas-android to libmpv-kmp-canvas, not to libmpv-kmp.
owned_folders() {
    local path folder owner candidate
    for path in "$group"/*/; do
        [ -d "$path" ] || continue
        folder="$(basename "$path")"
        owner=""
        for candidate in $all_modules; do
            if [[ "$folder" == "$candidate" || "$folder" == "$candidate"-* ]] && [[ ${#candidate} -gt ${#owner} ]]; then
                owner="$candidate"
            fi
        done
        [[ " $* " == *" $owner "* ]] || continue
        [[ -d "$group/$folder/$version" ]] || continue
        echo "$folder"
    done
}

# Refuse to touch a copy of this version that something else put into Maven Local.
existing="$(owned_folders "$@")"
if [ -n "$existing" ]; then
    echo "Maven Local already holds $version of these artifacts:" >&2
    printf '%s\n' "$existing" | sed 's/^/  /' >&2
    echo "Delete those version folders under $group first. A real version must not stay in Maven Local." >&2
    exit 1
fi
cleanup() {
    local folder
    for folder in $(owned_folders "$@"); do rm -rf "${group:?}/$folder/$version"; done
}
# shellcheck disable=SC2064 # expand the module names now
trap "cleanup $*" EXIT

if grep -q 'signArtifacts: true' "$ROOT/library.module-template.yaml"; then
    # shellcheck source-path=SCRIPTDIR source=test-signing-key.sh
    . "$ROOT/scripts/test-signing-key.sh"
fi
# One publish for every named module. --non-transitive skips the local dependencies that are not named.
module_flags=()
for module in "$@"; do module_flags+=(-m "$module"); done
(cd "$ROOT" && KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin publish mavenLocal --non-transitive "${module_flags[@]}")

rm -rf "$repository"
copied=0
for folder in $(owned_folders "$@"); do
    mkdir -p "$repository/io/github/yuroyami/$folder"
    cp -R "$group/$folder/$version" "$repository/io/github/yuroyami/$folder/"
    copied=$((copied + 1))
done
[[ "$copied" -ge $# ]] || { echo "Staged only $copied artifact folders for $# modules" >&2; exit 1; }
echo "Staged $copied artifact folders of $version into $repository"
