'use strict';
/* midnox Führerschein – Verkehrszeichen: amtliche StVO-Zeichen (gemeinfrei, Quelle Wikimedia Commons); Verkehrsszenen selbst gezeichnet (SVG). */

const DRAW = {};

// ------------------------------------------------------------------ Piktogramme (schwarz, für Schilder)
const PICT = {
  ped: '<circle cx="0" cy="-20" r="5"/><path d="M-2 -13 L4 -13 L7 2 L13 18 L8 20 L1 5 L-4 20 L-9 18 L-4 1 L-3 -6 L-10 2 L-13 -1 Z"/>',
  kids: '<circle cx="-9" cy="-19" r="4.5"/><path d="M-12 -12 L-5 -12 L-2 2 L2 17 L-2 18 L-7 5 L-11 18 L-15 17 L-12 2 L-17 -4 L-14 -7 Z"/><circle cx="9" cy="-14" r="4"/><path d="M6 -8 L12 -8 L14 4 L17 17 L13 18 L10 7 L7 18 L3 17 L6 4 L1 -1 L3 -4 Z"/>',
  bike: '<circle cx="-11" cy="10" r="8" fill="none" stroke="#000" stroke-width="3"/><circle cx="11" cy="10" r="8" fill="none" stroke="#000" stroke-width="3"/><path d="M-11 10 L-3 -6 L9 -6 L11 10 M-3 -6 L0 10 L9 -6 M-6 -11 L1 -11" fill="none" stroke="#000" stroke-width="3" stroke-linejoin="round"/><circle cx="1" cy="-18" r="4"/>',
  car: '<path d="M-20 4 L-17 -6 Q-15 -11 -9 -11 L9 -11 Q15 -11 17 -6 L20 4 L20 10 L-20 10 Z"/><circle cx="-11" cy="11" r="5"/><circle cx="11" cy="11" r="5"/><path d="M-13 -5 L-11 -9 L11 -9 L13 -5 Z" fill="#fff"/>',
  carRed: '<path fill="#d01c1f" d="M-20 4 L-17 -6 Q-15 -11 -9 -11 L9 -11 Q15 -11 17 -6 L20 4 L20 10 L-20 10 Z"/><circle fill="#d01c1f" cx="-11" cy="11" r="5"/><circle fill="#d01c1f" cx="11" cy="11" r="5"/>',
  truck: '<path d="M-22 -12 L6 -12 L6 9 L-22 9 Z M8 -5 L16 -5 L22 2 L22 9 L8 9 Z"/><circle cx="-14" cy="11" r="4.5"/><circle cx="14" cy="11" r="4.5"/>',
  deer: '<path d="M-14 6 L-10 -6 Q-2 -9 8 -7 L12 -14 L10 -22 L13 -22 L15 -15 L18 -22 L20 -21 L17 -13 L16 -8 L20 -4 L18 -1 L12 -3 L10 6 L12 18 L8 18 L6 7 L-6 8 L-8 18 L-12 18 Z"/>',
  train: '<path d="M-18 -14 L14 -14 Q20 -14 20 -6 L20 8 L-18 8 Z M-16 -11 L-6 -11 L-6 -3 L-16 -3 Z" fill-rule="evenodd"/><circle cx="-10" cy="12" r="4"/><circle cx="10" cy="12" r="4"/><path d="M-22 18 L24 18" stroke="#000" stroke-width="2.5"/>',
  light: '<rect x="-8" y="-24" width="16" height="44" rx="4"/><circle cx="0" cy="-14" r="5" fill="#d01c1f"/><circle cx="0" cy="-2" r="5" fill="#f5b800"/><circle cx="0" cy="10" r="5" fill="#1a9a4b"/>',
  work: '<circle cx="-4" cy="-20" r="4.5"/><path d="M-8 -13 L0 -13 L6 0 L-2 0 L2 18 L-3 18 L-7 4 L-12 18 L-17 17 L-10 -2 Z M3 -7 L14 -2 L20 2 L18 5 L10 2 Z"/><path d="M-20 18 L-8 10 L4 18 Z"/>',
  skid: '<path d="M-14 -2 L-11 -11 Q-9 -14 -4 -14 L6 -14 Q11 -14 12 -10 L15 -2 L15 3 L-14 3 Z"/><circle cx="-7" cy="4" r="3.5"/><circle cx="8" cy="4" r="3.5"/><path d="M-6 9 Q-12 14 -6 20 M2 9 Q-4 14 2 20" fill="none" stroke="#000" stroke-width="2.5"/>',
  wind: '<path d="M-22 -6 L10 -6 Q18 -6 18 -12 Q18 -18 12 -18 M-22 2 L16 2 Q24 2 24 9 Q24 16 16 16" fill="none" stroke="#000" stroke-width="3.5"/><path d="M-22 -14 L-16 -18 L-16 -10 Z"/>',
  bus: '<rect x="-20" y="-14" width="40" height="24" rx="3"/><rect x="-17" y="-11" width="10" height="8" fill="#fff"/><rect x="-5" y="-11" width="10" height="8" fill="#fff"/><rect x="7" y="-11" width="10" height="8" fill="#fff"/><circle cx="-11" cy="12" r="4"/><circle cx="11" cy="12" r="4"/>',
  moto: '<circle cx="-12" cy="10" r="7" fill="none" stroke="#000" stroke-width="3"/><circle cx="12" cy="10" r="7" fill="none" stroke="#000" stroke-width="3"/><path d="M-12 10 L-2 -2 L8 -2 L12 10 M-6 -4 L10 -4 L14 -9" fill="none" stroke="#000" stroke-width="3.5"/><circle cx="2" cy="-12" r="4"/>',
  horse: '<path d="M-16 0 Q-14 -8 -2 -8 L10 -9 L14 -18 L18 -16 L20 -10 L16 -8 L14 2 L15 16 L11 16 L9 4 L-6 4 L-8 16 L-12 16 L-12 4 Q-18 4 -20 12 L-22 10 Q-20 2 -16 0 Z"/>',
  falling: '<path d="M-22 18 L-22 -18 L-14 -18 L-14 18 Z"/><circle cx="2" cy="-8" r="5"/><circle cx="10" cy="4" r="6"/><circle cx="0" cy="12" r="4"/><circle cx="12" cy="-14" r="3"/>',
};

