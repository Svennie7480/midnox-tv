'use strict';
/* midnox Führerschein – automatisch erzeugte Fragen: Verkehrszeichen, Rechnen (Reaktions-/Brems-/Anhalteweg, Abstand), Vorfahrt an Kreuzungen.
   Jede Frage hat eine feste ID, damit der Lernstand erhalten bleibt. */

const GEN = {};
window.QDATA = window.QDATA || [];
/** Kurzschreibweise für handgeschriebene Fragen: Antworten mit „+“ sind richtig, mit „-“ falsch */
window.QF = (id, topic, pts, q, ans, ex, img) => QDATA.push({ id: 'h-' + id, topic, pts, q, a: ans.map(t => ({ t: t.slice(1), ok: t[0] === '+' })), ex, img });
window.QN = (id, topic, pts, q, ans, unit, ex, img) => QDATA.push({ id: 'h-' + id, topic, pts, q, type: 'num', ans, unit, ex, img });

// kleiner, fester Zufallsgenerator (gleiche Fragen auf jedem Gerät)
GEN.rng = (seed) => { let x = seed >>> 0 || 1; return () => { x ^= x << 13; x >>>= 0; x ^= x >> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; }; };
GEN.shuffle = (a, r) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };

// ------------------------------------------------------------------ Verkehrszeichen: Was bedeutet …?
GEN.SIGN_FAM = {
  gefahr: ['101', '102', '103-10', '103-20', '105-10', '108', '110', '112', '114', '117', '120', '123', '125', '131', '133', '136', '138', '142', '151'],
  vorfahrt: ['205', '206', '301', '306', '307', '308', '208', '102', '201'],
  gebot: ['209-20', '211-10', '214', '215', '220-20', '222', '237', '239', '240', '241-30'],
  verbot: ['250', '251', '254', '255', '259', '260', '262', '264', '265', '267', '276', '277', '283', '286'],
  tempo: ['274-30', '274-50', '274-70', '274-100', '274.1', '274.2', '278-50', '280', '282'],
  richt: ['310', '311', '314', '325.1', '330.1', '331.1', '350', '357'],
};
GEN.signs = () => {
  const out = [], all = Object.values(GEN.SIGN_FAM).flat();
  for (const [fam, codes] of Object.entries(GEN.SIGN_FAM)) for (const code of codes) {
    const r = GEN.rng(code.split('').reduce((a, c) => a * 31 + c.charCodeAt(0), 7));
    const pool = GEN.shuffle(codes.filter(c => c !== code && DRAW.signName(c) !== DRAW.signName(code)), r).concat(GEN.shuffle(all.filter(c => !codes.includes(c)), r));
    const wrong = []; for (const c of pool) { const n = DRAW.signName(c); if (!wrong.includes(n) && n !== DRAW.signName(code)) wrong.push(n); if (wrong.length === 2) break; }
    out.push({ id: 'zs-' + code, cat: 'G', topic: 'zeichen', pts: ['205', '206', '267', '201', '306', '301', '283'].includes(code) ? 3 : 2, q: 'Was bedeutet dieses Verkehrszeichen?', img: { sign: code },
      a: GEN.shuffle([{ t: DRAW.signName(code), ok: true }, { t: wrong[0] }, { t: wrong[1] }], r), ex: 'Zeichen ' + code.replace(/-\d+$/, '') + ' (StVO): ' + DRAW.signName(code) + '.' + (GEN.SIGN_TIPS[code] ? ' ' + GEN.SIGN_TIPS[code] : '') });
  }
  return out;
};
GEN.SIGN_TIPS = {
  '205': 'Du musst Vorfahrt gewähren – notfalls anhalten. Andere dürfen nicht behindert oder gefährdet werden.',
  '206': 'Hier musst du immer an der Haltlinie anhalten, auch wenn frei ist – und dann Vorfahrt gewähren.',
  '301': 'Gilt nur für die nächste Kreuzung oder Einmündung.',
  '306': 'Du hast Vorfahrt an allen folgenden Kreuzungen und Einmündungen bis zum Zeichen „Ende der Vorfahrtstraße“. Außerorts gilt dort ein Parkverbot auf der Fahrbahn.',
  '307': 'Ab hier gilt wieder die allgemeine Regel bzw. die nächste Beschilderung.',
  '102': 'An der nächsten Kreuzung gilt „rechts vor links“.',
  '201': 'Schienenfahrzeuge haben Vorrang. Am Andreaskreuz gilt: Warten, wenn ein Zug naht oder rotes Licht blinkt.',
  '208': 'Du musst dem Gegenverkehr den Vorrang lassen.',
  '308': 'Du hast Vorrang vor dem Gegenverkehr an der Engstelle.',
  '267': 'Einfahren ist für alle Fahrzeuge verboten (z. B. Einbahnstraße von der falschen Seite).',
  '250': 'Gilt für alle Fahrzeuge – Fahrräder dürfen geschoben werden.',
  '283': 'Halten ist auf der Fahrbahn verboten.',
  '286': 'Halten bis zu 3 Minuten (z. B. Ein- und Aussteigen, Be- und Entladen) ist erlaubt – Parken nicht.',
  '325.1': 'Schrittgeschwindigkeit, Fußgänger dürfen die ganze Straße nutzen, Parken nur auf gekennzeichneten Flächen.',
  '350': 'Fußgängern, die erkennbar den Überweg benutzen wollen, musst du das Überqueren ermöglichen.',
  '274.1': 'In der Zone gilt höchstens 30 km/h – und in der Regel „rechts vor links“.',
  '276': 'Kraftfahrzeuge dürfen keine mehrspurigen Kraftfahrzeuge überholen (Krafträder ohne Beiwagen dürfen überholt werden).',
  '280': 'Ab hier ist Überholen wieder erlaubt – mit der nötigen Vorsicht.',
  '282': 'Alle vorher durch Zeichen angeordneten Streckenverbote (Tempo, Überholverbot) sind aufgehoben.',
  '310': 'Ab hier gilt innerorts: höchstens 50 km/h, sofern nichts anderes angeordnet ist.',
  '311': 'Ab hier gelten die außerörtlichen Regeln: Pkw höchstens 100 km/h, sofern nichts anderes angeordnet ist.',
  '151': 'Kündigt einen Bahnübergang an – meist mit Baken (3, 2, 1 Streifen) davor.',
  '136': 'Mit Kindern rechnen: bremsbereit fahren, Geschwindigkeit verringern.',
  '142': 'Besonders bei Dämmerung mit Wild rechnen – Fernlicht abblenden, wenn Tiere auf der Fahrbahn sind, hupen und bremsen.',
};

