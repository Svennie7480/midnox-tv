import json, re, subprocess, urllib.parse, os, sys, time
API = "https://de1.api.radio-browser.info/json"
CACHE = "cache"; os.makedirs(CACHE, exist_ok=True)
def get(path, **q):
    q.setdefault("hidebroken", "true")
    url = API + path + "?" + urllib.parse.urlencode(q)
    fn = CACHE + "/" + re.sub(r"[^A-Za-z0-9]+", "_", path + "_" + urllib.parse.urlencode(sorted(q.items())))[:180] + ".json"
    if os.path.exists(fn): return json.load(open(fn))
    for i in range(3):
        r = subprocess.run(["curl", "-s", "-m", "40", "-A", "midnoxRadioBuilder/1.0", url], capture_output=True, text=True)
        try:
            d = json.loads(r.stdout); json.dump(d, open(fn, "w")); return d
        except Exception: time.sleep(2)
    return []
