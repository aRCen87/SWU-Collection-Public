import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prepare', Path(__file__).with_name('prepare.py'))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

class PlaystorePreparationTest(unittest.TestCase):
    def test_complete_project_is_isolated_and_original_sources_unchanged(self):
        files = [p for p in (prepare.ROOT / 'app').rglob('*') if p.is_file() and 'build' not in p.parts]
        before = {p: hashlib.sha256(p.read_bytes()).digest() for p in files}
        with tempfile.TemporaryDirectory() as temporary:
            out = prepare.prepare(Path(temporary) / 'source')
            prepare.audit(out)
            main = (out / 'app/src/main/java/fr/swucollection/app/MainActivity.kt').read_text()
            self.assertIn('PlaystoreHomeControls(', main)
            self.assertIn('PortableBackupPanel(', main)
            self.assertIn('CompetitiveHub(', main)
            self.assertNotIn('Mon compte', main)
            self.assertNotIn('Partager à des amis', main)
            tests = out / 'app/src/test/java/fr/swucollection/app'
            for name in ('BackupReliabilityTest', 'DeckBackupTest', 'PlaystoreIsolationTest'):
                self.assertTrue((tests / (name + '.kt')).is_file(), name)
            self.assertGreater(len(list(tests.glob('*.kt'))), 60)
            self.assertEqual(before, {p: hashlib.sha256(p.read_bytes()).digest() for p in files})

    def test_refuses_to_overwrite_existing_project(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ValueError):
                prepare.prepare(temporary)

    def test_source_drift_stops_preparation(self):
        with self.assertRaises(ValueError):
            prepare.once('changed', 'expected')
        with self.assertRaises(ValueError):
            prepare.once('duplicate duplicate', 'duplicate')

if __name__ == '__main__':
    unittest.main()
