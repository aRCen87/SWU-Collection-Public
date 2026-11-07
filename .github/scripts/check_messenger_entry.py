"""Check the home-only messaging entry and the life icon next to search without cloud accounts."""
import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE='fr.swucollection.app.test'
OUT=Path('smoke-private')
def adb(*args): return subprocess.check_output(['adb',*map(str,args)],text=True,stderr=subprocess.STDOUT,timeout=45)
def snapshot(name):
    adb('shell','uiautomator','dump','/sdcard/messenger.xml');p=OUT/('messenger-'+name+'.xml');adb('pull','/sdcard/messenger.xml',p);return ET.parse(p).getroot()
def find(root,label):
    found=[n for n in root.iter('node') if n.attrib.get('text')==label or label in n.attrib.get('content-desc','').split('\n')]
    assert found,label
    return found[-1]
def bounds(node): return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
def tap(node):
    x1,y1,x2,y2=bounds(node);assert x2>x1 and y2>y1
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(2)
try:
    adb('shell','wm','size','720x1560');adb('shell','wm','density','320')
    adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/fr.swucollection.app.MainActivity');time.sleep(8)
    root=snapshot('home');search=bounds(find(root,'Carte, deck, extension…'));heart=bounds(find(root,'Compteur de points de vie'))
    assert heart[0]>=search[2] and abs((heart[1]+heart[3])-(search[1]+search[3]))<=12,(heart,search)
    assert not any(n.attrib.get('text')=='Compteur point de vie' for n in root.iter('node'))
    tap(find(root,'Courrier, 0 en attente'))
    root=snapshot('entry');find(root,'Messagerie');find(root,'Tes discussions et tes amis');find(root,'Me connecter')
    assert not any(n.attrib.get('text')=='Communauté' for n in root.iter('node'))
    adb('shell','wm','size','1766x1472');adb('shell','wm','density','336');time.sleep(4)
    find(snapshot('unfolded'),'Messagerie')
    adb('shell','input','keyevent','4');time.sleep(2);find(snapshot('returned-home'),'Mon profil')
    assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
    print('Messenger home entry, isolated screen, resize, back and adjacent life icon passed.')
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