// ------------------------------------------------------------------ Rechnen: Faustformeln
GEN.calc = () => {
  const out = [], V = [30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130];
  const fmt = (x) => (Math.round(x * 10) / 10).toString().replace('.', ',');
  for (const v of V) {
    const rw = v / 10 * 3, bw = (v / 10) ** 2, gb = bw / 2;
    out.push({ id: 'rw-' + v, cat: 'G', topic: 'gefahr', pts: 3, type: 'num', unit: 'm', q: 'Wie lang ist der Reaktionsweg nach der Faustformel bei ' + v + ' km/h?', ans: rw, ex: 'Reaktionsweg = (Geschwindigkeit ÷ 10) × 3 = (' + v + ' ÷ 10) × 3 = ' + fmt(rw) + ' m.' });
    out.push({ id: 'bw-' + v, cat: 'G', topic: 'gefahr', pts: 3, type: 'num', unit: 'm', q: 'Wie lang ist der Bremsweg bei einer normalen Bremsung nach der Faustformel bei ' + v + ' km/h?', ans: bw, ex: 'Bremsweg = (Geschwindigkeit ÷ 10) × (Geschwindigkeit ÷ 10) = ' + v / 10 + ' × ' + v / 10 + ' = ' + fmt(bw) + ' m.' });
    out.push({ id: 'gb-' + v, cat: 'G', topic: 'gefahr', pts: 3, type: 'num', unit: 'm', q: 'Wie lang ist der Bremsweg bei einer Gefahrbremsung nach der Faustformel bei ' + v + ' km/h?', ans: gb, ex: 'Bei einer Gefahrbremsung halbiert sich der Bremsweg: (' + v / 10 + ' × ' + v / 10 + ') ÷ 2 = ' + fmt(gb) + ' m.' });
    out.push({ id: 'aw-' + v, cat: 'G', topic: 'gefahr', pts: 4, type: 'num', unit: 'm', q: 'Wie lang ist der Anhalteweg bei einer normalen Bremsung nach der Faustformel bei ' + v + ' km/h?', ans: rw + bw, ex: 'Anhalteweg = Reaktionsweg + Bremsweg = ' + fmt(rw) + ' m + ' + fmt(bw) + ' m = ' + fmt(rw + bw) + ' m.' });
    out.push({ id: 'ag-' + v, cat: 'G', topic: 'gefahr', pts: 4, type: 'num', unit: 'm', q: 'Wie lang ist der Anhalteweg bei einer Gefahrbremsung nach der Faustformel bei ' + v + ' km/h?', ans: rw + gb, ex: 'Anhalteweg = Reaktionsweg + Bremsweg der Gefahrbremsung = ' + fmt(rw) + ' m + ' + fmt(gb) + ' m = ' + fmt(rw + gb) + ' m.' });
  }
  for (const v of [50, 60, 70, 80, 90, 100, 110, 120, 130]) out.push({ id: 'ab-' + v, cat: 'G', topic: 'verhalten', pts: 3, type: 'num', unit: 'm', q: 'Wie groß sollte der Sicherheitsabstand zum Vorausfahrenden außerorts bei ' + v + ' km/h mindestens sein (Faustformel „halber Tacho“)?', ans: v / 2, ex: 'Halber Tacho: ' + v + ' ÷ 2 = ' + v / 2 + ' m. Das entspricht ungefähr dem Weg, den du in 1,8 Sekunden zurücklegst.' });
  // Vervielfachung
  const mult = [[30, 60], [50, 100], [40, 80], [60, 120]];
  for (const [a, b] of mult) out.push({ id: 'mx-' + a, cat: 'G', topic: 'gefahr', pts: 4, q: 'Sie verdoppeln Ihre Geschwindigkeit von ' + a + ' km/h auf ' + b + ' km/h. Wie verändert sich der Bremsweg nach der Faustformel?', a: [{ t: 'Er wird viermal so lang', ok: true }, { t: 'Er wird doppelt so lang' }, { t: 'Er bleibt gleich' }], ex: 'Der Bremsweg wächst mit dem Quadrat der Geschwindigkeit: doppelte Geschwindigkeit = vierfacher Bremsweg (' + (a / 10) ** 2 + ' m → ' + (b / 10) ** 2 + ' m).' });
  return out;
};

