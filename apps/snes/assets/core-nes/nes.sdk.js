// midnox Retro – NES-Core-Adapter.
// Hüllt jsnes (reiner JS-NES-Emulator) in dieselbe Schnittstelle wie die
// @wasm-gaming-Cores: load(config) -> { start, pause, resume, reset, input,
// saveState, loadState, screenshot, setTurbo, setMute, setSmoothing, destroy }.
// Audio läuft über einen eigenen AudioWorklet-Ring (meldet die echte
// Leseposition, damit die Latenz ehrlich bei ~90 ms bleibt).

let jsnesPromise = null;
function loadJsnes(url) {
  if (jsnesPromise) return jsnesPromise;
  jsnesPromise = new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = url; s.async = true;
    s.onload = () => res();
    s.onerror = () => rej(new Error('nes: jsnes konnte nicht geladen werden'));
    document.head.appendChild(s);
  });
  return jsnesPromise;
}

const WORKLET_SOURCE = `
class NesSink extends AudioWorkletProcessor {
  constructor() {
    super();
    this.cap = 32768;
    this.buf = new Float32Array(this.cap * 2);
    this.r = 0; this.w = 0; this.lastPost = 0;
    this.port.onmessage = (e) => {
      const s = e.data; const frames = s.length >> 1;
      for (let i = 0; i < frames; i++) {
        if (this.w - this.r >= this.cap) break;
        const idx = (this.w % this.cap) * 2;
        this.buf[idx] = s[i*2]; this.buf[idx+1] = s[i*2+1];
        this.w++;
      }
    };
  }
  process(inputs, outputs) {
    const out = outputs[0]; const L = out[0]; const R = out[1] || out[0]; const n = L.length;
    for (let i = 0; i < n; i++) {
      if (this.r < this.w) { const idx = (this.r % this.cap) * 2; L[i] = this.buf[idx]; R[i] = this.buf[idx+1]; this.r++; }
      else { L[i] = 0; R[i] = 0; }
    }
    if (this.r - this.lastPost >= 512) { this.port.postMessage(this.r); this.lastPost = this.r; }
    return true;
  }
}
registerProcessor('nes-sink', NesSink);
`;

