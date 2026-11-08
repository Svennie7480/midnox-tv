snes9x.wasm + die übrigen Core-JS nicht im Repo (2 MB, aus npm @wasm-gaming/snes9x-wasm beziehbar).
Ausnahme: snes9x.sdk.js liegt hier, weil sie einen lokalen Patch trägt:
Der Audio-Worklet meldet die echte Leseposition (this.r) statt ausgegebener Quanten
(this.consumed), sonst staut sich die Tonlatenz bis zur Ringpuffer-Grenze (~1 s).
Beim Neubeziehen aus npm diesen Patch erneut anwenden.
