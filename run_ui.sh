#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Use JDK 21 if available on Fedora
if [ -d "/home/rence/.jdks/jbr-21.0.11" ]; then
    export JAVA_HOME="/home/rence/.jdks/jbr-21.0.11"
fi

cd "$SCRIPT_DIR/KotlinProject"
./gradlew :composeApp:run "$@"
