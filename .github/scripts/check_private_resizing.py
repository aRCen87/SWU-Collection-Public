"""Exercise live phone/foldable window changes without cloud accounts or data."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], text=True)


def snapshot(name):
    adb("shell", "uiautomator", "dump", "/sdcard/layout.xml")
    path = Path("smoke-private") / (name + ".xml")
    adb("pull", "/sdcard/layout.xml", path)
    return ET.parse(path).getroot()


def rect(node):
    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def find(root, label):
    found = [n for n in root.iter("node") if label in
             n.attrib.get("content-desc", "").split("\n") or n.attrib.get("text") == label]
    assert found, label
    return found[-1]


def touch(root, label):
    x1, y1, x2, y2 = rect(find(root, label))
    assert x2 > x1 and y2 > y1, label
    adb("shell", "input", "tap", (x1+x2)//2, (y1+y2)//2)
    time.sleep(2)


def capture(name):
    adb("shell", "screencap", "-p", "/sdcard/layout.png")
    adb("pull", "/sdcard/layout.png", "smoke-private/" + name + ".png")


def home(root, width, height, density):
    windows = [n for n in root.iter("node") if
               n.attrib.get("resource-id") == "swu_content_window"]
    assert len(windows) == 1, "Missing measured content viewport"
    left, top, right, bottom = rect(windows[0])
    usable_width = right-left
    print(f"Requested {width}x{height}; measured safe viewport {(left,top,right,bottom)}", flush=True)
    x1, y1, x2, y2 = rect(find(root, "SWU Collection"))
    # UIAutomator trims the banner's accessibility bounds where the real
    # avatar/menu/mail targets overlap it. Its right bound is therefore not
    # the painted image edge. Recover that edge from the measured height and
    # verify it against the centered, padded content width independently.
    painted_width = (y2-y1) * 475/185
    expected_width = min(usable_width - 32*density, 568*density)
    assert abs(painted_width - expected_width) < 3, (width,density,x1,y1,x2,y2)
    assert abs(x1 + painted_width/2 - (left+right)/2) < 3, (left,right,x1,painted_width)
    assert x1 < x2 <= x1 + painted_width + 2, (x1,x2,painted_width)
    parents = {c:p for p in root.iter() for c in p}
    controls = []
    for label in ("Mon profil", "Ouvrir le menu", "Courrier, 0 en attente"):
        node = find(root, label)
        while node.attrib.get("clickable") != "true" and node in parents:
            node = parents[node]
        assert node.attrib.get("clickable") == "true", label
        r = rect(node)
        assert r[2]-r[0] >= 47*density and r[3]-r[1] >= 47*density, (label,r)
        assert left <= r[0] < r[2] <= right and top <= r[1] < r[3] <= bottom, (label,r)
        controls.append(r)
    assert controls[0][3] <= controls[1][1] and controls[1][3] <= controls[2][1], controls
    assert max((r[0]+r[2])/2 for r in controls) - min((r[0]+r[2])/2 for r in controls) < 3, controls


profiles = [("cover",1080,2340,480), ("phone",1080,2400,420),
            ("unfolded",2208,1840,420), ("landscape",2400,1080,420)]
try:
    for index, (name,width,height,dpi) in enumerate(profiles):
        # Remain on Collection while resizing: folding must retain the destination.
        adb("shell", "wm", "size", f"{width}x{height}")
        adb("shell", "wm", "density", dpi)
        time.sleep(4)
        root = snapshot(name)
        if index:
            find(root, "Ma collection")
            touch(root, "Accueil")
            root = snapshot(name + "-home")
        capture("home-test-" + name)
        home(root,width,height,dpi/160)
        if name == "unfolded":
            touch(root,"Ouvrir le menu")
            menu = snapshot("unfolded-menu")
            # Dialogs have their own Android window; measure the actual frame,
            # which may be smaller than wm's requested display on an emulator.
            frame = rect(next(menu.iter("node")))
            x1,_,_,_ = rect(find(menu,"Compte et communauté"))
            assert x1 >= frame[2] - 400*dpi/160 - 2, (frame,x1)
            capture("home-test-unfolded-menu")
            touch(menu,"Fermer le menu")
            root = snapshot("unfolded-home-restored")
        touch(root,"Collection")
        root = snapshot(name + "-collection")
        find(root,"Ma collection")
        if name == "unfolded":
            capture("home-test-unfolded-collection")
    touch(root,"Accueil")
finally:
    adb("shell","wm","size","reset")
    adb("shell","wm","density","reset")
    time.sleep(3)

print("Live resize checks passed: cover, phone, unfolded, landscape; navigation and 48dp targets retained.")
