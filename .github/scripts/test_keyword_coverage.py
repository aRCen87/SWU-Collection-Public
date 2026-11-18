"""Catalogue integration regression: indexing must retain organised-play prints."""
import gzip
import json
import unittest
from pathlib import Path

class KeywordCoverageTests(unittest.TestCase):
    def test_organised_play_and_showdown_keep_their_facets_and_searchable_rules(self):
        source = Path(__file__).resolve().parents[2] / 'app/src/main/assets/card-details.json.gz'
        cards = json.loads(gzip.decompress(source.read_bytes()))['cards']
        required = [f'HMWP:{n:03}' for n in range(1, 41)] + [f'P26:{n:03}' for n in range(26, 32)]
        for reference in required:
            with self.subTest(reference=reference):
                row = cards[reference]
                self.assertTrue(row['family'])
                self.assertIsInstance(row['aspects'], list)
                self.assertIsInstance(row['traits'], list)
                self.assertTrue(row.get('searchText', '').strip())

if __name__ == '__main__':
    unittest.main()
