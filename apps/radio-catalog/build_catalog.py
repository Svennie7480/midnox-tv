#!/usr/bin/env python3
"""Baut den midnox-Radio-Katalog (radio.json + logos/) aus radio-browser.info.

Sendergruppen werden ueber Namenssuche + Stream-Server gefiltert, bereinigt,
dedupliziert, die Streams geprueft und Logos gespiegelt (PNG 256x256).
"""
import json, re, os, sys, subprocess, hashlib, io, concurrent.futures as cf
sys.path.insert(0, os.path.dirname(__file__))
from rb import get
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
os.makedirs(OUT + "/logos", exist_ok=True)

# id, Anzeige, Farbe, Suchbegriffe, URL-Regex, Namens-Regex (fuer CDN-Streams), Praefix der weg soll
FAM = [
    ("ffn", "radio ffn", "#E4002B", ["ffn"], r"ffn\.de|/ffn|ffn[-_.]", r"^(radio\s*)?ffn\b", r"^(radio\s*)?ffn\s*"),
    ("rtl890", "89.0 RTL", "#E30613", ["89.0 RTL", "890 RTL"], r"89\.0rtl|890rtl|rtlinthemix|fhhal-rtl|fhhal_890", r"^89[.,]?0\s*rtl", r"^89[.,]?0\s*rtl\s*"),
    ("sunshine", "sunshine live", "#FFCC00", ["sunshine live"], r"sunshine-live|sunsl", r"^sunshine\s*live", r"^sunshine\s*live\s*"),
    ("bigfm", "bigFM", "#00A3E0", ["bigfm", "big fm"], r"bigfm", r"^big\s*fm", r"^big\s*fm\s*"),
    ("ilove", "I Love Radio", "#E5007D", ["I Love", "ilove"], r"ilovemusic|ilm\.stream|iloveradio", r"^i\s*love", r"^i\s*love\s*(radio\s*)?"),
    ("bob", "RADIO BOB!", "#C8102E", ["radio bob", "bob!"], r"radiobob", r"^radio\s*bob", r"^radio\s*bob!?\s*"),
    ("antenne", "ANTENNE BAYERN", "#0060A9", ["antenne bayern"], r"stream\.antenne\.de|webradio\.antenne\.de", r"^antenne\s*bayern", r"^antenne\s*bayern\s*"),
    ("rockantenne", "ROCK ANTENNE", "#111111", ["rock antenne", "rockantenne"], r"rockantenne", r"^rock\s*antenne", r"^rock\s*antenne\s*"),
    ("ffh", "HIT RADIO FFH", "#E2001A", ["ffh", "planet radio", "harmony"], r"ffh\.de|planetradio", r"^(hit\s*radio\s*)?ffh|^planet\s*radio|^harmony", r"^(hit\s*radio\s*)?ffh\s*"),
    ("energy", "ENERGY", "#E2001A", ["energy"], r"nrj\.de|nrj\.net", None, r"^energy\s*"),
    ("klassik", "Klassik Radio", "#9B7E4B", ["klassik radio"], r"klassikradio|klassikr\.", r"^klassik\s*radio", r"^klassik\s*radio\s*"),
    ("rautemusik", "RauteMusik", "#E2001A", ["rautemusik", "rm.fm"], r"rautemusik", r"^rautemusik", r"^rautemusik(\.fm)?\s*"),
    ("technobase", "We aRe One", "#00E5FF", ["technobase", "housetime", "hardbase", "trancebase", "coretime", "clubtime", "teatime", "replay.fm", "tekk.fm"],
        r"technobase|housetime|hardbase|trancebase|coretime|clubtime|teatime|replay\.fm|tekk\.fm|tb-group", r"(techno|house|hard|trance|core|club|tea)(base|time)|replay\.fm|tekk\.fm", r""),
    ("8090", "80s80s & 90s90s", "#FF2D87", ["80s80s", "90s90s"], r"80s80s|90s90s", r"^(80s80s|90s90s)", r""),
    ("kiss", "KISS FM", "#E4007C", ["kiss fm"], r"kissfm", r"^kiss\s*fm", r"^kiss\s*fm\s*"),
    ("jam", "JAM FM", "#FF6A00", ["jam fm", "jamfm"], r"jam\.fm|jamfm", r"^jam\s*fm", r"^jam\s*fm\s*"),
    ("rsh", "R.SH", "#E2001A", ["R.SH", "rsh"], r"streams\.rsh\.de|rsh\.streamabc", r"^r\.?\s*sh\b", r"^r\.?\s*sh\s*"),
    ("delta", "delta radio", "#00A0DF", ["delta radio"], r"deltaradio", r"^delta\s*radio", r"^delta\s*radio\s*"),
    ("hamburg", "Radio Hamburg", "#E30613", ["radio hamburg"], r"radiohamburg|rhh\.", r"^radio\s*hamburg", r"^radio\s*hamburg\s*"),
    ("ndr", "NDR", "#0C3C8C", ["NDR", "N-JOY"], r"ndr\.de|ndr-radio", r"^(ndr|n-joy)", r""),
    ("wdr", "WDR & 1LIVE", "#00345E", ["1LIVE", "1 LIVE", "WDR", "COSMO"], r"wdr", r"^(1\s*live|wdr)", r""),
    ("br", "Bayerischer Rundfunk", "#0064AA", ["Bayern 1", "Bayern 3", "BR-KLASSIK", "BR24", "PULS", "BR Heimat"], r"br-radio|br\.de|br-mp3", r"^(bayern\s*[13]|br[-\s]|puls|br24)", r""),
    ("swr", "SWR", "#00A1E4", ["SWR"], r"swr", r"^swr", r""),
    ("hr", "hr", "#0068B4", ["hr1", "hr2", "hr3", "hr4", "hr-iNFO", "YOU FM"], r"hr-|hr\.de|hrradio", r"^(hr[1-4]|hr-info|you\s*fm)", r""),
    ("mdr", "MDR", "#0078C0", ["MDR", "MDR JUMP", "SPUTNIK"], r"mdr", r"^(mdr|sputnik)", r""),
    ("rbb", "rbb", "#E30613", ["radioeins", "Fritz", "Antenne Brandenburg", "rbb 88.8", "radio3"], r"rbb", r"^(radioeins|fritz|antenne\s*brandenburg|rbb|radio\s*3)", r""),
    ("dlf", "Deutschlandradio", "#1D4F91", ["Deutschlandfunk", "Dlf"], r"deutschlandradio|dradio|dlf", r"^(deutschlandfunk|dlf)", r""),
    ("schlager", "Schlager", "#F39200", ["schlager radio", "radio paloma", "schlagerparadies", "radio b2"], r"schlagerradio|paloma|schlagerparadies|radiob2", r"^(schlager\s*radio|radio\s*paloma|schlagerparadies|radio\s*b2)", r""),
    ("teddy", "Radio TEDDY", "#00A0E3", ["radio teddy"], r"radioteddy", r"^radio\s*teddy", r"^radio\s*teddy\s*"),
]

