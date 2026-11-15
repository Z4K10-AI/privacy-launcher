#!/bin/sh
# Sets up the build toolchain for the helper scripts in tools/.
#
#   source tools/env.sh
#
# Then ./gradlew and adb both work.
#
# It looks for each tool in two places, in order:
#
#   1. where this project's own Mac keeps it, under ~/Library (a JDK, the
#      Android SDK and Gradle, installed in the home folder rather than
#      system-wide, so no admin password was involved);
#   2. wherever JAVA_HOME and ANDROID_HOME already point, which is how
#      most other machines, Linux ones and GitHub's included, are set up.
#
# So on the Mac it was written on nothing changes, and anywhere else your
# own settings are kept rather than replaced with folders that do not exist.

pl_pick() {
    # $1: the folder this project's Mac uses; $2: what is already set.
    if [ -d "$1" ]; then echo "$1"; else echo "$2"; fi
}

JAVA_HOME="$(pl_pick "$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" "${JAVA_HOME:-}")"
ANDROID_HOME="$(pl_pick "$HOME/Library/Android/sdk" "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}")"
# Gradle itself is optional: the scripts use the wrapper, ./gradlew.
GRADLE_HOME="$(pl_pick "$HOME/Library/Gradle/gradle-9.9.0" "${GRADLE_HOME:-}")"
export JAVA_HOME ANDROID_HOME GRADLE_HOME

# ANDROID_SDK_ROOT is the older name; some tools still read it.
export ANDROID_SDK_ROOT="$ANDROID_HOME"

PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
[ -n "$JAVA_HOME" ] && PATH="$JAVA_HOME/bin:$PATH"
[ -n "$GRADLE_HOME" ] && PATH="$GRADLE_HOME/bin:$PATH"
export PATH

[ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME" ] || echo "warning: no JDK found; set JAVA_HOME to a JDK 17" >&2
[ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ] || echo "warning: no Android SDK found; set ANDROID_HOME" >&2
unset -f pl_pick
