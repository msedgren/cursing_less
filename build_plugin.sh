#!/bin/bash

# Exit on error
set -e

is_arm64() {
    if [ "$(uname -m)" = "aarch64" ]; then
        return 0
    else
        return 1
    fi
}

if is_arm64; then
    echo "Detected aarch64 architecture, setting specific JVM options"
    export JDK_JAVA_OPTIONS="-XX:UseSVE=0"
    export JAVA_TOOL_OPTIONS="-XX:UseSVE=0"
fi

./gradlew --info qodanaScan test check buildPlugin verifyPlugin