// ------------------------------------------------------------------ Verkehrszeichen
const RED = '#c1121f', BLUE = '#0b5fb3', YEL = '#ffcc00';
const tri = (inner, inv) => '<svg viewBox="-50 -50 100 100"><path d="' + (inv ? 'M-44 -36 L44 -36 L0 42 Z' : 'M0 -44 L44 36 L-44 36 Z') + '" fill="#fff" stroke="' + RED + '" stroke-width="9" stroke-linejoin="round"/>' + (inner ? '<g transform="translate(0 ' + (inv ? -8 : 10) + ') scale(.62)">' + inner + '</g>' : '') + '</svg>';
const ring = (inner, fill) => '<svg viewBox="-50 -50 100 100"><circle r="44" fill="' + (fill || '#fff') + '" stroke="' + RED + '" stroke-width="9"/>' + (inner || '') + '</svg>';
const blueC = (inner) => '<svg viewBox="-50 -50 100 100"><circle r="46" fill="' + BLUE + '" stroke="#fff" stroke-width="2.5"/>' + inner + '</svg>';
const blueR = (inner, w, h) => '<svg viewBox="' + (-(w || 90) / 2 - 4) + ' ' + (-(h || 90) / 2 - 4) + ' ' + ((w || 90) + 8) + ' ' + ((h || 90) + 8) + '"><rect x="' + (-(w || 90) / 2) + '" y="' + (-(h || 90) / 2) + '" width="' + (w || 90) + '" height="' + (h || 90) + '" rx="7" fill="' + BLUE + '" stroke="#fff" stroke-width="3"/>' + inner + '</svg>';
const wArrow = (rot, s) => '<g transform="rotate(' + (rot || 0) + ') scale(' + (s || 1) + ')"><path d="M-7 26 L-7 -6 L-20 -6 L0 -30 L20 -6 L7 -6 L7 26 Z" fill="#fff"/></g>';
const num = (n, sz) => '<text x="0" y="' + (sz ? sz * .36 : 14) + '" text-anchor="middle" font-family="Arial,Helvetica,sans-serif" font-weight="700" font-size="' + (sz || 40) + '" fill="#000">' + n + '</text>';
const end = (inner) => '<svg viewBox="-50 -50 100 100"><circle r="44" fill="#fff" stroke="#222" stroke-width="2.5"/><g opacity=".45">' + (inner || '') + '</g><g stroke="#111" stroke-width="3"><path d="M-30 30 L30 -30 M-34 24 L24 -34 M-24 34 L34 -24"/></g></svg>';

