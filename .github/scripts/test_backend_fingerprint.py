from pathlib import Path
import tempfile
import unittest
from release_common import source_fingerprint

class BackendFingerprintTests(unittest.TestCase):
    def test_sealed_backend_is_part_of_publication_identity_but_generated_resource_is_not(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'docs').mkdir()
            generated = root/'app/src/release/res/values/community_config.xml'
            generated.parent.mkdir(parents=True)
            for name in ['app/build.gradle.kts', 'app/proguard-rules.pro', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties']:
                (root/name).write_text('source')
            sealed = root/'docs/official-backend-config.enc.properties'
            sealed.write_text('authenticated-envelope-1')
            original = source_fingerprint(root)
            generated.write_text('private-generated-client-resources')
            self.assertEqual(original, source_fingerprint(root))
            sealed.write_text('authenticated-envelope-2')
            self.assertNotEqual(original, source_fingerprint(root))
