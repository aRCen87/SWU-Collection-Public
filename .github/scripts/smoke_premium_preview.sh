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
 avatar=rect('Mon profil');settings=rect('Paramètres');mail=rect('Courrier, 0 en attente');counter=rect('Compteur de points de vie')
 assert avatar[2] < settings[0],(avatar,settings)
 assert settings[3] <= mail[1] and mail[3] <= counter[1],(settings,mail,counter)
 assert all(r[2]-r[0]>=48 and r[3]-r[1]>=48 for r in [avatar,settings,mail,counter])
 assert 'Mon compte · Communauté · Messages' not in texts
PYUI
  adb logcat -d -s AndroidRuntime:E > "smoke-private/$package-crashes.txt"
  if grep -q 'FATAL EXCEPTION' "smoke-private/$package-crashes.txt"; then exit 1; fi
done
# Core Android, scanner, fold, counter, guidance and messenger checks passed
# in run 42457412044. Recheck the affected visual grid and the simplified
# orbital drawing path, including live and disabled animation, here.
python3 .github/scripts/check_premium_collection.py
adb shell pm path fr.swucollection.app
adb shell pm path fr.swucollection.app.test
