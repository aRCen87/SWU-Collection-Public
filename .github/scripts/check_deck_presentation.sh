#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-private
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for format in cover unfolded landscape; do
  case "$format" in
    cover) adb shell wm size 720x1560; adb shell wm density 320 ;;
    unfolded) adb shell wm size 1766x1472; adb shell wm density 336 ;;
    landscape) adb shell wm size 1560x720; adb shell wm density 320 ;;
  esac
  adb shell am instrument -w -e class fr.swucollection.app.DeckPresentationTest fr.swucollection.app.test/androidx.test.runner.AndroidJUnitRunner | tee "smoke-private/deck-presentation-$format.txt"
  for view in overview picker; do
    adb pull "/sdcard/Android/data/fr.swucollection.app/files/home-test-deck-$view.png" "smoke-private/home-test-deck-$view-$format.png"
  done
  grep -q 'OK (3 tests)' "smoke-private/deck-presentation-$format.txt"
done
adb shell wm size reset
adb shell wm density reset
adb uninstall fr.swucollection.app.test
adb uninstall fr.swucollection.app
