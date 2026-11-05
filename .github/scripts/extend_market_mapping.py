#!/usr/bin/env python3
"""Prepare finish-specific Cardmarket links from publisher-verified card identities.

No price, ID ordering, or cheapest-product heuristic identifies a treatment.
Premium groups with several treatments stay unresolved unless explicitly reviewed.
"""
import argparse
import copy
import gzip
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from build_market_map import EXPANSIONS, PRODUCTS, GUIDE, download, normalized

EXTRAS = {'SOR':5638, 'SHD':5781, 'TWI':5937, 'JTL':6074, 'LOF':6188,
          'SEC':6364, 'LAW':6452, 'ASH':6642, 'HMW':6788}
FINISHES = {'Normale','Foil','Hyperspace','Hyperspace Foil','Showcase','Prestige',
            'Prestige Foil','Serialized','Sérialisée or rose','Sérialisée or','Weekly Play','Promo'}

def treatment(finish):
    if finish in ('Serialized','Sérialisée or rose','Sérialisée or'):
        return 'Serialized'
    return finish.removesuffix(' Foil')

def french_name(card):
    return card['name'] + (' — '+card['sub'] if card.get('sub') else '')

def default_finish(choices):
    if 'Normale' in choices:return 'Normale'
    if 'Hyperspace' in choices:return 'Hyperspace'
    return choices[0] if len(choices)==1 else ''

def link(ids, finish, base_range=False):
    foil = finish in ('Foil','Hyperspace Foil','Showcase','Prestige Foil',
                      'Serialized','Sérialisée or rose','Sérialisée or')
    return {'products':sorted(set(ids)), 'field':'trend-foil' if foil else 'trend',
            'range':bool(base_range)}

def make_entry(card, english, choices, selections, proof, source_reference=''):
    return {'nameFr':french_name(card), 'nameEn':english,
            'products':sorted({i for selection in selections.values() for i in selection['products']}),
            'range':any(s.get('range') for s in selections.values()),
            'defaultFinish':default_finish(choices), 'finishes':selections,
            'identity':proof, 'sourceReference':source_reference}

