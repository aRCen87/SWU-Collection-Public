"""Recognize only publisher language markers before the card title."""
import re

def language_marker(value):
 filename=value.rsplit('/',1)[-1]
 patterns=(r'^card_SWH_\d{2}(FR|EN|DE|ES|IT)_',r'^card_\d+(FR|EN|DE|ES|IT)_',r'^card_[^_]+_(?:\d+_)?(FR|EN|DE|ES|IT)_')
 for pattern in patterns:
  match=re.match(pattern,filename,re.I)
  if match:return match.group(1).upper()
 if re.match(r'^card_SWH_\d{2}_',filename,re.I):return 'EN'
 return None
