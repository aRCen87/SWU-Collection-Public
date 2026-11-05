"""Validate shipped visual identities without downloading images during CI."""
import base64
import gzip
import json
from pathlib import Path
import unittest


class VisualIndexTest(unittest.TestCase):
    def test_all_catalogue_references_have_valid_identity_checked_signatures(self):
        assets = Path(__file__).resolve().parents[2] / 'app/src/main/assets'
        catalogue = json.loads((assets / 'catalog-fr.json').read_text())['cards']
        art = json.loads(gzip.decompress((assets / 'card-art-languages.json.gz').read_bytes()))
        index = json.loads(gzip.decompress((assets / 'scan-visual-index.json.gz').read_bytes()))
        self.assertEqual('SWU_VISUAL_INDEX', index['format'])
        self.assertEqual('area-v1', index['sampling'])
        expected = {(c['s'], c['n']) for c in catalogue}
        covered = set()
        for row in index['entries']:
            code, number, name, url, face, landscape, encoded = row
            self.assertIn((code, number), expected)
            self.assertIn(face, (0, 1))
            self.assertIsInstance(landscape, bool)
            self.assertTrue(name)
            self.assertEqual(288, len(base64.b64decode(encoded, validate=True)))
            localized = art['cards'][code + ':' + number]
            self.assertIn(url, [art['urls'][pair[face]] for pair in localized.values()])
            covered.add((code, number))
        # Future source outages may leave a small number readable by OCR only.
        self.assertGreaterEqual(len(covered), len(expected) * .98)


if __name__ == '__main__':
    unittest.main()
