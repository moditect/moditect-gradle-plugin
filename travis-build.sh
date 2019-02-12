#!/bin/bash
set -ev
./gradlew --no-daemon -i -s --scan build
