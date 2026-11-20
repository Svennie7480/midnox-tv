#!/usr/bin/env python3
# Rendert alle Sprachclips des Krimi-Abends mit ElevenLabs nach assets/voice/ und schreibt voice/index.json.
#   Erzähler: Einleitungen, Akte, Verhörfragen, Lösungen, Lupen-Funde
#   Figuren:  Aussagen im Detektiv-Modus – jede Figur mit eigener Stimme (nach Geschlecht verteilt)
# Key nur aus der Umgebung – NIE ins Repo schreiben:
#   ELEVEN_KEY=... [ELEVEN_VOICE=<erzähler-id>] python3 tools/gen_voice.py [--only <fall-id>] [--force]
import os, sys, json, subprocess, urllib.request, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "assets" / "voice"
KEY = os.environ.get("ELEVEN_KEY")
NARRATOR = os.environ.get("ELEVEN_VOICE", "JBFqnCBsd6RMkjVDRZzb")        # George – warmer Erzähler
MODEL = os.environ.get("ELEVEN_MODEL", "eleven_multilingual_v2")
# Stimmen für Figuren (ElevenLabs-Standardstimmen, mehrsprachig). Per Umgebung überschreibbar: VOICES_M / VOICES_F (kommagetrennt)
POOL = {
  "m": os.environ.get("VOICES_M", "nPczCjzI2devNBz1zQrb,onwK4e9ZLuTAKqWW03F9,pqHfZKP75CVOlQylNhV4,iP95p4xoKVk53GoZ742B,cjVigY5qzO86Huf0OWal,TX3LPaxmHKxFdv7VOQHJ,N2lVS1w4EtoT3dr4eOWO,CwhRBWXzGAHq8TQ4Fs17").split(","),
  "f": os.environ.get("VOICES_F", "EXAVITQu4vr4xnSDxMaL,FGY2WhTYpPnrIDTdsKH5,Xb7hH8MSUJpSbSDYk0k2,XrExE9yKIg1WjnnlVkGX,cgSgspJ2msm6clMCkdW9,pFZP5JQG7iQjIQuC4Bku").split(","),
}

def load():
    a = ROOT / "assets"
    js = ("global.window={};require(%r);require(%r);require(%r);"
          "process.stdout.write(JSON.stringify({c:window.CASES,d:window.DETECTIVE}))") % (str(a/"cases.js"), str(a/"detective.js"), str(a/"cases2.js"))
    return json.loads(subprocess.check_output(["node", "-e", js]))

def lines():
    data = load(); cases, det = data["c"], data["d"]
    for c in cases:
        cid = c["id"]; used = {"m": 0, "f": 0}
        for i, t in enumerate(c["intro"]): yield cid, "%s_intro%d" % (cid, i), t, NARRATOR
        for a, act in enumerate(c["acts"], 1):
            yield cid, "%s_act%d" % (cid, a), act["text"], NARRATOR
            for qi, q in enumerate(act.get("ask", [])):
                yield cid, "%s_q%d_%d" % (cid, a, qi), "%s: %s" % (c["chars"][q["c"]]["name"], q["q"]), NARRATOR
        for i, t in enumerate(c["solution"]): yield cid, "%s_sol%d" % (cid, i), t, NARRATOR
        d = det.get(cid)
        if not d: continue
        for ci, ch in enumerate(c["chars"]):
            g = ch.get("g", "m"); pool = POOL[g]; v = pool[used[g] % len(pool)]; used[g] += 1
            for a, t in enumerate(d["talk"][ci], 1): yield cid, "%s_t%d_%d" % (cid, ci, a), t, v
        for idx, e in enumerate([e for act in c["acts"] for e in act["evidence"]]):
            t = d["look"].get(e["name"])
            if t: yield cid, "%s_l%d" % (cid, idx), "%s. %s" % (e["name"], t), NARRATOR

def render(key, text, voice):
    req = urllib.request.Request("https://api.elevenlabs.io/v1/text-to-speech/%s?output_format=mp3_44100_96" % voice,
        data=json.dumps({"text": text, "model_id": MODEL, "language_code": "de",
                         "voice_settings": {"stability": 0.5, "similarity_boost": 0.8, "style": 0.35}}).encode(),
        method="POST", headers={"xi-api-key": KEY, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(req, timeout=120) as r: data = r.read()
    (OUT / (key + ".mp3")).write_bytes(data)
    print("  ✓ %-20s %6d B  %s" % (key, len(data), voice[:6]))

def write_index():
    OUT.mkdir(parents=True, exist_ok=True)
    keys = sorted(p.stem for p in OUT.glob("*.mp3"))
    (OUT / "index.json").write_text(json.dumps(keys))
    print("index.json: %d Clips" % len(keys))

if __name__ == "__main__":
    if "--index" in sys.argv: write_index(); sys.exit()
    if "--count" in sys.argv:
        L = list(lines()); todo = [x for x in L if not (OUT / (x[1] + ".mp3")).exists()]
        print(len(L), "Clips gesamt,", len(todo), "fehlen,", sum(len(x[2]) for x in todo), "Zeichen"); sys.exit()
    if not KEY: sys.exit("ELEVEN_KEY setzen.")
    only = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    OUT.mkdir(parents=True, exist_ok=True)
    for cid, k, t, v in lines():
        if only and cid != only: continue
        if (OUT / (k + ".mp3")).exists() and "--force" not in sys.argv: continue
        try: render(k, t, v)
        except Exception as e: print("  ✗ %-20s %s" % (k, e))
    write_index()
