import copy
import unittest

from sync_homeworlds_promos import parse_promos, exact_face
from card_art_identity import language_marker


def showdown_payload():
    treatments = {26: '40', 27: '45', 28: '41', 29: '42', 30: '43', 31: '44'}
    return {'meta': {'pagination': {'total': 6, 'pageCount': 1}}, 'data': [
        {'attributes': {'locale': 'fr', 'cardNumber': number, 'serialCode': 'p26' + treatment + str(number).zfill(4),
                        'title': 'Mace Windu' if number < 28 else 'Empereur Palpatine', 'cardUid': str(number),
                        'expansion': {'data': {'attributes': {'code': 'P26'}}},
                        'type': {'data': {'attributes': {'value': 'Unit'}}},
                        'artFront': {'data': {'attributes': {'url': 'https://cdn.starwarsunlimited.com/card_P26_' + str(number) + '_FR_promo.png'}}}}}
        for number, treatment in treatments.items()]}


class PromoIdentityTests(unittest.TestCase):
    def test_publisher_weekly_artwork_marker_precedes_the_title(self):
        self.assertEqual('FR', language_marker('https://cdn.starwarsunlimited.com//card_09_W600001_FR_Eravana_OP.png'))
        self.assertEqual('EN', language_marker('https://cdn.starwarsunlimited.com//card_09_W350021_EN_Eravana_OP_Foil.png'))
        self.assertIsNone(language_marker('https://cdn.starwarsunlimited.com//card_09_W350021_Frontier.png'))

    def test_six_exact_showdown_references_include_judge_and_champion(self):
        rows = parse_promos('P26', 'FR', showdown_payload())
        self.assertEqual(set(range(26, 32)), set(rows))
        self.assertEqual('Mace Windu', rows[27]['title'])
        self.assertEqual('p26440031', rows[31]['serialCode'])

    def test_partial_response_never_counts_as_complete(self):
        payload = showdown_payload()
        payload['data'].pop()
        with self.assertRaises(ValueError):
            parse_promos('P26', 'FR', payload)

    def test_same_number_from_another_treatment_or_locale_is_rejected(self):
        for field, value in [('serialCode', 'p26600026'), ('locale', 'en')]:
            payload = showdown_payload()
            payload['data'][0]['attributes'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                parse_promos('P26', 'FR', payload)

    def test_foreign_or_english_artwork_cannot_be_saved_as_french(self):
        for url in ('https://example.com/card_FR.png', 'https://cdn.starwarsunlimited.com/card_P26_26_EN_promo.png'):
            payload = showdown_payload()
            payload['data'][0]['attributes']['artFront']['data']['attributes']['url'] = url
            with self.subTest(url=url), self.assertRaises(ValueError):
                parse_promos('P26', 'FR', payload)

    def test_english_publisher_placeholder_is_never_labeled_italian(self):
        card = showdown_payload()['data'][0]['attributes']
        card['artFront']['data']['attributes']['url'] = 'https://cdn.starwarsunlimited.com/card_P26_26_EN_promo.png'
        self.assertEqual('', exact_face(card, 'artFront', 'IT'))

    def test_duplicate_reference_cannot_hide_a_missing_reward(self):
        payload = showdown_payload()
        payload['data'][-1] = copy.deepcopy(payload['data'][0])
        with self.assertRaises(ValueError):
            parse_promos('P26', 'FR', payload)


if __name__ == '__main__':
    unittest.main()
