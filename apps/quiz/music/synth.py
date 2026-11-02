# midnox Quiz – eigene Musik und Effekte (komplett synthetisch, frei von Rechten Dritter)
import numpy as np, scipy.signal as sg, subprocess, os
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'assets', 'snd')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def hz(n): return 440.0 * 2 ** ((n - 69) / 12)            # MIDI -> Hz
def t_(d): return np.arange(int(d * SR)) / SR
def env(n, a=0.01, d=0.1, s=0.7, r=0.2, dur=None):
    N = n; A = int(a * SR); D = int(d * SR); R = int(r * SR)
    S = max(0, N - A - D - R)
    e = np.concatenate([np.linspace(0, 1, A, False), np.linspace(1, s, D, False), np.full(S, s), np.linspace(s, 0, R)])
    return np.pad(e, (0, max(0, N - len(e))))[:N]
def lp(x, fc, order=2):
    b, a = sg.butter(order, min(fc, SR / 2 - 100) / (SR / 2)); return sg.lfilter(b, a, x)
def hp(x, fc, order=2):
    b, a = sg.butter(order, fc / (SR / 2), 'high'); return sg.lfilter(b, a, x)
def saw(f, d, detune=0.0):
    t = t_(d); x = np.zeros_like(t)
    for k in range(1, int(min(40, 16000 / max(f, 1))) + 1): x += np.sin(2 * np.pi * k * f * (1 + detune) * t) / k
    return x * 0.6
def tri(f, d):
    t = t_(d); return 2 * np.abs(2 * ((f * t) % 1) - 1) - 1
def sine(f, d, ph=0): return np.sin(2 * np.pi * f * t_(d) + ph)
def bell(f, d):
    t = t_(d)
    x = sine(f, d) + 0.5 * sine(f * 2.76, d) * np.exp(-t * 6) + 0.25 * sine(f * 5.4, d) * np.exp(-t * 10)
    return x * np.exp(-t * 3.2)
def pluck(f, d):
    t = t_(d); x = saw(f, d) ; x = lp(x, 2400) ; return x * np.exp(-t * 7)
def pad(notes, d):
    x = np.zeros(int(d * SR))
    for n in notes:
        for det in (-0.004, 0.0, 0.0045): x += saw(hz(n), d, det)
    x = lp(x, 1400, 2)
    return x * env(len(x), 0.6, 0.3, 0.8, 0.8) / (3 * len(notes))
def kick(d=0.35):
    t = t_(d); f = 48 + 90 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
def hat(d=0.06, soft=0.3):
    x = hp(rng.standard_normal(int(d * SR)), 7000); return x * np.exp(-t_(d) * 70) * soft
def snare_brush(d=0.18):
    x = lp(hp(rng.standard_normal(int(d * SR)), 1500), 6000); return x * np.exp(-t_(d) * 22) * 0.25
def reverb(x, secs=2.2, wet=0.25):
    n = int(secs * SR); ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 7, n)); ir = lp(ir, 5000); ir /= np.sqrt(np.sum(ir ** 2))
    y = sg.fftconvolve(x, ir)[:len(x) + n]
    xx = np.pad(x, (0, n)); y = np.pad(y, (0, len(xx) - len(y)))
    return xx * (1 - wet) + y * wet * 0.6
def place(buf, x, at, gain=1.0):
    i = int(at * SR); j = min(len(buf), i + len(x)); buf[i:j] += x[:j - i] * gain
def stereo(l, r): return np.stack([l, r], 1)
def master(x, peak=0.85):
    x = np.tanh(x * 1.2) ; m = np.max(np.abs(x)); return x / m * peak if m > 0 else x
def save(name, st, loop=False):
    raw = os.path.join(OUT, name + '.f32')
    st.astype(np.float32).tofile(raw)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', raw,
                    '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(raw)
def looped(render, length):
    """Seamless loop: render with tail, fold the tail back onto the start."""
    L, R = render()
    n = int(length * SR)
    def fold(x):
        x = np.pad(x, (0, max(0, 2 * n - len(x))))
        y = x[:n].copy(); tail = x[n:]
        y[:min(n, len(tail))] += tail[:n]
        return y
    return fold(L), fold(R)

