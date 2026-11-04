#!/usr/bin/env python3
"""Exact publisher artwork per collector reference and language. No URL substitutions."""
import argparse,collections,concurrent.futures,gzip,json,pathlib,re,urllib.parse,urllib.request,unicodedata
LANGUAGES=('FR','EN','DE','ES','IT')
p=argparse.ArgumentParser()
p.add_argument('--catalog',default='app/src/main/assets/catalog-fr.json')
p.add_argument('--source-cache')
p.add_argument('--official-cache',default='.art-source-cache')
p.add_argument('--output',default='app/src/main/assets/card-art-languages.json.gz')
a=p.parse_args()
def fetch(url):
 return json.load(urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'SWU-Collection/1.0','Accept':'application/json'}),timeout=45))
source=json.loads(pathlib.Path(a.source_cache).read_text()) if a.source_cache else fetch('https://raw.githubusercontent.com/Team-Zura/swu-cards-json/main/data/v1/all-cards.json')
catalog=json.loads(pathlib.Path(a.catalog).read_text())['cards']
byref={c['s']+':'+c['n'].zfill(3):c for c in catalog}
expected={'Unité':'Unit','Événement':'Event','Évènement':'Event','Amélioration':'Upgrade','Base':'Base','Leader':'Leader','Jeton':'Token'}
rows={key:{} for key in byref}
print_art={}
choices=collections.defaultdict(set)
trusted_uids=collections.defaultdict(set)
unavailable=set()
TREATMENTS={'01':'Normale','02':'Hyperspace','03':'Showcase','31':'Foil','32':'Hyperspace Foil','06':'Prestige','33':'Prestige Foil','34':'Serialized','36':'Sérialisée or rose','37':'Sérialisée or'}
EXPANSIONS={'SOR':'01','SHD':'02','TWI':'03','JTL':'04','LOF':'05','SEC':'06','LAW':'07','ASH':'08','HMW':'09'}
def norm(value):return re.sub('[^a-z0-9]','',unicodedata.normalize('NFKD',value or '').encode('ascii','ignore').decode().lower())
def finish(code,serial):
 serial=str(serial or '')
 return TREATMENTS.get(serial[2:4]) if serial.startswith(EXPANSIONS.get(code,'!')) else None

cache=pathlib.Path(a.official_cache);cache.mkdir(parents=True,exist_ok=True)
def url(value):
 return value if isinstance(value,str) and value.startswith('https://cdn.starwarsunlimited.com/') else ''
from card_art_identity import language_marker

def add(key,kind,lang,front,back,french_proof='',treatment=None):
 c=byref.get(key)
 if not c:return
 # A mirror has one upgrade mistyped as a unit: an identical French art URL proves its identity.
 if expected.get(c['type'])!=kind and french_proof!=c.get('imageUrl'):return
 front,back=url(front),url(back)
 marker=language_marker(front)
 if marker and marker!=lang:
  unavailable.add((key,lang));return
 old=rows[key].get(lang)
 if old is None or (bool(front),bool(back))>(bool(old[0]),bool(old[1])):rows[key][lang]=[front,back]
 if treatment:
  choices[key].add(treatment)
  print_art.setdefault(key,{}).setdefault(treatment,{})[lang]=[front,back]
for c in source:
 code=(c.get('set')or{}).get('code','');key=code+':'+str(c.get('number','')).zfill(3)
 canonical=byref.get(key)
 if not canonical:continue
 proof=(c.get('front_image_url')or{}).get('fr')==canonical.get('imageUrl')
 title=norm((c.get('name')or{}).get('fr'))==norm(canonical['name'])
 subtitle=norm((c.get('subtitle')or{}).get('fr'))==norm(canonical.get('sub',''))
 if not (proof or (title and subtitle)):continue
 if c.get('api_id'):trusted_uids[key].add(str(c['api_id']))
 for lang in LANGUAGES:
  add(key,c.get('type'),lang,(c.get('front_image_url')or{}).get(lang.lower()),(c.get('back_image_url')or{}).get(lang.lower()),(c.get('front_image_url')or{}).get('fr'),finish(code,c.get('serial_code')))
def official(code,lang,page):
 f=cache/(code+'-'+lang.lower()+'-'+str(page)+'.json')
 if f.exists():return json.loads(f.read_text())
 q=urllib.parse.urlencode({'locale':lang.lower(),'filters[expansion][code][$eq]':code,'pagination[page]':str(page),'pagination[pageSize]':'100'})
 result=fetch('https://admin.starwarsunlimited.com/api/card-list?'+q)
 f.write_text(json.dumps(result));return result

