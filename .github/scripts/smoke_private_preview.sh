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
root=ET.parse('smoke-private/'+sys.argv[1]+'.xml').getroot()
nodes=list(root.iter('node'))
parents={child:parent for parent in root.iter() for child in parent}
texts={n.attrib.get('text','') for n in nodes}
assert 'SCANNER' in texts or (sys.argv[1]=='fr.swucollection.app.test' and any('SWU Collection' in n.attrib.get('content-desc','') for n in nodes)),texts
assert any(n.attrib.get('package')==sys.argv[1] for n in nodes)
if sys.argv[1]=='fr.swucollection.app.test':
 import re
 def rect(label):
  found=[n for n in nodes if label in n.attrib.get('content-desc','').split('\n')]
  assert found,label
  node=found[-1]
  while node.attrib.get('clickable')!='true' and node in parents: node=parents[node]
  assert node.attrib.get('clickable')=='true',label
  return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
 avatar=rect('Mon profil');menu=rect('Ouvrir le menu');mail=rect('Courrier, 0 en attente')
 assert avatar[3]<=menu[1] and menu[3]<=mail[1],(avatar,menu,mail)
 assert abs((avatar[0]+avatar[2])-(menu[0]+menu[2]))<=4 and abs((menu[0]+menu[2])-(mail[0]+mail[2]))<=4,(avatar,menu,mail)
 assert all(r[2]-r[0]>=48 and r[3]-r[1]>=48 for r in [avatar,menu,mail]),(avatar,menu,mail)
 assert 'Mon compte · Communauté · Messages' not in texts
PYUI
  adb logcat -d -s AndroidRuntime:E > "smoke-private/$package-crashes.txt"
  if grep -q 'FATAL EXCEPTION' "smoke-private/$package-crashes.txt"; then exit 1; fi
done
# The empty test account contains no private user data; keep its home capture encrypted.
adb shell am force-stop fr.swucollection.app
python3 .github/scripts/check_private_resizing.py
adb shell screencap -p /sdcard/test-home.png
adb pull /sdcard/test-home.png smoke-private/home-test.png >/dev/null
python3 .github/scripts/check_private_fold_interactions.py
python3 .github/scripts/check_match_counter.py
python3 .github/scripts/check_collection_workbench.py
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
nodes=[n for n in ET.parse('smoke-private/private-form.xml').iter('node') if n.attrib.get('text')==sys.argv[1] or n.attrib.get('content-desc')==sys.argv[1]]
assert nodes,sys.argv[1]
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].attrib['bounds']))
assert x2>x1 and y2>y1
print((x1+x2)//2,(y1+y2)//2)
PYCOORD
)
  adb shell input tap "$x" "$y"
  sleep 3
}
tap_private_label 'Mon profil'
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
# Registration may leave the shared scroll position below the home banner.
# Restart the empty session before the independent camera startup check.
adb shell am force-stop "$package"
adb shell am start -W -n "$package/fr.swucollection.app.MainActivity" >/dev/null
sleep 8
tap_private_label 'Ouvrir le menu'
tap_private_label 'Scanner'
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
assert any('0.8.19-test.14' in text for text in texts),texts
PYUPDATES
adb logcat -d -s AndroidRuntime:E > smoke-private/private-updates-crashes.txt
if grep -q 'FATAL EXCEPTION' smoke-private/private-updates-crashes.txt; then exit 1; fi
