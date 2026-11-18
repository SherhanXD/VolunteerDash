3import requests, json, time, sys, os, re
s = requests.Session()
s.headers['User-Agent'] = 'VolunteerDashAssetFetcher/1.0 (demo app asset collection; contact: local-dev)'
A = 'https://commons.wikimedia.org/w/api.php'
TMP = os.path.join(os.path.dirname(os.path.abspath(__file__)), '_tmp')
os.makedirs(TMP, exist_ok=True)

def get(**p):
    p['format'] = 'json'
    for i in range(6):
        time.sleep(2.5)
        r = s.get(A, params=p, timeout=60)
        if r.status_code == 200 and r.text.startswith('{'):
            return r.json()
        print('retry', r.status_code, file=sys.stderr)
        time.sleep(15 * (i + 1))
    raise SystemExit('failed')

def strip(h):
    return re.sub(r'<[^>]+>', '', h or '').strip()

mode = sys.argv[1]
if mode == 'search':
    for q in sys.argv[2:]:
        r = get(action='query', list='search', srnamespace=6, srsearch=q, srlimit=50)
        print('##', q, r['query']['searchinfo']['totalhits'])
        for x in r['query']['search']:
            print('   ', x['title'])
elif mode == 'cat':
    for c in sys.argv[2:]:
        r = get(action='query', list='categorymembers', cmtitle=c, cmlimit=500)
        m = r['query']['categorymembers']
        print('##', c, len(m))
        for x in m:
            print('   ', x['title'])
elif mode == 'info':
    # info title1|title2... ; downloads 400px thumbs to _tmp
    titles = sys.argv[2:]
    for i in range(0, len(titles), 40):
        r = get(action='query', titles='|'.join(titles[i:i+40]), prop='imageinfo|categories', cllimit=500,
                iiprop='url|extmetadata|size|mime', iiurlwidth=400)
        for pg in r['query']['pages'].values():
            ii = pg.get('imageinfo', [{}])[0]
            em = ii.get('extmetadata', {})
            g = lambda k: strip(em.get(k, {}).get('value', ''))
            print('==', pg['title'], ii.get('width'), ii.get('height'), ii.get('mime'))
            print('   lic:', g('LicenseShortName'), '|', g('Artist')[:80])
            print('   desc:', g('ImageDescription')[:250].replace('\n', ' '))
            print('   cats:', [c['title'][9:] for c in pg.get('categories', [])][:12])
            if ii.get('thumburl'):
                fn = re.sub(r'[^A-Za-z0-9]+', '_', pg['title'][5:])[:60] + '.jpg'
                time.sleep(1)
                d = s.get(ii['thumburl'], timeout=60)
                open(os.path.join(TMP, fn), 'wb').write(d.content)
                print('   thumb:', fn)

elif mode == 'sheet':
    # sheet out.jpg file1 file2 ... (files in _tmp); labeled grid 4 columns
    from PIL import Image, ImageDraw
    out, files = sys.argv[2], sys.argv[3:]
    W = 300; cols = 4; rows = (len(files) + cols - 1) // cols
    sheet = Image.new('RGB', (W * cols, (W + 20) * rows), 'white')
    d = ImageDraw.Draw(sheet)
    for i, f in enumerate(files):
        im = Image.open(os.path.join(TMP, f)).convert('RGB')
        im.thumbnail((W, W))
        x, y = (i % cols) * W, (i // cols) * (W + 20)
        sheet.paste(im, (x, y + 20))
        d.text((x + 2, y + 4), f'{i}: {f[:44]}', fill='black')
    sheet.save(os.path.join(TMP, out), quality=80)
