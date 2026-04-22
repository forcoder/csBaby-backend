#!/bin/bash

if [ -z "$JAVA_HOME" ]; then
    echo "JAVA_HOME is not set"
    exit 1
fi

GRADLE_HOME=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/gradle-8.2
export GRADLE_HOME
export PATH="$GRADLE_HOME/bin:$PATH"

exec "$GRADLE_HOME/bin/gradle" "$@"