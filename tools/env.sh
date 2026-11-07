#!/bin/sh
# Sets up the build toolchain for this project.
#
#   source tools/env.sh
#
# Then `gradle assembleDebug` and `adb` both work. Nothing here is installed
# system-wide: everything lives under your home folder, so no admin password
# is involved and removing those three directories removes the toolchain.

JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
ANDROID_HOME="$HOME/Library/Android/sdk"
GRADLE_HOME="$HOME/Library/Gradle/gradle-9.9.0"
export JAVA_HOME ANDROID_HOME GRADLE_HOME

# ANDROID_SDK_ROOT is the older name; some tools still read it.
export ANDROID_SDK_ROOT="$ANDROID_HOME"

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$GRADLE_HOME/bin:$PATH"

for d in "$JAVA_HOME" "$ANDROID_HOME" "$GRADLE_HOME"; do
    [ -d "$d" ] || echo "warning: missing $d" >&2
done
