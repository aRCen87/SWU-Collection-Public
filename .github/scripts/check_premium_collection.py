"""Exercise the real visual grid and orbital background on a disposable local emulator."""
import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops

PACKAGE = 'fr.swucollection.app.test'
OUT = Path('smoke-private')
def adb(*args):
    try:
        return subprocess.check_output(['adb', *map(str, args)], text=True, stderr=subprocess.STDOUT, timeout=45)
    except subprocess.CalledProcessError as error:
        print('ADB failure:', args, error.output, flush=True)
        raise
def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/premium.xml')
    p = OUT / ('premium-' + name + '.xml'); adb('pull', '/sdcard/premium.xml', p)
    return ET.parse(p).getroot()
def bounds(n): return list(map(int, re.findall(r'\d+', n.attrib['bounds'])))
def find(root, label):
    nodes = [n for n in root.iter('node') if n.attrib.get('text') == label or label in n.attrib.get('content-desc', '').split('\n')]
    assert nodes, (label, [n.attrib.get('text') for n in root.iter('node') if n.attrib.get('text')])
    return nodes[-1]
def tap(node):
    x1,y1,x2,y2 = bounds(node); assert x2>x1 and y2>y1
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2); time.sleep(2)
def capture(name):
    p = OUT / ('home-test-premium-' + name + '.png')
    adb('shell','screencap','-p','/sdcard/premium.png'); adb('pull','/sdcard/premium.png',p)
    return p

def reveal(label):
    for attempt in range(8):
        root = snapshot('reveal-' + str(attempt))
        candidates = [n for n in root.iter('node') if n.attrib.get('text') == label]
        if candidates:
            r = bounds(candidates[-1])
            if r[2]>r[0] and r[3]>r[1]: return candidates[-1]
        adb('shell','input','swipe',360,1100,360,600,400); time.sleep(1)
    raise AssertionError('Unreachable card action: ' + label)

def reveal_quantity(name):
    for attempt in range(5):
        root = snapshot(name + '-' + str(attempt))
        visible = [n for n in root.iter('node') if n.attrib.get('text') == '× 1' and bounds(n)[3] > bounds(n)[1]]
        if visible: return root
        window = next(n for n in root.iter('node') if n.attrib.get('resource-id') == 'swu_content_window')
        left,top,right,bottom = bounds(window)
        # A reflowed card can be taller than the visible grid viewport on Fold.
        # Scroll within the left pane, above its bottom navigation, to its quantity.
        x = (left + right)//2
        adb('shell','input','swipe',x,bottom-220,x,bottom-620,450); time.sleep(1)
    raise AssertionError('Owned quantity is not reachable in the visual grid')

