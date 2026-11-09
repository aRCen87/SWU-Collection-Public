"""Build a small data-only feed from official FFG rules; reject unrecognised input."""
import json, re, subprocess, tempfile, urllib.request, urllib.parse, urllib.error, os, base64
from pathlib import Path
from datetime import datetime, timezone
from html import unescape

RULES = 'https://admin.starwarsunlimited.com/api/game-info-chapters/2?locale=en&populate=deep,4'
OP = 'https://starwarsunlimited.com/organized-play'
PUBLIC = 'aRCen87/SWU-Collection-Releases'
NAMES = {'Spark of Rebellion':'SOR','Shadows of the Galaxy':'SHD','Twilight of the Republic':'TWI',
 'Jump to Lightspeed':'JTL','Legends of the Force':'LOF','Intro Battle: Hoth':'IBH',
 'Secrets of Power':'SEC','A Lawless Time':'LAW','Ashes of the Empire':'ASH','Homeworlds':'HMW',
 'Icons 2027 Edition':'IC27'}

def get(url, limit=12_000_000):
    assert urllib.parse.urlparse(url).scheme == 'https'
    assert urllib.parse.urlparse(url).hostname in {'starwarsunlimited.com','admin.starwarsunlimited.com','cdn.starwarsunlimited.com','raw.githubusercontent.com'}
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'SWU-Collection-Rules/1.0'}),timeout=40) as response:
        data=response.read(limit+1)
    assert len(data)<=limit, 'Official response too large'
    return data

def plain(html):
    return re.sub(r'\s+', ' ', unescape(re.sub('<[^>]+>', ' ', html))).strip()

def suspension_refs(html, format="Premier"):
    section=re.search(r'<h1[^>]*>\s*Suspended Cards\s*</h1>(.*?)<h2',html,re.S)
    assert section, 'Suspension section missing; keep previous feed'
    headings=list(re.finditer(r'<p[^>]*>\s*<strong>\s*(Premier|Twin Suns|Eternal)\s*:(?:&nbsp;|\s)*</strong>\s*</p>',section[1],re.S))
    index=next((i for i,h in enumerate(headings) if h[1]==format),None)
    assert index is not None, 'Format boundaries missing'
    start=headings[index].end();end=headings[index+1].start() if index+1<len(headings) else len(section[1])
    match=(None,section[1][start:end])
    body=match[1]
    if plain(body).lower()=='no suspended cards': return []
    # Only complete paragraphs containing the official card tooltip and set reference.
    paragraphs=re.findall(r'<p[^>]*>(.*?)</p>',body,re.S)
    refs=[]
    for paragraph in paragraphs:
        if not plain(paragraph): continue
        assert 'data-card=' in paragraph, 'Unrecognised suspension row'
        m=re.search(r'\(([^,]+),\s*(\d+)\)',plain(paragraph))
        assert m, 'Missing suspended card reference'
        refs.append((m[1].strip(),m[2]))
    assert refs and len(refs)<=100, 'Suspension list empty or excessive'
    return refs

def legal_product_names(text):
    m=re.search(r'The following products are legal for Premier format events:\s*(.*?)(?:The following cards are suspended|9\.1\.2)',text,re.S)
    assert m, 'Premier product list missing'
    names=[]
    for line in m[1].splitlines():
        line=line.strip()
        if not line: continue
        if line.startswith(('●','•')): names.append(line[1:].strip())
        elif re.fullmatch(r'\d+|V\s*[\d.]+.*|Updated:.*',line):continue
        else:raise ValueError('Unrecognised product list row: '+line)
    assert 1<=len(names)<=50 and len(names)==len(set(names))
    return names

def resolve_code(name):
    if name in NAMES:return NAMES[name]
    # Future expansions are resolved through the same official card database as the website.
    query=urllib.parse.urlencode({'locale':'en','filters[expansion][name][$eq]':name,'pagination[pageSize]':'1','populate':'*'})
    data=json.loads(get('https://admin.starwarsunlimited.com/api/card-list?'+query))['data']
    assert len(data)==1,'Unknown product; keep previous rules'
    expansion=data[0]['attributes']['expansion']['data']['attributes']
    assert expansion.get('name')==name
    code=expansion['code'];assert re.fullmatch('[A-Z0-9]{2,12}',code)
    return code