# ---------------------------------------------------------------- Frage-Loop (Spannung, a-Moll, 92 BPM, 8 Takte)
def think():
    bpm = 92; beat = 60 / bpm; bar = 4 * beat; bars = 8; total = bars * bar
    N = int((total + 3) * SR); L = np.zeros(N); R = np.zeros(N)
    prog = [(45, [57, 60, 64]), (41, [57, 60, 65]), (43, [55, 59, 62]), (40, [56, 59, 64])] * 2   # Am F G E
    for b, (root, ch) in enumerate(prog):
        t0 = b * bar
        p = pad(ch, bar + 0.6) * 0.55
        place(L, p, t0); place(R, np.roll(p, 300), t0)
        for e in range(8):   # pulsierender Bass in Achteln, „geduckt“
            f = hz(root - 12 + (12 if e % 4 == 3 else 0))
            x = lp(saw(f, beat / 2), 500) * env(int(beat / 2 * SR), 0.005, 0.08, 0.5, 0.06) * 0.42
            place(L, x, t0 + e * beat / 2); place(R, x, t0 + e * beat / 2)
        for q in range(4):
            place(L, kick() * 0.42, t0 + q * beat); place(R, kick() * 0.42, t0 + q * beat)
        for s in range(8):   # leises Ticken wie eine Uhr
            hh = lp(hat(0.05, 0.07 if s % 2 else 0.11), 9000)
            place(L if s % 2 else R, hh, t0 + s * beat / 2)
        if b % 2 == 1: place(L, snare_brush(), t0 + 3.5 * beat); place(R, snare_brush(), t0 + 3.5 * beat)
    motif = [76, 72, 69, 72, 77, 72, 69, 72, 79, 74, 71, 74, 76, 71, 68, 71]  # Glocken-Motiv (eigene Melodie)
    for b in range(bars):
        if b in (2, 3, 6, 7):
            for i in range(4):
                n = motif[(b % 4) * 4 + i]
                x = bell(hz(n), 1.4) * 0.16
                place(L, x, b * bar + i * beat); place(R, np.roll(x, 600), b * bar + i * beat)
    L = reverb(L, 1.8, 0.22); R = reverb(R, 1.9, 0.22)
    return L, R
L, R = looped(think, 8 * 4 * 60 / 92)
save('think', stereo(master(L, 0.7), master(R, 0.7)))

# ---------------------------------------------------------------- Menü-Loop (entspannt, C-Dur, 100 BPM, 8 Takte)
def menu():
    bpm = 100; beat = 60 / bpm; bar = 4 * beat; bars = 8; total = bars * bar
    N = int((total + 3) * SR); L = np.zeros(N); R = np.zeros(N)
    prog = [(48, [60, 64, 67, 71]), (45, [57, 60, 64, 67]), (41, [57, 60, 65, 69]), (43, [59, 62, 65, 67])] * 2  # Cmaj7 Am7 Fmaj7 G7
    for b, (root, ch) in enumerate(prog):
        t0 = b * bar
        p = pad(ch, bar + 0.6) * 0.45
        place(L, p, t0); place(R, np.roll(p, 400), t0)
        bass = lp(tri(hz(root - 12), beat * 1.5), 800) * env(int(beat * 1.5 * SR), 0.01, 0.2, 0.6, 0.2) * 0.5
        place(L, bass, t0); place(R, bass, t0); place(L, bass, t0 + 2.5 * beat); place(R, bass, t0 + 2.5 * beat)
        arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[3] + 12, ch[2] + 12, ch[1] + 12, ch[2] + 12, ch[3] + 12]
        for i, n in enumerate(arp):
            x = pluck(hz(n), 0.6) * 0.16
            place(L if i % 2 else R, x, t0 + i * beat / 2, 1.0); place(R if i % 2 else L, x, t0 + i * beat / 2, 0.45)
        for q in range(4):
            if q % 2 == 0: place(L, kick() * 0.4, t0 + q * beat); place(R, kick() * 0.4, t0 + q * beat)
            else: place(L, snare_brush(), t0 + q * beat); place(R, snare_brush(), t0 + q * beat)
            place(L, hat(0.05, 0.12), t0 + q * beat + beat / 2); place(R, hat(0.05, 0.12), t0 + q * beat + beat / 2)
    L = reverb(L, 2.2, 0.25); R = reverb(R, 2.3, 0.25)
    return L, R
L, R = looped(menu, 8 * 4 * 60 / 100)
save('menu', stereo(master(L, 0.6), master(R, 0.6)))

# ---------------------------------------------------------------- Einloggen: Spannung (2,4 s) – steigender Ton + Herzschlag
d = 2.6; N = int(d * SR); t = t_(d)
f = hz(57) * 2 ** (t / d * 1.0)
sw = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.25 + np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * 0.12
raw_ = sw + 0.15 * saw(hz(45), d); k_ = t / d; sw = (lp(raw_, 900) * (1 - k_) + lp(raw_, 3900) * k_)[:N] * np.linspace(0.2, 1, N) * np.exp(-np.maximum(0, t - 2.2) * 12)
L = sw.copy(); R = np.roll(sw, 200)
for i, at in enumerate([0.0, 0.32, 0.9, 1.22, 1.8, 2.06]):
    k = kick(0.3) * (0.7 if i % 2 == 0 else 0.5); place(L, k, at); place(R, k, at)
