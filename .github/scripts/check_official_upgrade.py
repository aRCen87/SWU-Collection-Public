"""Install the published signed APK, seed disposable data, then upgrade without uninstalling.

This test refuses physical devices. It never uses real accounts or collections.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import time
import urllib.request

PACKAGE = 'fr.swucollection.app'
SIGNER = '54912449ae2e4ecd6e88243d3e11fb0bb2c1d2e7da77af2038752ee50e7b549b'
OUT = Path('smoke-private/official-upgrade')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True, stderr=subprocess.STDOUT, timeout=90)

def verify_apk(path):
    tools = Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
    cert = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(path)], text=True)
    assert re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)', cert) == [SIGNER]
    info = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(path)], text=True)
    package, code, version = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", info).groups()
    assert package == PACKAGE
    return int(code), version

def launch():
    start = adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/fr.swucollection.app.MainActivity')
    time.sleep(10)
    crashes = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
    process = subprocess.run(['adb', 'shell', 'pidof', PACKAGE], text=True, capture_output=True, timeout=30)
    if process.returncode or 'FATAL EXCEPTION' in crashes:
        print(start, crashes, flush=True)
        print(adb('shell', 'dumpsys', 'activity', 'exit-info', PACKAGE), flush=True)
        raise AssertionError('Official upgrade launch failed; crash and exit reason are printed above')
    adb('shell', 'am', 'force-stop', PACKAGE)

def copy_database(name):
    destination = OUT/name
    destination.mkdir(exist_ok=True)
    adb('pull', '/data/data/'+PACKAGE+'/databases', destination)
    return destination/'databases/swu_collection.db'

def inventory(path):
    with sqlite3.connect(path) as db:
        return {table: db.execute('SELECT * FROM '+table+' ORDER BY 1').fetchall()
                for table in ('collection_cards', 'card_lists', 'decks')}

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Disposable Android emulator required'
candidate = Path('app/build/outputs/apk/release/app-release.apk')
new_code, new_version = verify_apk(candidate)
with urllib.request.urlopen('https://api.github.com/repos/aRCen87/SWU-Collection-Releases/releases/latest', timeout=40) as response:
    release = json.load(response)
asset = next(item for item in release['assets'] if item['name'] == 'SWU-Collection.apk')
baseline = OUT/'published-baseline.apk'
urllib.request.urlretrieve(asset['browser_download_url'], baseline)
assert baseline.stat().st_size == asset['size']
assert asset.get('digest') == 'sha256:'+hashlib.sha256(baseline.read_bytes()).hexdigest()
old_code, old_version = verify_apk(baseline)
assert old_code < new_code

# Only discard the earlier empty smoke fixture in this verified disposable emulator.
adb('uninstall', PACKAGE)
adb('install', '-r', baseline)
# This fixture has just been reinstalled, so its notification grant was reset.
# Resolve it before launch: force-stopping behind an unanswered system dialog leaves
# PermissionController on top and can prevent the next app launch from starting.
adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
adb('logcat', '-c')
launch()
adb('root'); adb('wait-for-device')
remote = '/data/data/'+PACKAGE+'/databases/swu_collection.db'
uid = adb('shell', 'stat', '-c', '%u', remote).strip()
assert uid.isdigit()
before = copy_database('before')
with sqlite3.connect(before) as db:
    assert db.execute('PRAGMA user_version').fetchone()[0] == 11
    assert db.execute('SELECT count(*) FROM collection_cards').fetchone()[0] == 0
    db.executemany('INSERT INTO collection_cards(name,set_code,card_number,variant,quantity,language) VALUES(?,?,?,?,?,?)', [
        ('R2-D2', 'SOR', '236', 'Foil', 3, 'FR'), ('C-3PO', 'SOR', '238', 'Normale', 2, 'EN')])
    db.execute('INSERT INTO card_lists(id,name) VALUES(?,?)', ('upgrade-list', 'Liste conservée'))
    payload = json.dumps({'leader': {'id': 'SOR_005', 'count': 1}, 'base': {'id': 'SOR_024', 'count': 1}, 'deck': [], 'sideboard': []})
    db.execute('INSERT INTO decks(id,name,format,payload,updated_at) VALUES(?,?,?,?,?)', ('upgrade-deck', 'Deck conservé', 'Premier', payload, 1))
    db.commit()
    db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
expected = inventory(before)
adb('shell', 'rm', '-f', remote+'-wal', remote+'-shm', remote+'-journal')
adb('push', before, remote)
adb('shell', 'chown', uid+':'+uid, remote)
adb('shell', 'chmod', '660', remote)
adb('shell', 'restorecon', remote)
# Prove that the fixture itself is readable by the published application before upgrading it.
adb('logcat', '-c')
launch()
adb('install', '-r', candidate)  # The actual upgrade must retain the application's UID and data.
adb('logcat', '-c')
launch()
assert adb('shell', 'stat', '-c', '%u', remote).strip() == uid
after = copy_database('after')
assert inventory(after) == expected, 'Existing collection, printings, quantities, languages, lists or decks changed'
with sqlite3.connect(after) as db:
    assert db.execute('PRAGMA user_version').fetchone()[0] == 12
adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'swucollection://account', '-p', PACKAGE)
time.sleep(3)
adb('shell', 'pidof', PACKAGE)
print('Signed official upgrade passed:', old_version, '→', new_version, '; cards, IDs, variants, languages, quantities, lists and decks preserved.', flush=True)
