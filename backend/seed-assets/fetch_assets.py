"""Fetch licensed seed images for the VolunteerDash demo app.

Usage (Python 3.12, `pip install pillow requests`):
    python fetch_assets.py discover   # search Commons, write candidate thumbnails + contact sheets to _tmp/
    python fetch_assets.py build      # download the curated selections below, write photos/, portraits/, credits

All photos come from Wikimedia Commons. Only CC0, public domain, CC BY and CC BY-SA files are accepted;
the license is re-checked at build time from the Commons API (extmetadata).
"""
import io
import json
import os
import re
import sys
import time
import html

import requests
from PIL import Image, ImageDraw, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
TMP = os.path.join(HERE, '_tmp')
PHOTOS = os.path.join(HERE, 'photos')
PORTRAITS = os.path.join(HERE, 'portraits')
API = 'https://commons.wikimedia.org/w/api.php'
UA = 'VolunteerDashAssetFetcher/1.0 (demo app asset collection)'

S = requests.Session()
S.headers['User-Agent'] = UA


# ----------------------------------------------------------------------------- helpers

def api(**params):
    params['format'] = 'json'
    for attempt in range(6):
        time.sleep(2.5)  # be polite: sequential requests with a delay
        r = S.get(API, params=params, timeout=60)
        if r.status_code == 200 and r.text.startswith('{'):
            return r.json()
        print(f'  api retry ({r.status_code})', file=sys.stderr)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError('Commons API failed repeatedly')


def download(url):
    for attempt in range(6):
        time.sleep(1.5)
        r = S.get(url, timeout=120)
        if r.status_code == 200:
            return r.content
        print(f'  download retry ({r.status_code}) {url}', file=sys.stderr)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError(f'download failed: {url}')


def strip_html(s):
    s = re.sub(r'<[^>]+>', '', s or '')
    s = html.unescape(s)
    return re.sub(r'\s+', ' ', s).strip()


def meta(ii, key):
    return strip_html(ii.get('extmetadata', {}).get(key, {}).get('value', ''))


def classify_license(ii):
    """Return (license_name, license_url) if acceptable, else None."""
    short = meta(ii, 'LicenseShortName')
    code = meta(ii, 'License').lower()
    url = meta(ii, 'LicenseUrl')
    if meta(ii, 'NonFree').lower() in ('true', '1'):
        return None
    s = short.lower()
    if re.search(r'\bnc\b|\bnd\b|non-?commercial|no ?deriv|fair use', s + ' ' + code):
        return None
    if s.startswith('cc0') or code == 'cc0':
        return 'CC0 1.0', url or 'https://creativecommons.org/publicdomain/zero/1.0/'
    if s.startswith('public domain') or code.startswith('pd') or s.startswith('pd'):
        return 'Public domain', url or 'https://commons.wikimedia.org/wiki/Commons:Copyright_tags#Public_domain'
    m = re.match(r'cc[ -]by(-sa)?[ -]([0-9.]+)', s)
    if m:
        name = ('CC BY-SA ' if m.group(1) else 'CC BY ') + m.group(2)
        if not url:
            kind = 'by-sa' if m.group(1) else 'by'
            url = f'https://creativecommons.org/licenses/{kind}/{m.group(2)}/'
        return name, url
    return None


def file_page(title):
    return 'https://commons.wikimedia.org/wiki/' + title.replace(' ', '_')


def imageinfo(titles, width):
    out = {}
    for i in range(0, len(titles), 40):
        r = api(action='query', titles='|'.join(titles[i:i + 40]), prop='imageinfo',
                iiprop='url|extmetadata|size|mime', iiurlwidth=width)
        norm = {n['to']: n['from'] for n in r['query'].get('normalized', [])}
        for pg in r['query']['pages'].values():
            if 'imageinfo' in pg:
                out[norm.get(pg['title'], pg['title'])] = pg['imageinfo'][0]
                out[pg['title']] = pg['imageinfo'][0]
    return out


# ----------------------------------------------------------------------------- discovery

