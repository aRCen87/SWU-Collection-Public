#!/usr/bin/env python3
"""Build compact offline image signatures from exact catalogue artwork URLs."""
import argparse, base64, concurrent.futures, gzip, hashlib, io, json, pathlib, time, urllib.request
from PIL import Image, ImageOps, ImageStat


def signature(image, size=(8,12)):
    image = image.convert('RGB')
    w,h=image.size
    image=image.crop((w*.06,h*.06,w*.94,h*.94)).resize(size,Image.Resampling.BOX)
    pixels=list(image.getdata())
    gray=[.299*r+.587*g+.114*b for r,g,b in pixels]
    mean=sum(gray)/len(gray)
    deviation=max(18,(sum((v-mean)**2 for v in gray)/len(gray))**.5)
    out=[]
    for (r,g,b),v in zip(pixels,gray):
        out.extend((round(max(0,min(255,128+(v-mean)*48/deviation))),
                    round(255*r/max(1,r+g+b)),round(255*b/max(1,r+g+b))))
    return bytes(out)


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--catalog',default='app/src/main/assets/catalog-fr.json')
    p.add_argument('--art',default='app/src/main/assets/card-art-languages.json.gz')
    p.add_argument('--details',default='app/src/main/assets/card-details.json.gz')
    p.add_argument('--output',default='app/src/main/assets/scan-visual-index.json.gz')
    p.add_argument('--report',help='Optional JSON report of unavailable images')
    p.add_argument('--cache',default='/tmp/swu-visual-images')
    p.add_argument('--workers',type=int,default=12)
    p.add_argument('--thumbnails',help='Exact publisher thumbnail URL mapping, keyed by original URL')
    p.add_argument('--cached-only',action='store_true',help='Build a development index from already cached images')
    a=p.parse_args()
    cards=json.loads(pathlib.Path(a.catalog).read_text())['cards']
    art=json.loads(gzip.decompress(pathlib.Path(a.art).read_bytes()))
    details=json.loads(gzip.decompress(pathlib.Path(a.details).read_bytes()))['cards']
    cache=pathlib.Path(a.cache);cache.mkdir(parents=True,exist_ok=True)
    thumbnails=json.loads(pathlib.Path(a.thumbnails).read_text()) if a.thumbnails else {}
    work=[]
    for c in cards:
        key=c['s']+':'+c['n']
        faces=art['cards'].get(key,{})
        for face in range(2):
            url=next((art['urls'][faces[l][face]] for l in ('FR','EN') if l in faces and art['urls'][faces[l][face]]),'')
            if url:work.append((c,url,face))
    def build(item):
        c,url,face=item
        file=cache/hashlib.sha256(url.encode()).hexdigest()
        try:
            if not file.exists():
                if a.cached_only:raise FileNotFoundError('not cached')
                download_url=thumbnails.get(url.replace('com//','com/'),url)
                if not download_url.startswith('https://cdn.starwarsunlimited.com/'):raise ValueError('untrusted image host')
                req=urllib.request.Request(download_url,headers={'User-Agent':'SWU-Collection/1.0'})
                for attempt in range(3):
                    try:
                        with urllib.request.urlopen(req,timeout=30) as response:data=response.read(5_000_001)
                        if len(data)>5_000_000:raise ValueError('oversized image')
                        temporary=file.with_suffix('.tmp');temporary.write_bytes(data);temporary.replace(file);break
                    except Exception:
                        if attempt==2:raise
                        time.sleep(attempt+1)
            with Image.open(file) as img:
                img=ImageOps.exif_transpose(img)
                return [c['s'],c['n'],c['name'],url,face,img.width>img.height,
                        base64.b64encode(signature(img)).decode(),
                        base64.b64encode(signature(img,(16,24))).decode(),
                        details.get(c['s']+':'+c['n'],{}).get('en','')]
        except Exception as e:return {'reference':c['s']+':'+c['n'],'face':face,'error':str(e)}
    entries=[];missing=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as pool:
        for i,row in enumerate(pool.map(build,work),1):
            (missing if isinstance(row,dict) else entries).append(row)
            if i%500==0:print(f'{i}/{len(work)} images, {len(missing)} unavailable',flush=True)
    result={'format':'SWU_VISUAL_INDEX','version':2,'width':8,'height':12,'detailWidth':16,'detailHeight':24,'sampling':'area-v1','entries':entries}
    if not a.cached_only and len(set((v[0],v[1]) for v in entries)) < len(cards)*.98:
        raise RuntimeError('Incomplete visual index; refusing to replace the shipped index')
    pathlib.Path(a.output).write_bytes(gzip.compress(json.dumps(result,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
    if a.report:pathlib.Path(a.report).write_text(json.dumps(missing,ensure_ascii=False,indent=2))
    print(json.dumps({'images':len(entries),'missing':len(missing),'references':len(set((v[0],v[1]) for v in entries))}),flush=True)

if __name__=='__main__':main()
