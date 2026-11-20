"""Verify Android sandbox isolation in a disposable emulator, never on a real phone."""
import hashlib
from pathlib import Path
import subprocess
import time
import urllib.request

PLAY='fr.swucollection.app.playstore'
OFFICIAL='fr.swucollection.app'
OUT=Path('smoke-playstore/coexistence')
OUT.mkdir(parents=True,exist_ok=True)
def adb(*args):
    return subprocess.check_output(['adb',*map(str,args)],text=True,stderr=subprocess.STDOUT,timeout=90)
assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
# Public official APK; verify its exact published bytes before installing.
apk=OUT/'official.apk'
with urllib.request.urlopen('https://github.com/aRCen87/SWU-Collection-Releases/releases/download/v0.9.11/SWU-Collection.apk',timeout=90) as response:
    apk.write_bytes(response.read())
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='adabddd0b91b9ebe529df6d525a597f03e4588abc88009759f726745191f88e6'
adb('install','-r',apk)
adb('shell','pm','grant',OFFICIAL,'android.permission.POST_NOTIFICATIONS')
def launch(package):
    adb('shell','am','start','-W','-n',package+'/fr.swucollection.app.MainActivity')
    time.sleep(8)
    assert adb('shell','pidof',package).strip()
    adb('shell','am','force-stop',package)
launch(OFFICIAL)
for attempt in range(3):
    subprocess.run(['adb','root'],text=True,capture_output=True,timeout=90)
    try:
        adb('wait-for-device')
        if adb('shell','id','-u').strip()=='0':
            break
    except subprocess.CalledProcessError:
        pass
    time.sleep(1)
else:
    raise AssertionError('Disposable emulator root unavailable')
def uid(package):
    return adb('shell','stat','-c','%u','/data/data/'+package).strip()
assert uid(OFFICIAL)!=uid(PLAY)
def snapshot(package):
    # Only persistent collection/preferences; cache/logs and OS-owned metadata are excluded.
    raw=adb('shell', 'sh','-c',f"'find /data/data/{package}/databases /data/data/{package}/shared_prefs -type f -exec sha256sum {{}} \\; 2>/dev/null'")
    return '\n'.join(sorted(raw.splitlines()))
before=snapshot(OFFICIAL)
assert 'swu_collection.db' in before
launch(PLAY)
assert snapshot(OFFICIAL)==before,'Playstore changed official data'
# Removing the standalone package must leave official private data intact.
adb('uninstall',PLAY)
assert snapshot(OFFICIAL)==before,'Uninstalling Playstore changed official data'
launch(OFFICIAL)
(OUT/'PASS.txt').write_text('Distinct Android UIDs, official data unchanged by standalone launch/uninstall, official restart: PASS\n')
print('Official and Playstore coexist independently: PASS')
