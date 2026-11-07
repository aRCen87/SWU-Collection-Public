"""Exercise keyboard, open-card navigation and CameraX during live window changes."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "fr.swucollection.app.test"
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
    edit = next(n for n in root.iter("node") if n.attrib.get("class") == "android.widget.EditText")
    tap(edit); adb("shell", "input", "keyevent", *(["67"] * len("FoldProbe")))
    adb("shell", "input", "keyevent", "4"); time.sleep(2)
    resize(756,1680,294)
    root = snapshot("collection-restored"); tap(find(root,"Extensions"))
    root = snapshot("extensions")
    extension = next(n for n in root.iter("node") if "références possédées" in n.attrib.get("text", ""))
    tap(extension)
    root = snapshot("catalogue")
    artwork = next(n for n in root.iter("node") if n.attrib.get("content-desc") == "Visuel de la carte" and bounds(n)[3] > bounds(n)[1])
    tap(artwork)
    root = snapshot("card-phone"); first = page(root)
    assert first.startswith("1 / "), first
    resize(1766,1472,336)
    root = snapshot("card-unfolded"); assert page(root) == first
    assert any(n.attrib.get("resource-id") == "swu_detail_pane" for n in root.iter("node"))
    tap(find(root,"Carte suivante")); root = snapshot("card-next")
    assert page(root).startswith("2 / ")
    capture("card")
    resize(1680,756,294)
    root = snapshot("card-landscape"); capture("card-landscape"); assert page(root).startswith("2 / ")
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
