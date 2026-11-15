#!/usr/bin/env python3
"""Supplement missing Homeworlds promos from exact publisher collector identities."""
import concurrent.futures
import gzip
import hashlib
import json
import pathlib
import urllib.parse
import urllib.request
from datetime import datetime, timezone

from build_card_details import build
from build_scan_visual_index import signature
from card_art_identity import language_marker

LANGUAGES = ('FR', 'EN', 'DE', 'ES', 'IT')
SHOWDOWN = {26: '40', 27: '45', 28: '41', 29: '42', 30: '43', 31: '44'}


def expected_serial(code, number):
    if code == 'HMWP' and 1 <= number <= 40:
        return '09w' + ('60' if number <= 20 else '35') + str(number).zfill(4)
    if code == 'P26' and number in SHOWDOWN:
        return 'p26' + SHOWDOWN[number] + str(number).zfill(4)
    raise ValueError('Unexpected promo reference')


def relation(card, field):
    return ((card.get(field) or {}).get('data') or {}).get('attributes') or {}


def face(card, field):
    art = relation(card, field)
    return ((art.get('formats') or {}).get('card') or {}).get('url') or art.get('url') or ''


def exact_face(card, field, language):
    image = face(card, field)
    return image if image.startswith('https://cdn.starwarsunlimited.com/') and language_marker(image) == language else ''


def parse_promos(code, language, payload):
    expected = set(range(1, 41)) if code == 'HMWP' else set(SHOWDOWN)
    rows = payload.get('data', [])
    meta = payload.get('meta', {}).get('pagination', {})
    if len(rows) != len(expected) or meta.get('total') != len(expected) or meta.get('pageCount') != 1:
        raise ValueError('Incomplete publisher promo response: ' + code + ' ' + language)
    found = {}
    for row in rows:
        card = row.get('attributes', row)
        number = int(card['cardNumber'])
        if card.get('locale') != language.lower() or relation(card, 'expansion').get('code') != code:
            raise ValueError('Wrong promo expansion or language')
        if card.get('serialCode', '').lower() != expected_serial(code, number) or number in found:
            raise ValueError('Wrong or duplicate promo collector identity')
        if language in ('FR', 'EN') and not exact_face(card, 'artFront', language):
            raise ValueError('Missing exact localized publisher artwork')
        if not card.get('title') or not card.get('cardUid') or not relation(card, 'type').get('value'):
            raise ValueError('Incomplete promo identity')
        found[number] = card
    if set(found) != expected:
        raise ValueError('Missing promo collector numbers')
    return found


def fetch_promos(code, language, cache):
    query = {'locale': language.lower(), 'filters[expansion][code][$eq]': code,
             'pagination[pageSize]': '100'}
    if code == 'P26':
        query.update({'filters[cardNumber][$gte]': '26', 'filters[cardNumber][$lte]': '31'})
    path = cache / (code + '-' + language + '.json')
    if path.exists():
        payload = json.loads(path.read_text())
    else:
        request = urllib.request.Request('https://admin.starwarsunlimited.com/api/card-list?' + urllib.parse.urlencode(query),
                                         headers={'User-Agent': 'SWU-Collection/1.0', 'Accept': 'application/json'})
        with urllib.request.urlopen(request, timeout=45) as response:
            payload = json.load(response)
        path.write_text(json.dumps(payload, ensure_ascii=False))
    return code, language, parse_promos(code, language, payload)


