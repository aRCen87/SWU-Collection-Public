"""Exercise the actual first-card guide and retain its step while resizing the window."""
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
    adb('shell', 'uiautomator', 'dump', '/sdcard/guidance.xml')
    path = OUT / ('guidance-' + name + '.xml')
    adb('pull', '/sdcard/guidance.xml', path)
    return ET.parse(path).getroot()
def find(root, label):
    return next((n for n in root.iter('node') if n.attrib.get('text') == label or label in n.attrib.get('content-desc', '').split('\n')), None)
def tap(label):
    for attempt in range(7):
        root = snapshot('find')
        node = find(root, label)
        if node is not None:
            x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
            if x2>x1 and y2>y1:
                adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(2);return
        adb('shell','input','swipe',350,1250,350,450,450);time.sleep(1)
    raise AssertionError(label)
try:
    adb('shell','wm','size','720x1560');adb('shell','wm','density','320')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/fr.swucollection.app.MainActivity');time.sleep(8)
    tap('Ouvrir le menu');tap('Paramètres');tap('Revoir le guide des premiers pas')
    assert find(snapshot('one'),'1/3 · Scanner ta première carte') is not None
    tap('Suivant')
    assert find(snapshot('two'),'2/3 · Reconnaître les versions') is not None
    for name,size,dpi in [('unfolded','1766x1472',336),('landscape','1680x756',294),('cover','720x1560',320)]:
        adb('shell','wm','size',size);adb('shell','wm','density',dpi);time.sleep(4)
        root=snapshot(name)
        assert find(root,'2/3 · Reconnaître les versions') is not None
        assert find(root,'Suivant') is not None
    tap('Suivant');assert find(snapshot('three'),'3/3 · Vérifier et retrouver ta carte') is not None
    tap('Scanner ma première carte')
    assert find(snapshot('scanner'),'Terminer') is not None
    assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
    print('First-card guide passed: three steps, resize retention and direct scanner entry.')
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