def extend_mapping(catalogue, seed, details, finishes, products, overrides=None):
    cards={c['s']+':'+str(c['n']).zfill(3):c for c in catalogue['cards']}
    product_by_id={p['idProduct']:p for p in products if p.get('idCategory')==1644}
    product_by_name=defaultdict(list)
    for p in product_by_id.values():
        product_by_name[(p['idExpansion'],normalized(p['name']))].append(p)
    result={}; counts=Counter(); standard=defaultdict(list); groups=defaultdict(set)
    choices_by_family=defaultdict(set)
    for key,detail in details.items():
        if key not in cards:continue
        family=(cards[key]['s'],detail['family'])
        for finish in finishes.get(key,[]):
            if finish not in ('Normale','Foil'):
                groups[family].add(treatment(finish))
                choices_by_family[family].add(finish)
    for key,entry in seed.items():
        card=cards.get(key)
        if card is None or normalized(entry.get('nameFr',''))!=normalized(french_name(card)):
            continue
        # A regenerated v3 table is seeded by its verified standard links only.
        if 'finishes' in entry and entry.get('defaultFinish')!='Normale':continue
        if card['s'] not in EXPANSIONS:continue
        selections={f:link(entry['products'],f,entry.get('range',False)) for f in ('Normale','Foil')}
        result[key]=make_entry(card,entry['nameEn'],['Normale'],selections,'verified-standard')
        if key in details:standard[(card['s'],details[key]['family'])].append(key)
    for key,card in cards.items():
        if key in result or key not in details:continue
        choices=finishes.get(key,[])
        family=(card['s'],details[key]['family'])
        bases=standard.get(family,[])
        if len(bases)!=1:
            counts['standard_identity_not_unique']+=1;continue
        base_key=bases[0];base=result[base_key]
        if choices==['Foil']:
            # Distinct foil collector number, same verified standard product family.
            result[key]=make_entry(card,base['nameEn'],choices,
                {'Foil':copy.deepcopy(base['finishes']['Foil'])},'publisher-foil-family',base_key)
            counts['distinct_foil_added']+=1;continue
        allowed=groups.get(family,set())
        if len(allowed)!=1 or not choices or next(iter(allowed)) not in ('Hyperspace','Showcase','Prestige'):
            counts['premium_treatment_ambiguous']+=1;continue
        matches=product_by_name.get((EXTRAS.get(card['s']),normalized(base['nameEn'])),[])
        is_base=card.get('type')=='Base'
        base_range=False
        if not matches and is_base:
            matches=[p for p in product_by_id.values() if p['idExpansion']==EXTRAS.get(card['s'])
                and normalized(p['name'].split(' // ')[0])==normalized(base['nameEn'])]
            base_range=bool(matches)
        baseline_metas={product_by_id[i]['idMetacard'] for i in base['products'] if i in product_by_id}
        candidate_metas={p['idMetacard'] for p in matches}
        all_choices=choices_by_family[family]
        if (not matches or not candidate_metas.issubset(baseline_metas) or
            (not is_base and (len(candidate_metas)!=1 or len(matches)>len(all_choices)))):
            counts['premium_product_not_unique']+=1;continue
        ids=[p['idProduct'] for p in matches]
        result[key]=make_entry(card,base['nameEn'],choices,
            {f:link(ids,f,base_range) for f in choices},'single-published-premium-treatment',base_key)
        counts['premium_added']+=1
    # Reviewed exact links can resolve multi-treatment families without heuristics.
    for row in (overrides or {}).get('cards',[]):
        key=row['reference'];finish=row['finish'];card=cards[key]
        if finish not in FINISHES or finish not in finishes.get(key,[]):
            raise ValueError('Override has an incompatible publisher finish: '+key)
        if not row.get('evidenceUrl','').startswith('https://www.cardmarket.com/') or not row.get('evidence'):
            raise ValueError('A reviewed Cardmarket source is required: '+key)
        english=details[key]['en'].replace(' — ',', ',1)
        if normalized(row['nameEn'])!=normalized(english):
            raise ValueError('Override changes card identity: '+key)
        ids=row['products'];field=row['field'];expected_expansion=row['expansionId']
        if not ids or field not in ('trend','trend-foil'):
            raise ValueError('Invalid reviewed product selection: '+key)
        for identifier in ids:
            p=product_by_id[identifier]
            if p['idExpansion']!=expected_expansion or normalized(p['name'])!=normalized(row['nameEn']):
                raise ValueError('Override product identity mismatch: '+key)
        entry=result.setdefault(key,make_entry(card,row['nameEn'],finishes[key],{},'reviewed-exact-product'))
        entry['finishes'][finish]={'products':sorted(set(ids)),'field':field,'range':False}
        entry['products']=sorted({i for s in entry['finishes'].values() for i in s['products']})
        if entry['defaultFinish'] not in entry['finishes']:entry['defaultFinish']=''
        counts['reviewed_links']+=1
    counts['mapped_references']=len(result)
    counts['unmapped_references']=len(cards)-len(result)
    return result,counts

def write_outputs(assets, mapping, guide):
    root={'format':'SWU_CARDMARKET_MAP','version':3,
          'createdAt':datetime.now(timezone.utc).isoformat(),'cards':mapping}
    snapshot=dict(guide,priceGuides=[p for p in guide['priceGuides'] if p.get('idCategory')==1644])
    for name,value in [('cardmarket-map.json',root),('cardmarket-guide.json',snapshot)]:
        (assets/name).write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--cache-dir',default='.market-cache')
    parser.add_argument('--refresh',action='store_true')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2];assets=root/'app/src/main/assets'
    cache=Path(args.cache_dir);cache.mkdir(parents=True,exist_ok=True)
    products=download(PRODUCTS,cache/'products.json',args.refresh)['products']
    guide=download(GUIDE,cache/'price-guide.json',args.refresh)
    catalogue=json.loads((assets/'catalog-fr.json').read_text())
    seed=json.loads((assets/'cardmarket-map.json').read_text())['cards']
    details=json.loads(gzip.decompress((assets/'card-details.json.gz').read_bytes()))['cards']
    finishes=json.loads(gzip.decompress((assets/'card-print-finishes.json.gz').read_bytes()))['cards']
    override_file=root/'.github/cardmarket-overrides.json'
    overrides=json.loads(override_file.read_text()) if override_file.exists() else None
    mapping,counts=extend_mapping(catalogue,seed,details,finishes,products,overrides)
    if len(mapping)<len(seed)*.95:raise RuntimeError('Refusing a major loss of existing verified links')
    write_outputs(assets,mapping,guide)
    print(json.dumps(dict(counts),ensure_ascii=False))

if __name__=='__main__':main()
