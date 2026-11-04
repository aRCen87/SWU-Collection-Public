import unittest
from card_art_identity import language_marker

class LanguageMarkerTest(unittest.TestCase):
    def test_french_words_are_not_language_markers(self):
        self.assertEqual('FR',language_marker('https://cdn.starwarsunlimited.com/card_SWH_01fr_229_Garde_du_Bloc_de_Detention.png'))
        self.assertIsNone(language_marker('https://cdn.starwarsunlimited.com/card_SWOP_0103_002_A_Wing_de_l_Escadron.png'))
    def test_old_english_filename_does_not_become_italian_because_of_the_title(self):
        self.assertEqual('EN',language_marker('https://cdn.starwarsunlimited.com/card_SWH_01_141_Make_It_Happen.png'))
    def test_markers_before_the_title_are_exact(self):
        for language in ('FR','EN','DE','ES','IT'):
            self.assertEqual(language,language_marker('https://cdn.starwarsunlimited.com/card_09010001_'+language+'_Asajj.png'))
            self.assertEqual(language,language_marker('https://cdn.starwarsunlimited.com/card_SWH_01'+language.lower()+'_005_Luke.png'))
