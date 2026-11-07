"""Exercise the real counter, live resizing and retained state on an empty emulator."""
import re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE='fr.swucollection.app.test'
OUT=Path('smoke-private')
def adb(*args): return subprocess.check_output(['adb',*map(str,args)],text=True,stderr=subprocess.STDOUT,timeout=45)
def snapshot(name):
    adb('shell','uiautomator','dump','/sdcard/counter.xml');p=OUT/('counter-'+name+'.xml');adb('pull','/sdcard/counter.xml',p);return ET.parse(p).getroot()
def find(root,label):
    found=[n for n in root.iter('node') if n.attrib.get('text')==label or label in n.attrib.get('content-desc','').split('\n')]
    assert found,(label,[n.attrib.get('text') for n in root.iter('node') if n.attrib.get('text')]);return found[-1]
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']));assert x2>x1 and y2>y1
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(2)
def lives(root): return [n.attrib.get('content-desc','') for n in root.iter('node') if 'points de vie restants sur' in n.attrib.get('content-desc','')]
def capture(name):
    adb('shell','screencap','-p','/sdcard/counter.png');adb('pull','/sdcard/counter.png',OUT/('home-test-counter-'+name+'.png'))
try:
    adb('shell','wm','size','720x1560');adb('shell','wm','density','320');time.sleep(3)
    adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/fr.swucollection.app.MainActivity');time.sleep(8)
    root=snapshot('home');tap(find(root,'Ouvrir le menu'));root=snapshot('menu');tap(find(root,'Compteur de partie'))
    root=snapshot('opened');assert len(lives(root))==2,lives(root)
    buttons=[n for n in root.iter('node') if n.attrib.get('text')=='−1 PV'];assert buttons;tap(buttons[0])
    root=snapshot('damage');assert any('29 points' in s for s in lives(root)),lives(root)
    tap(find(root,'Annuler'));root=snapshot('undo');assert all('30 points' in s for s in lives(root))
    tap(find(root,'Options de la partie'));root=snapshot('options');tap(find(root,'Design et base du joueur 1'))
    for attempt in range(8):
        root=snapshot('skins-'+str(attempt))
        skins=[n for n in root.iter('node') if n.attrib.get('text')=='Temple émeraude']
        if skins and all(v>0 for v in map(int,re.findall(r'\d+',skins[-1].attrib['bounds']))):
            tap(skins[-1]);break
        adb('shell','input','swipe','360','1120','360','650','400');time.sleep(1)
    else: raise AssertionError('Skin picker cannot be scrolled to Jedi style')
    tap(find(snapshot('chosen'),'Appliquer'))
    for name,w,h,d in [('cover',720,1560,320),('unfolded',1766,1472,336),('landscape',1680,756,294)]:
        adb('shell','wm','size',f'{w}x{h}');adb('shell','wm','density',d);time.sleep(4)
        root=snapshot(name);assert len(lives(root))==2,lives(root);assert all('30 points' in s for s in lives(root))
        find(root,'Options de la partie');capture(name)
    tap(find(root,'Fermer le compteur'));root=snapshot('closed');find(root,'Ouvrir le menu')
    adb('shell','wm','size','720x1560');adb('shell','wm','density','320');time.sleep(3)
    root=snapshot('home-return');tap(find(root,'Ouvrir le menu'));tap(find(snapshot('menu-return'),'Compteur de partie'))
    root=snapshot('resumed');assert len(lives(root))==2
    # Complete a private result report, then inspect statistics from the real UI.
    tap(find(root,'Options de la partie'))
    for attempt in range(5):
        root=snapshot('finish-option-'+str(attempt))
        options=[n for n in root.iter('node') if n.attrib.get('text')=='Enregistrer ce match et recommencer']
        if options:
            rect=list(map(int,re.findall(r'\d+',options[-1].attrib['bounds'])))
            if rect[2]>rect[0] and rect[3]>rect[1]: tap(options[-1]);break
        adb('shell','input','swipe','360','1100','360','600','400');time.sleep(1)
    else: raise AssertionError('Match report option is not reachable')
    root=snapshot('report');find(root,'Bilan de mon match');tap(find(root,'Victoire'))
    tap(find(snapshot('report-result'),'Enregistrer et recommencer'))
    root=snapshot('new-match');assert len(lives(root))==2
    tap(find(root,'Options de la partie'));tap(find(snapshot('saved-options'),'Mes matchs enregistrés'))
    root=snapshot('statistics');find(root,'Mes résultats')
    assert any('1 V' in n.attrib.get('text','') for n in root.iter('node'))
    capture('report-statistics');tap(find(root,'Fermer'))
    assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
    print('Match counter checks passed: two players, damage, undo, per-player skin, cover/unfolded/landscape and reopen.')
except Exception:
    capture('failure');raise
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