L = reverb(L, 1.2, 0.2)[:N + SR // 2]; R = reverb(R, 1.2, 0.2)[:N + SR // 2]
save('lock', stereo(master(L, 0.8), master(R, 0.8)))

# ---------------------------------------------------------------- Richtig (hell, Dur-Arpeggio + Glitzer)
d = 2.0; N = int(d * SR); L = np.zeros(N + SR); R = np.zeros(N + SR)
for i, n in enumerate([72, 76, 79, 84]):
    x = bell(hz(n), 1.6) * 0.35; x[:int(0.8 * SR)] += pluck(hz(n), 0.8) * 0.25
    place(L, x, i * 0.09); place(R, np.roll(x, 250), i * 0.09)
ch = pad([60, 64, 67, 72], 1.6) * 1.2; place(L, ch, 0.3); place(R, ch, 0.3)
for i in range(14):
    s = bell(hz(96 + rng.integers(-3, 8)), 0.5) * 0.06; place(L if i % 2 else R, s, 0.35 + i * 0.07)
L = reverb(L, 1.6, 0.3); R = reverb(R, 1.6, 0.3)
save('right', stereo(master(L, 0.85), master(R, 0.85)))

# ---------------------------------------------------------------- Falsch (zwei absteigende, weiche Töne)
d = 2.0; L = np.zeros(int(3 * SR)); R = np.zeros(int(3 * SR))
for i, (n, at, dur) in enumerate([(58, 0.0, 0.42), (52, 0.45, 1.3)]):
    x = lp(saw(hz(n), dur, 0.0) + saw(hz(n), dur, 0.006) + 0.5 * saw(hz(n - 12), dur), 1100) * env(int(dur * SR), 0.01, 0.15, 0.6, 0.35 if i else 0.08) * 0.35
    place(L, x, at); place(R, np.roll(x, 300), at)
bump = kick(0.5) * 0.6; place(L, bump, 0.45); place(R, bump, 0.45)
L = reverb(L, 1.4, 0.25); R = reverb(R, 1.4, 0.25)
save('wrong', stereo(master(L, 0.8), master(R, 0.8)))

# ---------------------------------------------------------------- Fanfare (Gewinn, Siegerehrung)
bpm = 120; beat = 0.5; L = np.zeros(int(7 * SR)); R = np.zeros(int(7 * SR))
mel = [(67, 0, .5), (72, .5, .5), (76, 1, .5), (79, 1.5, 1.0), (76, 2.5, .5), (79, 3, 2.0)]
for n, at, dur in mel:
    x = lp(saw(hz(n), dur * beat + 0.3) + saw(hz(n), dur * beat + 0.3, 0.005), 3500) * env(int((dur * beat + 0.3) * SR), 0.02, 0.1, 0.8, 0.25) * 0.25
    x += bell(hz(n + 12), dur * beat + 0.5)[:len(x)] * 0.1
    place(L, x, at * beat); place(R, np.roll(x, 300), at * beat)
for at, chord in [(0, [48, 55, 64]), (1.5, [53, 60, 69]), (3, [55, 62, 71]), (3.0, [48, 60, 67, 72])]:
    p = pad(chord, 2.4) * 1.1; place(L, p, at * beat); place(R, p, at * beat)
for q in range(8):
    place(L, kick() * 0.5, q * beat * 0.5 if q < 6 else 1.5 + q * 0.1); place(R, kick() * 0.5, q * beat * 0.5 if q < 6 else 1.5 + q * 0.1)
for i in range(30):
    s = bell(hz(84 + rng.integers(0, 12)), 0.6) * 0.05; place(L if i % 2 else R, s, 1.5 + i * 0.08)
L = reverb(L, 2.5, 0.3); R = reverb(R, 2.5, 0.3)
save('win', stereo(master(L, 0.85), master(R, 0.85)))

# ---------------------------------------------------------------- Tick (letzte Sekunden) und Klick (Auswahl)
x = bell(hz(88), 0.25) * 0.4
save('tick', stereo(x, x))
x = lp(rng.standard_normal(int(0.04 * SR)), 3000) * np.exp(-t_(0.04) * 120) * 0.4 + sine(hz(81), 0.04) * np.exp(-t_(0.04) * 80) * 0.25
save('click', stereo(x, x))
print('fertig')