def add_official(code,lang,payload,only_number=None):
 for row in payload.get('data',[]):
  c=row.get('attributes',row)
  def rel(field):return ((c.get(field)or{}).get('data')or{}).get('attributes')or{}
  number=str(c.get('cardNumber','')).zfill(3)
  if c.get('locale')!=lang.lower() or rel('expansion').get('code')!=code or (only_number and number!=only_number):continue
  def face(field):
   obj=rel(field);return ((obj.get('formats')or{}).get('card')or{}).get('url') or obj.get('url') or ''
  key=code+':'+number
  if trusted_uids.get(key) and str(c.get('cardUid')) not in trusted_uids[key]:continue
  canonical=byref.get(key)
  if not canonical:continue
  french=[loc.get('attributes',loc) for loc in (c.get('localizations')or{}).get('data',[]) if loc.get('attributes',loc).get('locale')=='fr']
  if french:
   if not any(norm(loc.get('title'))==norm(canonical['name']) and norm(loc.get('subtitle'))==norm(canonical.get('sub','')) for loc in french):continue
  elif not trusted_uids.get(key):continue
  kind=rel('type').get('name')
  # Publisher type labels are localized. Their invariant abbreviations identify leaders/tokens.
  kind_map={'Anführer':'Leader','Anführer-Einheit':'Leader','Einheit':'Unit','Aufwertung':'Upgrade','Ereignis':'Event','Basis':'Base',
            'Líder':'Leader','Unidad':'Unit','Mejora':'Upgrade','Evento':'Event','Base':'Base',
            'Leader':'Leader','Unità':'Unit','Miglioria':'Upgrade','Evento':'Event','Segnalino':'Token'}
  kind=kind_map.get(kind,kind)
  add(code+':'+number,kind,lang,face('artFront'),face('artBack'),treatment=finish(code,c.get('serialCode')))
# HMW is not yet in the community mirror. Download the complete localized publisher sets.
for lang in LANGUAGES[1:]:
 first=official('HMW',lang,1);meta=first.get('meta',{}).get('pagination',{})
 with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
  batches=[first]+list(pool.map(lambda page:official('HMW',lang,page),range(2,meta.get('pageCount',0)+1)))
 assert sum(len(b.get('data',[])) for b in batches)==meta.get('total') and meta.get('total',0)>900,'Incomplete HMW '+lang
 for payload in batches:add_official('HMW',lang,payload)
# Prefer the validated French catalogue for the French face (corrected identities included).
for key,c in byref.items():
 faces=[]
 for field in ('imageUrl','backImageUrl'):
  value=url(c.get(field,''));marker=language_marker(value)
  faces.append('' if marker not in (None,'FR') else value)
 rows[key]['FR']=faces
# Exact localized reference lookups fill any mirror omissions. Unavailable art stays explicitly empty.
for key in byref:
 for lang in LANGUAGES[1:]:
  if rows[key].get(lang,[''])[0] or (key,lang) in unavailable:continue
  code,number=key.split(':');f=cache/('missing-'+code+'-'+number+'-'+lang.lower()+'.json')
  if f.exists():payload=json.loads(f.read_text())
  else:
   q=urllib.parse.urlencode({'locale':lang.lower(),'filters[expansion][code][$eq]':code,'filters[cardNumber][$eq]':str(int(number)),'pagination[pageSize]':'100'})
   payload=fetch('https://admin.starwarsunlimited.com/api/card-list?'+q);f.write_text(json.dumps(payload))
  add_official(code,lang,payload,number)
for faces in rows.values():
 for lang in LANGUAGES:faces.setdefault(lang,['',''])
# Only shared-reference finishes need extra artwork; unique references already have exact faces.
print_art={key:variants for key,variants in print_art.items() if len(choices.get(key,()))>1}
for variants in print_art.values():
 for faces in variants.values():
  for lang in LANGUAGES:faces.setdefault(lang,['',''])
dictionary={'':0}
def packed_faces(faces):
 def idx(value):
  if value not in dictionary:dictionary[value]=len(dictionary)
  return dictionary[value]
 return {lang:[idx(url) for url in pair] for lang,pair in faces.items()}
packed_cards={key:packed_faces(faces) for key,faces in sorted(rows.items())}
packed_prints={key:{kind:packed_faces(faces) for kind,faces in variants.items()} for key,variants in print_art.items()}
result={'format':'SWU_CARD_ART_LANGUAGES','version':1,'languages':LANGUAGES,'source':'Exact publisher URLs from identity-checked localized records','urls':list(dictionary),'cards':packed_cards,'printArt':packed_prints}
out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True)
out.write_bytes(gzip.compress(json.dumps(result,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
finishes={'format':'SWU_PRINT_FINISHES','version':1,'cards':{k:sorted(v) for k,v in sorted(choices.items())}}
finish_path=out.with_name('card-print-finishes.json.gz')
finish_path.write_bytes(gzip.compress(json.dumps(finishes,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
print(json.dumps({'references':len(rows),'with_art':{lang:sum(bool(v[lang][0]) for v in rows.values()) for lang in LANGUAGES},'shared_reference_prints':len(print_art),'gzip_bytes':out.stat().st_size}))
