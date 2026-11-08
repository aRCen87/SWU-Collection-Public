#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-private
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class fr.swucollection.app.MessengerKeyboardTest fr.swucollection.app.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-private/messenger-instrumentation.txt
grep -q 'OK (1 test)' smoke-private/messenger-instrumentation.txt
adb pull /sdcard/Android/data/fr.swucollection.app/files/home-test-messenger-keyboard.png smoke-private/home-test-messenger-keyboard.png
adb uninstall fr.swucollection.app.test
adb uninstall fr.swucollection.app
