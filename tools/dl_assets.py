import json, os, urllib.request, concurrent.futures, threading, time
BASE = r'F:\MCBBS发布端[喵之物语]\Ultimate Solar\.minecraft\assets'
dl = json.load(open(r'C:\Users\Administrator\AppData\Local\Temp\asset_dl.json', encoding='utf-8'))
MIRRORS = [
    'https://bmclapi2.bangbang93.com/assets/{p}/{h}',
    'https://resources.download.minecraft.net/{p}/{h}',
]
lock = threading.Lock()
done = [0]; failed = []
def fetch(job):
    h, dest = job
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    for m in MIRRORS:
        url = m.format(p=h[:2], h=h)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'UltimateSolar-setup'})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            tmp = dest + '.tmp'
            open(tmp, 'wb').write(data)
            os.replace(tmp, dest)
            with lock:
                done[0] += 1
                if done[0] % 200 == 0:
                    print('progress:', done[0], '/', len(dl), flush=True)
            return True
        except Exception:
            continue
    with lock:
        failed.append(h)
    return False
with concurrent.futures.ThreadPoolExecutor(max_workers=16) as ex:
    list(ex.map(fetch, dl))
print('DONE. failed:', len(failed))
