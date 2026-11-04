import unittest
from build_card_details import build

class CardIdentityTests(unittest.TestCase):
    def test_promo_with_same_set_number_never_overwrites_classic_leader(self):
        catalog={'cards':[{'s':'SOR','n':'001','name':'Directeur Krennic','sub':'Aspire au pouvoir',
            'imageUrl':'https://cdn.starwarsunlimited.com/krennic.png'}]}
        classic={'set':{'code':'SOR'},'number':'1','name':{'en':'Director Krennic','fr':'Directeur Krennic'},
            'subtitle':{'en':'Aspiring to Authority','fr':'Aspire au pouvoir'},'aspects':['Vigilance','Villainy'],
            'front_image_url':{'fr':catalog['cards'][0]['imageUrl']}}
        promo={'set':{'code':'SOR'},'number':'1','name':{'en':'Darth Vader'},
            'subtitle':{'en':'Dark Lord of the Sith'},'front_image_url':{'en':'https://cdn.starwarsunlimited.com/promo.png'}}
        for rows in ([classic,promo],[promo,classic]):
            result=build(catalog,rows,[])
            self.assertEqual('Director Krennic — Aspiring to Authority',result['SOR:001']['en'])
            self.assertEqual(['Vigilance','Villainy'],result['SOR:001']['aspects'])
    def test_same_title_different_subtitle_is_not_a_match(self):
        catalog={'cards':[{'s':'SOR','n':'001','name':'Dark Vador','sub':'Seigneur noir des Sith'}]}
        wrong={'set':{'code':'SOR'},'number':'1','name':{'en':'Darth Vader','fr':'Dark Vador'},
            'subtitle':{'en':'Commanding the First Legion','fr':'Commandant de la Première Légion'}}
        self.assertEqual({},build(catalog,[wrong],[]))

    def test_official_token_number_does_not_replace_a_different_leader(self):
        catalog={'cards':[{'s':'HMW','n':'001','name':'Asajj Ventress','sub':"L’Heure N’Est Pas Aux Regrets"}]}
        def row(fr,sub,en):
            return {'attributes':{'cardNumber':1,'title':fr,'subtitle':sub,'cost':0,
                'localizations':{'data':[{'attributes':{'locale':'en','title':en,'subtitle':''}}]}}}
        leader=row('Asajj Ventress',"L'Heure N'Est Pas Aux Regrets",'Asajj Ventress')
        token=row('Bouclier','','Shield')
        for rows in ([leader,token],[token,leader]):
            result=build(catalog,[],rows)
            self.assertEqual('Asajj Ventress',result['HMW:001']['en'])

if __name__=='__main__':unittest.main()

