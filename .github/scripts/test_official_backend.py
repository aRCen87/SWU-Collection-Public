import tempfile
from pathlib import Path
import unittest
from check_official_backend import verify

class OfficialBackendTests(unittest.TestCase):
    def test_missing_and_cross_channel_configuration_cannot_be_released(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.xml'
            for project, key, app in [('swu-collection-test','key','app'), ('swu-collection-official','','app')]:
                path.write_text(f'<resources><string name="swu_official_project_id">{project}</string><string name="swu_official_api_key">{key}</string><string name="swu_official_app_id">{app}</string></resources>')
                with self.assertRaises(ValueError): verify(path)
            path.write_text('<resources><string name="swu_official_project_id">swu-collection-official</string><string name="swu_official_api_key">key</string><string name="swu_official_app_id">app</string></resources>')
            verify(path)
    def test_official_rules_preserve_all_test_security_checks(self):
        root = Path(__file__).resolve().parents[2]
        self.assertEqual((root/'social/official/firestore.rules').read_text(), (root/'social/firestore.rules').read_text().replace('testMembers','members'))
