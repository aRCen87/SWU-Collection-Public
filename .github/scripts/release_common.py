import hashlib
from pathlib import Path
import re

REPOSITORY='aRCen87/SWU-Collection-Releases'
SOURCE_REPOSITORY='aRCen87/SWU-Collection-Public'
PACKAGE='fr.swucollection.app'
SIGNER='54912449ae2e4ecd6e88243d3e11fb0bb2c1d2e7da77af2038752ee50e7b549b'
MAX_BYTES=200*1024*1024

def validate(metadata):
    assert re.fullmatch(r'\d+\.\d+\.\d+',metadata['version']), 'Invalid version'
    assert type(metadata['version_code']) is int and 0<metadata['version_code']<2**31, 'Invalid version code'
    assert metadata['package']==PACKAGE, 'Wrong package'
    assert type(metadata['apk_size']) is int and 0<metadata['apk_size']<=MAX_BYTES, 'Invalid APK size'
    for key in ['apk_sha256','source_fingerprint']:
        assert re.fullmatch('[a-f0-9]{64}',metadata[key]), 'Invalid digest'
    assert isinstance(metadata['notes'],str) and 0<len(metadata['notes'])<=6000, 'Invalid release notes'
    return metadata

def source_fingerprint(root):
    root=Path(root)
    files=list((root/'app/src').rglob('*'))+[root/'app/build.gradle.kts',root/'app/proguard-rules.pro',root/'build.gradle.kts',root/'settings.gradle.kts',root/'gradle.properties']
    digest=hashlib.sha256()
    for path in sorted(p for p in files if p.is_file()):
        # Unit tests do not change the application binary or trigger a new publication.
        if str(path.relative_to(root)).startswith(('app/src/test/','app/src/androidTest/')):continue
        digest.update(path.relative_to(root).as_posix().encode('utf-8')+b'\0')
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()

def release_notes(history, version):
    """Select exactly one version section without sending the accumulated changelog."""
    headings=list(re.finditer(r'^## Version ([^\r\n]+)\r?$', history, re.MULTILINE))
    matches=[index for index, heading in enumerate(headings) if heading.group(1)==version]
    if len(matches)!=1:
        raise ValueError('Release notes must contain exactly one section for version '+version)
    index=matches[0]
    heading=headings[index]
    end=headings[index+1].start() if index+1<len(headings) else len(history)
    body=history[heading.end():end].strip()
    notes=history[heading.start():end].strip()
    if not body or len(notes)>6000:
        raise ValueError('Version release notes must have a nonempty body and at most 6000 characters')
    return notes
