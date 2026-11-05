#!/usr/bin/env python3
"""Build a small native companion feed from SWU API's documented public exports.
No credentials, page scraping, invented win rates or silently dropped card rows.
"""
import argparse, gzip, json, os, re, time, urllib.request, unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
API = 'https://api.swuapi.com/export/'

def export_rows(entity, cache):
    cursor = None
    seen = set()
    for page in range(200):
        path = cache / f'{entity}-{page}.json.gz'
        if path.exists():
            with gzip.open(path, 'rt') as f: payload = json.load(f)
        else:
            url = API + entity + '?limit=5000' + ('&cursor=' + cursor if cursor else '')
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(url, timeout=90) as response: payload = json.load(response)
                    break
                except Exception:
                    if attempt == 2: raise
                    time.sleep(2)
            with gzip.open(path, 'wt') as f: json.dump(payload, f)
        rows = payload['data']
        assert isinstance(rows, list)
        print(f'{entity}: page {page + 1}, {len(rows)} rows', flush=True)
        yield from rows
        if not payload.get('has_more'): return
        cursor = payload.get('next_cursor')
        assert cursor and cursor not in seen, 'Invalid export pagination'
        seen.add(cursor)
    raise ValueError('Export pagination limit reached')

def identity_name(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value).casefold() if c.isalnum())

def english_aliases(cards, details, dates):
    candidates = defaultdict(list)
    for key, detail in details.items():
        ref = key.replace(':', '-')
        if ref in cards and detail.get('en') and detail.get('family'):
            candidates[identity_name(detail['en'])].append((ref, detail['family']))
    aliases = {}
    for name, entries in candidates.items():
        if len({family for _, family in entries}) != 1: continue
        aliases[name] = min((ref for ref, _ in entries), key=lambda r: (int(r.split('-')[1]) >= 300, dates.get(r.split('-')[0], '9999'), int(r.split('-')[1]), r))
    return aliases

def portable(rows, cards, aliases=None):
    leader = []; base = []; zones = [Counter(), Counter()]
    for row in rows:
        raw = row.get('collector_number') or row.get('id') or ''
        ref = ''
        if re.fullmatch(r'[A-Z0-9]{2,12}[_-][0-9]{1,5}', raw):
            code, number = re.split('[_-]', raw)
            ref = code + '-' + number.zfill(3)
        if ref not in cards:
            # Recent source rows sometimes expose only the exact English title/subtitle.
            # Resolve only a unique verified family, never a fuzzy title or inferred base.
            name = (row.get('cardName') + ' ' + (row.get('subtitle') or '')) if row.get('cardName') else (row.get('name') or '')
            ref = (aliases or {}).get(identity_name(name), '')
        if ref not in cards: raise ValueError('Unknown card')
        count = row.get('count')
        if type(count) is not int or not 1 <= count <= 99: raise ValueError('Invalid quantity')
        side = row.get('sideboard', False)
        if type(side) is not bool: raise ValueError('Invalid zone')
        kind = cards[ref]['type'].lower()
        if kind in ('leader', 'chef', 'base'):
            if side or count != 1: raise ValueError('Invalid identity')
            (base if kind == 'base' else leader).append(ref)
        else: zones[int(side)][ref] += count
    if len(leader) != 1 or len(base) != 1 or sum(zones[0].values()) < 50:
        raise ValueError('Incomplete list')
    if any(n > 99 for zone in zones for n in zone.values()): raise ValueError('Quantity overflow')
    return {'leader': {'id': leader[0], 'count': 1}, 'base': {'id': base[0], 'count': 1},
            'deck': [{'id': k, 'count': v} for k, v in sorted(zones[0].items())],
            'sideboard': [{'id': k, 'count': v} for k, v in sorted(zones[1].items())]}

