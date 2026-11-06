#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-private
adb install -r distribution/SWU-Collection-Official.apk
adb install -r distribution/SWU-Collection-Test.apk
for package in fr.swucollection.app fr.swucollection.app.test; do
  adb shell pm path "$package"
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
  adb logcat -c
  for launch in 1 2; do
    adb shell am force-stop "$package"
    adb shell am start -W -n "$package/fr.swucollection.app.MainActivity" > "smoke-private/$package-launch-$launch.txt"
    sleep 8
    adb shell pidof "$package"
  done
  adb shell uiautomator dump /sdcard/private-preview.xml
  adb pull /sdcard/private-preview.xml "smoke-private/$package.xml"
  python3 - "$package" <<'PYUI'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke-private/'+sys.argv[1]+'.xml').iter('node'))
texts={n.attrib.get('text','') for n in nodes}
assert {'SCANNER','EXTENSIONS'} <= texts,texts
assert any(n.attrib.get('package')==sys.argv[1] for n in nodes)
PYUI
  adb logcat -d -s AndroidRuntime:E > "smoke-private/$package-crashes.txt"
  if grep -q 'FATAL EXCEPTION' "smoke-private/$package-crashes.txt"; then exit 1; fi
done
# Both identities still exist after launching each application.
adb shell pm path fr.swucollection.app
adb shell pm path fr.swucollection.app.test