def verify_motion():
    # UIAutomator waits for an idle window; collect geometry before continuous motion.
    root=snapshot('motion-bounds')
    banner=next(n for n in root.iter('node') if n.attrib.get('resource-id')=='swu_premium_banner')
    x1,y1,x2,y2=bounds(banner); w=x2-x1; h=y2-y1
    assert not any('«' in n.attrib.get('text','') for n in root.iter('node')), 'Quote card still on Test home'
    search=find(root,'Carte, deck, extension…')
    search_parent=[n for n in root.iter('node') if search in list(n.iter('node')) and n.attrib.get('clickable')=='true'][-1]
    assert bounds(search_parent)[1]<y2, 'Search no longer overlaps the banner fade'
    assert x1==0 and y1==0, ('Banner has top/left margin', (x1,y1,x2,y2))
    display=adb('shell','wm','size')
    screen_width=int(re.findall(r'(\d+)x\d+',display)[-1])
    assert x2==screen_width, ('Banner has right margin',x2,screen_width)
    edge=capture('edge-to-edge')
    image=Image.open(edge).convert('RGB')
    for point in [(screen_width//4,2),(screen_width*3//4,2),(2,y1+h//2),(screen_width-3,y1+h//2)]:
        assert max(image.getpixel(point))>3, ('Black band at edge',point)
    area=(int(x1+w*.2),int(y1+h*.55),int(x1+w*.8),int(y2-h*.08))
    collection_entry = find(root, 'Collection')
    home_entry = find(root, 'Accueil')
    adb('shell','settings','put','global','animator_duration_scale',1)
    # Recreate the home composition without killing its live graphics process.
    # Both anchors belong to the fixed bottom navigation on the same viewport.
    tap(collection_entry)
    tap(home_entry)
    first=capture('orbit-first'); time.sleep(12); second=capture('orbit-second')
    diff=ImageChops.difference(Image.open(first).crop(area),Image.open(second).crop(area)).convert('L')
    assert sum(v>2 for v in diff.getdata())>diff.width*diff.height*.01, 'Background does not drift when animations are enabled'
    adb('shell','settings','put','global','animator_duration_scale',0); time.sleep(3)
    first=capture('still-first'); time.sleep(3); second=capture('still-second')
    assert ImageChops.difference(Image.open(first).crop(area).convert('RGB'),Image.open(second).crop(area).convert('RGB')).getbbox() is None, 'Reduced-motion background keeps moving'
    print('Live orbital movement and reduced-motion pixels passed.', flush=True)

def verify_secondary_shortcuts():
    heights={}
    keys=['MES DECKS','TOURNOIS & MÉTA','BADGES','STATISTIQUES','EXTENSIONS','ÉCHANGES']
    for attempt in range(9):
        root=snapshot('shortcuts-'+str(attempt))
        for key in keys:
            for node in root.iter('node'):
                if node.attrib.get('resource-id')=='swu_home_tile_'+key:
                    x1,y1,x2,y2=bounds(node)
                    if 65<y1<y2<1320:
                        heights[key]=(x2-x1,y2-y1)
        if len(heights)==len(keys):break
        adb('shell','input','swipe',360,1100,360,780,400);time.sleep(1)
    assert len(heights)==len(keys), ('Missing full shortcut bounds',heights)
    assert max(h for w,h in heights.values())-min(h for w,h in heights.values())<=2, heights
    assert max(w for w,h in heights.values())-min(w for w,h in heights.values())<=2, heights
    capture('secondary-shortcuts')
    # Return to the very top before existing navigation checks.
    for attempt in range(8):
        adb('shell','input','swipe',360,500,360,1200,250)
    time.sleep(2)
    print('All six secondary shortcuts have matching dimensions, including Échanges.',flush=True)

original_scale = adb('shell','settings','get','global','animator_duration_scale').strip()
try:
    adb('shell','wm','size','720x1560'); adb('shell','wm','density',320)
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/fr.swucollection.app.MainActivity'); time.sleep(8)
    verify_motion()
    verify_secondary_shortcuts()
    tap(find(snapshot('home'),'Extensions'))
    root = snapshot('extensions')
    tap(next(n for n in root.iter('node') if 'références possédées' in n.attrib.get('text','')))
    root = snapshot('catalogue')
    tap(next(n for n in root.iter('node') if n.attrib.get('content-desc')=='Visuel de la carte' and bounds(n)[3]>bounds(n)[1]))
    tap(reveal('+'))
    tap(find(snapshot('added'),'Fermer la carte'))
    tap(find(snapshot('catalogue-return'),'Collection'))
    root = snapshot('grid'); find(root,'× 1')
    assert any('Cote' in n.attrib.get('text','') for n in root.iter('node')), 'Visual grid lost price information'
    capture('collection-cover')
    adb('shell','wm','size','1766x1472'); adb('shell','wm','density',336); time.sleep(4)
    root = reveal_quantity('grid-unfolded'); find(root,'× 1'); capture('collection-unfolded')
    adb('shell','wm','size','720x1560'); adb('shell','wm','density',320); time.sleep(4)
    tap(find(reveal_quantity('grid-return'),'× 1'))
    tap(reveal('−'))
    tap(find(snapshot('removed'),'Fermer la carte'))
    find(snapshot('empty'),'Ta collection est vide. Scanne ta première carte pour commencer.')
    tap(find(snapshot('empty-return'),'Accueil'))
    assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
    print('Premium collection passed: add/open/remove, cover/unfolded, price visibility, live drift and reduced motion.')
except Exception:
    try: capture('failure')
    except Exception as capture_error: print('Failure capture unavailable:', capture_error, flush=True)
    raise
finally:
    try:
        adb('shell','settings','put','global','animator_duration_scale',original_scale)
        adb('shell','wm','size','reset'); adb('shell','wm','density','reset')
    except Exception as cleanup_error: print('Emulator cleanup unavailable:', cleanup_error, flush=True)
