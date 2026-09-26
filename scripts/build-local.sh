#!/bin/sh
# Convenience entry point for the toolchain installed on this Mac.
# Other machines can configure their own JDK/SDK and use ./gradlew instead.
set -eu

toolchain="${ANDROID_TOOLCHAIN_HOME:-$HOME/Library/Android/personal-agent-env}"
export JAVA_HOME="${JAVA_HOME:-$toolchain/amazon-corretto-17.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$toolchain/sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$toolchain/gradle-cache}"

if [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -x "$toolchain/gradle-8.11.1/bin/gradle" ] || [ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]; then
    echo "Android toolchain missing. See README.md, or set ANDROID_TOOLCHAIN_HOME." >&2
    exit 1
fi

cd "$(dirname "$0")/.."
if [ "$#" -eq 0 ]; then
    set -- assembleDebug lintDebug
fi
exec "$toolchain/gradle-8.11.1/bin/gradle" "$@"