L = os.path.expanduser("~/midnox-tv/assets/logos/")
BRAND_LOGOS = {"rtl890": L+"rtl890.jpg", "sunshine": L+"sunshine.jpg", "bigfm": L+"bigfm.jpg", "bob": L+"bob.jpg", "antenne": L+"antenne.jpg", "rockantenne": L+"rockantenne.jpg", "energy": L+"energy.jpg", "klassik": L+"klassik.jpg", "kiss": L+"kiss.jpg", "rsh": L+"rsh.jpg", "delta": L+"delta.jpg", "hamburg": L+"rhh.jpg", "8090": L+"80s80s.jpg"}

GENRES = [  # Anzeige, Tags
    ("Pop & Hits", ["pop", "hits", "top 40"]), ("Rock", ["rock"]), ("Dance & EDM", ["dance", "edm"]),
    ("Techno", ["techno"]), ("House", ["house"]), ("Trance", ["trance"]), ("Hip-Hop & R'n'B", ["hip-hop", "hiphop", "rnb"]),
    ("Schlager", ["schlager"]), ("80er", ["80s", "80er"]), ("90er", ["90s", "90er"]), ("Oldies", ["oldies", "60s", "70s"]),
    ("Chillout & Lounge", ["chillout", "lounge", "ambient"]), ("Jazz", ["jazz"]), ("Klassik", ["classical", "klassik"]),
    ("Metal", ["metal", "heavy metal"]), ("Country", ["country"]), ("Reggae", ["reggae"]), ("Kinder", ["kinder", "kids"]),
    ("Nachrichten & Talk", ["news", "talk", "nachrichten"]),
]
STATES = ["Schleswig-Holstein", "Hamburg", "Mecklenburg-Vorpommern", "Niedersachsen", "Bremen", "Berlin", "Brandenburg",
          "Sachsen-Anhalt", "Sachsen", "Thüringen", "Nordrhein-Westfalen", "Hessen", "Rheinland-Pfalz", "Saarland",
          "Baden-Württemberg", "Bayern"]