// Verkehrszeichen: Bilder (signs/*.png) sind die amtlichen Zeichen nach StVO (gemeinfrei, amtliche Werke; Quelle: Wikimedia Commons).
// Nur Zeichen mit Zahlenwerten (Tempo, Zusatzzeichen) und das Andreaskreuz sind hier als SVG nachgezeichnet.
const SIGN_NAMES = {"101": "Gefahrstelle","102": "Kreuzung oder Einmündung mit Vorfahrt von rechts","103-10": "Kurve (links)","103-20": "Kurve (rechts)","105-10": "Doppelkurve (zunächst links)","108": "Gefälle","110": "Steigung","112": "Unebene Fahrbahn","113": "Schnee- oder Eisglätte","114": "Schleuder- oder Rutschgefahr","116": "Splitt, Schotter","117-10": "Seitenwind von rechts","117-20": "Seitenwind von links","120": "Verengte Fahrbahn","123": "Arbeitsstelle","124": "Stau","125": "Gegenverkehr","128": "Bewegliche Brücke","129": "Ufer","131": "Lichtzeichenanlage","133": "Fußgänger","134": "Fußgängerüberweg (Warnung)","136": "Kinder","138": "Radverkehr","142": "Wildwechsel","145": "Kraftomnibusse","150": "Beschrankter Bahnübergang","151": "Bahnübergang","201": "Andreaskreuz","205": "Vorfahrt gewähren!","206": "Halt! Vorfahrt gewähren!","208": "Vorrang des Gegenverkehrs","209-10": "Vorgeschriebene Fahrtrichtung – links","209-20": "Vorgeschriebene Fahrtrichtung – rechts","209-30": "Vorgeschriebene Fahrtrichtung – geradeaus","209-31": "Vorgeschriebene Fahrtrichtung – rechts und links","211-10": "Vorgeschriebene Fahrtrichtung – hier links","214": "Vorgeschriebene Fahrtrichtung – geradeaus oder rechts","214-10": "Vorgeschriebene Fahrtrichtung – geradeaus oder links","215": "Kreisverkehr","220-20": "Einbahnstraße (rechtsweisend)","222": "Vorgeschriebene Vorbeifahrt – rechts vorbei","222-10": "Vorgeschriebene Vorbeifahrt – links vorbei","223.1": "Seitenstreifen befahrbar","224": "Haltestelle","229": "Taxenstand","237": "Radweg","238": "Reitweg","239": "Gehweg","240": "Gemeinsamer Geh- und Radweg","241-30": "Getrennter Rad- und Gehweg","242.1": "Beginn einer Fußgängerzone","244.1": "Beginn einer Fahrradstraße","245": "Bussonderfahrstreifen","250": "Verbot für Fahrzeuge aller Art","251": "Verbot für Kraftwagen","253": "Verbot für Lastkraftwagen über 3,5 t","254": "Verbot für Radverkehr","255": "Verbot für Krafträder","256": "Verbot für Mofas","258": "Verbot für Reiter","259": "Verbot für Fußgänger","260": "Verbot für Kraftfahrzeuge","261": "Verbot für Fahrzeuge mit gefährlichen Gütern","262": "Verbot für Fahrzeuge über angegebenem tatsächlichen Gewicht","263": "Verbot für Fahrzeuge über angegebener Achslast","264": "Verbot für Fahrzeuge über angegebene Breite","265": "Verbot für Fahrzeuge über angegebene Höhe","266": "Verbot für Fahrzeuge über angegebene Länge","267": "Verbot der Einfahrt","268": "Schneeketten sind vorgeschrieben","272": "Verbot des Wendens","273": "Verbot des Fahrens ohne Mindestabstand","274-30": "Zulässige Höchstgeschwindigkeit 30 km/h","274-50": "Zulässige Höchstgeschwindigkeit 50 km/h","274-70": "Zulässige Höchstgeschwindigkeit 70 km/h","274-100": "Zulässige Höchstgeschwindigkeit 100 km/h","274.1": "Beginn einer Tempo-30-Zone","274.2": "Ende einer Tempo-30-Zone","275": "Vorgeschriebene Mindestgeschwindigkeit","276": "Überholverbot für Kraftfahrzeuge aller Art","277": "Überholverbot für Kraftfahrzeuge über 3,5 t","278-50": "Ende der zulässigen Höchstgeschwindigkeit (50)","280": "Ende des Überholverbots","281": "Ende des Überholverbots für Kraftfahrzeuge über 3,5 t","282": "Ende sämtlicher Streckenverbote","283": "Absolutes Haltverbot","286": "Eingeschränktes Haltverbot","301": "Vorfahrt","306": "Vorfahrtstraße","307": "Ende der Vorfahrtstraße","308": "Vorrang vor dem Gegenverkehr","310": "Ortstafel (Anfang)","311": "Ortstafel (Ende)","314": "Parkplatz","316": "Parken und Reisen","318": "Parkscheibe","325.1": "Beginn eines verkehrsberuhigten Bereichs","325.2": "Ende eines verkehrsberuhigten Bereichs","327": "Tunnel","328": "Nothalte- und Pannenbucht","330.1": "Autobahn","330.2": "Ende der Autobahn","331.1": "Kraftfahrstraße","332-20": "Ausfahrt von der Autobahn","350": "Fußgängerüberweg","353": "Einbahnstraße","357": "Sackgasse","358": "Erste Hilfe","359": "Pannenhilfe","1002-12": "Zusatzzeichen: Verlauf der Vorfahrtstraße (abknickend nach links)"};
const SIGN_PNG = new Set(["101", "102", "103-10", "103-20", "105-10", "108", "110", "112", "113", "114", "116", "117-10", "117-20", "120", "123", "124", "125", "128", "129", "131", "133", "134", "136", "138", "142", "145", "150", "151", "205", "206", "208", "209-10", "209-20", "209-30", "209-31", "211-10", "214", "214-10", "215", "220-20", "222", "222-10", "223.1", "224", "229", "237", "238", "239", "240", "241-30", "242.1", "244.1", "245", "250", "251", "253", "254", "255", "256", "258", "259", "260", "261", "262", "263", "264", "265", "266", "267", "268", "272", "273", "274.1", "274.2", "275", "276", "277", "280", "281", "282", "283", "286", "301", "306", "307", "308", "310", "311", "314", "316", "318", "325.1", "325.2", "327", "328", "330.1", "330.2", "331.1", "332-20", "350", "353", "357", "358", "359"]);
const ANDREAS = '<svg viewBox="-62 -42 124 84">' + [32,-32].map(a => '<g transform="rotate(' + a + ')"><rect x="-55" y="-9" width="110" height="18" fill="#fff" stroke="#222" stroke-width="0.8"/><rect x="-55" y="-9" width="24" height="18" fill="#c1121f"/><rect x="31" y="-9" width="24" height="18" fill="#c1121f"/></g>').join('') + '</svg>';
const OWN_SVG = {
  '274-30': ['Zulässige Höchstgeschwindigkeit 30 km/h', ring(num('30'))],
  '274-50': ['Zulässige Höchstgeschwindigkeit 50 km/h', ring(num('50'))],
  '274-70': ['Zulässige Höchstgeschwindigkeit 70 km/h', ring(num('70'))],
  '274-100': ['Zulässige Höchstgeschwindigkeit 100 km/h', ring(num('100', 34))],
  '278-50': ['Ende der zulässigen Höchstgeschwindigkeit (50)', end(num('50'))],
  '1002-12': ['Zusatzzeichen: Verlauf der Vorfahrtstraße (abknickend nach links)', '<svg viewBox="-60 -40 120 80"><rect x="-56" y="-36" width="112" height="72" rx="4" fill="#fff" stroke="#111" stroke-width="2"/><path d="M0 28 L0 -2 L-36 -2" fill="none" stroke="#111" stroke-width="9"/><path d="M0 -2 L0 -28 M0 -2 L32 -2" fill="none" stroke="#111" stroke-width="3"/></svg>'],
  '201': [null, ANDREAS],
};
DRAW.SIGNS = {};
for (const [c, n] of Object.entries(SIGN_NAMES)) DRAW.SIGNS[c] = [n, OWN_SVG[c] ? OWN_SVG[c][1] : (SIGN_PNG.has(c) ? '<img src="signs/' + c + '.png" alt="" draggable="false">' : '')];
DRAW.PNG = SIGN_PNG;

DRAW.sign = (code, cls) => { const s = DRAW.SIGNS[code]; return s ? '<div class="sign ' + (cls || '') + '">' + s[1] + '</div>' : ''; };
DRAW.signName = (code) => (DRAW.SIGNS[code] || [code])[0];

// ------------------------------------------------------------------ Verkehrsszenen von oben
// Kreuzung: Arme N/O/S/W. Fahrzeuge kommen aus einem Arm und fahren geradeaus/links/rechts.
const COLORS = { blau: '#2563eb', rot: '#dc2626', gruen: '#16a34a', gelb: '#eab308', weiss: '#f1f5f9', schwarz: '#1f2937', orange: '#f97316', lila: '#9333ea' };
const COLNAME = { blau: 'Blau', rot: 'Rot', gruen: 'Grün', gelb: 'Gelb', weiss: 'Weiß', schwarz: 'Schwarz', orange: 'Orange', lila: 'Lila' };
DRAW.COLNAME = COLNAME;
const ROT = { S: 0, W: 90, N: 180, O: 270 };   // Blickrichtung: Fahrzeug aus S fährt nach oben
/**
 * scene = { arms:['N','O','S','W'], main:['N','S'] | null, signs:{S:'205'}, cars:[{from:'S', turn:'left'|'right'|'straight', color:'blau', kind:'car'|'bike'|'bus'|'truck'}],
 *           extras:['zebra-S','light'], peds:[{at:'S', side:'O'}] }
 */

