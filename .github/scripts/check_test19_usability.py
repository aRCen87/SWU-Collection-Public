"""Check common collection actions and search/filter recovery on an empty Test account."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'fr.swucollection.app.test'
OUT = Path('smoke-private')

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True, stderr=subprocess.STDOUT, timeout=45)

def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/usability.xml')
    path = OUT / ('usability-' + name + '.xml')
    adb('pull', '/sdcard/usability.xml', path)
    return ET.parse(path).getroot()

def find(root, label):
    nodes = [n for n in root.iter('node') if n.attrib.get('text') == label or
             label in n.attrib.get('content-desc', '').split('\n')]
    assert nodes, (label, [n.attrib.get('text') for n in root.iter('node') if n.attrib.get('text')])
    return nodes[-1]

def rect(node):
    return list(map(int, re.findall(r'\d+', node.attrib['bounds'])))

def tap(root, label):
    x1, y1, x2, y2 = rect(find(root, label))
    assert x2 > x1 and y2 > y1, label
    adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
    time.sleep(2)

def capture(name):
    adb('shell', 'screencap', '-p', '/sdcard/usability.png')
    adb('pull', '/sdcard/usability.png', OUT / ('home-test-usability-' + name + '.png'))

original_keyboard = adb('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard').strip()
try:
    adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', 1)
    adb('shell', 'wm', 'size', '720x1560'); adb('shell', 'wm', 'density', 320)
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/fr.swucollection.app.MainActivity')
    time.sleep(8)
    tap(snapshot('home'), 'Collection')
    root = snapshot('collection')
    tap(root, 'Rechercher dans ma collection')
    adb('shell', 'input', 'text', 'R2')
    adb('shell', 'input', 'keyevent', 4); time.sleep(2)
    root = snapshot('search'); tap(root, 'Effacer la recherche')
    root = snapshot('cleared')
    field = next(n for n in root.iter('node') if n.attrib.get('class') == 'android.widget.EditText')
    assert 'R2' not in field.attrib.get('text', '')
    tap(root, 'Filtres')
    root = snapshot('filters'); tap(root, 'Doublons')
    tap(snapshot('doubles'), 'Voir les cartes')
    root = snapshot('active-filter'); find(root, 'Filtres (1)')
    tap(root, 'Filtres (1)')
    tap(snapshot('reset-dialog'), 'Tout réinitialiser')
    tap(snapshot('reset'), 'Voir les cartes')
    for name, size, density in [('cover', '720x1560', 320), ('unfolded', '1766x1472', 336), ('landscape', '1680x756', 294)]:
        adb('shell', 'wm', 'size', size); adb('shell', 'wm', 'density', density); time.sleep(4)
        root = snapshot(name)
        window = next(n for n in root.iter('node') if n.attrib.get('resource-id') == 'swu_content_window')
        left, top, right, bottom = rect(window)
        for label in ('Scanner', 'Filtres', 'Ajout manuel', 'Gérer ma collection'):
            x1, y1, x2, y2 = rect(find(root, label))
            assert left <= x1 < x2 <= right and top <= y1 < y2 <= bottom, (name, label, (x1,y1,x2,y2))
        capture(name)
    assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')
    print('Test 19 usability passed: clear search, active/reset filters, visible actions on cover/unfolded/landscape.')
finally:
    if original_keyboard == 'null': adb('shell', 'settings', 'delete', 'secure', 'show_ime_with_hard_keyboard')
    else: adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', original_keyboard)
    adb('shell', 'wm', 'size', 'reset'); adb('shell', 'wm', 'density', 'reset')
