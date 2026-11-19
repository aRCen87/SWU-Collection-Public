import unittest
import xml.etree.ElementTree as ET
from ui_probe_common import reader_page


class ReaderPageTests(unittest.TestCase):
    def test_fold_ignores_collection_progress_beside_the_reader(self):
        root = ET.fromstring('<hierarchy><node text="0 / 566"/><node resource-id="swu_detail_pane"><node text="1 / 9065"/></node></hierarchy>')
        self.assertEqual("1 / 9065", reader_page(root))

    def test_phone_dialog_keeps_its_reader_count(self):
        root = ET.fromstring('<hierarchy><node text="2 / 9065"/></hierarchy>')
        self.assertEqual("2 / 9065", reader_page(root))

    def test_duplicate_counts_inside_the_reader_fail(self):
        root = ET.fromstring('<hierarchy><node resource-id="swu_detail_pane"><node text="1 / 9065"/><node text="2 / 9065"/></node></hierarchy>')
        with self.assertRaises(AssertionError):
            reader_page(root)

    def test_missing_or_invalid_reader_counts_fail(self):
        for xml in ('<hierarchy/>', '<hierarchy><node text="0 / 566"/></hierarchy>', '<hierarchy><node text="3 / 2"/></hierarchy>'):
            with self.subTest(xml=xml), self.assertRaises(AssertionError):
                reader_page(ET.fromstring(xml))
