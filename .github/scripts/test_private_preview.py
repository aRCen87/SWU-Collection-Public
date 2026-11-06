import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
class PrivatePreviewTests(unittest.TestCase):
    def test_preview_has_separate_package_and_identity(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('applicationIdSuffix = ".test"',gradle)
        app=ET.parse(ROOT/'app/src/preview/AndroidManifest.xml').getroot().find('application')
        ns='{http://schemas.android.com/apk/res/android}'
        self.assertEqual(app.get(ns+'label'),'SWU Collection Test')
        self.assertEqual(app.get(ns+'allowBackup'),'false')
        self.assertEqual(app.get(ns+'icon'),'@mipmap/ic_launcher_test')
        main=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('${applicationId}.updates',main)
    def test_official_publication_requires_explicit_matching_approval(self):
        workflow=(ROOT/'.github/workflows/android.yml').read_text()
        self.assertIn("inputs.publish_official == true",workflow)
        self.assertIn("inputs.approved_sha == github.sha",workflow)
        self.assertIn("github.event_name == 'workflow_dispatch'",workflow)
