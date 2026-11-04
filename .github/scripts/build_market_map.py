#!/usr/bin/env python3
"""Join exact card identities to Cardmarket products. No positional or fuzzy joins."""
import argparse
import json
import unicodedata
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

UPSTREAM = 'https://raw.githubusercontent.com/Team-Zura/swu-cards-json/main/data/v1/all-cards.json'
PRODUCTS = 'https://downloads.s3.cardmarket.com/productCatalog/productList/products_singles_21.json'
GUIDE = 'https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_21.json'
# Cardmarket's standard expansion IDs; premium treatments have separate expansions.
EXPANSIONS = {'SOR':5618, 'SHD':5769, 'TWI':5888, 'JTL':5995, 'LOF':6105,
              'IBH':6268, 'SEC':6333, 'LAW':6451, 'TS26':6532, 'ASH':6591, 'HMW':6759}

# This double-sided card is named using BOTH faces in Cardmarket's product catalogue.
CARDMARKET_NAMES = {('TWI', '017'): 'Chancellor Palpatine // Darth Sidious, Playing Both Sides'}

def normalized(text):
    # Preserve diacritics; only canonical Unicode, punctuation and case are normalized for joins.
    return ' '.join(unicodedata.normalize('NFC', text).replace('’', "'").replace('“', '"').replace('”', '"').replace('""', '"').replace('…', '...').casefold().split())

def full_name(name, subtitle, separator=', '):
    return name + (separator + subtitle if subtitle else '')

def download(url, path, refresh=False):
    if refresh or not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent':'SWU-Collection-market-audit/1.0'})
        with urllib.request.urlopen(request, timeout=90) as response:
            payload = response.read()
        json.loads(payload)  # Never overwrite a valid cache with an HTML/error response.
        path.write_bytes(payload)
    return json.loads(path.read_text(encoding='utf-8'))

def source_identities(source, official):
    identities = defaultdict(list)
    for card in source:
        code = card.get('set', {}).get('code', '').upper()
        serial = str(card.get('serial_code') or '')
        # 01 is the publisher's standard printing, not foil/premium/showcase.
        if code not in ('TS26', 'IBH') and (len(serial) < 6 or serial[2:4] != '01'):
            continue
        n = str(card.get('number', '')).zfill(3)
        en, fr = card.get('name') or {}, card.get('subtitle') or {}
        if not en.get('en') or not en.get('fr'):
            continue
        identities[(code, n)].append((full_name(en['en'], fr.get('en', '')),
                                      full_name(en['fr'], fr.get('fr', ''), ' — '), card.get('type')))
    for row in official:
        a = row.get('attributes', row)
        code = (((a.get('expansion') or {}).get('data') or {}).get('attributes') or {}).get('code')
        variants = (a.get('variantTypes') or {}).get('data') or []
        if code != 'HMW' or a.get('locale') != 'en':
            continue
        if not any(v.get('attributes', {}).get('variantId') == '01' for v in variants):
            continue
        n = str(a['cardNumber']).zfill(3)
        # English official identities are linked by exact collector number to the French catalogue.
        kind = (((a.get('type') or {}).get('data') or {}).get('attributes') or {}).get('name')
        identities[(code, n)].append((full_name(a['title'], a.get('subtitle') or ''), None, kind))
    return identities

