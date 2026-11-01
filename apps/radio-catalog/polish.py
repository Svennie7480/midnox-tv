import json, re, sys
from PIL import Image
P = sys.argv[1]
d = json.load(open(P + "/radio.json"))
def clean(n, label):
    n = re.sub(r"\s*\|\s*DLF\s*\|.*$", "", n)
    n = re.sub(r"\s*[-|]\s*(AAC|MP3|AACplus|AAC\+|HD)\b.*$", "", n, flags=re.I)
    n = re.sub(r"[-\s]+(low|lo)$", "", n, flags=re.I)
    n = re.sub(r"\bDdepeche\b", "Depeche", n)
    n = re.sub(r"\b(Wdr|Mdr|Ndr|Swr|Rbb)\b", lambda m: m.group(1).upper(), n)
    n = re.sub(r"\bRADIO BOB! (BOB!?'?s?\s+)+", "RADIO BOB! ", n)
    if label == "ENERGY":
        n = re.sub(r"\s*\(?\bNRJ\b\)?\s*", " ", n, flags=re.I).replace("Radio Energy ", "").replace("Radio ENERGY ", "")
        n = re.sub(r"\s+", " ", n).strip()
    if label == "We aRe One":
        n = re.sub(r"\.fm\b", ".FM", n, flags=re.I)
        n = re.sub(r"^(\w+)Base\.FM", lambda m: m.group(1).capitalize() + "Base.FM", n, flags=re.I)
        n = re.sub(r"^Housetime", "HouseTime", n, flags=re.I)
        if not n.endswith(".FM"): n = n + ".FM"
    return re.sub(r"\s+", " ", n).strip(" -|")
def key(n): return re.sub(r"[^a-z0-9äöüß]", "", n.lower())
tot = 0
for g in d["groups"]:
    seen = {}; out = []
    for s in g["stations"]:
        s["name"] = clean(s["name"], g["name"])
        if s.get("sub"): s["sub"] = clean(s["sub"], g["name"]) if g["name"] != "We aRe One" else s["sub"]
        k = key(s["name"])
        if k in seen:
            o = seen[k]
            if not o.get("logo") and s.get("logo"): o["logo"] = s["logo"]
            continue
        seen[k] = s; out.append(s)
    # Kanalname (sub) = Name ohne Markenpraefix
    if g["type"] == "network":
        for s in out:
            if s["name"].lower().startswith(g["name"].lower() + " "): s["sub"] = s["name"][len(g["name"]) + 1:]
            elif s["name"].lower() == g["name"].lower(): s["sub"] = ""
        out.sort(key=lambda s: (s["name"].lower() != g["name"].lower(), s["name"].lower()))
    g["stations"] = out; tot += len(out)
# midnox-Logo
Image.open("/root/midnox-tv/assets/logos/midnox.jpg").convert("RGB").resize((256, 256), Image.LANCZOS).save(P + "/logos/midnox.png")
d["count"] = tot
json.dump(d, open(P + "/radio.json", "w"), ensure_ascii=False, separators=(",", ":"))
print("Sender:", tot, " Gruppen:", len(d["groups"]))