def build(catalog, previous=None):
    source=json.loads(get(RULES))['data']['attributes']
    html=''.join(b.get('content','') for b in source['content'] if b['__component']=='blocks.wysiwyg')
    refs=suspension_refs(html)
    other_refs={"Twin Suns":suspension_refs(html,"Twin Suns"),"Éternel":suspension_refs(html,"Eternal")}
    op=get(OP).decode('utf-8')
    urls=set(re.findall(r'https://cdn\.starwarsunlimited\.com/[^" <>]+Comprehensive_Event[^" <>]+\.pdf',op))
    assert len(urls)==1,'Ambiguous event guide'
    guide=urls.pop()
    with tempfile.TemporaryDirectory() as directory:
        pdf=Path(directory)/'guide.pdf';txt=Path(directory)/'guide.txt'
        pdf.write_bytes(get(guide))
        subprocess.run(['pdftotext','-layout',str(pdf),str(txt)],check=True,capture_output=True)
        active=[resolve_code(n) for n in legal_product_names(txt.read_text())]
    cards={(c['s'],str(int(c['n']))):c for c in catalog['cards'] if c['n'].isdigit()}
    def resolve_refs(refs):
        suspended=[]
        for name,number in refs:
            code=resolve_code(name)
            c=cards.get((code,str(int(number))))
            assert c, 'Suspended card absent from catalogue; keep previous feed'
            suspended.append({'setCode':code,'number':number,'name':c['name']+(' — '+c['sub'] if c.get('sub') else ''),'type':c['type']})
        return suspended
    suspended=resolve_refs(refs)
    formats={f:resolve_refs(rows) for f,rows in other_refs.items()}
    # Ship identities too: a phone with an older catalogue can recognise newly legal reprints.
    assert set(active) <= {c['s'] for c in catalog['cards']}, 'Active product absent from current catalogue'
    legal = {(c['name']+(' — '+c['sub'] if c.get('sub') else ''), c['type'])
             for c in catalog['cards'] if c['s'] in active}
    assert all(name and kind for name,kind in legal)
    legal_cards = [{'name':name,'type':kind} for name,kind in sorted(legal)]
    prior=set((previous or {}).get('activeSets',[]))|set((previous or {}).get('rotatedSets',[]))|{'SOR','SHD','TWI'}
    return {'format':'SWU_LEGALITY','schema':1,'checkedAt':datetime.now(timezone.utc).isoformat(),
      'sourceUpdatedAt':source['updatedAt'],'source':'https://starwarsunlimited.com/how-to-play?chapter=rules',
      'rotationSource':guide,'activeSets':sorted(active),'rotatedSets':sorted(prior-set(active)),
      'suspended':suspended,'legalCards':legal_cards,'formatSuspensions':formats}

def publish(feed):
    # Only the dedicated releases token is available to this process.
    target=f'repos/{PUBLIC}/contents/legality.json'
    existing=subprocess.run(['gh','api',target],capture_output=True,text=True)
    assert existing.returncode==0 or 'HTTP 404' in existing.stderr, 'Cannot read public feed'
    payload={'message':'Actualiser les statuts officiels SWU',
      'content':base64.b64encode(json.dumps(feed,ensure_ascii=False).encode()).decode()}
    if existing.returncode==0:payload['sha']=json.loads(existing.stdout)['sha']
    subprocess.run(['gh','api','--method','PUT',target,'--input','-'], input=json.dumps(payload),
                   check=True,capture_output=True,text=True)


if __name__=='__main__':
    catalogue=json.loads(Path('app/src/main/assets/catalog-fr.json').read_text())
    try:previous=json.loads(get(f'https://raw.githubusercontent.com/{PUBLIC}/main/legality.json'))
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
        previous=None
    feed=build(catalogue,previous)
    Path('legality.json').write_text(json.dumps(feed,ensure_ascii=False,indent=2)+'\n')
    if os.environ.get('PUBLISH_LEGALITY')=='1':publish(feed)

