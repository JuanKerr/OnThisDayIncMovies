#!/bin/sh
# Run this script once after cloning to download the Gradle wrapper JAR.
# Requires curl or wget.

JAR="gradle/wrapper/gradle-wrapper.jar"
URL="https://github.com/gradle/gradle/raw/v8.6.0/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$JAR" ]; then
    echo "gradle-wrapper.jar already present."
    exit 0
fi

echo "Downloading gradle-wrapper.jar..."

if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$JAR" "$URL" && echo "Done." || { echo "Download failed."; exit 1; }
elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$JAR" "$URL" && echo "Done." || { echo "Download failed."; exit 1; }
else
    echo "Error: neither curl nor wget found. Please download:"
    echo "  $URL"
    echo "and place it at: $JAR"
    exit 1
fi