QUERIES = {
    'opp-pantry-boxes': ['volunteers packing food boxes', 'food bank volunteers sorting food'],
    'opp-pantry-greeters': ['food pantry distribution volunteers', 'food bank distribution volunteers serving'],
    'opp-lake-litter': ['volunteers litter pickup park', 'river cleanup volunteers trash bags'],
    'opp-kayak-cleanup': ['kayaks on lake', 'Tempe Town Lake kayak'],
    'opp-papago-trail': ['trail maintenance volunteers tools', 'Papago Park trail'],
    'opp-butte-tidy': ['Hayden Butte Tempe', 'Arizona desert hiking trail'],
    'opp-dog-walks': ['dog on leash walking', 'shelter dog walking volunteer'],
    'opp-kitten-social': ['kittens animal shelter', 'kittens playing'],
    'opp-reading-buddies': ['children library bookshelves', 'picture books library shelf', 'reading a book library'],
    'opp-homework-help': ['tutoring student homework', 'students studying table library'],
    'opp-senior-calls': ['elderly woman telephone call', 'senior man talking on phone', 'grandmother on the phone'],
    'opp-senior-tech': ['elderly woman using tablet', 'old man using smartphone', 'senior citizens computer class'],
    'opp-blood-drive': ['blood donation', 'blood donor drive'],
    'opp-hygiene-kits': ['volunteers packing care packages', 'assembling hygiene kits', 'toiletries donation volunteers'],
    'opp-build-day': ['house framing', 'Habitat for Humanity volunteers build', 'wood frame house construction'],
    'opp-fence-paint': ['painting a fence', 'volunteers painting fence'],
    'opp-dinner-prep': ['chopping vegetables kitchen volunteers', 'community kitchen cooking volunteers'],
    'opp-garden-water': ['community garden watering', 'watering garden vegetables'],
    'opp-mural': ['artist painting mural wall', 'community mural painting', 'painting mural'],
    'opp-concert-ushers': ['outdoor concert audience', 'amphitheater concert crowd'],
    'opp-sandwich-line': ['making sandwiches', 'volunteers prepare sandwiches', 'sandwich preparation'],
    'opp-seed-library': ['seed library', 'seed packets'],
    'city-tempe-1': ['Tempe Town Lake', 'Tempe Arizona skyline'],
    'city-tempe-2': ['Mill Avenue Tempe', 'Tempe Center for the Arts'],
    'city-tempe-3': ['Papago Park Arizona', 'Hole in the Rock Papago'],
}
BAD_WORDS = re.compile(r'\b(logos?|maps?|diagrams?|charts?|posters?|flyers?|screenshots?|icons?|banners?|'
                       r'coats? of arms|infographics?)\b', re.I)
PER_THEME = 8


