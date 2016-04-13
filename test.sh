#!/bin/bash -eu

./check_config.sh

function no_device_running_create_one {
    if ! adb devices | grep -w -q 'device'; then
        create_emulator_with_no_gui
    fi
}

function create_emulator_with_no_gui {
    echo "[$0] Creating emulator..."
    echo yes | android update sdk --filter sysimg-17 --no-ui --force > /dev/null

    echo no | android create avd --force -n test -t android-17 --abi armeabi-v7a
    emulator -avd test -no-skin -no-audio -no-window &

    chmod +x ci/wait_for_emulator.sh
    ci/wait_for_emulator.sh
    adb shell input keyevent 82

    echo "[$0] Complete!"
}

function kill_running_emulator {
    echo "[$0] Killing emulator..."
    adb -s emulator-5554 emu kill
    echo "[$0] Done!"
}

# test build requires a running emulator. Create and run and emulator
echo "[$0] About to build test app"
no_device_running_create_one

echo "[$0] Building test app..."
./gradlew connectedInstrumentTest --continue
echo "[$0] Test app built."

echo "[$0] BUILD COMPLETE"
