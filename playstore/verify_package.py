"""Verify identity, absence of account/messenger code and install permissions in APK."""
from pathlib import Path
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
aapt = sys.argv[2]
badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
assert "package: name='fr.swucollection.app.playstore'" in badging
assert "versionCode='1'" in badging
assert "application-label:'SWU Collection Playstore'" in badging
permissions = subprocess.check_output([aapt, 'dump', 'permissions', str(apk)], text=True)
for forbidden in ('REQUEST_INSTALL_PACKAGES', 'FOREGROUND_SERVICE', 'POST_NOTIFICATIONS'):
    assert forbidden not in permissions, forbidden
with zipfile.ZipFile(apk) as archive:
    dex = b'\n'.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
    for forbidden in (b'Lcom/google/firebase/auth/', b'Lcom/google/firebase/firestore/',
                      b'Lfr/swucollection/app/CommunityService;', b'Lfr/swucollection/app/RealtimeMessagingService;'):
        assert forbidden not in dex, forbidden
print('Verified Playstore package, permissions and absence of Auth/Firestore and messaging code.')
