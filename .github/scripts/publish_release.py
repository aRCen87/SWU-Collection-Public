#!/usr/bin/env python3
"""Publish a finalized signed build, using a token limited to the public releases repository."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from release_common import REPOSITORY,SOURCE_REPOSITORY,PACKAGE,SIGNER,validate,source_fingerprint

def run(*args):return subprocess.check_output(args,text=True,encoding='utf-8').strip()

def api(path='',optional=False):
    suffix='/'+path if path else ''
    result=subprocess.run(['gh','api',f'repos/{REPOSITORY}{suffix}'],text=True,capture_output=True)
    if result.returncode:
        status_match=re.search(r'HTTP\s*(\d{3})',result.stderr)
        status=status_match.group(1) if status_match else 'unknown'
        if optional and status=='404':return None
        # GitHub returns 409, not 404, when an existing repository has no first commit.
        if optional and path.startswith('git/ref/') and status=='409' and 'empty' in result.stderr.lower():return None
        raise RuntimeError(f'Publication API {path or "repository"} returned HTTP {status}; check the repository authorization if access is denied')
    return json.loads(result.stdout)

def release_by_tag(tag):
    # GET releases/tags/{tag} only returns published releases; draft tags may not exist yet.
    for page in range(1,101):
        releases=api(f'releases?per_page=100&page={page}')
        matches=[release for release in releases if release['tag_name']==tag]
        assert len(matches)<=1,'Ambiguous release tag'
        if matches:return matches[0]
        if len(releases)<100:return None
    raise RuntimeError('Release pagination exceeds safety limit')

def create_draft(tag,title,notes):
    # Use the created release ID returned by POST. Listing may lag after creation.
    release=json.loads(run('gh','api','--method','POST',f'repos/{REPOSITORY}/releases',
        '-f',f'tag_name={tag}','-F','draft=true','-f',f'name={title}','-F',f'body=@{notes}'))
    assert release.get('id') and release.get('tag_name')==tag and release.get('draft') is True, 'Invalid draft creation response'
    return release

def verify_apk(apk,metadata,tools):
    assert apk.stat().st_size==metadata['apk_size'],'APK size mismatch'
    assert hashlib.sha256(apk.read_bytes()).hexdigest()==metadata['apk_sha256'],'APK digest mismatch'
    cert=run(str(tools/'apksigner'),'verify','--print-certs',str(apk))
    assert re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)',cert)==[SIGNER],'Permanent signing identity mismatch'
    info=run(str(tools/'aapt'),'dump','badging',str(apk))
    identity=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",info)
    assert identity and identity.groups()==(PACKAGE,str(metadata['version_code']),metadata['version']),'APK package/version mismatch'

def published_metadata(release):
    assets=[a for a in release['assets'] if a['name']=='release-metadata.json']
    assert len(assets)==1,'Published version has no verified metadata'
    # Draft assets are intentionally not anonymous yet. Download through the authenticated
    # asset endpoint, then make the release public only after verification.
    raw=run('gh','api',f"repos/{REPOSITORY}/releases/assets/{assets[0]['id']}",'-H','Accept: application/octet-stream')
    metadata=validate(json.loads(raw))
    apks=[a for a in release['assets'] if a['name']=='SWU-Collection.apk']
    assert len(apks)==1 and apks[0].get('digest')=='sha256:'+metadata['apk_sha256'] and apks[0]['size']==metadata['apk_size'],'Published asset metadata mismatch'
    return metadata

def main():
    apk=Path(sys.argv[1]);path=Path(sys.argv[2]);metadata=validate(json.loads(path.read_text(encoding='utf-8')))
    assert os.environ.get('GITHUB_REPOSITORY')==SOURCE_REPOSITORY,'Run from the authorized source repository'
    assert os.environ.get('GITHUB_REF')=='refs/heads/main','Only finalized main builds may be published'
    assert metadata['source_fingerprint']==source_fingerprint(Path(__file__).resolve().parents[2]), 'Artifact source does not match this finalized commit'
    verify_apk(apk,metadata,Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0')
    repository=api()
    assert not repository['private'], 'The distribution repository must allow anonymous application downloads'
    branch=repository['default_branch']
    if api(f'git/ref/heads/{branch}',optional=True) is None:
        text='# SWU Collection — versions Android\n\nVersions signées de SWU Collection. La collection reste enregistrée sur le téléphone.\n'
        run('gh','api','--method','PUT',f'repos/{REPOSITORY}/contents/README.md','-f','message=Initialiser la distribution signée','-f','content='+base64.b64encode(text.encode('utf-8')).decode('ascii'))
    tag='v'+metadata['version']
    existing=release_by_tag(tag)
    if existing and not existing['draft']:
        previous=published_metadata(existing)
        assert previous['source_fingerprint']==metadata['source_fingerprint'] and previous['version_code']==metadata['version_code'],'Application changed: increase versionCode and versionName instead of replacing a published version'
        print('This application version is already finalized; no duplicate publication or notification');return
    latest=api('releases/latest',optional=True)
    if latest:
        previous=published_metadata(latest)
        assert tuple(map(int,metadata['version'].split('.')))>tuple(map(int,previous['version'].split('.'))),'Version downgrade refused'
        assert metadata['version_code']>previous['version_code'],'versionCode must increase'
    with tempfile.TemporaryDirectory() as directory:
        folder=Path(directory);payload=folder/'SWU-Collection.apk';payload.write_bytes(apk.read_bytes())
        meta=folder/'release-metadata.json';meta.write_text(json.dumps(metadata,ensure_ascii=False),encoding='utf-8')
        notes=folder/'notes.md';notes.write_text(metadata['notes'],encoding='utf-8')
        if not existing:
            existing=create_draft(tag,f"SWU Collection {metadata['version']}",notes)
        run('gh','release','upload',tag,str(payload),str(meta),'--repo',REPOSITORY,'--clobber')
        for attempt in range(8):
            draft=api(f"releases/{existing['id']}")
            assets=[a for a in draft['assets'] if a['name']=='SWU-Collection.apk']
            if len(assets)==1 and assets[0].get('digest')=='sha256:'+metadata['apk_sha256'] and assets[0]['size']==metadata['apk_size']:break
            time.sleep(3)
        else:raise RuntimeError('Uploaded bytes not verified; release remains invisible as a draft')
        assert published_metadata(draft)==metadata, 'Uploaded release metadata differs from the finalized build' 
        run('gh','release','edit',tag,'--repo',REPOSITORY,'--draft=false','--latest','--notes-file',str(notes))
        latest=api('releases/latest')
        assert latest['tag_name']==tag and not latest['draft'] and not latest['prerelease']
        print(f"Version {metadata['version']} published and available to application update checks")

if __name__=='__main__':main()
