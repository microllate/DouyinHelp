#!/usr/bin/env sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
#

# Resolve the location of this script
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Default JVM options
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Use the Java executable
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Stop if Java is not available
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found."
    exit 1
fi

# Locate Gradle wrapper JAR
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: Could not find $CLASSPATH"
    exit 1
fi

# Start Gradle Wrapper
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
