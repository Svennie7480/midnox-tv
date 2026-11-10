#!/usr/bin/env python3
# Rendert die Ansager-Sätze von midnox Party mit ElevenLabs zu MP3s in assets/voice/.
# Key & Stimme kommen aus der Umgebung – NICHT ins Repo schreiben:
#   ELEVEN_KEY=... ELEVEN_VOICE=<voice_id> python3 tools/gen_voice.py
import os, sys, json, urllib.request, pathlib

KEY = os.environ.get("ELEVEN_KEY")
VOICE = os.environ.get("ELEVEN_VOICE", "")  # Stimmen-ID; leer -> Fehlermeldung
MODEL = os.environ.get("ELEVEN_MODEL", "eleven_multilingual_v2")
OUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "voice"

LINES = {
    "willkommen": "Willkommen bei midnox Party!",
    "los": "Los!",
    "bonus": "Bonusrunde! Doppelte Punkte!",
    "sieger": "Und der Sieger steht fest!",
    "ausgeschieden": "Ausgeschieden!",
    "game_reaktor": "Reaktor!",
    "game_trivia": "Buzzer Battle!",
    "game_estimate": "Schätzmeister!",
    "game_bomb": "Wort-Bombe!",
    "game_reveal": "Flaggen-Blitz!",
    "game_flunkerei": "Flunkerei!",
    "game_kritzel": "Kritzel-Klau!",
    "game_duel": "Mal-Battle!",
    "game_royale": "Royale! Wer überlebt am längsten?",
}

def render(key, text):
    url = "https://api.elevenlabs.io/v1/text-to-speech/%s" % VOICE
    body = json.dumps({
        "text": text,
        "model_id": MODEL,
        "voice_settings": {"stability": 0.4, "similarity_boost": 0.8, "style": 0.3}
    }).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "xi-api-key": KEY, "Content-Type": "application/json", "Accept": "audio/mpeg"
    })
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    (OUT / (key + ".mp3")).write_bytes(data)
    print("  ✓ %-16s %5d B  „%s\"" % (key, len(data), text))

def main():
    if not KEY or not VOICE:
        sys.exit("Bitte ELEVEN_KEY und ELEVEN_VOICE (Stimmen-ID) als Umgebungsvariablen setzen.")
    OUT.mkdir(parents=True, exist_ok=True)
    print("Rendere %d Ansager-Clips nach %s" % (len(LINES), OUT))
    for k, t in LINES.items():
        try: render(k, t)
        except Exception as e: print("  ✗ %-16s FEHLER: %s" % (k, e))
    print("Fertig. Danach die App neu bauen (./build.sh).")

if __name__ == "__main__":
    main()
