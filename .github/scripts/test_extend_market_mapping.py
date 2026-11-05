import unittest
from extend_market_mapping import extend_mapping


class FinishMappingTests(unittest.TestCase):
    def fixtures(self):
        catalogue={'cards':[{'s':'SOR','n':n,'name':'Carte','sub':'','type':'Unité'}
                            for n in ('001','253','500')]}
        seed={'SOR:001':{'nameFr':'Carte','nameEn':'Card','products':[1],'range':False}}
        details={key:{'family':'same-card','en':'Card'} for key in ('SOR:001','SOR:253','SOR:500')}
        finishes={'SOR:001':['Normale','Foil'],'SOR:253':['Hyperspace','Hyperspace Foil'],
                  'SOR:500':['Foil']}
        products=[{'idProduct':1,'idCategory':1644,'idExpansion':5618,'idMetacard':10,'name':'Card'},
                  {'idProduct':2,'idCategory':1644,'idExpansion':5638,'idMetacard':10,'name':'Card'}]
        return catalogue,seed,details,finishes,products

    def test_distinct_foil_keeps_reference_and_foil_field(self):
        result,_=extend_mapping(*self.fixtures())
        self.assertEqual('SOR:001',result['SOR:500']['sourceReference'])
        self.assertEqual({'Foil':{'products':[1],'field':'trend-foil','range':False}},
                         result['SOR:500']['finishes'])

    def test_only_one_premium_treatment_family_can_be_joined(self):
        c,s,d,f,p=self.fixtures()
        result,_=extend_mapping(c,s,d,f,p)
        self.assertEqual([2],result['SOR:253']['finishes']['Hyperspace']['products'])
        c['cards'].append(dict(c['cards'][1],n='600'))
        d['SOR:600']=dict(d['SOR:253']);f['SOR:600']=['Showcase']
        result,_=extend_mapping(c,s,d,f,p)
        self.assertNotIn('SOR:253',result)
        self.assertNotIn('SOR:600',result)

    def test_same_name_with_different_metacard_is_rejected(self):
        c,s,d,f,p=self.fixtures();p[1]['idMetacard']=99
        self.assertNotIn('SOR:253',extend_mapping(c,s,d,f,p)[0])

    def test_wrong_extras_expansion_is_rejected(self):
        c,s,d,f,p=self.fixtures();p[1]['idExpansion']=5781
        self.assertNotIn('SOR:253',extend_mapping(c,s,d,f,p)[0])

    def test_regeneration_does_not_reseed_premium_as_normal(self):
        c,s,d,f,p=self.fixtures()
        once,_=extend_mapping(c,s,d,f,p)
        twice,_=extend_mapping(c,once,d,f,p)
        self.assertEqual(once,twice)
