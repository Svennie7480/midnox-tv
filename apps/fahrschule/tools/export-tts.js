// Erzeugt tts.json (Schlüssel → gesprochener Text) für den Sprachserver.  node tools/export-tts.js <ziel.json>
const path = require('path'), fs = require('fs');
const W = path.join(__dirname, '..', 'assets', 'www');
global.window = global; global.document = { querySelectorAll: () => [] };
require(path.join(W, 'draw.js')); require(path.join(W, 'gen.js'));
for (const f of fs.readdirSync(path.join(W, 'data')).sort()) require(path.join(W, 'data', f));
const TTS = require(path.join(W, 'tts.js'));
const src = fs.readFileSync(path.join(W, 'app.js'), 'utf8');
const topics = Object.keys(eval('(' + src.match(/const TOPICS = (\{[\s\S]*?\n\});/)[1] + ')'));
const list = GEN.prep([...QDATA, ...GEN.signs(), ...GEN.calc(), ...GEN.vorfahrt(150)].filter(q => topics.includes(q.topic)));
const t = TTS.all(list);
const out = process.argv[2] || 'tts.json';
fs.writeFileSync(out, JSON.stringify({ v: 1, n: list.length, t }));
const chars = Object.values(t).reduce((a, s) => a + s.length, 0), qc = list.reduce((a, q) => a + t[q.id].length, 0);
console.log(out, list.length, 'Fragen,', Object.keys(t).length, 'Texte,', chars, 'Zeichen gesamt (davon Fragen', qc + ')');
