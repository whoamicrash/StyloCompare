#!/bin/bash
set -e
cd "$(dirname "$0")"
rm -rf classes
mkdir -p classes
javac -encoding UTF-8 --release 8 -d classes $(find src -name "*.java")
jar --create --file StyloCompare.jar --main-class com.stylo.Main -C classes .
echo OK: StyloCompare.jar
