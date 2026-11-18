#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-private
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell ime list -s | tr -d '\r' > smoke-private/messenger-input-methods.txt
input_method=$(adb shell settings get secure default_input_method | tr -d '\r')
if ! grep -Fxq -- "$input_method" smoke-private/messenger-input-methods.txt; then
  input_method=$(head -n 1 smoke-private/messenger-input-methods.txt)
fi
test -n "$input_method"
adb shell ime enable "$input_method"
adb shell ime set "$input_method"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for format in cover unfolded landscape; do
  case "$format" in
    cover) adb shell wm size 720x1560; adb shell wm density 320 ;;
    unfolded) adb shell wm size 1766x1472; adb shell wm density 336 ;;
    landscape) adb shell wm size 1560x720; adb shell wm density 320 ;;
  esac
  adb shell am instrument -w -e class fr.swucollection.app.MessengerKeyboardTest fr.swucollection.app.test/androidx.test.runner.AndroidJUnitRunner | tee "smoke-private/messenger-instrumentation-$format.txt"
  adb pull /sdcard/Android/data/fr.swucollection.app/files/home-test-messenger-keyboard.png "smoke-private/home-test-messenger-keyboard-$format.png"
  adb pull /sdcard/Android/data/fr.swucollection.app/files/home-test-messenger-metrics.txt "smoke-private/messenger-metrics-$format.txt"
  cat "smoke-private/messenger-metrics-$format.txt"
  grep -q 'OK (1 test)' "smoke-private/messenger-instrumentation-$format.txt"
done
adb shell wm size reset
adb shell wm density reset
adb uninstall fr.swucollection.app.test
adb uninstall fr.swucollection.app
