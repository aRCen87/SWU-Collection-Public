"""Exercise the account-free optimized APK on an Android emulator."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'fr.swucollection.app.playstore'
OUT = Path('smoke-playstore')
OUT.mkdir(exist_ok=True)
Path('smoke-private').mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)

def screen(name):
    for attempt in range(4):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/playstore.xml')
            xml = adb('shell', 'cat', '/sdcard/playstore.xml')
            root = ET.fromstring(xml)
            (OUT / (name + '.xml')).write_text(xml)
            for node in root.iter('node'):
                for field in ('text', 'content-desc'):
                    value = node.get(field, '')
                    assert value not in {'Mon compte', 'Messagerie', 'Messages', 'Amis', 'Membres', 'Créer un compte', 'Se connecter', 'Connexion de messagerie'}, value
            return root
        except ET.ParseError:
            time.sleep(2)
    raise RuntimeError('No Android hierarchy')

def tap(root, label):
    for node in root.iter('node'):
        if label in (node.get('text'), node.get('content-desc')):
            values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(values) == 4:
                x1, y1, x2, y2 = values
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(2)
                return
    raise AssertionError(f'Missing control: {label}')

adb('install', '-r', 'build/playstore-source/app/build/outputs/apk/release/app-release.apk')
adb('logcat', '-c')
adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/fr.swucollection.app.MainActivity')
time.sleep(10)
root = screen('home')
tap(root, 'Paramètres')
root = screen('settings')
tap(root, 'Sauvegarder et restaurer')
root = screen('local-backups')
assert any(node.get('text') == 'Sauvegarder collection, listes et decks' for node in root.iter('node'))
assert any(node.get('text') == 'Restaurer un fichier' for node in root.iter('node'))
adb('shell', 'input', 'keyevent', '4')
time.sleep(2)
adb('shell', 'input', 'keyevent', '4')
time.sleep(2)
root = screen('home-returned')
tap(root, 'Mes decks')
screen('decks')
# Reopen the optimized build: persisted local database must still open normally.
adb('shell', 'am', 'force-stop', PACKAGE)
adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/fr.swucollection.app.MainActivity')
time.sleep(5)
screen('second-start')
logs = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
(OUT / 'runtime.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs, logs[-5000:]
services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE)
assert 'RealtimeMessagingService' not in services
(OUT / 'PASS.txt').write_text('Startup twice, account-free home/settings, local backups, decks and no messaging service: PASS\n')
print('Playstore Android smoke checks passed.')
