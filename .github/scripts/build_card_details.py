#!/usr/bin/env python3
"""Build compact, offline card facets/families from publisher-linked identities."""
import concurrent.futures, gzip, hashlib, json, math, re, unicodedata, urllib.parse, urllib.request
from pathlib import Path

def norm(value):
    return ' '.join(unicodedata.normalize('NFC', value or '').replace('’', "'").casefold().split())
def family(name, subtitle):
    return hashlib.sha256((norm(name)+'|'+norm(subtitle)).encode()).hexdigest()[:32]
def download(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'SWU-Collection-details/1.0'}),timeout=90) as r:
        return json.load(r)
def build(catalog, upstream, official):
    allowed={c['s']+':'+str(c['n']).zfill(3) for c in catalog['cards']}
    catalog_by_key={c['s']+':'+str(c['n']).zfill(3):c for c in catalog['cards']}
    result={}
    for c in upstream:
        code=c['set']['code']; key=code+':'+str(c['number']).zfill(3)
        if key not in allowed: continue
        # Promo numbers restart at 1 inside the same expansion upstream. Anchor
        # an identity to the actual catalogue print, never the bare number alone.
        target=catalog_by_key[key]
        source_images={url for field in ('front_image_url','back_image_url')
            for url in (c.get(field) or {}).values() if url}
        target_images={target.get('imageUrl'),target.get('backImageUrl')}-{None,''}
        same_name=norm((c.get('name') or {}).get('fr'))==norm(target.get('name'))
        same_subtitle=norm((c.get('subtitle') or {}).get('fr'))==norm(target.get('sub'))
        if not (source_images & target_images) and not (same_name and same_subtitle): continue
        en=(c.get('name')or{}).get('en'); sub=(c.get('subtitle')or{}).get('en','')
        if not en: continue
        cost=c.get('cost'); cost=cost if isinstance(cost,int) and 0<=cost<=99 else None
        traits=(c.get('traits')or{}).get('fr','') if isinstance(c.get('traits'),dict) else ''
        result[key]={'family':family(en,sub),'en':en+(' — '+sub if sub else ''),'cost':cost,
            'aspects':c.get('aspects')or[],'traits':[x.strip() for x in traits.split(',') if x.strip()],
            'arena':c.get('arena')or''}
    for row in official:
        c=row.get('attributes',row)
        code=(((c.get('expansion')or{}).get('data')or{}).get('attributes')or{}).get('code','HMW')
        key=code+':'+str(c['cardNumber']).zfill(3)
        if key not in allowed: continue
        # Tokens restart numbering within an expansion. Never let Shield/Weakness/
        # Beast replace an unrelated numbered leader in the collectible catalogue.
        target=catalog_by_key[key]
        if not (norm(c.get('title'))==norm(target.get('name')) and
                norm(c.get('subtitle'))==norm(target.get('sub'))): continue
        locales=(c.get('localizations')or{}).get('data')or[]
        en=next((x.get('attributes',{}) for x in locales if x.get('attributes',{}).get('locale')=='en'),None)
        if not en: continue
        def rel(field): return [x.get('attributes',{}) for x in (c.get(field)or{}).get('data',[]) or []]
        arena=next(iter(rel('arenas')),{}).get('englishName') or next(iter(rel('arenas')),{}).get('name','')
        result[key]={'family':family(en['title'],en.get('subtitle')),
            'en':en['title']+(' — '+en['subtitle'] if en.get('subtitle') else ''),'cost':c.get('cost'),
            'aspects':[x.get('englishName')or x.get('name') for x in rel('aspects')],
            'traits':[x['name'] for x in rel('traits')], 'arena':arena}
    return result
def main():
    root=Path(__file__).resolve().parent
    if (root/'upstream.json').exists():
        catalog=json.loads((root.parent/'catalog-current.json').read_text()); upstream=json.loads((root/'upstream.json').read_text())
        dest=root/'card-details.json.gz'
    else:
        root=Path(__file__).resolve().parents[2]
        catalog=json.loads((root/'app/src/main/assets/catalog-fr.json').read_text())
        upstream=download('https://raw.githubusercontent.com/Team-Zura/swu-cards-json/main/data/v1/all-cards.json')
        dest=root/'app/src/main/assets/card-details.json.gz'
    def page(code,n):
        q=urllib.parse.urlencode({'locale':'fr','filters[expansion][code][$eq]':code,'pagination[page]':n,'pagination[pageSize]':100})
        return download('https://admin.starwarsunlimited.com/api/card-list?'+q)
    official=[]
    for code in ('HMW','IC27'):
        first=page(code,1); pages=[first]
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            pages+=list(pool.map(lambda n:page(code,n),range(2,first['meta']['pagination']['pageCount']+1)))
        rows=[c for p in pages for c in p['data']]
        assert len(rows)==first['meta']['pagination']['total']
        official+=rows
    details=build(catalog,upstream,official)
    if dest.exists():
        old=json.loads(gzip.decompress(dest.read_bytes()))['cards']
        # Preserve previously publisher-verified HMW localizations only. Unknown
        # other identities must stay explicit instead of retaining promo collisions.
        for k,v in old.items():
            if k.startswith(('HMW:','IC27:')):details.setdefault(k,v)
    payload={'format':'SWU_CARD_DETAILS','version':1,'source':'Team-Zura CC0 / official card-list','cards':details}
    dest.write_bytes(gzip.compress(json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode(),mtime=0))
    print(json.dumps({'mapped':len(details),'catalog':len(catalog['cards']),'gzipBytes':dest.stat().st_size},ensure_ascii=False),flush=True)
if __name__=='__main__':main()