// ------------------------------------------------------------------ Bausteine für Szenen (Draufsicht)
const shade = (hex, f) => { const n = parseInt(hex.slice(1), 16); const c = [n >> 16, (n >> 8) & 255, n & 255].map(v => Math.max(0, Math.min(255, Math.round(f > 0 ? v + (255 - v) * f : v * (1 + f))))); return '#' + c.map(v => v.toString(16).padStart(2, '0')).join(''); };
let GID = 0;
/** Fahrzeug von oben, Mitte (0,0), Front zeigt nach oben (−y) */
DRAW.vehicle = (kind, col, o = {}) => {
  col = col || '#2563eb';
  const hi = shade(col, .35), lo = shade(col, -.35), glass = '#1e293b', gl2 = '#334155';
  const sh = (w, hh, r) => '<rect x="' + (-w / 2 + 3) + '" y="' + (-hh / 2 + 4) + '" width="' + w + '" height="' + hh + '" rx="' + r + '" fill="#000" opacity=".28"/>';
  if (kind === 'bike') return sh(10, 30, 5) +
    '<rect x="-1.6" y="-15" width="3.2" height="30" rx="1.6" fill="#111"/><rect x="-7" y="-9" width="14" height="2.4" rx="1.2" fill="#444"/>' +
    '<ellipse cy="1" rx="8" ry="5.5" fill="' + col + '" stroke="' + lo + '" stroke-width="1"/><circle cy="-2" r="4.6" fill="' + (o.helmet || '#f8fafc') + '" stroke="#111" stroke-width=".8"/>';
  if (kind === 'ped' || kind === 'kid') { const k = kind === 'kid' ? 1.05 : 1.4; return '<g transform="scale(' + k + ')">' + '<ellipse cx="2" cy="3" rx="9" ry="6" fill="#000" opacity=".25"/><ellipse rx="9" ry="5.5" fill="' + col + '" stroke="' + lo + '" stroke-width="1"/><circle r="4.8" fill="' + (o.hair || '#5b3a1e') + '" stroke="#111" stroke-width=".6"/>' + (o.cane ? '<path d="M7 -2 L22 -14" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/><path d="M19 -11.6 L22 -14" stroke="#ef4444" stroke-width="2.4" stroke-linecap="round"/>' : '') + '</g>'; }
  if (kind === 'tram') return sh(28, 98, 8) + '<rect x="-14" y="-49" width="28" height="98" rx="8" fill="#fde047" stroke="#a16207" stroke-width="1.2"/><rect x="-11" y="-46" width="22" height="9" rx="3" fill="' + glass + '"/><rect x="-10" y="-30" width="20" height="62" rx="3" fill="#facc15" stroke="#ca8a04"/><rect x="-11" y="38" width="22" height="7" rx="3" fill="' + glass + '"/>';
  if (kind === 'truck' || kind === 'bus') {
    const L = kind === 'bus' ? 76 : 70;
    let b = sh(28, L, 5) + '<rect x="-14" y="' + (-L / 2) + '" width="28" height="' + L + '" rx="5" fill="' + col + '" stroke="' + lo + '" stroke-width="1.2"/>';
    if (kind === 'bus') b += '<rect x="-11" y="' + (-L / 2 + 3) + '" width="22" height="9" rx="2.5" fill="' + glass + '"/><rect x="-11" y="' + (-L / 2 + 16) + '" width="22" height="' + (L - 26) + '" rx="3" fill="' + hi + '" opacity=".55"/>' +
      [0, 1, 2].map(i => '<rect x="-6" y="' + (-L / 2 + 22 + i * 15) + '" width="12" height="6" rx="1.5" fill="' + lo + '" opacity=".5"/>').join('') + '<rect x="-11" y="' + (L / 2 - 6) + '" width="22" height="3" rx="1" fill="' + glass + '"/>';
    else b += '<rect x="-13" y="' + (-L / 2 + 1) + '" width="26" height="17" rx="4" fill="' + hi + '"/><rect x="-11" y="' + (-L / 2 + 3) + '" width="22" height="7" rx="2" fill="' + glass + '"/><rect x="-14" y="' + (-L / 2 + 21) + '" width="28" height="' + (L - 21) + '" rx="2" fill="#e2e8f0" stroke="#94a3b8"/><path d="M-10 ' + (-L / 2 + 28) + ' V' + (L / 2 - 4) + ' M0 ' + (-L / 2 + 28) + ' V' + (L / 2 - 4) + ' M10 ' + (-L / 2 + 28) + ' V' + (L / 2 - 4) + '" stroke="#cbd5e1" stroke-width="1.5"/>';
    b += '<rect x="-12" y="' + (-L / 2 - 1) + '" width="5" height="2.4" rx="1" fill="#fef9c3"/><rect x="7" y="' + (-L / 2 - 1) + '" width="5" height="2.4" rx="1" fill="#fef9c3"/><rect x="-13" y="' + (L / 2 - 1.5) + '" width="5" height="2.4" fill="#ef4444"/><rect x="8" y="' + (L / 2 - 1.5) + '" width="5" height="2.4" fill="#ef4444"/>';
    if (o.warn) b += '<g class="blinkall"><circle cx="-13" cy="' + (-L / 2 + 2) + '" r="3.6" fill="#fb923c"/><circle cx="13" cy="' + (-L / 2 + 2) + '" r="3.6" fill="#fb923c"/><circle cx="-13" cy="' + (L / 2 - 2) + '" r="3.6" fill="#fb923c"/><circle cx="13" cy="' + (L / 2 - 2) + '" r="3.6" fill="#fb923c"/></g>';
    return b;
  }
  // Pkw
  const id = 'cg' + (++GID);
  let b = '<defs><linearGradient id="' + id + '" x1="0" x2="1"><stop offset="0" stop-color="' + lo + '"/><stop offset=".5" stop-color="' + hi + '"/><stop offset="1" stop-color="' + lo + '"/></linearGradient></defs>' + sh(24, 44, 8) +
    '<rect x="-13.2" y="-14" width="3" height="5" rx="1.2" fill="' + lo + '"/><rect x="10.2" y="-14" width="3" height="5" rx="1.2" fill="' + lo + '"/>' +   // Spiegel
    '<rect x="-11.5" y="-22" width="23" height="44" rx="8" fill="url(#' + id + ')" stroke="' + shade(col, -.55) + '" stroke-width="1"/>' +
    '<path d="M-9.5 -11 Q0 -14.5 9.5 -11 L8 -4 Q0 -5.5 -8 -4 Z" fill="' + glass + '"/>' +                                                                 // Frontscheibe
    '<rect x="-8" y="-4" width="16" height="15" rx="2.5" fill="' + hi + '" opacity=".75"/>' +                                                            // Dach
    '<path d="M-8 12 Q0 13 8 12 L9 17 Q0 19 -9 17 Z" fill="' + gl2 + '"/>' +                                                                           // Heckscheibe
    '<path d="M-10 -5 L-10.6 10 M10 -5 L10.6 10" stroke="' + glass + '" stroke-width="1.6" opacity=".8"/>' +                                            // Seitenfenster
    '<rect x="-10" y="-22.3" width="5" height="2.6" rx="1.2" fill="#fef9c3"/><rect x="5" y="-22.3" width="5" height="2.6" rx="1.2" fill="#fef9c3"/>' + // Scheinwerfer
    '<rect x="-10.5" y="19.8" width="5" height="2.2" rx="1" fill="#dc2626"/><rect x="5.5" y="19.8" width="5" height="2.2" rx="1" fill="#dc2626"/>';    // Rückleuchten
  if (o.brake) b += '<rect x="-11" y="19" width="6" height="3.5" rx="1" fill="#ff3030"/><rect x="5" y="19" width="6" height="3.5" rx="1" fill="#ff3030"/><circle cx="-8" cy="24" r="6" fill="#ef4444" opacity=".35"/><circle cx="8" cy="24" r="6" fill="#ef4444" opacity=".35"/>';
  if (o.door) b += '<path d="M' + (o.door === 'left' ? '-11 -4 L-22 2' : '11 -4 L22 2') + '" stroke="' + col + '" stroke-width="3" stroke-linecap="round"/>';
  if (o.warn) b += '<g class="blinkall"><circle cx="-10" cy="-20" r="3.2" fill="#fb923c"/><circle cx="10" cy="-20" r="3.2" fill="#fb923c"/><circle cx="-10" cy="20" r="3.2" fill="#fb923c"/><circle cx="10" cy="20" r="3.2" fill="#fb923c"/></g>';
  if (o.blue) b += '<rect x="-7" y="-2" width="14" height="3.5" rx="1.5" fill="#3b82f6"/><circle cx="-5" cy="0" r="4" fill="#60a5fa" opacity=".5"/><circle cx="5" cy="0" r="4" fill="#60a5fa" opacity=".5"/>';
  return b;
};
/** Blinker-Leuchten passend zur Fahrzeuglänge */
DRAW.blink = (kind, turn) => {
  if (!turn || turn === 'straight' || kind === 'bike') return '';
  const x = turn === 'left' ? -1 : 1, L = kind === 'bus' ? 38 : kind === 'truck' ? 35 : kind === 'tram' ? 49 : 20, w = kind === 'car' || !kind ? 10.5 : 13.5;
  return '<g class="blink"><circle cx="' + x * w + '" cy="' + (-L + 1) + '" r="3.4" fill="#fb923c"/><circle cx="' + x * w + '" cy="' + (L - 1) + '" r="3.4" fill="#fb923c"/></g>';
};
DRAW.tree = (x, y, r) => '<g transform="translate(' + x + ' ' + y + ')"><circle cx="3" cy="4" r="' + r + '" fill="#000" opacity=".22"/><circle r="' + r + '" fill="#2f6b35"/><circle cx="' + (-r * .3) + '" cy="' + (-r * .3) + '" r="' + r * .62 + '" fill="#3f8a45"/><circle cx="' + (-r * .45) + '" cy="' + (-r * .45) + '" r="' + r * .25 + '" fill="#58a85d"/></g>';
DRAW.house = (x, y, w, hh, col) => { const roof = col || '#b45309'; const v = w > hh;   // Dachfirst entlang der langen Seite
  return '<g><rect x="' + (x + 4) + '" y="' + (y + 5) + '" width="' + w + '" height="' + hh + '" fill="#000" opacity=".25"/><rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + hh + '" fill="' + roof + '"/>' +
    (v ? '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + hh / 2 + '" fill="' + shade(roof, .18) + '"/><path d="M' + x + ' ' + (y + hh / 2) + ' H' + (x + w) + '" stroke="' + shade(roof, -.4) + '" stroke-width="2"/>'
       : '<rect x="' + x + '" y="' + y + '" width="' + w / 2 + '" height="' + hh + '" fill="' + shade(roof, .18) + '"/><path d="M' + (x + w / 2) + ' ' + y + ' V' + (y + hh) + '" stroke="' + shade(roof, -.4) + '" stroke-width="2"/>') +
    '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + hh + '" fill="none" stroke="' + shade(roof, -.45) + '" stroke-width="1.5"/></g>'; };
const ROOFS = ['#b45309', '#9a3412', '#57534e', '#7c2d12', '#475569', '#a16207'];
const GRASS = '<defs><pattern id="grs" width="18" height="18" patternUnits="userSpaceOnUse"><rect width="18" height="18" fill="#7cae6a"/><path d="M3 5 l1.5 -3 M11 13 l1.5 -3 M14 4 l1 -2.5 M6 15 l1 -2.5" stroke="#6a9c59" stroke-width="1.3"/></pattern></defs>';

DRAW.scene = (sc) => {
  const W = 400, R = 34;   // Straßenhalbbreite
  const arms = sc.arms || ['N', 'O', 'S', 'W'];
  let g = '<svg viewBox="0 0 ' + W + ' ' + W + '" class="scene"><rect width="' + W + '" height="' + W + '" fill="#7cae6a"/>' + GRASS + '<rect width="' + W + '" height="' + W + '" fill="url(#grs)"/>';
  const c = W / 2;
  // Häuser und Bäume in den Ecken (fest je Szene, damit jede Kreuzung etwas anders aussieht)
  const rr = typeof GEN !== 'undefined' && GEN.rng ? GEN.rng(JSON.stringify(sc).split('').reduce((a, ch) => (a * 31 + ch.charCodeAt(0)) >>> 0, 7)) : Math.random;
  const blk = c - R - 12;   // Kantenlänge einer Ecke
  for (const [qx, qy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
    const ox = qx ? W - blk : 0, oy = qy ? W - blk : 0, w = 60 + rr() * 30, hh = 46 + rr() * 30;
    const hx = ox + (qx ? blk - w - 14 - rr() * 30 : 14 + rr() * 30) - (qx ? 0 : 0), hy = oy + (qy ? blk - hh - 14 - rr() * 30 : 14 + rr() * 30);
    g += DRAW.house(Math.round(hx), Math.round(hy), Math.round(w), Math.round(hh), ROOFS[Math.floor(rr() * ROOFS.length)]);
    const tx = ox + (qx ? 22 + rr() * 30 : blk - 22 - rr() * 30), ty = oy + (qy ? 30 + rr() * 20 : blk - 30 - rr() * 20);
    if (rr() > .25) g += DRAW.tree(Math.round(tx), Math.round(ty), 11 + rr() * 6);
  }
  // fehlender Arm (Einmündung): Hecke und Haus statt Straße
  for (const d of ['N', 'O', 'S', 'W']) if (!arms.includes(d)) {
    const v = d === 'N' || d === 'S', far = d === 'N' || d === 'W';
    const x = v ? c - 46 : (far ? 30 : W - 30 - 70), y = v ? (far ? 28 : W - 28 - 60) : c - 40;
    g += DRAW.house(x, y, v ? 92 : 70, v ? 60 : 80, ROOFS[Math.floor(rr() * ROOFS.length)]);
    g += v ? '<rect x="' + (c - R - 12) + '" y="' + (far ? c - R - 22 : c + R + 14) + '" width="' + (2 * R + 24) + '" height="8" rx="4" fill="#3f7a3a"/>' : '<rect x="' + (far ? c - R - 22 : c + R + 14) + '" y="' + (c - R - 12) + '" width="8" height="' + (2 * R + 24) + '" rx="4" fill="#3f7a3a"/>';
  }
  const road = (d) => {
    const L = '#474e5a';
    if (d === 'N') return '<rect x="' + (c - R) + '" y="0" width="' + 2 * R + '" height="' + c + '" fill="' + L + '"/>';
    if (d === 'S') return '<rect x="' + (c - R) + '" y="' + c + '" width="' + 2 * R + '" height="' + c + '" fill="' + L + '"/>';
    if (d === 'W') return '<rect x="0" y="' + (c - R) + '" width="' + c + '" height="' + 2 * R + '" fill="' + L + '"/>';
    return '<rect x="' + c + '" y="' + (c - R) + '" width="' + c + '" height="' + 2 * R + '" fill="' + L + '"/>';
  };
  // Bürgersteige
  for (const d of arms) g += road(d).replace('#474e5a', '#d6d3cd').replace(/width="(\d+)" height="(\d+)"/, (m, w, hh) => (d === 'N' || d === 'S') ? 'width="' + (+w + 24) + '" height="' + hh + '"' : 'width="' + w + '" height="' + (+hh + 24) + '"').replace(/x="(\d+)"/, (m, x) => (d === 'N' || d === 'S') ? 'x="' + (+x - 12) + '"' : m).replace(/y="(\d+)"/, (m, y) => (d === 'W' || d === 'O') ? 'y="' + (+y - 12) + '"' : m);
  g += '<rect x="' + (c - R - 12) + '" y="' + (c - R - 12) + '" width="' + (2 * R + 24) + '" height="' + (2 * R + 24) + '" fill="#d6d3cd"/>';
  // Bordsteinkante
  for (const d of arms) g += road(d).replace('fill="#474e5a"', 'fill="none" stroke="#9ca3af" stroke-width="5"');
  for (const d of arms) g += road(d);
  g += '<rect x="' + (c - R) + '" y="' + (c - R) + '" width="' + 2 * R + '" height="' + 2 * R + '" fill="#474e5a"/>';
  // Mittellinien (gestrichelt) und Wartelinien
  const dash = 'stroke="#fff" stroke-width="2.5" stroke-dasharray="14 12"';
  if (arms.includes('N')) g += '<path d="M' + c + ' 0 L' + c + ' ' + (c - R - 4) + '" ' + dash + '/>';
  if (arms.includes('S')) g += '<path d="M' + c + ' ' + W + ' L' + c + ' ' + (c + R + 4) + '" ' + dash + '/>';
  if (arms.includes('W')) g += '<path d="M0 ' + c + ' L' + (c - R - 4) + ' ' + c + '" ' + dash + '/>';
  if (arms.includes('O')) g += '<path d="M' + W + ' ' + c + ' L' + (c + R + 4) + ' ' + c + '" ' + dash + '/>';
  // Vorfahrtstraße: Fahrbahnränder der Hauptstraße als durchgehende Linien
  if (sc.main) {
    const m = sc.main.join('');
    if (/N/.test(m) && /S/.test(m)) g += '<path d="M' + (c - R + 3) + ' 0 V' + W + ' M' + (c + R - 3) + ' 0 V' + W + '" stroke="#fff" stroke-width="2" opacity=".55"/>';
    if (/W/.test(m) && /O/.test(m)) g += '<path d="M0 ' + (c - R + 3) + ' H' + W + ' M0 ' + (c + R - 3) + ' H' + W + '" stroke="#fff" stroke-width="2" opacity=".55"/>';
  }
  // Wartelinien an Armen mit 205/206
  for (const [d, code] of Object.entries(sc.signs || {})) {
    if (code !== '205' && code !== '206') continue;
    const l = { S: 'M' + c + ' ' + (c + R + 6) + ' H' + (c + R), N: 'M' + (c - R) + ' ' + (c - R - 6) + ' H' + c, W: 'M' + (c - R - 6) + ' ' + c + ' V' + (c + R), O: 'M' + (c + R + 6) + ' ' + (c - R) + ' V' + c }[d];
    g += '<path d="' + l + '" stroke="#fff" stroke-width="5" stroke-dasharray="' + (code === '206' ? '0' : '7 5') + '"/>';
  }
  // Zebrastreifen
  for (const x of sc.extras || []) {
    const m = String(x).match(/^zebra-([NOSW])$/); if (!m) continue;
    const d = m[1], off = R + 22;
    for (let i = -R + 4; i < R - 4; i += 10) {
      if (d === 'S') g += '<rect x="' + (c + i) + '" y="' + (c + off) + '" width="6" height="18" fill="#fff"/>';
      if (d === 'N') g += '<rect x="' + (c + i) + '" y="' + (c - off - 18) + '" width="6" height="18" fill="#fff"/>';
      if (d === 'W') g += '<rect x="' + (c - off - 18) + '" y="' + (c + i) + '" width="18" height="6" fill="#fff"/>';
      if (d === 'O') g += '<rect x="' + (c + off) + '" y="' + (c + i) + '" width="18" height="6" fill="#fff"/>';
    }
  }
  // Schilder an den Armen (rechts neben der Zufahrt)
  const signPos = { S: [c + R + 30, c + R + 46], N: [c - R - 30, c - R - 46], W: [c - R - 46, c + R + 30], O: [c + R + 46, c - R - 30] };
  for (const [d, code] of Object.entries(sc.signs || {})) {
    const s = DRAW.SIGNS[code]; if (!s || !signPos[d]) continue;
    const [x, y] = signPos[d];
    g += '<g transform="translate(' + x + ' ' + y + ')"><rect x="-1.5" y="0" width="3" height="16" fill="#334155"/>' + (SIGN_PNG.has(code) ? '<image href="signs/' + code + '.png" x="-17" y="-34" width="34" height="34" preserveAspectRatio="xMidYMax meet"/>' : '<svg x="-17" y="-34" width="34" height="34" viewBox="' + (s[1].match(/viewBox="([^"]+)"/) || [, '-50 -50 100 100'])[1] + '">' + s[1].replace(/^<svg[^>]*>|<\/svg>$/g, '') + '</svg>') + '</g>';
  }
  if ((sc.extras || []).includes('light')) for (const d of arms) { const [x, y] = signPos[d]; g += '<g transform="translate(' + x + ' ' + y + ')"><rect x="-6" y="-26" width="12" height="30" rx="3" fill="#111"/><circle cy="-19" r="3.5" fill="' + (sc.lights && sc.lights[d] === 'rot' ? '#ef4444' : '#3f3f46') + '"/><circle cy="-11" r="3.5" fill="' + (sc.lights && sc.lights[d] === 'gelb' ? '#facc15' : '#3f3f46') + '"/><circle cy="-3" r="3.5" fill="' + (sc.lights && sc.lights[d] === 'gruen' ? '#22c55e' : '#3f3f46') + '"/></g>'; }
  // Fahrzeuge: in der rechten Spur ihrer Zufahrt, mit Pfeil für die Fahrtrichtung
  const lane = R / 2;
  for (const v of sc.cars || []) {
    const col = COLORS[v.color] || v.color || '#2563eb';
    const rot = ROT[v.from];
    const dist = v.dist || (R + 40);
    // lokale Koordinaten: Fahrzeug aus Süden, Zentrum (0,0) = Kreuzungsmitte, Spur rechts = +x
    const kind = v.kind || 'car';
    const body = DRAW.vehicle(kind, col, v), blink = DRAW.blink(kind, v.turn);
    // Fahrweg-Pfeil
    const y0 = dist - 30, A = '#facc15';
    let path;
    if (v.turn === 'left') path = 'M' + lane + ' ' + y0 + ' L' + lane + ' ' + (lane / 2) + ' Q' + lane + ' ' + (-lane) + ' ' + (-lane / 2) + ' ' + (-lane) + ' L' + (-R - 26) + ' ' + (-lane);
    else if (v.turn === 'right') path = 'M' + lane + ' ' + y0 + ' L' + lane + ' ' + (R + 4) + ' Q' + lane + ' ' + lane + ' ' + (R + 4) + ' ' + lane + ' L' + (R + 30) + ' ' + lane;
    else path = 'M' + lane + ' ' + y0 + ' L' + lane + ' ' + (-R - 34);
    const arrowEnd = v.turn === 'left' ? [(-R - 26), -lane, 180] : v.turn === 'right' ? [R + 30, lane, 0] : [lane, -R - 34, -90];
    const head = '<path d="M0 -7 L12 0 L0 7 Z" fill="' + A + '" transform="translate(' + arrowEnd[0] + ' ' + arrowEnd[1] + ') rotate(' + arrowEnd[2] + ')"/>';
    g += '<g transform="translate(' + c + ' ' + c + ') rotate(' + rot + ')">' + (v.noPath ? '' : '<path d="' + path + '" fill="none" stroke="' + A + '" stroke-width="4" stroke-dasharray="9 6" opacity=".9"/>' + head) +
      '<g transform="translate(' + lane + ' ' + dist + ')">' + body + blink + '</g></g>';
    if (v.label) { const p = { S: [c + lane + 26, c + dist], N: [c - lane - 26, c - dist], W: [c - dist, c + lane + 26], O: [c + dist, c - lane - 26] }[v.from]; g += '<g transform="translate(' + p[0] + ' ' + p[1] + ')"><circle r="12" fill="#fff" stroke="' + col + '" stroke-width="3"/><text y="5" text-anchor="middle" font-size="14" font-weight="800" font-family="Arial" fill="#111">' + v.label + '</text></g>'; }
  }
  // Fußgänger
  for (const p of sc.peds || []) {
    const pos = { S: [c + (p.x || 0), c + R + 31], N: [c + (p.x || 0), c - R - 31], W: [c - R - 31, c + (p.y || 0)], O: [c + R + 31, c + (p.y || 0)] }[p.at] || [p.x, p.y];
    g += '<g transform="translate(' + pos[0] + ' ' + pos[1] + ') rotate(' + (p.rot || 0) + ')">' + DRAW.vehicle(p.kid ? 'kid' : 'ped', p.color || '#e11d48', p) + '</g>';
  }
  return g + '</svg>';
};

/** Gerade Straße von oben (Gefahrenlehre). Spuren: l = Gegenfahrbahn, r = eigene Spur, pl/pr = Parkstreifen, swl/swr = Gehweg.
 *  { items:[{t:'car'|'bus'|'truck'|'bike'|'ped'|'kid'|'ball'|'zebra'|'stop'|'tree', x:'r'|Zahl, y, rot, color, label, warn, door, brake, blink, me}], seed }
 */
DRAW.street = (sc) => {
  const W = 400, X = { swl: 88, pl: 126, l: 170, r: 230, pr: 274, swr: 312 };
  const rr = typeof GEN !== 'undefined' && GEN.rng ? GEN.rng((sc.seed || 1) * 7919) : Math.random;
  let g = '<svg viewBox="0 0 ' + W + ' ' + W + '" class="scene">' + GRASS + '<rect width="' + W + '" height="' + W + '" fill="url(#grs)"/>';
  // Häuser und Bäume am Rand
  for (const side of [0, 1]) for (let y = -10 + rr() * 30; y < W; y += 100 + rr() * 40) {
    const w = 44 + rr() * 14, hh = 60 + rr() * 20; g += DRAW.house(side ? W - w - 6 : 6, Math.round(y), Math.round(w), Math.round(hh), ROOFS[Math.floor(rr() * ROOFS.length)]);
  }
  g += '<rect x="70" y="0" width="36" height="' + W + '" fill="#d6d3cd"/><rect x="294" y="0" width="36" height="' + W + '" fill="#d6d3cd"/>';
  for (let y = 0; y < W; y += 20) g += '<path d="M70 ' + y + ' H106 M294 ' + y + ' H330" stroke="#c4c0b8" stroke-width="1"/>';
  g += '<rect x="104" y="0" width="192" height="' + W + '" fill="#474e5a"/><path d="M106 0 V' + W + ' M294 0 V' + W + '" stroke="#9ca3af" stroke-width="4"/>';
  g += '<path d="M146 0 V' + W + ' M254 0 V' + W + '" stroke="#fff" stroke-width="1.6" stroke-dasharray="6 8" opacity=".55"/><path d="M200 0 V' + W + '" stroke="#fff" stroke-width="2.5" stroke-dasharray="16 12"/>';
  for (let y = 30 + rr() * 30; y < W; y += 120 + rr() * 60) { g += DRAW.tree(88, Math.round(y), 9); g += DRAW.tree(312, Math.round(y + 50), 9); }
  const items = (sc.items || []).slice().sort((a, b) => (a.t === 'zebra' || a.t === 'stop' ? 0 : 1) - (b.t === 'zebra' || b.t === 'stop' ? 0 : 1));
  for (const it of items) {
    const x = typeof it.x === 'number' ? it.x : X[it.x || 'r'], y = it.y || 0;
    if (it.t === 'zebra') { for (let i = 0; i < 9; i++) g += '<rect x="' + (110 + i * 20.5) + '" y="' + (y - 14) + '" width="12" height="28" fill="#fff"/>'; continue; }
    if (it.t === 'stop') { g += '<g transform="translate(' + x + ' ' + y + ')"><rect x="-1.5" y="0" width="3" height="16" fill="#334155"/><circle cy="-6" r="10" fill="#facc15" stroke="#15803d" stroke-width="2.5"/><text y="-1.5" text-anchor="middle" font-family="Arial" font-weight="900" font-size="13" fill="#15803d">H</text></g>'; continue; }
    if (it.t === 'tree') { g += DRAW.tree(x, y, it.r || 10); continue; }
    if (it.t === 'ball') { g += '<g transform="translate(' + x + ' ' + y + ')"><circle cx="2" cy="3" r="6" fill="#000" opacity=".25"/><circle r="6" fill="#f8fafc" stroke="#111" stroke-width="1"/><path d="M-6 0 Q0 -4 6 0 M0 -6 V6" stroke="#ef4444" stroke-width="1.6" fill="none"/></g>' + (it.trail ? '<path d="M' + (x + 26) + ' ' + y + ' L' + (x + 10) + ' ' + y + '" stroke="#fff" stroke-width="2" stroke-dasharray="3 4" opacity=".8"/>' : ''); continue; }
    const col = COLORS[it.color] || it.color || (it.me ? COLORS.blau : '#64748b');
    const kind = it.t === 'car' ? 'car' : it.t;
    g += '<g transform="translate(' + x + ' ' + y + ') rotate(' + (it.rot || 0) + ')">' + (it.move ? '<path d="M0 -26 V' + (-26 - it.move) + '" stroke="#facc15" stroke-width="4" stroke-dasharray="9 6"/><path d="M-7 ' + (-26 - it.move) + ' L0 ' + (-38 - it.move) + ' L7 ' + (-26 - it.move) + ' Z" fill="#facc15"/>' : '') +
      DRAW.vehicle(kind, col, it) + DRAW.blink(kind, it.blink) + '</g>';
    if (it.label || it.me) { const lx = x + (x > 200 ? 26 : -26) * (kind === 'bus' || kind === 'truck' ? 1.2 : 1); g += '<g transform="translate(' + lx + ' ' + y + ')"><circle r="13" fill="#fff" stroke="' + col + '" stroke-width="3"/><text y="' + (it.me ? 3.5 : 5) + '" text-anchor="middle" font-size="' + (it.me ? 10 : 14) + '" font-weight="800" font-family="Arial" fill="#111">' + (it.me ? 'Sie' : it.label) + '</text></g>'; }
  }
  return g + '</svg>';
};
window.DRAW = DRAW;
