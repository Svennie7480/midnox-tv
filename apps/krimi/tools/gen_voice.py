#!/usr/bin/env python3
# Rendert alle Erzählertexte des Krimi-Abends mit ElevenLabs zu MP3s in assets/voice/.
# Key & Stimme nur aus der Umgebung – NIE ins Repo schreiben:
#   ELEVEN_KEY=... ELEVEN_VOICE=<voice_id> python3 tools/gen_voice.py [--only seeblick]
import os, sys, json, subprocess, urllib.request, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "assets" / "voice"
KEY, VOICE = os.environ.get("ELEVEN_KEY"), os.environ.get("ELEVEN_VOICE", "")
MODEL = os.environ.get("ELEVEN_MODEL", "eleven_multilingual_v2")

def lines():
    js = "global.window={};require(%r);process.stdout.write(JSON.stringify(window.CASES))" % str(ROOT / "assets" / "cases.js")
    cases = json.loads(subprocess.check_output(["node", "-e", js]))
    for c in cases:
        for i, t in enumerate(c["intro"]): yield c["id"], "%s_intro%d" % (c["id"], i), t
        for a, act in enumerate(c["acts"], 1):
            yield c["id"], "%s_act%d" % (c["id"], a), act["text"]
            for qi, q in enumerate(act["ask"]):
                yield c["id"], "%s_q%d_%d" % (c["id"], a, qi), "%s: %s" % (c["chars"][q["c"]]["name"], q["q"])
        for i, t in enumerate(c["solution"]): yield c["id"], "%s_sol%d" % (c["id"], i), t

def render(key, text):
    req = urllib.request.Request("https://api.elevenlabs.io/v1/text-to-speech/%s?output_format=mp3_44100_96" % VOICE,
        data=json.dumps({"text": text, "model_id": MODEL,
                         "voice_settings": {"stability": 0.55, "similarity_boost": 0.8, "style": 0.35}}).encode(),
        method="POST", headers={"xi-api-key": KEY, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(req, timeout=120) as r: data = r.read()
    (OUT / (key + ".mp3")).write_bytes(data)
    print("  ✓ %-18s %6d B" % (key, len(data)))

if __name__ == "__main__":
    if not KEY or not VOICE: sys.exit("ELEVEN_KEY und ELEVEN_VOICE setzen.")
    only = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    OUT.mkdir(parents=True, exist_ok=True)
    for cid, k, t in lines():
        if only and cid != only: continue
        if (OUT / (k + ".mp3")).exists() and "--force" not in sys.argv: continue
        try: render(k, t)
        except Exception as e: print("  ✗ %-18s %s" % (k, e))
