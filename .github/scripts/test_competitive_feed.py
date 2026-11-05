import unittest
from datetime import datetime, timezone
from build_competitive_feed import build, portable, english_aliases, identity_name

class CompetitiveFeedTest(unittest.TestCase):
    def setUp(self):
        self.cards = {'SOR-001':{'type':'Leader','name':'Leader français'}, 'SOR-020':{'type':'Base','name':'Base'}, 'SOR-100':{'type':'Unité','name':'Unité'}}
        self.rows = [{'collector_number':'SOR_001','count':1,'type':'Leader'}, {'collector_number':'SOR_020','count':1,'type':'Base'}, {'collector_number':'SOR_100','count':50,'type':'Ground Unit'}, {'collector_number':'SOR_100','count':2,'sideboard':True}]
        self.event = {'uuid':'event','name':'SQ test','date':'2026-11-01T00:00:00Z','tier':'SQ','format':'Premier','status':'completed'}
        self.deck = {'uuid':'deck','tournament_uuid':'event','account_uuid':'account','decklist':self.rows}
    def test_rows_and_sideboard_survive_exactly(self):
        deck = portable(self.rows,self.cards)
        self.assertEqual(deck['deck'],[{'id':'SOR-100','count':50}])
        self.assertEqual(deck['sideboard'],[{'id':'SOR-100','count':2}])
    def test_missing_unknown_or_fractional_row_rejects_entire_list(self):
        for row in [{'collector_number':'SOR_999','count':1},{'collector_number':'SOR_100','count':1.5},{'collector_number':'SOR_100','count':True}]:
            with self.assertRaises(ValueError): portable(self.rows+[row],self.cards)
        with self.assertRaises(ValueError): portable(self.rows[1:],self.cards)
    def test_only_current_completed_premier_and_verified_final_placement(self):
        standings=[{'tournament_uuid':'event','account_uuid':'wrong','rank':1,'final_placement':1}, {'tournament_uuid':'event','account_uuid':'account','rank':1,'final_placement':5}]
        result=build([self.event,dict(self.event,uuid='future',date='2027-01-01',status='upcoming')],[self.deck],standings,self.cards,datetime(2026,11,5,tzinfo=timezone.utc))
        self.assertEqual(result['listCount'],1)
        self.assertEqual(result['decks'][0]['placement'],5)
        self.assertNotIn('player',result['decks'][0]); self.assertNotIn('account',result['decks'][0])
    def test_unverified_placement_is_never_invented(self):
        result=build([self.event],[self.deck],[{'tournament_uuid':'event','account_uuid':'account','rank':1}],self.cards,datetime(2026,11,5,tzinfo=timezone.utc))
        self.assertNotIn('placement',result['decks'][0])
    def test_empty_source_cannot_replace_published_selection(self):
        with self.assertRaises(AssertionError): build([],[],[],self.cards,datetime(2026,11,5,tzinfo=timezone.utc))

    def test_name_only_source_uses_unique_full_english_identity_and_keeps_reserve(self):
        aliases={identity_name('Unité, Sous-titre'):'SOR-100'}
        rows=self.rows[:2]+[{'name':'Unité, Sous-titre','count':50},{'id':'SOR_100','count':2,'sideboard':True}]
        deck=portable(rows,self.cards,aliases)
        self.assertEqual(deck['deck'][0]['count'],50)
        self.assertEqual(deck['sideboard'][0]['count'],2)
        with self.assertRaises(ValueError): portable(self.rows[:2]+[{'name':'Unité','count':50}],self.cards,aliases)
    def test_same_title_in_distinct_families_is_never_guessed(self):
        aliases=english_aliases(self.cards,{'SOR:001':{'en':'Ambiguous','family':'a'},'SOR:100':{'en':'Ambiguous','family':'b'}},{})
        self.assertEqual(aliases,{})
