from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import publish_release
from release_common import validate,source_fingerprint,MAX_BYTES

class ReleaseMetadataTests(unittest.TestCase):
    def metadata(self):
        return {'version':'0.3.4','version_code':7,'package':'fr.swucollection.app','apk_size':100,
                'apk_sha256':'a'*64,'source_fingerprint':'b'*64,'notes':'Mises à jour et réglages préservés.'}
    def test_accepts_only_explicit_verified_metadata(self):
        self.assertEqual('0.3.4',validate(self.metadata())['version'])
    def test_refuses_invalid_versions_packages_hashes_and_sizes(self):
        for key,value in [('version','0.3.4-beta'),('version_code',0),('package','other.app'),('apk_sha256','bad'),('apk_size',MAX_BYTES+1)]:
            with self.subTest(key=key):
                data=self.metadata();data[key]=value
                with self.assertRaises(AssertionError):validate(data)
    def test_source_fingerprint_ignores_builds_tests_and_notes_but_tracks_app_changes(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'app/src/main').mkdir(parents=True);(root/'app/src/test').mkdir(parents=True);(root/'app/build').mkdir()
            app=root/'app/src/main/data.txt';app.write_text('Rébellion',encoding='utf-8');first=source_fingerprint(root)
            (root/'app/build/output.apk').write_bytes(b'build');(root/'RELEASE_NOTES.md').write_text('Notes');(root/'app/src/test/test.kt').write_text('Test')
            self.assertEqual(first,source_fingerprint(root))
            app.write_text('Rebellion',encoding='utf-8');self.assertNotEqual(first,source_fingerprint(root))

    def test_empty_git_repository_is_a_bootstrap_case_not_an_authorization_failure(self):
        response=SimpleNamespace(returncode=1,stdout='',stderr='gh: Git Repository is empty. (HTTP 409)')
        with patch('publish_release.subprocess.run',return_value=response):
            self.assertIsNone(publish_release.api('git/ref/heads/main',optional=True))
    def test_denied_repository_access_is_never_treated_as_empty(self):
        response=SimpleNamespace(returncode=1,stdout='',stderr='gh: Resource not accessible (HTTP 403)')
        with patch('publish_release.subprocess.run',return_value=response):
            with self.assertRaisesRegex(RuntimeError,'403'):publish_release.api('git/ref/heads/main',optional=True)
    def test_other_conflicts_are_not_treated_as_empty(self):
        response=SimpleNamespace(returncode=1,stdout='',stderr='gh: Conflict (HTTP 409)')
        with patch('publish_release.subprocess.run',return_value=response):
            with self.assertRaisesRegex(RuntimeError,'409'):publish_release.api('releases/latest',optional=True)

    def test_drafts_are_found_by_release_listing_without_a_published_git_tag(self):
        draft={'id':123,'tag_name':'v0.3.4','draft':True}
        with patch('publish_release.api',return_value=[draft]) as api:
            self.assertEqual(draft,publish_release.release_by_tag('v0.3.4'))
            api.assert_called_once_with('releases?per_page=100&page=1')
    def test_absent_release_is_not_confused_with_an_existing_draft(self):
        with patch('publish_release.api',return_value=[]):
            self.assertIsNone(publish_release.release_by_tag('v0.3.5'))

    def test_created_draft_uses_post_response_without_waiting_for_release_listing(self):
        with patch('publish_release.run',return_value='{"id":123,"tag_name":"v0.6.2","draft":true}') as run:
            with patch('publish_release.release_by_tag',side_effect=AssertionError('Listing is stale')):
                self.assertEqual(123,publish_release.create_draft('v0.6.2','Version',Path('notes.md'))['id'])
            self.assertIn('draft=true',run.call_args.args)
            self.assertIn('body=@notes.md',run.call_args.args)
    def test_draft_creation_refuses_unexpected_or_already_public_response(self):
        for response in ['{"id":123,"tag_name":"v0.6.2","draft":false}',
            '{"id":123,"tag_name":"v0.5.0","draft":true}']:
            with patch('publish_release.run',return_value=response):
                with self.assertRaises(AssertionError):publish_release.create_draft('v0.6.2','Version',Path('notes.md'))

if __name__=='__main__':unittest.main()
