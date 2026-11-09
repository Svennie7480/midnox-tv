NES-Core: jsnes (reiner JS-Emulator) aus npm jsnes@2.1.0 (dist/jsnes.min.js -> jsnes.js).
Nicht im Repo: jsnes.js (Vendor). Hier liegt nur nes.sdk.js – mein Adapter, der jsnes
in dieselbe Schnittstelle wie die @wasm-gaming-Cores hüllt (load(config) mit start/pause/
resume/reset/input/saveState/loadState/screenshot/setTurbo/setMute/setSmoothing).