export async function load(config) {
  const canvas = config.canvasEl;
  const opts = config.options || {};
  canvas.width = 256; canvas.height = 240;
  canvas.style.imageRendering = 'pixelated';
  canvas.style.aspectRatio = opts.aspect === 'native' ? '256 / 240' : '4 / 3';
  const ctx2d = canvas.getContext('2d');
  const img = ctx2d.createImageData(256, 240);
  const data = img.data;
  for (let i = 0; i < 256 * 240; i++) data[i * 4 + 3] = 255;

  // ---- Audio ----
  const audioCtx = new AudioContext();
  const sampleRate = Math.round(audioCtx.sampleRate);
  const wUrl = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' }));
  await audioCtx.audioWorklet.addModule(wUrl); URL.revokeObjectURL(wUrl);
  const sink = new AudioWorkletNode(audioCtx, 'nes-sink', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [2] });
  const gain = audioCtx.createGain(); gain.gain.value = 1;
  sink.connect(gain).connect(audioCtx.destination);
  let enqueued = 0, consumed = 0;
  sink.port.onmessage = (e) => { consumed = e.data; };

  // ---- jsnes laden ----
  await loadJsnes(new URL('./jsnes.js', import.meta.url).href);
  const NES = (window.jsnes || globalThis.jsnes).NES;

  let abuf = new Float32Array(8192), an = 0;
  const onAudioSample = (l, r) => { if (an + 2 > abuf.length) { const nb = new Float32Array(abuf.length * 2); nb.set(abuf); abuf = nb; } abuf[an++] = l; abuf[an++] = r; };
  let frameBuf = null;
  const nes = new NES({ sampleRate, emulateSound: true, onFrame: (fb) => { frameBuf = fb; }, onAudioSample });

  // jsnes akzeptiert das ROM direkt als Uint8Array
  const rom = config.assets.rom;
  nes.loadROM(rom);

  const framerate = 60.0988;
  const TARGET = Math.round(sampleRate * 0.09);
  const MAXT = 4, FFMAX = 8;
  let turbo = false, running = false, paused = false, rafId = 0;
  const lastMask = [0, 0];

  function flushAudio() {
    if (an === 0) return;
    if (!turbo) { const chunk = abuf.slice(0, an); sink.port.postMessage(chunk, [chunk.buffer]); enqueued += an >> 1; }
    an = 0;
  }
  function render() {
    if (!frameBuf) return;
    for (let i = 0; i < 256 * 240; i++) { const p = frameBuf[i], j = i * 4; data[j] = p & 0xFF; data[j + 1] = (p >> 8) & 0xFF; data[j + 2] = (p >> 16) & 0xFF; }
    ctx2d.putImageData(img, 0, 0);
  }
  function runFrames(n) { for (let i = 0; i < n; i++) { try { nes.frame(); } catch (e) {} flushAudio(); } render(); }

  let lastT = -1, lastWall = 0, stalled = false;
  const clockOk = (now) => { const t = audioCtx.currentTime; if (t !== lastT) { lastT = t; lastWall = now; stalled = false; } else if (now - lastWall > 500) stalled = true; return !stalled; };
  let wallStart = 0, wallFrames = 0;
  function tick(now) {
    if (!running || paused) return;
    rafId = requestAnimationFrame(tick);
    let frames = 0;
    if (turbo) { frames = FFMAX; wallStart = 0; }
    else if (audioCtx.state === 'running' && clockOk(now)) { const buffered = enqueued - consumed; const deficit = TARGET - buffered; if (deficit > 0) frames = Math.ceil(deficit / (sampleRate / framerate)); wallStart = 0; }
    else { if (!wallStart) { wallStart = now; wallFrames = 0; } const due = Math.floor(((now - wallStart) / 1000) * framerate); frames = due - wallFrames; wallFrames = due; }
    frames = Math.max(0, Math.min(turbo ? FFMAX : MAXT, frames));
    if (frames > 0) runFrames(frames);
  }
  const resumeAudio = () => { if (audioCtx.state === 'suspended') void audioCtx.resume(); };
  window.addEventListener('pointerdown', resumeAudio);
  window.addEventListener('keydown', resumeAudio);

  // Meine Bitmaske -> jsnes-Buttons (A=256,B=1,Select=4,Start=8,Dpad). X/Y/L/R entfallen.
  const BITMAP = { 256: 0, 1: 1, 4: 2, 8: 3, 16: 4, 32: 5, 64: 6, 128: 7 };

  return {
    system: 'nes',
    start() { if (running) return; running = true; paused = false; resumeAudio(); rafId = requestAnimationFrame(tick); },
    pause() { if (!running || paused) return; paused = true; if (rafId) cancelAnimationFrame(rafId); rafId = 0; void audioCtx.suspend(); },
    resume() { if (!running || !paused) return; paused = false; void audioCtx.resume(); rafId = requestAnimationFrame(tick); },
    reset() { try { nes.reset(); nes.loadROM(rom); } catch (e) {} },
    input(p, mask) {
      const c = (p | 0) + 1; if (c < 1 || c > 2) return;
      const prev = lastMask[p] | 0;
      for (const bit in BITMAP) { const bv = +bit, was = prev & bv, now = mask & bv; if (was && !now) nes.buttonUp(c, BITMAP[bit]); else if (!was && now) nes.buttonDown(c, BITMAP[bit]); }
      lastMask[p] = mask;
    },
    async saveState() { return new TextEncoder().encode(JSON.stringify(nes.toJSON())); },
    async loadState(bytes) { nes.fromJSON(JSON.parse(new TextDecoder().decode(bytes))); },
    async screenshot() { render(); return new Promise((res, rej) => canvas.toBlob((b) => b ? res(b) : rej(new Error('nes: screenshot')), 'image/png')); },
    setTurbo(on) { turbo = !!on; },
    setMute(on) { gain.gain.value = on ? 0 : 1; },
    setSmoothing(on) { canvas.style.imageRendering = on ? 'auto' : 'pixelated'; },
    setInput() {},
    destroy() { running = false; if (rafId) cancelAnimationFrame(rafId); window.removeEventListener('pointerdown', resumeAudio); window.removeEventListener('keydown', resumeAudio); try { sink.disconnect(); gain.disconnect(); void audioCtx.close(); } catch (e) {} },
  };
}
export default { load };