def build(catalogue, source, official, products):
    identities = source_identities(source, official)
    by_name = defaultdict(list)
    for product in products:
        if product.get('idCategory') == 1644:
            by_name[(product['idExpansion'], normalized(product['name']))].append(product)
    result, skipped = {}, Counter()
    for card in catalogue['cards']:
        key = (card['s'], str(card['n']).zfill(3))
        current_fr = full_name(card['name'], card.get('sub') or '', ' — ')
        candidates = identities.get(key, [])
        types = {'Leader':'Leader', 'Unit':'Unité', 'Upgrade':'Amélioration', 'Event':'Événement', 'Base':'Base', 'Token':'Jeton'}
        current_type = 'Événement' if card.get('type') == 'Évènement' else card.get('type')
        names = set(en for en, fr, kind in candidates if types.get(kind, kind) == current_type
                    and (fr is None or normalized(fr) == normalized(current_fr)))
        if len(names) != 1:
            skipped['identity_unverified'] += 1
            continue
        english = names.pop()
        market_name = CARDMARKET_NAMES.get(key, english)
        matches = by_name.get((EXPANSIONS.get(card['s']), normalized(market_name)), [])
        base_range = False
        if not matches and card.get('type') == 'Base':
            matches = [p for p in products if p.get('idCategory') == 1644
                       and p.get('idExpansion') == EXPANSIONS.get(card['s'])
                       and normalized(p['name'].split(' // ')[0]) == normalized(english)]
            base_range = bool(matches)
        # Several IDs are permitted only when Cardmarket explicitly groups them as the SAME metacard.
        if not matches or (not base_range and len({m['idMetacard'] for m in matches}) != 1):
            skipped['product_missing_or_ambiguous'] += 1
            continue
        result[':'.join(key)] = {'nameFr':current_fr, 'nameEn':market_name,
                                'products': sorted({m['idProduct'] for m in matches}), 'range':base_range}
    return result, skipped

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache-dir', default='.market-cache')
    parser.add_argument('--refresh', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    cache = Path(args.cache_dir); cache.mkdir(parents=True, exist_ok=True)
    source = download(UPSTREAM, cache/'upstream.json', args.refresh)
    products = download(PRODUCTS, cache/'products.json', args.refresh)
    guide = download(GUIDE, cache/'price-guide.json', args.refresh)
    page1 = download('https://admin.starwarsunlimited.com/api/card-list?' + urllib.parse.urlencode({
        'locale':'en', 'filters[expansion][code][$eq]':'HMW', 'pagination[pageSize]':250,
        'pagination[page]':1}), cache/'hmw-en.json', args.refresh)
    pages = [page1]
    for page in range(2, page1['meta']['pagination']['pageCount'] + 1):
        url = 'https://admin.starwarsunlimited.com/api/card-list?' + urllib.parse.urlencode({
            'locale':'en', 'filters[expansion][code][$eq]':'HMW', 'pagination[pageSize]':250,
            'pagination[page]':page})
        pages.append(download(url, cache/f'hmw-en-{page}.json', args.refresh))
    official = [c for page in pages for c in page['data']]
    assert len(official) == page1['meta']['pagination']['total'], 'Incomplete official HMW pagination'
    catalogue = json.loads((root/'app/src/main/assets/catalog-fr.json').read_text(encoding='utf-8'))
    mapping, skipped = build(catalogue, source, official, products['products'])
    assert 'HMW:001' in mapping and mapping['SOR:001']['nameEn'] == 'Director Krennic, Aspiring to Authority'
    ids = {p for card in mapping.values() for p in card['products']}
    snapshot = dict(guide, priceGuides=[p for p in guide['priceGuides'] if p['idProduct'] in ids and p['idCategory']==1644])
    payload = {'version':2, 'createdAt':datetime.now(timezone.utc).isoformat(), 'cards':mapping}
    assets = root/'app/src/main/assets'
    (assets/'cardmarket-map.json').write_text(json.dumps(payload, ensure_ascii=False, separators=(',', ':'))+'\n', encoding='utf-8')
    (assets/'cardmarket-guide.json').write_text(json.dumps(snapshot, ensure_ascii=False, separators=(',', ':'))+'\n', encoding='utf-8')
    print(json.dumps({'mapped':len(mapping),'bySet':dict(Counter(k.split(':')[0] for k in mapping)),
                      'guideDate':guide['createdAt'],'prices':len(snapshot['priceGuides']), 'skipped':dict(skipped)}, ensure_ascii=False))

if __name__ == '__main__':
    main()