def main():
    import argparse
    from PIL import Image, ImageOps
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', default='app/src/main/assets')
    parser.add_argument('--cache', default='/tmp/swu-homeworlds-promos')
    parser.add_argument('--metadata-only', action='store_true', help='Validate publisher identities without downloading images; CI runs the full check')
    args = parser.parse_args()
    assets, cache = pathlib.Path(args.assets), pathlib.Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        localized = {(code, lang): rows for code, lang, rows in pool.map(
            lambda pair: fetch_promos(*pair, cache), [(c, l) for c in ('HMWP', 'P26') for l in LANGUAGES])}
    catalog = json.loads((assets / 'catalog-fr.json').read_text())
    old_cards = {(c['s'], c['n']): c for c in catalog['cards']}
    updated = dict(old_cards)
    additions = []
    official = []
    for code in ('HMWP', 'P26'):
        for number, card in localized[code, 'FR'].items():
            for lang in LANGUAGES:
                if localized[code, lang][number]['cardUid'] != card['cardUid']:
                    raise ValueError('Localization belongs to a different promo')
            row = {'s': code, 'n': str(number).zfill(3), 'name': card['title'], 'sub': card.get('subtitle') or '',
                   'type': relation(card, 'type')['name'], 'rarity': relation(card, 'rarity').get('name', ''),
                   'imageUrl': face(card, 'artFront'), 'backImageUrl': face(card, 'artBack')}
            updated[code, row['n']] = row
            additions.append(row)
            official.append({'attributes': card})
    catalog['cards'] = list(updated.values())
    sets = {s['code']: dict(s) for s in catalog['sets']}
    sets.setdefault('HMWP', {'code': 'HMWP', 'name': 'Jeu organisé hebdomadaire — Mondes Natals', 'releaseDate': ''})
    for code in ('HMWP', 'P26'):
        sets[code]['cardCount'] = sum(c['s'] == code for c in catalog['cards'])
    catalog['sets'] = list(sets.values())
    if any(updated[key] != value for key, value in old_cards.items() if key[0] != 'HMWP' and not (key[0] == 'P26' and int(key[1]) in SHOWDOWN)):
        raise ValueError('Unrelated catalogue row changed')
    if len(updated) != len(catalog['cards']):
        raise ValueError('Duplicate catalogue identity')

    def read_gzip(name):
        return json.loads(gzip.decompress((assets / name).read_bytes()))
    details = read_gzip('card-details.json.gz')
    facets = build({'cards': additions}, [], official)
    if len(facets) != 46:
        raise ValueError('Incomplete promo families and deck metadata')
    details['cards'].update(facets)
    art = read_gzip('card-art-languages.json.gz')
    dictionary = {url: index for index, url in enumerate(art['urls'])}
    def index(url):
        if url not in dictionary:
            dictionary[url] = len(art['urls'])
            art['urls'].append(url)
        return dictionary[url]
    finishes = read_gzip('card-print-finishes.json.gz')
    for row in additions:
        code, number = row['s'], int(row['n'])
        key = code + ':' + row['n']
        art['cards'][key] = {lang: [index(exact_face(localized[code, lang][number], field, lang)) for field in ('artFront', 'artBack')]
                             for lang in LANGUAGES}
        finishes['cards'][key] = [('Weekly Play' if number <= 20 else 'Foil') if code == 'HMWP' else 'Promo']

    visual = read_gzip('scan-visual-index.json.gz')
    if visual.get('version') != 2 or visual.get('sampling') != 'area-v1':
        raise ValueError('Unsupported existing scanner index')
    def visual_row(row):
        import base64
        url = row['imageUrl']
        image_file = cache / hashlib.sha256(url.encode()).hexdigest()
        if not image_file.exists():
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'SWU-Collection/1.0'}), timeout=35) as response:
                data = response.read(5_000_001)
            if len(data) > 5_000_000:
                raise ValueError('Oversized promo artwork')
            image_file.write_bytes(data)
        with Image.open(image_file) as image:
            image = ImageOps.exif_transpose(image)
            if image.width < 100 or image.height < 100:
                raise ValueError('Invalid promo artwork dimensions')
            key = row['s'] + ':' + row['n']
            return [row['s'], row['n'], row['name'], url, 0, image.width > image.height,
                    base64.b64encode(signature(image)).decode(), base64.b64encode(signature(image, (16, 24))).decode(), facets[key]['en']]
    visuals = []
    if not args.metadata_only:
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
            visuals = list(pool.map(visual_row, additions))
    target_keys = {(r['s'], r['n']) for r in additions}
    if not args.metadata_only:
        visual['entries'] = [r for r in visual['entries'] if (r[0], r[1]) not in target_keys] + visuals
    catalog['updatedAt'] = datetime.now(timezone.utc).isoformat()
    outputs = {'catalog-fr.json': json.dumps(catalog, ensure_ascii=False, separators=(',', ':')).encode()}
    for name, payload in [('card-details.json.gz', details), ('card-art-languages.json.gz', art),
                          ('card-print-finishes.json.gz', finishes)] + ([] if args.metadata_only else [('scan-visual-index.json.gz', visual)]):
        outputs[name] = gzip.compress(json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0)
    # Every source, identity and image is validated before changing any shipped file.
    for name, data in outputs.items():
        (assets / (name + '.tmp')).write_bytes(data)
    for name in outputs:
        (assets / (name + '.tmp')).replace(assets / name)
    print(json.dumps({'targetReferences': 46, 'addedReferences': len(updated) - len(old_cards),
                      'weeklyPlay': 40, 'showdown': 6,
                      'localizedFronts': sum(bool(art['cards'][r['s'] + ':' + r['n']][lang][0]) for r in additions for lang in LANGUAGES),
                      'scannerSignatures': len(visuals)}), flush=True)


if __name__ == '__main__':
    main()
