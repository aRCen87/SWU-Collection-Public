#!/usr/bin/env python3
import hashlib,json,os,re,subprocess
from pathlib import Path
from release_common import source_fingerprint,validate,release_notes

ROOT=Path(__file__).resolve().parents[2]
APK=ROOT/'app/build/outputs/apk/release/app-release.apk'
SIGNER='54912449ae2e4ecd6e88243d3e11fb0bb2c1d2e7da77af2038752ee50e7b549b'
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
cert=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(APK)],text=True)
assert re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)',cert)==[SIGNER], 'Permanent signing key changed'
info=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(APK)],text=True)
package,code,version=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",info).groups()
assert package=='fr.swucollection.app' and re.fullmatch(r'\d+\.\d+\.\d+',version)
config=(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
expected_code=re.search(r'versionCode\s*=\s*(\d+)',config).group(1)
expected_version=re.search(r'versionName\s*=\s*"([^"]+)"',config).group(1)
assert (code,version)==(expected_code,expected_version), 'Built APK does not match the configured source version'
notes=release_notes((ROOT/'RELEASE_NOTES.md').read_text(encoding='utf-8'),version)
print(json.dumps(validate({'package':package,'version_code':int(code),'version':version,'apk_size':APK.stat().st_size,
                  'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),'source_fingerprint':source_fingerprint(ROOT),'notes':notes}),ensure_ascii=False))