// ------------------------------------------------------------------ Vorfahrt an Kreuzungen
// Positionen auf einem Kreis (im Uhrzeigersinn): N_ein, N_aus, O_ein, O_aus, S_ein, S_aus, W_ein, W_aus
const POS = { N: [0, 1], O: [2, 3], S: [4, 5], W: [6, 7] };
const RIGHT_OF = { S: 'O', O: 'N', N: 'W', W: 'S' };   // wer kommt von rechts?
const OPP = { S: 'N', N: 'S', O: 'W', W: 'O' };
const LEFT_OF = { S: 'W', W: 'N', N: 'O', O: 'S' };
GEN.exit = (from, turn) => { const heading = OPP[from]; if (turn === 'straight') return heading; const cw = ['N', 'O', 'S', 'W']; const i = cw.indexOf(heading); return turn === 'right' ? cw[(i + 1) % 4] : cw[(i + 3) % 4]; };
GEN.conflict = (a, b) => {
  const a1 = POS[a.from][0], a2 = POS[GEN.exit(a.from, a.turn)][1], b1 = POS[b.from][0], b2 = POS[GEN.exit(b.from, b.turn)][1];
  if (a2 === b2) return true;                                   // gleiche Ausfahrt
  const inside = (x, p, q) => { const lo = Math.min(p, q), hi = Math.max(p, q); return x > lo && x < hi; };
  return inside(b1, a1, a2) !== inside(b2, a1, a2);              // Wege kreuzen sich
};
/** muss a auf b warten? */
GEN.waits = (a, b, main) => {
  if (!GEN.conflict(a, b)) return false;
  const ma = main ? main.includes(a.from) : false, mb = main ? main.includes(b.from) : false;
  if (main && ma !== mb) return mb;                              // Hauptstraße vor Nebenstraße
  if (OPP[a.from] === b.from) return a.turn === 'left' && b.turn !== 'left';   // Linksabbieger lässt Gegenverkehr durch
  return RIGHT_OF[a.from] === b.from;                            // rechts vor links
};
/** Reihenfolge → [[Fahrzeuge, die zusammen fahren], …] oder null bei Blockade */
GEN.order = (cars, main) => {
  let left = cars.slice(); const steps = [];
  while (left.length) {
    const go = left.filter(a => !left.some(b => b !== a && GEN.waits(a, b, main)));
    if (!go.length) return null;
    steps.push(go); left = left.filter(a => !go.includes(a));
  }
  return steps;
};
GEN.orderText = (steps) => steps.map((s, i) => (i + 1) + '. ' + s.map(c => DRAW.COLNAME[c.color]).join(' und ') + (s.length > 1 ? ' (gleichzeitig)' : '')).join('  ·  ');
GEN.vorfahrt = (n) => {
  const out = [], seen = new Set(), r = GEN.rng(2026);
  const COLS = ['blau', 'rot', 'gruen', 'weiss'], TURNS = ['straight', 'left', 'right'];
  const LAY = [{ arms: ['N', 'O', 'S', 'W'], main: null }, { arms: ['N', 'O', 'S', 'W'], main: ['N', 'S'] }, { arms: ['N', 'O', 'S', 'W'], main: ['O', 'W'] }, { arms: ['O', 'S', 'W'], main: null }, { arms: ['O', 'S', 'W'], main: ['O', 'W'] }];
  let guard = 0;
  while (out.length < n && guard++ < 20000) {
    const lay = LAY[Math.floor(r() * LAY.length)];
    const k = 2 + Math.floor(r() * (lay.arms.length === 4 ? 3 : 2));   // 2–4 Fahrzeuge
    const froms = GEN.shuffle(lay.arms, r).slice(0, k), cols = GEN.shuffle(COLS, r);
    const cars = froms.map((f, i) => {
      let turns = TURNS.filter(t => lay.arms.includes(GEN.exit(f, t)));
      return { from: f, turn: turns[Math.floor(r() * turns.length)], color: cols[i], kind: r() < 0.12 ? 'bike' : 'car' };
    });
    const steps = GEN.order(cars, lay.main); if (!steps || steps.length < 2) continue;
    const key = lay.arms.join('') + (lay.main || []).join('') + cars.map(c => c.from + c.turn[0] + c.color).sort().join('');
    if (seen.has(key)) continue; seen.add(key);
    // falsche Antworten: andere Reihenfolgen
    const right = GEN.orderText(steps), wrong = new Set();
    let g2 = 0;
    while (wrong.size < 2 && g2++ < 200) {
      const perm = GEN.shuffle(cars, r).map(c => [c]);
      if (r() < 0.3 && perm.length > 2) { perm[0].push(...perm[1]); perm.splice(1, 1); }
      const t = GEN.orderText(perm); if (t !== right) wrong.add(t);
    }
    if (wrong.size < 2) continue;
    const signs = {};
    if (lay.main) for (const d of lay.arms) signs[d] = lay.main.includes(d) ? '306' : '205';
    const why = [];
    for (const s of steps) for (const a of s) {
      const ws = cars.filter(b => b !== a && GEN.waits(a, b, lay.main));
      if (!ws.length) continue;
      const reasons = ws.map(b => {
        const mb = lay.main && lay.main.includes(b.from) && !lay.main.includes(a.from);
        return DRAW.COLNAME[b.color] + (mb ? ' (fährt auf der Vorfahrtstraße)' : OPP[a.from] === b.from ? ' (Gegenverkehr – ' + DRAW.COLNAME[a.color] + ' biegt links ab)' : ' (kommt von rechts)');
      });
      why.push(DRAW.COLNAME[a.color] + ' wartet auf ' + reasons.join(' und '));
    }
    const sc = { arms: lay.arms, main: lay.main, signs, cars: cars.map(c => Object.assign({ label: DRAW.COLNAME[c.color][0] }, c)) };
    out.push({ id: 'vf-' + key, cat: 'G', topic: 'vorfahrt', pts: 4, q: 'In welcher Reihenfolge dürfen die Fahrzeuge fahren?', img: { scene: sc },
      a: GEN.shuffle([{ t: right, ok: true }, ...[...wrong].map(t => ({ t }))], r),
      ex: (lay.main ? 'Die Vorfahrtstraße (gelbe Raute) geht vor; wer das Dreieck „Vorfahrt gewähren“ hat, wartet. ' : 'Keine Schilder: Es gilt „rechts vor links“. ') + 'Linksabbieger lassen den Gegenverkehr durch. ' + why.join('. ') + '.' });
  }
  return out;
};
window.GEN = GEN;
