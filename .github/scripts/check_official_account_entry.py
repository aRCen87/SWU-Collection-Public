"""Verify that the signed candidate initializes its own backend without creating a real account."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True, stderr=subprocess.STDOUT, timeout=45)

def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/official-account.xml')
    path = Path('smoke-private')/(name+'.xml')
    adb('pull', '/sdcard/official-account.xml', path)
    return ET.parse(path).getroot()

root = snapshot('official-home')
profile = next(n for n in root.iter('node') if 'Mon profil' in n.attrib.get('content-desc', '').split('\n'))
parents = {child: parent for parent in root.iter() for child in parent}
while profile.attrib.get('clickable') != 'true' and profile in parents: profile = parents[profile]
x1,y1,x2,y2 = map(int, re.findall(r'\d+', profile.attrib['bounds']))
adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
time.sleep(3)
root = snapshot('official-account')
text = ' '.join(n.attrib.get('text', '') for n in root.iter('node'))
assert 'Les comptes ne sont pas encore disponibles' not in text
assert 'Adresse e-mail' in text and 'Créer un compte' in text, text
print('Official account entry initialized; no account created, no message or verification e-mail sent.', flush=True)
