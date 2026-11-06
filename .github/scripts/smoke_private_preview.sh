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
assert 'SCANNER' in texts,texts
assert any(n.attrib.get('package')==sys.argv[1] for n in nodes)
PYUI
  adb logcat -d -s AndroidRuntime:E > "smoke-private/$package-crashes.txt"
  if grep -q 'FATAL EXCEPTION' "smoke-private/$package-crashes.txt"; then exit 1; fi
done
# Both identities still exist after launching each application.
adb shell pm path fr.swucollection.app
adb shell pm path fr.swucollection.app.test

# Test the new account entry and a real CameraX startup without creating any cloud user.
package=fr.swucollection.app.test
adb shell am force-stop "$package"
adb shell pm grant "$package" android.permission.CAMERA
adb shell am start -W -n "$package/fr.swucollection.app.MainActivity" >/dev/null
sleep 8
tap_private_label() {
  adb shell uiautomator dump /sdcard/private-form.xml >/dev/null
  adb pull /sdcard/private-form.xml smoke-private/private-form.xml >/dev/null
  read -r x y < <(python3 - "$1" <<'PYCOORD'
import re,sys,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse('smoke-private/private-form.xml').iter('node') if n.attrib.get('text')==sys.argv[1]]
assert nodes,sys.argv[1]
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].attrib['bounds']))
assert x2>x1 and y2>y1
print((x1+x2)//2,(y1+y2)//2)
PYCOORD
)
  adb shell input tap "$x" "$y"
  sleep 3
}
tap_private_label 'Mon compte · Communauté · Messages'
adb shell uiautomator dump /sdcard/private-account.xml >/dev/null
adb pull /sdcard/private-account.xml smoke-private/private-account.xml >/dev/null
python3 - <<'PYACCOUNT'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke-private/private-account.xml').iter('node')}
assert {'Adresse e-mail','Mot de passe','Me connecter','Créer un compte'} <= texts,texts
PYACCOUNT
tap_private_label 'Créer un compte'
# Registration is taller than the small emulator screen: reveal its submit button.
adb shell input swipe 160 480 160 260 500
sleep 2
adb shell uiautomator dump /sdcard/private-registration.xml >/dev/null
adb pull /sdcard/private-registration.xml smoke-private/private-registration.xml >/dev/null
python3 - <<'PYREGISTRATION'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke-private/private-registration.xml').iter('node')}
assert 'Créer mon compte de test' in texts,texts
PYREGISTRATION
adb shell input keyevent 4
sleep 3
tap_private_label 'SCANNER'
sleep 5
adb shell pidof "$package"
adb logcat -d -s AndroidRuntime:E > smoke-private/community-and-camera-crashes.txt
if grep -q 'FATAL EXCEPTION' smoke-private/community-and-camera-crashes.txt; then exit 1; fi

# Notification navigation opens the Test update screen, with its own installed version.
adb shell am start -W -n "$package/fr.swucollection.app.MainActivity" -a fr.swucollection.app.OPEN_UPDATES -f 0x24000000 >/dev/null
sleep 4
adb shell uiautomator dump /sdcard/private-updates.xml >/dev/null
adb pull /sdcard/private-updates.xml smoke-private/private-updates.xml >/dev/null
python3 - <<'PYUPDATES'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke-private/private-updates.xml').iter('node')}
assert 'Mise à jour de l’application' in texts,texts
assert any('0.8.19-test.3' in text for text in texts),texts
PYUPDATES
adb logcat -d -s AndroidRuntime:E > smoke-private/private-updates-crashes.txt
if grep -q 'FATAL EXCEPTION' smoke-private/private-updates-crashes.txt; then exit 1; fi