def host(u): return u.split("/")[2].lower() if "//" in u else ""
def chan_key(u):
    segs = [x for x in re.sub(r"^https?://", "", u.lower()).split("?")[0].split("/")[1:] if x]
    keep = []
    for x in segs:
        x = re.sub(r"\.(mp3|aac|m3u|pls|ogg|opus)$", "", x)
        x = re.sub(r"[-_](mp3|aac|aacp|ogg|opus)([-_].*)?$", "", x)
        x = re.sub(r"[-_]\d{4,}.*$", "", x)
        if re.match(r"^(mp3|aac|aacp|ogg|opus)([-_]\d+)?$", x) or re.match(r"^(hq|lq|stream|live|listen|surfmusik|radiosure|high|low|0|32|48|56|64|96|112|128|160|192|256|320|;|[a-z]\d+k?)$", x) or "." in x: continue
        keep.append(x)
    dom = ".".join(host(u).split(".")[-2:])
    if keep:
        k = "/".join(keep)
        # generische Pfade (z. B. tunein-mp3) nur zusammen mit der Domain eindeutig
        return k if len(k) > 6 and not re.match(r"^(tunein|stream|listen|live|radio|play|high|mobile)", k) else dom + "/" + k
    return chan_key_old(u)
def chan_key_old(u):
    u = re.sub(r"^https?://", "", u.lower()).split("?")[0].rstrip("/")
    u = re.sub(r"[-_/](mp3|aac|aacp|ogg|opus|hq|lq|low|high)([-_/].*)?$", "", u)
    u = re.sub(r"-\d{4,}$", "", u)
    return u
def norm(n): return re.sub(r"[^a-z0-9äöüß]+", "", re.sub(r"(\d+)(er|s)\b", r"\1", re.sub(r"\b(radio|die|the)\b", "", n.lower())))
def kbps(s):
    m = re.search(r"(?:mp3|aac)[-_/](\d{2,3})", s["url_resolved"]); return int(m.group(1)) if m else (s.get("bitrate") or 128)