def build(tournaments, decks, standings, cards, now, aliases=None):
    cutoff = (now - timedelta(days=60)).date().isoformat()
    today = now.date().isoformat()
    eligible = {t['uuid']: t for t in tournaments if t.get('tier') in ('PQ','SQ','RQ','GC','LCQ')
                and t.get('format') == 'Premier' and t.get('status') == 'completed'
                and cutoff <= t.get('date', '')[:10] <= today}
    lists = []; ignored = 0
    for d in decks:
        if d.get('tournament_uuid') not in eligible: continue
        try: deck = portable(d['decklist'], cards, aliases)
        except (ValueError, KeyError, TypeError): ignored += 1; continue
        lists.append({'id': d['uuid'], 'tournamentId': d['tournament_uuid'],
                      'player': d.get('player_uuid'), 'account': d.get('account_uuid'), 'deck': deck})
    by_id = {d['id']: d for d in lists}
    account_lists = defaultdict(list)
    for d in lists:
        if d['account']: account_lists[(d['tournamentId'], d['account'])].append(d)
    by_account = {key: rows[0] for key, rows in account_lists.items() if len(rows) == 1}
    for s in standings:
        if s.get('tournament_uuid') not in eligible: continue
        # Only an explicit deck link or the exact tournament account identifies the list.
        d = by_id.get(s.get('decklist_uuid')) or by_account.get((s['tournament_uuid'], s.get('account_uuid')))
        rank = s.get('final_placement')
        if d and d['tournamentId'] == s['tournament_uuid'] and type(rank) is int and rank > 0: d['placement'] = rank
    groups = defaultdict(list)
    for d in lists:
        groups[(d['deck']['leader']['id'], d['deck']['base']['id'])].append(d)
    def order(d):
        return (d.get('placement', 100000), -datetime.fromisoformat(eligible[d['tournamentId']]['date'].replace('Z','+00:00')).timestamp(), d['id'])
    selected = {}
    meta = []
    for (leader, base), rows in sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[0]))[:30]:
        samples = sorted(rows, key=order)[:3]
        for d in samples: selected[d['id']] = d
        meta.append({'leader': leader, 'base': base, 'count': len(rows), 'deckIds': [d['id'] for d in samples]})
    events = []
    for tid, t in sorted(eligible.items(), key=lambda kv: (kv[1]['date'], kv[0]), reverse=True)[:30]:
        samples = sorted((d for d in lists if d['tournamentId'] == tid), key=order)[:12]
        for d in samples: selected[d['id']] = d
        events.append({'id': tid, 'name': t.get('display_name') or t['name'], 'date': t['date'][:10],
                       'tier': t['tier'], 'deckIds': [d['id'] for d in samples],
                       'players': t.get('player_count')})
    # Include event metadata for every representative list, even beyond the latest 30 events.
    referenced = {d['tournamentId'] for d in selected.values()}
    for tid in sorted(referenced - {e['id'] for e in events}):
        t = eligible[tid]
        events.append({'id': tid, 'name': t.get('display_name') or t['name'], 'date': t['date'][:10],
                       'tier': t['tier'], 'deckIds': [], 'players': t.get('player_count')})
    for d in selected.values():
        del d['player']; del d['account']
        t = eligible[d['tournamentId']]
        d['name'] = (cards[d['deck']['leader']['id']]['name'] + ' / ' + cards[d['deck']['base']['id']]['name'] + ' · ' + t['date'][:10])[:120]
    assert meta and selected, 'No usable current competitive decks; preserve previous feed'
    return {'format': 'SWU_COMPETITIVE_FEED', 'version': 1, 'source': 'SWU API',
            'updatedAt': now.isoformat(), 'from': cutoff, 'to': today,
            'listCount': len(lists), 'excludedCount': ignored, 'eventCount': len(eligible),
            'groups': meta, 'events': events, 'decks': list(selected.values())}

def main():
    p = argparse.ArgumentParser(); p.add_argument('--cache', default='/tmp/swu-competitive-export')
    p.add_argument('--output', default=str(ROOT / 'app/src/main/assets/competitive-feed.json'))
    a = p.parse_args(); now = datetime.now(timezone.utc)
    # Daily snapshots: a failed refresh never replaces the previous published payload.
    cache = Path(a.cache) / now.date().isoformat(); cache.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((ROOT / 'app/src/main/assets/catalog-fr.json').read_text())
    cards = {c['s'] + '-' + str(c['n']).zfill(3): c for c in catalog['cards']}
    tournaments = list(export_rows('tournaments', cache))
    details = json.loads(gzip.decompress((ROOT / 'app/src/main/assets/card-details.json.gz').read_bytes()))['cards']
    aliases = english_aliases(cards, details, {s['code']: s.get('releaseDate','9999') for s in catalog['sets']})
    result = build(tournaments, export_rows('decklists', cache), export_rows('standings', cache), cards, now, aliases)
    out = Path(a.output); tmp = out.with_suffix('.tmp')
    tmp.write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n')
    os.replace(tmp, out)
    print(f"Feed: {len(result['decks'])} decks, {result['listCount']} compatible lists, {result['excludedCount']} excluded")

if __name__ == '__main__': main()
