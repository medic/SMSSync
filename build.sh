#!/bin/bash -eu

./check_config.sh

echo "[$0] Building smssync..."
./gradlew clean assemble
echo "[$0] Smssync built."

./test.sh