def clean_channel(name, strip):
    n = name.strip().replace("\ufffd", "ü")
    n = re.sub(r"\s*\[[^\]]*(kbit|kbps|mp3|aac|ogg|hq|low)[^\]]*\]", "", n, flags=re.I)
    n = re.sub(r"\s*\((ogg|aac|mp3|hq|low quality|high quality)\)", "", n, flags=re.I)
    n = re.sub(r"\s*(low|high)\s*quality\s*$", "", n, flags=re.I)
    n = re.sub(r"\s*by\s*(rautemusik|rm\.fm)\s*\((rm\.fm|rautemusik)\)", "", n, flags=re.I)
    n = re.sub(r"__([^_]+)__", r"\1", n).replace("__", " ")
    n = re.sub(r"(?i)^(radio\s*)?bob!?\s*bob'?s\s+", "RADIO BOB! ", n)
    n = re.sub(r"(?i)^(radio\s*bob!?)\s+bob!\s+", r"\1 ", n)
    n = re.sub(r"\s*\((aac|mp3|hq|lq|\d+\s*kbps)[^)]*\)\s*$", "", n, flags=re.I)
    n = re.sub(r"\s*(aac\+?|mp3|hq)(\s*\d+)?(\s*kbps)?$|\s*\d+\s*kbps$", "", n, flags=re.I)
    if strip: n = re.sub(strip, "", n, flags=re.I)
    n = re.sub(r"^[\s\-–:|#*•.]+|[\s\-–:|*•]+$", "", n).strip()
    n = n.replace("*", "")
    n = re.sub(r"\.fm$", "", n, flags=re.I) if re.search(r"rautemusik", strip or "", re.I) else n
    if re.match(r"^(aac|mp3|ogg)\b", n, re.I): return ""
    if (n.isupper() and len(n) > 4) or (n.islower() and len(n) > 3): n = " ".join((w.upper() if len(w) <= 3 else w) if any(ch.isdigit() for ch in w) else w[:1].upper() + w[1:].lower() for w in n.split(" "))
    return n.strip()

def brand_of(s):
    n = clean_channel(s["name"], "").lower()
    n = re.sub(r"^(radio|die|the)\s+", "", n)
    h = ".".join(host(s["url_resolved"]).split(".")[-2:])
    w = re.split(r"[\s\-–:|]+", n)[0] if n else h
    return w if len(w) > 2 else h

def score(s):
    return ((s["url_resolved"].startswith("https")) * 2 + bool(s["favicon"]) + min(kbps(s), 256) / 128.0
            + min(s.get("votes", 0), 2000) / 500.0 + (s.get("codec", "").upper() in ("MP3", "AAC", "AAC+")) * 2)

def ok_codec(s):
    u = s["url_resolved"].lower()
    return not (u.endswith(".m3u8") or ".m3u8?" in u or s.get("hls") == 1 or u.endswith(".pls") or u.endswith(".m3u") or u.endswith(".asx"))

def test_stream(url):
    r = subprocess.run(["curl", "-s", "-L", "-m", "6", "-A", "VLC/3.0", "-o", "/dev/null", "-w", "%{http_code} %{content_type} %{size_download}", url],
                       capture_output=True, text=True)
    try:
        code, ctype, size = r.stdout.split(" ")[0], r.stdout.split(" ")[1].lower(), float(r.stdout.split(" ")[-1])
    except Exception: return False
    return code == "200" and size > 6000 and ("audio" in ctype or "ogg" in ctype or "octet" in ctype or "mpeg" in ctype or "aac" in ctype)

