"""Exercise keyboard, open-card navigation and CameraX during live window changes."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = os.environ.get("SWU_SMOKE_PACKAGE", "fr.swucollection.app.test")
OUT = Path("smoke-private")

def adb(*args):
    command = ["adb", *map(str, args)]
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT, timeout=45)
    except subprocess.CalledProcessError as error:
        read_only = args[0] == "pull" or args[:2] in (("shell", "uiautomator"), ("shell", "screencap"))
        if not read_only or "offline" not in error.output: raise
        subprocess.check_call(["adb", "wait-for-device"], timeout=30)
        return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT, timeout=45)

assert PACKAGE in ('fr.swucollection.app', 'fr.swucollection.app.test')
assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Disposable emulator required'

def snapshot(name):
    adb("shell", "uiautomator", "dump", "/sdcard/fold-check.xml")
    path = OUT / ("fold-" + name + ".xml")
    adb("pull", "/sdcard/fold-check.xml", path)
    return ET.parse(path).getroot()

def bounds(node):
    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))

def find(root, label):
    nodes = [n for n in root.iter("node") if n.attrib.get("text") == label or label in n.attrib.get("content-desc", "").split("\n")]
    assert nodes, label
    return nodes[-1]

def tap(node):
    x1,y1,x2,y2 = bounds(node)
    assert x2>x1 and y2>y1
    adb("shell", "input", "tap", (x1+x2)//2, (y1+y2)//2)
    time.sleep(2)

def resize(width, height, dpi):
    adb("shell", "wm", "size", f"{width}x{height}")
    adb("shell", "wm", "density", dpi)
    time.sleep(4)

def capture(name):
    adb("shell", "screencap", "-p", "/sdcard/fold-check.png")
    adb("pull", "/sdcard/fold-check.png", OUT / ("home-test-fold-" + name + ".png"))

def page(root):
    pages = [n.attrib.get("text", "") for n in root.iter("node") if re.fullmatch(r"\d+ / \d+", n.attrib.get("text", ""))]
    assert len(pages) == 1, (pages, [n.attrib.get("text", "") for n in root.iter("node") if n.attrib.get("text")][:60])
    return pages[0]

original_keyboard = adb("shell", "settings", "get", "secure", "show_ime_with_hard_keyboard").strip()
try:
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    resize(720,1560,320)
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/fr.swucollection.app.MainActivity")
    time.sleep(8)
    root = snapshot("home"); tap(find(root,"Collection"))
    root = snapshot("collection")
    edit = next(n for n in root.iter("node") if n.attrib.get("class") == "android.widget.EditText")
    tap(edit); adb("shell", "input", "text", "FoldProbe")
    keyboard = adb("shell", "dumpsys", "input_method")
    assert any(marker in keyboard for marker in ("mInputShown=true", "mIsInputViewShown=true", "isInputViewShown=true")), "Software keyboard did not open"
    resize(1766,1472,336)
    root = snapshot("keyboard-unfolded")
    assert any("FoldProbe" in n.attrib.get("text", "") for n in root.iter("node")), "Search lost while unfolding with keyboard"
    capture("keyboard")
    # The known probe is the only text entered; leave collection filters clean.
    # A compact window exposes the clear action on the search field; a roomy
    # layout may instead expose the active-filter chip. Both must clear the query.
    clear_actions = [n for n in root.iter("node") if
        "Effacer la recherche" in n.attrib.get("content-desc", "").split("\n")
        and bounds(n)[2] > bounds(n)[0] and bounds(n)[3] > bounds(n)[1]]
    tap(clear_actions[-1] if clear_actions else find(root, "FoldProbe ×"))
    keyboard = adb("shell", "dumpsys", "input_method")
    if any(marker in keyboard for marker in ("mInputShown=true", "mIsInputViewShown=true", "isInputViewShown=true")):
        adb("shell", "input", "keyevent", "4")
    time.sleep(2)
    cleared = snapshot("query-cleared")
    assert not any("FoldProbe" in n.attrib.get("text", "") for n in cleared.iter("node")), "Search clear action did not clear the retained query"
    resize(756,1680,294)
    root = snapshot("collection-restored"); tap(find(root,"Toutes les cartes"))
    # The extension catalogue now lives in the unified Collection page. Keep
    # testing the real ordered card reader, using the visible gallery bounds.
    for attempt in range(12):
        root = snapshot("catalogue-" + str(attempt))
        images = [n for n in root.iter("node") if n.attrib.get("content-desc") == "Visuel de la carte" and bounds(n)[3] - bounds(n)[1] >= 48]
        if images:
            artwork = images[0]
            break
        grid = next(n for n in root.iter("node") if n.attrib.get("resource-id") == "swu_unified_catalog")
        x1,y1,x2,y2 = bounds(grid)
        assert x2 > x1 and y2 > y1, "Unified catalogue has collapsed"
        adb("shell","input","swipe",(x1+x2)//2,y2-8,(x1+x2)//2,y1+8,400); time.sleep(1)
    else: raise AssertionError("No reachable card in the unified catalogue")
    tap(artwork)
    root = snapshot("card-phone"); first = page(root)
    # Extension headers may require scrolling past the first catalogue row.
    # The reader must retain the selected visible card and move exactly one slot.
    initial_index, total = map(int, first.split(" / "))
    assert 1 <= initial_index < total, first
    following = f"{initial_index + 1} / {total}"
    resize(1766,1472,336)
    root = snapshot("card-unfolded"); assert page(root) == first
    assert any(n.attrib.get("resource-id") == "swu_detail_pane" for n in root.iter("node"))
    tap(find(root,"Carte suivante")); root = snapshot("card-next")
    assert page(root) == following
    capture("card")
    resize(1680,756,294)
    root = snapshot("card-landscape"); capture("card-landscape"); assert page(root) == following
    tap(find(root,"Carte précédente")); root = snapshot("card-previous")
    assert page(root) == first
    tap(find(root,"Fermer la carte")); root = snapshot("closed")
    tap(find(root,"Accueil")); root = snapshot("home-restored")
    # In a short landscape window the home buttons are below the scrollable
    # banner. Open the camera from the cover, then resize the active scanner
    # through every profile below instead of tapping an offscreen home item.
    resize(720,1560,320)
    root = snapshot("home-cover-restored")
    adb("shell", "pm", "grant", PACKAGE, "android.permission.CAMERA")
    tap(find(root,"Collection")); root = snapshot("scanner-collection")
    tap(find(root,"Ouvrir le menu")); root = snapshot("scanner-menu")
    tap(find(root,"Scanner")); time.sleep(4)
    for name,w,h,d in [("cover",720,1560,320),("unfolded",1766,1472,336),("landscape",1680,756,294)]:
        resize(w,h,d)
        root = snapshot("scanner-" + name)
        assert not any("Caméra indisponible" in n.attrib.get("text", "") or "Impossible d’ouvrir la caméra" in n.attrib.get("text", "") for n in root.iter("node")), "CameraX failed during resize"
        button = find(root,"Terminer"); x1,y1,x2,y2 = bounds(button)
        assert x2>x1 and y2>y1, "Scanner close control lost during resize"
        assert adb("shell", "pidof", PACKAGE).strip(), "App stopped while scanner resized"
        capture("scanner-" + name)
    tap(find(root,"Terminer"))
    crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    assert "FATAL EXCEPTION" not in crashes, crashes
    print("Fold interaction checks passed: keyboard query retained; open card retained; next/previous retained; CameraX survives cover/unfolded/landscape.")
except Exception:
    capture("failure")
    print(adb("logcat", "-d", "-s", "AndroidRuntime:E"), flush=True)
    raise
finally:
    if original_keyboard == "null": adb("shell", "settings", "delete", "secure", "show_ime_with_hard_keyboard")
    else: adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", original_keyboard)
    adb("shell", "wm", "size", "reset"); adb("shell", "wm", "density", "reset")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/fr.swucollection.app.MainActivity")
    time.sleep(4)
