import unittest
import json
from unittest.mock import patch
from types import SimpleNamespace
from sync_legality import suspension_refs,legal_product_names,publish
class LegalityParserTest(unittest.TestCase):
 def test_suspensions(self):
  row='<p><a data-card="1">Card</a> (<i>Homeworlds</i>, 12)</p>'
  wrap=lambda body:'<h1>Suspended Cards</h1><p><strong>Premier:</strong></p>'+body+'<p><strong>Twin Suns:</strong></p><h2>Explanation</h2>'
  self.assertEqual([('Homeworlds','12')],suspension_refs(wrap(row)))
  self.assertEqual([],suspension_refs(wrap('<p><i>No suspended cards</i></p>')))
  for bad in ['<h1>Other page</h1>',wrap('<p>Loading...</p>'),wrap('')]:
   with self.assertRaises(AssertionError):suspension_refs(bad)
 def test_other_formats_are_isolated(self):
  html='<h1>Suspended Cards</h1><p><strong>Premier:</strong></p><p><i>No suspended cards</i></p><p><strong>Twin Suns:&nbsp;</strong></p><p><a data-card="x">X</a> (Homeworlds, 2)</p><p><strong>Eternal:&nbsp;</strong></p><p><i>No suspended cards</i></p><h2>Explanation</h2>'
  self.assertEqual([],suspension_refs(html))
  self.assertEqual([('Homeworlds','2')],suspension_refs(html,'Twin Suns'))
  self.assertEqual([],suspension_refs(html,'Eternal'))
  with self.assertRaises(AssertionError):suspension_refs(html.replace('No suspended cards','Unknown'),'Eternal')
 def test_rotation(self):
  text='The following products are legal for Premier format events:\n ● Homeworlds\n ● Intro Battle: Hoth\n48\nV 1.5 Updated: 10/9/2026\nThe following cards are suspended'
  self.assertEqual(['Homeworlds','Intro Battle: Hoth'],legal_product_names(text))
  with self.assertRaises(AssertionError):legal_product_names('Missing list')
  with self.assertRaises(ValueError):legal_product_names(text.replace('● Homeworlds','Loading...'))

 def test_large_feed_uses_stdin_not_command_arguments(self):
  feed={'legalCards':['x'*200000]}
  with patch('sync_legality.subprocess.run') as run:
   run.side_effect=[SimpleNamespace(returncode=0,stdout='{"sha":"abc"}',stderr=''),SimpleNamespace(returncode=0)]
   publish(feed)
   args,kw=run.call_args
   self.assertEqual(['--input','-'],args[0][-2:])
   self.assertEqual('abc',json.loads(kw['input'])['sha'])
   self.assertGreater(len(kw['input']),200000)