LOGO_CACHE = {}
def fetch_logo(url):
    if not url or not url.startswith("http"): return None
    if url in LOGO_CACHE: return LOGO_CACHE[url]
    r = subprocess.run(["curl", "-s", "-L", "-m", "10", "-A", "Mozilla/5.0", url], capture_output=True)
    img = None
    try:
        im = Image.open(io.BytesIO(r.stdout)); im.load()
        if min(im.size) >= 48:
            im = im.convert("RGBA")
            bb = im.getbbox()
            if bb: im = im.crop(bb)
            w, h = im.size
            if w / h < 3.5 and h / w < 3.5:   # keine extremen Banner
                s = max(w, h); can = Image.new("RGBA", (s, s), (0, 0, 0, 0)); can.paste(im, ((s - w) // 2, (s - h) // 2))
                img = can.resize((256, 256), Image.LANCZOS)
    except Exception: img = None
    LOGO_CACHE[url] = img
    return img

def save_logo(img, key):
    fn = hashlib.md5(key.encode()).hexdigest()[:12] + ".png"
    path = OUT + "/logos/" + fn
    if not os.path.exists(path):
        im = img
        if im.getextrema()[3][0] == 255:   # undurchsichtig -> runde Ecken bleiben der App
            pass
        im.save(path, optimize=True)
    return "logos/" + fn

def collect(queries, url_re, name_re):
    seen, out = set(), []
    for q in queries:
        for s in get("/stations/search", name=q, limit=500):
            if s["stationuuid"] in seen: continue
            u = s["url_resolved"] or s["url"]
            if re.search(url_re, u, re.I) or (name_re and re.search(name_re, s["name"].strip(), re.I) and not re.search(r"laut\.fm|radio\.net|streema", u, re.I)):
                if s.get("lastcheckok") == 1 and ok_codec(s) and s.get("countrycode", "") in ("DE", "AT", "CH", "") and "\ufffd" not in s["name"][:3]:
                    seen.add(s["stationuuid"]); out.append(s)
    return out

def pick(groups, need_ok=True):
    """groups: Liste von Kandidatenlisten -> bester funktionierender Stream je Gruppe (parallel)."""
    def best(c):
        cand = sorted(c, key=score, reverse=True)
        for s in cand[:3]:
            if test_stream(s["url_resolved"]): return s
        return cand[0] if cand and cand[0].get("lastcheckok") == 1 else None   # Test von hier unzuverlaessig (CDN)
    with cf.ThreadPoolExecutor(32) as ex: return list(ex.map(best, groups))

def family(f):
    fid, label, color, queries, url_re, name_re, strip = f
    raw = collect(queries, url_re, name_re)
    # nach Kanal gruppieren (gleicher Stream-Pfad ODER gleicher bereinigter Name)
    groups, by_key, by_name = [], {}, {}
    for s in raw:
        ch = clean_channel(s["name"], strip) or label
        k, n = chan_key(s["url_resolved"]), norm(ch)
        gi = by_key.get(k, by_name.get(n))
        if gi is None:
            gi = len(groups); groups.append({"name": ch, "c": []})
        groups[gi]["c"].append(s); by_key[k] = gi; by_name[n] = gi
    for g in groups:   # Name vom beliebtesten Eintrag
        top = max(g["c"], key=lambda x: x.get("votes", 0))
        g["name"] = clean_channel(top["name"], strip) or label
    chosen = pick([g["c"] for g in groups])
    out = []
    for g, s in zip(groups, chosen):
        if not s: continue
        logo_src = s["favicon"] or next((x["favicon"] for x in sorted(g["c"], key=lambda x: -x.get("votes", 0)) if x["favicon"]), "")
        out.append({"g": g, "s": s, "logo_src": logo_src})
    return out

def station(s, name, sub, logo_src, color, fam_logo=None):
    img = fetch_logo(logo_src) if logo_src else None
    logo = save_logo(img, logo_src) if img else fam_logo
    tags = [t for t in (s.get("tags") or "").split(",") if t][:4]
    return {"id": s["stationuuid"], "name": name, "sub": sub, "url": s["url_resolved"], "logo": logo,
            "color": color, "tags": tags, "codec": s.get("codec", ""), "kbps": kbps(s), "home": s.get("homepage", "")}

def main():
    only = os.environ.get("ONLY")
    global FAM, GENRES, STATES
    if only: FAM = [f for f in FAM if f[0] in only.split(",")]; GENRES = GENRES if "genres" in only else []; STATES = STATES if "regions" in only else []
    cat = {"version": 1, "groups": []}
    # midnox zuerst
    cat["groups"].append({"id": "midnox", "name": "midnox", "type": "featured", "stations": [{
        "id": "midnox", "name": "midnox Radio", "sub": "Electronic · Dance · eigene Produktionen", "url": "https://radio.midnox.de/listen/midnox/radio.mp3",
        "logo": "logos/midnox.png", "color": "#7C3AED", "tags": ["electronic", "dance", "midnox"], "codec": "MP3", "kbps": 192,
        "home": "https://midnox.de", "nowplaying": "https://radio.midnox.de/api/nowplaying/midnox"}]})
    total = 1
    for f in FAM:
        res = family(f)
        if not res: print("  !! leer:", f[1]); continue
        fid, label, color = f[0], f[1], f[2]
        # Familienlogo = Logo des Hauptsenders (meiste Stimmen)
        fam_logo = None
        for r in sorted(res, key=lambda r: -r["s"].get("votes", 0)):
            im = fetch_logo(r["logo_src"])
            if im: fam_logo = save_logo(im, r["logo_src"]); break
        lf = BRAND_LOGOS.get(fid)
        if lf and os.path.exists(lf):
            im = Image.open(lf).convert("RGBA").resize((256, 256), Image.LANCZOS); fam_logo = save_logo(im, "brand:" + fid)
        sts = []
        for r in res:
            nm = r["g"]["name"]
            full = label if nm == label else (nm if fid in ("ndr", "wdr", "br", "swr", "hr", "mdr", "rbb", "dlf", "schlager", "technobase", "8090") else label + " " + nm)
            sts.append(station(r["s"], full, nm if full != nm else "", r["logo_src"], color, fam_logo))
        # Hauptsender zuerst, dann alphabetisch
        sts.sort(key=lambda x: (x["name"] != label, x["name"].lower()))
        cat["groups"].append({"id": fid, "name": label, "type": "network", "color": color, "logo": fam_logo, "stations": sts})
        total += len(sts); print(f"{label}: {len(sts)}  (Rohdaten {sum(len(r['g']['c']) for r in res)})", flush=True)
    # Genres
    for gname, tags in GENRES:
        cand, seen = [], set()
        for t in tags:
            for s in get("/stations/bytag/" + t, order="votes", reverse="true", limit=120):
                if s["countrycode"] in ("DE", "AT", "CH") and s["stationuuid"] not in seen and s.get("lastcheckok") == 1 and ok_codec(s):
                    seen.add(s["stationuuid"]); cand.append(s)
        cand.sort(key=lambda s: -s.get("votes", 0))
        groups, names, brands = [], set(), {}
        for s in cand:
            n = norm(clean_channel(s["name"], ""))
            if n in names or not n: continue
            br = brand_of(s)
            if brands.get(br, 0) >= 3: continue
            brands[br] = brands.get(br, 0) + 1
            names.add(n); groups.append([s])
            if len(groups) >= 40: break
        chosen = [s for s in pick(groups) if s][:30]
        sts = [station(s, clean_channel(s["name"], ""), "", s["favicon"], None) for s in chosen]
        cat["groups"].append({"id": "g_" + norm(gname), "name": gname, "type": "genre", "stations": sts})
        total += len(sts); print(f"Genre {gname}: {len(sts)}", flush=True)
    # Regionen
    for st in STATES:
        cand = [s for s in get("/stations/search", state=st, countrycode="DE", order="votes", reverse="true", limit=150) if s.get("lastcheckok") == 1 and ok_codec(s)]
        groups, names, brands = [], set(), {}
        for s in cand:
            n = norm(clean_channel(s["name"], ""))
            if n in names or not n: continue
            br = brand_of(s)
            if brands.get(br, 0) >= 3: continue
            brands[br] = brands.get(br, 0) + 1
            names.add(n); groups.append([s])
            if len(groups) >= 36: break
        chosen = [s for s in pick(groups) if s][:28]
        sts = [station(s, clean_channel(s["name"], ""), "", s["favicon"], None) for s in chosen]
        cat["groups"].append({"id": "r_" + norm(st), "name": st, "type": "region", "stations": sts})
        total += len(sts); print(f"Region {st}: {len(sts)}", flush=True)
    json.dump(cat, open(OUT + "/radio.json", "w"), ensure_ascii=False, indent=0)
    print("GESAMT", total)

if __name__ == "__main__":
    main()
