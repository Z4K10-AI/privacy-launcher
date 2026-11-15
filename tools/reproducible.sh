#!/bin/bash
# Checks that the release build is reproducible: the same source gives the
# same APK, byte for byte, wherever it is built.
#
#   tools/reproducible.sh
#
# It copies the committed project (git clone, so nothing uncommitted comes
# along) into build/reproducible/ under a new folder name, builds the
# release there and here, both from clean with Gradle's build cache off,
# and compares the two APKs.
#
# Why it matters: it lets anyone, F-Droid included, rebuild the app from
# this source and confirm that the APK on the Releases page is what the
# source says it is. A build that changed with the folder it ran in, the
# time of day or the machine's name could not be checked that way.
#
# Both builds use the same signing key, if one is set up (see
# docs/building.md); with none, both come out unsigned. Either way the two
# files should be identical. The copy is left in place for inspection.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "note: there are uncommitted changes; the copy is built from the last commit, so the two may differ for that reason alone"
fi

COPY="build/reproducible/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$COPY"
git clone -q . "$COPY/project"
echo "building the release in $COPY/project ..."
(cd "$COPY/project" && ./gradlew -q --no-build-cache --no-configuration-cache assembleRelease) > "$COPY/build.log" 2>&1 \
  || { echo "the build in the copy failed: see $COPY/build.log"; exit 2; }
echo "building the release here, from clean ..."
./gradlew -q clean > /dev/null 2>&1
./gradlew -q --no-build-cache --no-configuration-cache assembleRelease > "$COPY/build-here.log" 2>&1 \
  || { echo "the build here failed: see $COPY/build-here.log"; exit 2; }

pick() { ls "$1"/app/build/outputs/apk/release/app-release*.apk 2>/dev/null | head -1; }
HERE="$(pick .)"
THERE="$(pick "$COPY/project")"
shasum -a 256 "$HERE" "$THERE"
if cmp -s "$HERE" "$THERE"; then
  echo "REPRODUCIBLE: the two APKs are identical, byte for byte"
else
  echo "NOT REPRODUCIBLE: the files differ. Entries that differ:"
  diff <(unzip -lv "$HERE" | awk '{print $7, $8}') <(unzip -lv "$THERE" | awk '{print $7, $8}') | head -20
  exit 1
fi
