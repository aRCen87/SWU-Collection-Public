"""Inspect the new collection workbench on an empty local account, including resizing."""
import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE='fr.swucollection.app.test'
OUT=Path('smoke-private')
def adb(*args): return subprocess.check_output(['adb',*map(str,args)],text=True,stderr=subprocess.STDOUT,timeout=45)
def snapshot(name):
    adb('shell','uiautomator','dump','/sdcard/workbench.xml');p=OUT/('workbench-'+name+'.xml');adb('pull','/sdcard/workbench.xml',p);return ET.parse(p).getroot()
def find(root,label):
    found=[n for n in root.iter('node') if n.attrib.get('text')==label or label in n.attrib.get('content-desc','').split('\n')]
    assert found,(label,[n.attrib.get('text') for n in root.iter('node') if n.attrib.get('text')]);return found[-1]
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']));assert x2>x1 and y2>y1
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(2)
def capture(name):
    adb('shell','screencap','-p','/sdcard/workbench.png');adb('pull','/sdcard/workbench.png',OUT/('home-test-workbench-'+name+'.png'))
try:
    adb('shell','wm','size','720x1560');adb('shell','wm','density','320');time.sleep(3)
    adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/fr.swucollection.app.MainActivity');time.sleep(8)
    tap(find(snapshot('home'),'Collection'))
    tap(find(snapshot('collection'),'Vérifier et gérer ma collection'))
    root=snapshot('opened');find(root,'Gérer ma collection');find(root,'Sélection');find(root,'Disponibilité');find(root,'À vérifier')
    for name,w,h,d in [('cover',720,1560,320),('unfolded',1766,1472,336),('landscape',1680,756,294)]:
        adb('shell','wm','size',f'{w}x{h}');adb('shell','wm','density',d);time.sleep(4)
        root=snapshot(name);find(root,'Fermer');find(root,'Disponibilité');capture(name)
    tap(find(root,'À vérifier'));root=snapshot('review');find(root,'Aucun point à vérifier avec le catalogue actuel.');capture('review')
    tap(find(root,'Disponibilité'));root=snapshot('availability');find(root,'Fermer');capture('availability')
    tap(find(root,'Fermer'));root=snapshot('closed');find(root,'Vérifier et gérer ma collection')
    assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
    print('Collection workbench checks passed: selection, availability, cover/unfolded/landscape and close.')
except Exception:
    capture('failure');raise
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
