from pathlib import Path
import unittest
from release_common import release_notes

class ReleaseNotesTests(unittest.TestCase):
    def test_old_history_cannot_overflow_current_version_notes(self):
        latest='## Version 0.7.0\n\n- Brouillons vérifiés et aperçu PDF.'
        history=latest+'\n\n## Version 0.6.4\n\n'+('Anciennes nouveautés. '*1000)
        self.assertEqual(latest,release_notes(history,'0.7.0'))
    def test_selects_exact_version_and_preserves_accents(self):
        history='## Version 0.7.01\n\nAutre.\n\n## Version 0.7.0\n\nRéserve et unités.\n'
        self.assertEqual('## Version 0.7.0\n\nRéserve et unités.',release_notes(history,'0.7.0'))
    def test_missing_and_duplicate_version_fail_closed(self):
        for history in ['## Version 0.6.4\n\nAncienne.', '## Version 0.7.0\n\nUne.\n\n## Version 0.7.0\n\nDeux.']:
            with self.subTest(history=history):
                with self.assertRaises(ValueError):release_notes(history,'0.7.0')
    def test_empty_or_oversized_version_notes_fail_closed(self):
        for body in ['', ' '*20, 'x'*6000]:
            with self.subTest(size=len(body)):
                with self.assertRaises(ValueError):release_notes('## Version 0.7.0\n\n'+body,'0.7.0')
    def test_real_changelog_matches_configured_version(self):
        root=Path(__file__).resolve().parents[2]
        import re
        version=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'app/build.gradle.kts').read_text()).group(1)
        notes=release_notes((root/'RELEASE_NOTES.md').read_text(encoding='utf-8'),version)
        self.assertTrue(notes.startswith('## Version '+version+'\n'))
        self.assertLessEqual(len(notes),6000)
        self.assertEqual(1,notes.count('## Version '))

if __name__=='__main__':unittest.main()
