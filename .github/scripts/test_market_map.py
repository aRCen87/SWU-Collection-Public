import unittest
from build_market_map import build, normalized

class MarketMapTests(unittest.TestCase):
    def fixtures(self):
        catalogue = {'cards':[{'s':'SOR','n':'001','name':'Directeur Krennic','sub':'Aspire Au Pouvoir','type':'Leader'}]}
        def source(name, en, kind):
            return {'set':{'code':'SOR'},'number':'1','serial_code':'0101001','type':kind,
                    'name':{'fr':name,'en':en},'subtitle':{'fr':'Aspire Au Pouvoir','en':'Aspiring to Authority'}}
        cards = [source('Directeur Krennic','Director Krennic','Leader'),source('Expérience','Experience','Token')]
        products = [{'idProduct':754505,'idCategory':1644,'idExpansion':5618,'idMetacard':1,
                     'name':'Director Krennic, Aspiring to Authority'}]
        return catalogue, cards, products
    def test_number_collisions_do_not_replace_krennic_with_a_token(self):
        c,s,p = self.fixtures(); result,_ = build(c,s,[],p)
        self.assertEqual([754505], result['SOR:001']['products'])
    def test_different_french_identity_is_not_linked(self):
        c,s,p = self.fixtures();c['cards'][0]['name']='Dark Vador'
        self.assertEqual({},build(c,s,[],p)[0])
    def test_premium_printings_do_not_inherit_standard_prices(self):
        c,s,p = self.fixtures();s[0]['serial_code']='0103001'
        self.assertEqual({},build(c,s,[],p)[0])
    def test_french_unicode_is_preserved(self):
        self.assertEqual(normalized('Rébellion'),normalized('Re\u0301bellion'))
        self.assertNotEqual(normalized('Rébellion'),normalized('Rebellion'))
    def test_different_metacards_are_not_guessed(self):
        c,s,p = self.fixtures();p.append(dict(p[0],idProduct=2,idMetacard=2))
        self.assertEqual({},build(c,s,[],p)[0])
    def test_other_expansion_is_never_linked(self):
        c,s,p = self.fixtures();p[0]['idExpansion']=5781
        self.assertEqual({},build(c,s,[],p)[0])

if __name__ == '__main__':
    unittest.main()