def discover(only=None):
    os.makedirs(TMP, exist_ok=True)
    cands_path = os.path.join(TMP, 'candidates.json')
    cands = json.load(open(cands_path, encoding='utf-8')) if os.path.exists(cands_path) else {}
    for theme, queries in QUERIES.items():
        if only and theme not in only:
            continue
        if theme in cands and not only:
            continue
        found = []
        for q in queries:
            r = api(action='query', generator='search', gsrnamespace=6,
                    gsrsearch=f'{q} filetype:bitmap filew:>999', gsrlimit=30,
                    prop='imageinfo', iiprop='url|extmetadata|size|mime', iiurlwidth=330)
            pages = sorted(r.get('query', {}).get('pages', {}).values(), key=lambda p: p.get('index', 0))
            for pg in pages:
                ii = pg.get('imageinfo', [{}])[0]
                t = pg['title']
                if any(c['title'] == t for c in found):
                    continue
                if ii.get('mime') not in ('image/jpeg', 'image/png', 'image/webp', 'image/tiff'):
                    continue
                if ii.get('width', 0) < 1000 or ii['width'] < ii.get('height', 0) * 1.15:
                    continue
                if BAD_WORDS.search(t) or BAD_WORDS.search(meta(ii, 'Categories')):
                    continue
                lic = classify_license(ii)
                if not lic:
                    continue
                found.append({'title': t, 'thumb': ii.get('thumburl'), 'w': ii['width'], 'h': ii['height'],
                              'license': lic[0], 'desc': meta(ii, 'ImageDescription')[:200]})
            if len(found) >= PER_THEME * 2:
                break
        found = found[:PER_THEME]
        for i, c in enumerate(found):
            fn = f'{theme}__{i}.jpg'
            c['tmp'] = fn
            if not os.path.exists(os.path.join(TMP, fn)):
                open(os.path.join(TMP, fn), 'wb').write(download(c['thumb']))
        cands[theme] = found
        json.dump(cands, open(cands_path, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
        print(theme, len(found))
    # contact sheets: two themes per sheet
    themes = list(cands)
    for k in range(0, len(themes), 2):
        tiles = [(t, i, c) for t in themes[k:k + 2] for i, c in enumerate(cands[t])]
        W, cols = 300, 4
        rows = max(1, (len(tiles) + cols - 1) // cols)
        sheet = Image.new('RGB', (W * cols, (W * 3 // 4 + 16) * rows), 'white')
        d = ImageDraw.Draw(sheet)
        for n, (t, i, c) in enumerate(tiles):
            im = Image.open(os.path.join(TMP, c['tmp'])).convert('RGB')
            im.thumbnail((W, W * 3 // 4))
            x, y = (n % cols) * W, (n // cols) * (W * 3 // 4 + 16)
            sheet.paste(im, (x, y + 16))
            d.text((x + 2, y + 2), f'{t} #{i}', fill='black')
        sheet.save(os.path.join(TMP, f'sheet_{k // 2:02d}.jpg'), quality=80)


# ----------------------------------------------------------------------------- curated selections
# Filled in after reviewing the discovery contact sheets.
PHOTO_SELECTIONS = {
    'opp-pantry-boxes': ['File:Alaska National Guard covid.jpg', 'File:20220919-DA-TEW-00903.jpg'],
    'opp-pantry-greeters': ['File:20190425-PJK-AMS-0924 TONED.jpg', 'File:20190425-PJK-AMS-0917 TONED.jpg'],
    'opp-lake-litter': ['File:Trinity River Cleanup NPLD (43983508200).jpg',
                        'File:KFOR and Kosovo community members unite for river cleanup (6711435).jpg'],
    'opp-kayak-cleanup': ['File:Two kayaks on a lake.jpg'],
    'opp-papago-trail': ['File:Papago Park.jpg'],
    'opp-butte-tidy': ['File:Tempe Butte 2023 04.jpg'],
    'opp-dog-walks': ['File:Girl walking dog at Lake Johnson.jpg'],
    'opp-kitten-social': ['File:Shleter kitten 6.jpg'],
    'opp-reading-buddies': ["File:Children's Section of Library.jpg"],
    'opp-homework-help': ['File:Tutoring Chemistry.jpg'],
    'opp-senior-calls': ['File:Elderly woman with a laptop on her lap talking on the phone and celebrating.jpg'],
    'opp-senior-tech': ['File:Tablet-PC Parkwohnstift 05.JPG'],
    'opp-blood-drive': ['File:Donating platelets at Swedish blood donation center April 2025.jpg'],
    'opp-hygiene-kits': ['File:Pueblo Chemical Agent-Destruction Pilot Plant Employee Donation Drive (28702014593).jpg'],
    'opp-build-day': ['File:Wood-framed house.jpg'],
    'opp-fence-paint': ['File:Men painting fence green.jpg'],
    'opp-dinner-prep': ['File:20131022-OO-AC-0019 (10440550166).jpg'],
    'opp-garden-water': ['File:Watering the garden.jpg'],
    'opp-mural': ['File:ARTIST RON BLACKBURN PAINTING AN OUTDOOR WALL MURAL AT THE CORNER OF 33RD AND GILES STREETS IN '
                  'CHICAGO. HE IS ONE OF... - NARA - 556229.jpg'],
    'opp-concert-ushers': ['File:Outdoor music.JPG'],
    'opp-sandwich-line': ['File:Sandwich-making.JPG'],
    'opp-seed-library': ['File:Seed Library.jpg'],
    'city-tempe-1': ['File:Tempe Town Lake - panoramio.jpg'],
    'city-tempe-2': ['File:Tempe-Bridge-1931.jpg'],
    'city-tempe-3': ['File:Papago Park, Phoenix, Arizona, USA - 2024 2.jpg'],
}

# (Commons file, crop box as fractions (left, top, right, bottom) or None for auto-centre,
#  apparentGender, apparentAgeBand, description)
AI_PORTRAITS = [
    ('File:GAN Mensch StyleGAN2.png', (0.06, 0.03, 0.94, 0.91), 'man', '20s', 'smiling, short dark hair, light stubble'),
    ('File:This Person Does Not Exist example.jpg', (0.06, 0.03, 0.94, 0.91), 'woman', '40s',
     'broad smile, shoulder-length brown hair'),
    ("File:This person doesn't exist Imagined by a GAN (generative adversarial network) StyleGAN2 (Dec 2019).jpg",
     (0.06, 0.03, 0.94, 0.91), 'man', '30s', 'slight smile, tousled light-brown hair'),
    ('File:John Wealthington.png', (0.06, 0.03, 0.94, 0.91), 'man', '50s',
     'smiling, short grey hair, glasses, dark suit'),
    ('File:Man 2.jpg', (0.06, 0.03, 0.94, 0.91), 'man', '30s', 'calm expression, short dark hair, trimmed beard'),
    ('File:Woman 1.jpg', (0.06, 0.03, 0.94, 0.91), 'woman', '20s', 'soft smile, brown hair with bangs'),
    ('File:Male and female deepfake.jpg', (0.0, 0.0, 0.5, 1.0), 'man', '20s', 'smiling, short brown hair'),
    ('File:Male and female deepfake.jpg', (0.5, 0.0, 1.0, 1.0), 'woman', '20s', 'smiling, long dark hair'),
    ('File:Male facial morphology.jpg', (0.3333, 0.0, 0.6667, 0.775), 'man', '20s',
     'neutral expression, short dark hair, light background'),
    ('File:SDXL P2-4.png', (0.535, 0.095, 0.885, 0.445), 'man', '60s+',
     'older man, grey hair and moustache, navy blazer, looking down'),
    ('File:SDXL P2-2.png', (0.27, 0.135, 0.565, 0.43), 'man', '60s+',
     'older man, white hair, sweater vest, focused on work'),
    ('File:Adobe Firefly generated realistic portrait.jpg', (0.53, 0.13, 0.93, 0.53), 'woman', '20s',
     'smiling, long brown hair, warm golden light'),
]

DICEBEAR_URL = ('https://api.dicebear.com/9.x/avataaars/png?seed={seed}&size=384'
                '&mouth=smile,default,twinkle&eyes=default,happy,wink'
                '&eyebrows=default,defaultNatural,raisedExcited'
                '&backgroundColor=c7d7fe,ffd9cf,c9f2e1,fde7b0&backgroundType=solid')
# Seeds picked from tempe-volunteer-01..72 after visual review (skipped renders with non-natural skin tones,
# eyepatch, or slogan/graphic shirts). Gender/age for cartoons is a loose impression only.
DICEBEAR_SEEDS = [(f'tempe-volunteer-{n:02d}', g, a, d) for n, g, a, d in [
    (1, 'man', '30s', 'short dark hair, winking, blue sweater'),
    (2, 'woman', '60s+', 'long wavy white hair, dark top'),
    (3, 'man', '20s', 'short blond hair, white blazer, winking'),
    (4, 'man', '40s', 'white knit cap, red moustache'),
    (5, 'man', '20s', 'short auburn hair, overalls'),
    (6, 'woman', '30s', 'long wavy brown hair, blue top, winking'),
    (7, 'man', '20s', 'sandy quiff, red shirt'),
    (8, 'person', '30s', 'pink sun hat, red shirt'),
    (9, 'woman', '50s', 'long blonde hair, light blue blazer, eyes closed smiling'),
    (10, 'person', '60s+', 'long silver hair, navy sweater, eyes closed smiling'),
    (11, 'man', '40s', 'full black beard, white headband, grey top'),
    (12, 'man', '20s', 'red quiff, mint blazer, winking'),
    (13, 'woman', '20s', 'big curly hair, light blue headband'),
    (14, 'woman', '30s', 'straight brown hair, grey hoodie'),
    (15, 'woman', '30s', 'blue hijab, smiling'),
    (16, 'man', '30s', 'short blond buzz cut, yellow shirt'),
    (18, 'man', '20s', 'short black hair, blue shirt, winking'),
    (21, 'woman', '40s', 'wavy chestnut hair, pink top'),
    (25, 'man', '30s', 'short dark hair, light blue blazer, winking'),
    (26, 'woman', '20s', 'pink bob, red top, winking'),
    (27, 'man', '60s+', 'short white hair, blue blazer'),
    (28, 'woman', '30s', 'long locs, denim overalls'),
    (33, 'woman', '20s', 'long curly brown hair, white top, winking'),
    (35, 'man', '50s', 'grey beard and moustache, long brown hair, grey blazer'),
    (38, 'man', '40s', 'bald, blue glasses, overalls, winking'),
    (39, 'woman', '30s', 'voluminous curly dark hair, blue headband'),
    (40, 'person', '30s', 'yellow sun hat, blue shirt'),
    (42, 'man', '30s', 'curly hair, full beard, white shirt'),
    (43, 'man', '20s', 'spiky red hair, black blazer'),
    (47, 'man', '30s', 'short dark hair, light grey shirt'),
    (51, 'man', '20s', 'red hair, dark hoodie, winking'),
    (54, 'man', '60s+', 'bald with grey sides, beige blazer'),
    (56, 'woman', '20s', 'long light-brown hair, denim overalls, winking'),
    (63, 'man', '30s', 'short auburn hair, light blue shirt, eyes closed smiling'),
    (64, 'woman', '40s', 'shoulder-length light-brown hair, pink overalls, laughing'),
    (71, 'woman', '40s', 'black hijab, blue top'),
]]


def save_jpeg(im, path, quality, max_bytes=None):
    im = ImageOps.exif_transpose(im).convert('RGB')
    q = quality
    while True:
        buf = io.BytesIO()
        im.save(buf, 'JPEG', quality=q, optimize=True, progressive=True)  # no exif passed -> stripped
        if not max_bytes or buf.tell() <= max_bytes or q <= 60:
            break
        q -= 5
    open(path, 'wb').write(buf.getvalue())
    return q


def build_photos():
    os.makedirs(PHOTOS, exist_ok=True)
    titles = [t for lst in PHOTO_SELECTIONS.values() for t in lst]
    info = imageinfo(titles, 1280)
    credits = []
    for pid, lst in PHOTO_SELECTIONS.items():
        for n, title in enumerate(lst, 1):
            ii = info[title]
            lic = classify_license(ii)
            if not lic:
                raise SystemExit(f'License no longer acceptable: {title}')
            fn = f'{pid}.jpg' if pid.startswith('city-') else f'{pid}-{n}.jpg'
            path = os.path.join(PHOTOS, fn)
            if not os.path.exists(path):
                src = ii.get('thumburl') or ii['url']
                im = Image.open(io.BytesIO(download(src)))
                if im.width > 1200:
                    im = im.resize((1200, round(im.height * 1200 / im.width)), Image.LANCZOS)
                q = save_jpeg(im, path, 80, max_bytes=250_000)
                print(f'{fn}: q{q} {os.path.getsize(path) // 1024} KB')
            credits.append({
                'file': fn,
                'title': title[5:].rsplit('.', 1)[0],
                'sourceUrl': file_page(title),
                'author': meta(ii, 'Artist') or 'Unknown',
                'license': lic[0],
                'licenseUrl': lic[1],
                'changes': 'Resized and recompressed',
            })
    json.dump(credits, open(os.path.join(PHOTOS, 'credits.json'), 'w', encoding='utf-8'),
              indent=2, ensure_ascii=False)
    return credits


def build_portraits():
    os.makedirs(PORTRAITS, exist_ok=True)
    info = imageinfo([p[0] for p in AI_PORTRAITS], 1280) if AI_PORTRAITS else {}
    records = []
    _cache = {}
    n = 0
    for title, box, gender, age, desc in AI_PORTRAITS:
        n += 1
        ii = info[title]
        lic = classify_license(ii)
        if not lic:
            raise SystemExit(f'License not acceptable: {title}')
        fn = f'p{n:02d}.jpg'
        path = os.path.join(PORTRAITS, fn)
        if not os.path.exists(path):
            # thumbnails (1280px) are less rate-limited than originals; cache repeated sources
            src = ii.get('thumburl') or ii['url']
            if src not in _cache:
                _cache[src] = download(src)
            im = ImageOps.exif_transpose(Image.open(io.BytesIO(_cache[src]))).convert('RGB')
            W, H = im.size
            if box:
                l, t, r, b = box
                im = im.crop((round(l * W), round(t * H), round(r * W), round(b * H)))
            else:
                side = min(W, H)
                im = im.crop(((W - side) // 2, (H - side) // 2, (W + side) // 2, (H + side) // 2))
            im = im.resize((384, 384), Image.LANCZOS)
            save_jpeg(im, path, 82)
        records.append({'file': fn, 'kind': 'ai-photo', 'sourceUrl': file_page(title),
                        'author': meta(ii, 'Artist') or 'Unknown', 'license': lic[0], 'licenseUrl': lic[1],
                        'apparentGender': gender, 'apparentAgeBand': age, 'description': desc,
                        'changes': 'Cropped to square, resized to 384x384 and recompressed'})
    for seed, gender, age, desc in DICEBEAR_SEEDS:
        n += 1
        if n > 48:
            break
        fn = f'p{n:02d}.jpg'
        path = os.path.join(PORTRAITS, fn)
        url = DICEBEAR_URL.format(seed=seed)
        if not os.path.exists(path):
            im = Image.open(io.BytesIO(download(url))).convert('RGBA')
            bg = Image.new('RGB', im.size, (255, 255, 255))
            bg.paste(im, mask=im.split()[3])
            save_jpeg(bg, path, 82)
        records.append({'file': fn, 'kind': 'illustrated', 'sourceUrl': url,
                        'author': 'Pablo Stanley (Avataaars), rendered via DiceBear',
                        'license': 'Avataaars by Pablo Stanley — free for personal and commercial use; '
                                   'rendered via DiceBear (MIT)',
                        'licenseUrl': 'https://www.dicebear.com/styles/avataaars/#license',
                        'apparentGender': gender, 'apparentAgeBand': age, 'description': desc,
                        'seed': seed})
    json.dump(records, open(os.path.join(PORTRAITS, 'portraits.json'), 'w', encoding='utf-8'),
              indent=2, ensure_ascii=False)
    return records


def md_escape(s):
    return s.replace('|', '\\|')


def write_license_md(credits, portraits):
    root = os.path.abspath(os.path.join(HERE, '..', '..'))
    lines = ['# Asset licenses', '',
             'Third-party images bundled as seed data for the demo app. Photos were resized and recompressed; '
             'portraits were cropped, resized and recompressed. Machine-readable versions: '
             '`backend/seed-assets/photos/credits.json` and `backend/seed-assets/portraits/portraits.json`.', '',
             '## Opportunity and city photos', '',
             '| File | Author | License | Source |', '|---|---|---|---|']
    for c in credits:
        lines.append(f"| `photos/{c['file']}` | {md_escape(c['author'])} | [{c['license']}]({c['licenseUrl']}) "
                     f"| [{md_escape(c['title'])}]({c['sourceUrl']}) |")
    lines += ['', '## Profile portraits', '',
              'AI-generated photos depict people who do not exist. Illustrated avatars are DiceBear renders of '
              'the Avataaars style.', '',
              '| File | Author | License | Source |', '|---|---|---|---|']
    for p in portraits:
        src = p['sourceUrl']
        label = src.rsplit('/', 1)[-1].replace('_', ' ') if p['kind'] == 'ai-photo' else f"DiceBear seed `{p['seed']}`"
        lines.append(f"| `portraits/{p['file']}` | {md_escape(p['author'])} "
                     f"| [{md_escape(p['license'])}]({p['licenseUrl']}) | [{md_escape(label)}]({src}) |")
    lines += ['', '## Other assets', '',
              '- Map tiles: OpenFreeMap (MIT) © OpenMapTiles, map data © OpenStreetMap contributors (ODbL).',
              '- Fonts: see android/app/src/main/res/font/OFL.txt (SIL Open Font License).',
              '- Badge art, app icon and UI illustrations: original vector artwork created for VolunteerDash.',
              '', '## Note', '',
              'All people, organizations and activities in the demo community are fictional; venue names refer '
              'to real public places in Tempe, AZ.', '']
    open(os.path.join(root, 'ASSET_LICENSES.md'), 'w', encoding='utf-8').write('\n'.join(lines))


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'build'
    if cmd == 'discover':
        discover(sys.argv[2:] or None)
    elif cmd == 'build':
        credits = build_photos()
        portraits = build_portraits()
        write_license_md(credits, portraits)
        print(f'{len(credits)} photos, {len(portraits)} portraits')
