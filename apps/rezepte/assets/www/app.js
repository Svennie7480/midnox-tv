'use strict';
/* midnox Rezepte – Oberfläche für Fernseher (Fernbedienung), Handy (Touch) und Browser (Maus/Tastatur).
   Die App liefert diese Seite selbst aus; alle Daten kommen über /api vom Gerät. */

// ------------------------------------------------------------------ Grundlagen
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const h = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
const AND = window.Android || null;
const Q = new URLSearchParams(location.search);
const TV = Q.has('tv') || !!(AND && AND.tv && AND.tv());
const INAPP = !!AND;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

const ICON = {
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  book: '<path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17H6.5A2.5 2.5 0 0 0 4 21.5z"/><path d="M4 21.5A2.5 2.5 0 0 1 6.5 19H20v3H6.5"/><path d="M9 7h7M9 11h5"/>',
  cal: '<rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/><path d="M7.5 13.5h3v3h-3z"/>',
  cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2.5 3h2.6l2.4 12.2a1.6 1.6 0 0 0 1.6 1.3h8.7a1.6 1.6 0 0 0 1.6-1.2L21.5 7H6.2"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>', minus: '<path d="M5 12h14"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
  heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6.5 6.5 0 0 1 3.5 6"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/>',
  chef: '<path d="M6 13.9V20h12v-6.1"/><path d="M6 14a4 4 0 0 1-.6-7.9 5 5 0 0 1 9.3-1.8A4 4 0 1 1 18 14"/><path d="M6 17h12"/>',
  play: '<path d="M7 4v16l13-8z"/>', left: '<path d="m15 18-6-6 6-6"/>', right: '<path d="m9 18 6-6-6-6"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  cloud: '<path d="M17.5 19H7a5 5 0 1 1 1.1-9.9A6 6 0 0 1 19.6 11 4 4 0 0 1 17.5 19z"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2.5"/><path d="M11 18h2"/>',
  dice: '<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.2"/><circle cx="16" cy="16" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="16" cy="8" r="1.2"/><circle cx="8" cy="16" r="1.2"/>',
  share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
  check: '<path d="M20 6 9 17l-5-5"/>', refresh: '<path d="M21 12a9 9 0 1 1-2.6-6.4L21 8"/><path d="M21 3v5h-5"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9.5 2.5h5"/>', move: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  wifi: '<path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0"/><circle cx="12" cy="19.5" r="1"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>', copy: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  down: '<path d="M12 3v13M6 11l6 6 6-6M4 21h16"/>', up: '<path d="M12 21V8M6 13l6-6 6 6M4 3h16"/>', eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
};
const ico = (n, sw = 2) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' + sw + '" stroke-linecap="round" stroke-linejoin="round">' + (ICON[n] || '') + '</svg>';

function toast(msg, ms = 2600) { const t = $('#toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove('on'), ms); }

// ------------------------------------------------------------------ API
const API = {
  async get(p) { const r = await fetch('/api/' + p, { cache: 'no-store' }); const j = await r.json(); if (!j.ok) throw new Error(j.err || 'Fehler'); return j; },
  async post(p, body, raw) {
    const r = await fetch('/api/' + p, { method: 'POST', body: raw ? body : JSON.stringify(body), headers: raw ? { 'Content-Type': 'application/octet-stream' } : { 'Content-Type': 'application/json' } });
    const j = await r.json(); if (!j.ok) throw new Error(j.err || 'Fehler'); return j;
  },
};

// ------------------------------------------------------------------ Daten
const DB = {
  list: [], byId: {}, plan: { days: {} }, shop: { items: [] }, st: { settings: {} }, cloud: {}, ver: 0,
  async load(fresh) {
    const [rs, pl, sh, st] = await Promise.all([API.get('recipes' + (fresh ? '?fresh=1' : '')), API.get('plan'), API.get('shop'), API.get('state')]);
    DB.list = rs.list.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'de'));
    DB.byId = {}; for (const r of DB.list) DB.byId[r.id] = r;
    DB.cloud = rs.cloud; DB.plan = pl.plan; if (!DB.plan.days) DB.plan.days = {};
    DB.shop = sh.shop; if (!DB.shop.items) DB.shop.items = [];
    DB.st = st; DB.ver = st.ver;
  },
  get S() { return DB.st.settings || {}; },
  slots() { const s = DB.S.slots; return Array.isArray(s) && s.length ? s : ['Mittag', 'Abend']; },
  async savePlan() { const r = await API.post('plan', DB.plan); DB.ver = r.ver; },
  async saveShop() { const r = await API.post('shop', DB.shop); DB.ver = r.ver; },
  tags() { const c = {}; for (const r of DB.list) for (const t of r.tags || []) c[t] = (c[t] || 0) + 1; return Object.keys(c).sort((a, b) => c[b] - c[a]); },
};
/** Wie viele Portionen beim Einplanen? Haushaltsgröße – außer bei Kuchen, Blechen & Co. */
const defP = (r) => (r.portions >= 8 || (r.tags || []).some(t => /kuchen|backen|torte|dessert|brot/i.test(t))) ? (r.portions || 2) : (DB.S.persons || r.portions || 2);
const imgUrl = (r) => r && r.img ? '/img/' + encodeURIComponent(r.id) + '?v=' + (r.upd || 0) : '';
const EMO = ['🍝', '🥘', '🍲', '🥗', '🍛', '🥙', '🍜', '🥧', '🍳', '🥞', '🍰', '🌮'];
const emoji = (r) => { const t = ((r && r.title) || '').toLowerCase(); if (/pizza|flammkuchen/.test(t)) return '🍕'; if (/kuchen|torte|muffin|kekse|plätzchen/.test(t)) return '🍰'; if (/salat/.test(t)) return '🥗'; if (/suppe|eintopf/.test(t)) return '🍲'; if (/pasta|nudel|spaghetti|lasagne/.test(t)) return '🍝'; if (/curry/.test(t)) return '🍛'; if (/burger/.test(t)) return '🍔'; let n = 0; for (const c of t) n += c.charCodeAt(0); return EMO[n % EMO.length]; };
const pic = (r, cls = '') => '<div class="pic ' + cls + '">' + (r && r.img ? '<img loading="lazy" src="' + imgUrl(r) + '" alt="" onerror="this.remove()">' : '') + '<div class="ph">' + (r && r.img ? '' : emoji(r)) + '</div></div>';

// ------------------------------------------------------------------ Zutaten: lesen, umrechnen, zusammenfassen
const ING = {
  UNITS: ['Esslöffel', 'Teelöffel', 'Messerspitze', 'Päckchen', 'Packungen', 'Packung', 'Handvoll', 'Scheiben', 'Scheibe', 'Flaschen', 'Flasche', 'Tassen', 'Tasse', 'Gläser', 'Glas', 'Prisen', 'Prise', 'Becher', 'Dosen', 'Dose', 'Bund', 'Stück', 'Stk.', 'Stk', 'St.', 'Zehen', 'Zehe', 'Würfel', 'Blätter', 'Blatt', 'Zweige', 'Zweig', 'Spritzer', 'Schuss', 'Tropfen', 'Stangen', 'Stange', 'Knollen', 'Knolle', 'Köpfe', 'Kopf', 'Rollen', 'Rolle', 'Tüten', 'Tüte', 'Pakete', 'Paket', 'Liter', 'Gramm', 'Kilo', 'Msp.', 'Msp', 'Pck.', 'Pck', 'Pkt.', 'gr.', 'kg', 'mg', 'ml', 'cl', 'dl', 'EL', 'TL', 'cm', 'g', 'l'],
  FR: { '½': .5, '¼': .25, '¾': .75, '⅓': 1 / 3, '⅔': 2 / 3, '⅛': .125 },
  num(s) {
    s = (s || '').trim(); if (!s) return null;
    let v = 0; const m1 = s.match(/^(\d+)\s*([½¼¾⅓⅔⅛])$/); if (m1) return +m1[1] + ING.FR[m1[2]];
    if (ING.FR[s] != null) return ING.FR[s];
    for (const part of s.split(/\s+/)) { const f = part.match(/^(\d+)\/(\d+)$/); if (f) v += +f[1] / +f[2]; else { const n = parseFloat(part.replace(',', '.')); if (isNaN(n)) return null; v += n; } }
    return v;
  },
  _re: null,
  parse(line) {
    line = (line || '').trim();
    if (/^#+\s*/.test(line)) return { grp: line.replace(/^#+\s*/, '') };
    if (!ING._re) {
      const N = '(?:\\d+(?:[.,]\\d+)?(?:\\s+\\d+\\/\\d+)?(?:\\s*[½¼¾⅓⅔⅛])?|\\d+\\/\\d+|[½¼¾⅓⅔⅛])';
      const U = ING.UNITS.map(u => u.replace(/\./g, '\\.')).join('|');
      ING._re = new RegExp('^(' + N + ')(?:\\s*(?:-|–|bis)\\s*(' + N + '))?\\s*(?:(' + U + ')(?=[\\s,.]|$)\\.?)?\\s*(.*)$');
    }
    const m = line.match(ING._re);
    if (!m) return { a: null, u: '', n: line };
    return { a: ING.num(m[1]), b: m[2] ? ING.num(m[2]) : null, u: m[3] || '', n: (m[4] || '').trim() || (m[3] ? '' : line) };
  },
  fmt(v, u) {
    if (v == null) return '';
    if (/^(g|ml|Gramm)$/.test(u) && v >= 20) v = v >= 200 ? Math.round(v / 10) * 10 : Math.round(v / 5) * 5;
    const w = Math.floor(v + 1e-6), f = v - w;
    const FR = [[.125, '⅛'], [.25, '¼'], [1 / 3, '⅓'], [.5, '½'], [2 / 3, '⅔'], [.75, '¾']];
    if (f < .06) return String(w || (v > 0 ? Math.round(v * 100) / 100 : 0)).replace('.', ',');
    if (f > .94) return String(w + 1);
    if (v < 20) for (const [x, s] of FR) if (Math.abs(f - x) < .04) return (w ? w : '') + s;
    return (Math.round(v * 10) / 10).toString().replace('.', ',');
  },
  qty(p, k) { if (p.a == null) return p.u; let s = ING.fmt(p.a * k, p.u); if (p.b != null) s += '–' + ING.fmt(p.b * k, p.u); return s + (p.u ? ' ' + p.u : ''); },
  NORM: { 'Esslöffel': 'EL', 'Teelöffel': 'TL', 'Gramm': 'g', 'gr.': 'g', 'Liter': 'l', 'Kilo': 'kg', 'Stk.': '', 'Stk': '', 'St.': '', 'Stück': '', 'Msp.': 'Msp', 'Messerspitze': 'Msp', 'Pck.': 'Pck', 'Pck': 'Pck', 'Pkt.': 'Pck', 'Päckchen': 'Pck', 'Packung': 'Pck', 'Packungen': 'Pck', 'Prisen': 'Prise', 'Zehen': 'Zehe', 'Dosen': 'Dose', 'Scheiben': 'Scheibe', 'Tassen': 'Tasse', 'Gläser': 'Glas', 'Zweige': 'Zweig', 'Blätter': 'Blatt', 'Stangen': 'Stange', 'Knollen': 'Knolle', 'Köpfe': 'Kopf', 'Rollen': 'Rolle', 'Tüten': 'Tüte', 'Pakete': 'Paket', 'Flaschen': 'Flasche' },
  key(n) { return n.toLowerCase().replace(/\(.*?\)/g, '').split(',')[0].replace(/\b(frisch(e|er|es)?|gehackt(e|er)?|gerieben(e|er)?|klein(e|er)?|groß(e|er)?|mittelgroß(e|er)?|fein|grob|tk|tiefgekühlt(e|er)?|bio|optional|evtl\.?|nach belieben|zum bestreuen|zum braten)\b/g, '').replace(/\s+/g, ' ').trim(); },
  CATS: [
    ['Gewürze & Basics', ['salz', 'pfeffer', 'paprikapulver', 'curry', 'zimt', 'muskat', 'kreuzkümmel', 'kümmel', 'lorbeer', 'gewürz', 'chiliflocken', 'kurkuma', 'oregano, getr', 'majoran', 'cayenne', 'piment', 'nelke', 'kardamom', 'sternanis', 'vanillezucker', 'öl', 'olivenöl', 'rapsöl', 'sonnenblumenöl', 'essig', 'balsamico', 'brühe', 'fond', 'senf', 'zucker']],
    ['Fleisch & Fisch', ['fleisch', 'hack', 'rind', 'schwein', 'hähnchen', 'hühn', 'huhn', 'pute', 'speck', 'schinken', 'wurst', 'salami', 'lachs', 'fisch', 'garnele', 'thunfisch', 'guanciale', 'pancetta', 'chorizo', 'filet', 'steak', 'schnitzel', 'bratwurst', 'lamm', 'ente', 'kassler', 'gulasch', 'leber', 'kabeljau', 'forelle', 'scampi', 'muschel']],
    ['Milch, Käse & Eier', ['milch', 'sahne', 'butter', 'käse', 'quark', 'joghurt', 'schmand', 'crème', 'creme', 'mozzarella', 'parmesan', 'pecorino', 'feta', 'frischkäse', 'ei', 'eier', 'eigelb', 'eiweiß', 'mascarpone', 'ricotta', 'tofu', 'gouda', 'emmentaler', 'gruyère', 'burrata', 'skyr', 'kefir', 'margarine', 'halloumi', 'cheddar']],
    ['Brot & Backen', ['mehl', 'teig', 'hefe', 'backpulver', 'vanille', 'brot', 'brötchen', 'toast', 'speisestärke', 'stärke', 'kakao', 'schokolade', 'puderzucker', 'natron', 'blätterteig', 'pizzateig', 'tortilla', 'wrap', 'baguette', 'semmelbrösel', 'paniermehl', 'gelatine', 'kuvertüre']],
    ['Nudeln, Reis & Vorrat', ['nudel', 'spaghetti', 'penne', 'pasta', 'tagliatelle', 'fusilli', 'reis', 'linsen', 'kichererbsen', 'bohnen', 'tomatenmark', 'passierte', 'stückige tomaten', 'dosentomaten', 'couscous', 'bulgur', 'quinoa', 'haferflocken', 'nüsse', 'nuss', 'mandel', 'sesam', 'kokosmilch', 'honig', 'ketchup', 'mayonnaise', 'sojasoße', 'sojasauce', 'tortellini', 'gnocchi', 'lasagne', 'mais', 'erdnuss', 'rosinen', 'oliven', 'kapern', 'pesto', 'sirup', 'marmelade', 'polenta', 'glasnudel', 'mie', 'ramen', 'sushi']],
    ['Obst & Gemüse', ['tomate', 'zwiebel', 'knoblauch', 'kartoffel', 'möhre', 'karotte', 'paprika', 'zucchini', 'gurke', 'salat', 'spinat', 'lauch', 'porree', 'sellerie', 'brokkoli', 'blumenkohl', 'kohl', 'pilz', 'champignon', 'aubergine', 'kürbis', 'apfel', 'äpfel', 'banane', 'zitrone', 'limette', 'orange', 'beere', 'ingwer', 'chili', 'petersilie', 'basilikum', 'schnittlauch', 'koriander', 'dill', 'rosmarin', 'thymian', 'minze', 'avocado', 'erbsen', 'frühlingszwiebel', 'schalotte', 'radieschen', 'rucola', 'fenchel', 'spargel', 'kräuter', 'mango', 'ananas', 'birne', 'trauben', 'rote bete', 'süßkartoffel', 'pastinake', 'mangold', 'kirsch', 'pfirsich', 'salbei', 'oregano', 'rhabarber']],
    ['Getränke', ['wein', 'bier', 'saft', 'sekt', 'prosecco', 'rum', 'likör', 'cola', 'espresso', 'kaffee']],
  ],
  cat(n) {
    const t = ' ' + n.toLowerCase().split(',')[0] + ' ', L = /[a-zäöüß]/;
    let best = null, be = -1, bl = 0;
    for (const [c, ws] of ING.CATS) for (const w of ws) {
      let i = t.indexOf(w);
      while (i > 0) {
        const e = i + w.length, start = !L.test(t[i - 1]), end = !L.test(t[e]) || /^(n|e|en|s|er)[^a-zäöüß]/.test(t.slice(e, e + 3));
        if ((start || end) && (e > be || (e === be && w.length > bl))) { best = c; be = e; bl = w.length; }
        i = t.indexOf(w, i + 1);
      }
    }
    return best || 'Sonstiges';
  },
  SKIP: /^(wasser|leitungswasser|heißes wasser|kaltes wasser|eiswürfel)$/,
};

// ------------------------------------------------------------------ Fokus-Steuerung (Fernbedienung / Tastatur)
const NAV = {
  cur: null,
  scope() { const ov = $$('#layer .ov'); return ov.length ? ov[ov.length - 1] : document.body; },
  items() { const sc = NAV.scope(); return $$('.f', sc).filter(e => (sc !== document.body || !e.closest('#layer')) && e.offsetParent !== null && !e.disabled && !e.classList.contains('off')); },
  set(e, noScroll) {
    if (NAV.cur && NAV.cur !== e) NAV.cur.classList.remove('foc');
    NAV.cur = e; if (!e) return;
    e.classList.add('foc');
    if (!/^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName)) { if (document.activeElement && document.activeElement !== e && /^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) document.activeElement.blur(); }
    if (!noScroll) NAV.reveal(e);
  },
  reveal(e) {
    const r = e.getBoundingClientRect(), row = e.closest('.row');
    if (row) { const rr = row.getBoundingClientRect(); if (r.left < rr.left + 20 || r.right > rr.right - 20) row.scrollLeft += r.left - rr.left - rr.width / 2 + r.width / 2; }
    const box = e.closest('.dlg, .cook .side, .cook .txt') || null;
    if (box) { const b = box.getBoundingClientRect(); if (r.top < b.top + 30 || r.bottom > b.bottom - 30) box.scrollTop += r.top - b.top - b.height / 3; return; }
    const top = TV ? 140 : 90, bot = innerHeight - (document.body.classList.contains('narrow') ? 110 : 60);
    if (r.top < top) scrollBy({ top: r.top - top - (TV ? 60 : 20), behavior: 'smooth' });
    else if (r.bottom > bot) scrollBy({ top: r.bottom - bot + (TV ? 60 : 20), behavior: 'smooth' });
  },
  first(root) { const sc = root || NAV.scope(); const e = $$('.f', sc).find(x => x.offsetParent !== null && !x.disabled && !(sc === document.body && x.closest('#layer')) && !x.closest('#rail')); if (e) NAV.set(e, true); else NAV.set(null); },
  move(dir) {
    const items = NAV.items(); if (!items.length) return;
    if (!NAV.cur || !items.includes(NAV.cur)) { NAV.first(); return; }
    const a = NAV.cur.getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    let best = null, bs = 1e9;
    for (const e of items) {
      if (e === NAV.cur) continue;
      const b = e.getBoundingClientRect(), bx = b.left + b.width / 2, by = b.top + b.height / 2;
      let pri, sec, ov;
      if (dir === 'left' || dir === 'right') {
        pri = dir === 'left' ? a.left - b.right : b.left - a.right; if ((dir === 'left' ? bx >= ax : bx <= ax)) continue;
        ov = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top); sec = ov > 0 ? 0 : Math.abs(by - ay);
      } else {
        pri = dir === 'up' ? a.top - b.bottom : b.top - a.bottom; if ((dir === 'up' ? by >= ay : by <= ay)) continue;
        ov = Math.min(a.right, b.right) - Math.max(a.left, b.left); sec = ov > 0 ? Math.abs(bx - ax) * .15 : Math.abs(bx - ax);
      }
      const s = Math.max(pri, -5) + sec * 2.2 + (e.closest('#rail') && !NAV.cur.closest('#rail') && dir !== 'left' ? 1e5 : 0);
      if (s < bs) { bs = s; best = e; }
    }
    if (best) NAV.set(best);
    else if (dir === 'left' && !NAV.cur.closest('#rail') && NAV.scope() === document.body) { const on = $('#rail .it.on') || $('#rail .it'); if (on) NAV.set(on); }
  },
  ok() {
    const e = NAV.cur; if (!e) return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName)) { e.focus(); if (e.select && e.type !== 'file' && e.tagName === 'INPUT') try { e.setSelectionRange(e.value.length, e.value.length); } catch (x) {} return; }
    e.click();
  },
};
document.addEventListener('keydown', (ev) => {
  const k = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', Escape: 'back', GoBack: 'back', BrowserBack: 'back' }[ev.key];
  if (!k) return;
  const ae = document.activeElement, typing = ae && /^(INPUT|TEXTAREA|SELECT)$/.test(ae.tagName) && ae.type !== 'checkbox';
  if (typing) {
    if (k === 'back') { ev.preventDefault(); ae.blur(); NAV.set(ae, true); return; }
    if (ae.tagName === 'TEXTAREA' && k !== 'ok') { const atTop = ae.selectionStart === 0, atEnd = ae.selectionEnd === ae.value.length; if (!((k === 'up' && atTop) || (k === 'down' && atEnd))) return; }
    else if (k === 'left' || k === 'right' || (k === 'ok' && ae.tagName === 'TEXTAREA')) return;
    if (k === 'ok') { if (ae._enter) { ev.preventDefault(); ae._enter(); } return; }
    ae.blur(); NAV.set(ae, true);
  }
  if (!TV && !document.body.classList.contains('kb') && k !== 'back') { document.body.classList.add('kb'); if (!NAV.cur || !NAV.cur.isConnected) { NAV.first(); ev.preventDefault(); return; } }
  ev.preventDefault();
  if (VIEW.key && VIEW.key(k)) return;
  if (k === 'back') { APP.back(); return; }
  if (k === 'ok') NAV.ok(); else NAV.move(k);
});
document.addEventListener('mousedown', () => { if (!TV) document.body.classList.remove('kb'); }, true);
document.addEventListener('focusin', (e) => { const f = e.target.closest && e.target.closest('.f'); if (f && f !== NAV.cur && /^(INPUT|TEXTAREA|SELECT)$/.test(f.tagName)) { if (NAV.cur) NAV.cur.classList.remove('foc'); NAV.cur = f; f.classList.add('foc'); } });

// ------------------------------------------------------------------ Dialoge
const UI = {
  open(html, cls, onClose) {
    const prev = NAV.cur;
    const ov = h('div', 'ov'), d = h('div', 'dlg ' + (cls || ''), html); ov.appendChild(d); $('#layer').appendChild(ov);
    ov.addEventListener('click', (e) => { if (e.target === ov) UI.close(ov); });
    ov._close = () => { ov.remove(); if (onClose) onClose(); if (prev && prev.isConnected) NAV.set(prev, true); else NAV.first(); };
    setTimeout(() => NAV.first(d), 0);
    return d;
  },
  close(ov) { ov = ov || $$('#layer .ov').pop(); if (ov) ov._close(); },
  top() { return $$('#layer .ov').length > 0; },
  /** Auswahl: items = [{t, i(icon), v, danger, sub}] → Promise<v|null> */
  choose(title, items, sub) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2>' + (sub ? '<p>' + sub + '</p>' : '') + '<div class="opts"></div>', '', () => { if (!done) res(null); });
      const box = $('.opts', d);
      for (const it of items) {
        const b = h('button', 'opt f' + (it.danger ? ' danger' : ''), (it.i ? ico(it.i) : '') + '<span>' + esc(it.t) + (it.sub ? '<br><small class="mut">' + esc(it.sub) + '</small>' : '') + '</span>');
        b.onclick = () => { done = true; UI.close(); res(it.v); }; box.appendChild(b);
      }
    });
  },
  confirm(title, text, yes = 'Ja', danger) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2><p>' + (text || '') + '</p><div class="btns"><button class="btn f no">Abbrechen</button><button class="btn pri f yes' + (danger ? ' danger' : '') + '">' + esc(yes) + '</button></div>', '', () => { if (!done) res(false); });
      $('.no', d).onclick = () => UI.close();
      $('.yes', d).onclick = () => { done = true; UI.close(); res(true); };
      setTimeout(() => NAV.set($('.yes', d), true), 0);
    });
  },
  prompt(title, val, opt = {}) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2>' + (opt.text ? '<p>' + opt.text + '</p>' : '') + (opt.area ? '<textarea class="in f" rows="5"></textarea>' : '<input class="in f" type="' + (opt.type || 'text') + '">') + '<div class="btns"><button class="btn f no">Abbrechen</button><button class="btn pri f yes">' + esc(opt.ok || 'OK') + '</button></div>', '', () => { if (!done) res(null); });
      const inp = $('.in', d); inp.value = val || ''; if (opt.ph) inp.placeholder = opt.ph;
      const ok = () => { done = true; const v = inp.value; UI.close(); res(v); };
      inp._enter = ok; if (!opt.area) inp.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !TV) ok(); });
      $('.no', d).onclick = () => UI.close(); $('.yes', d).onclick = ok;
      setTimeout(() => { NAV.set(inp, true); if (!TV) inp.focus(); }, 30);
    });
  },
};

// ------------------------------------------------------------------ Ansichten & Navigation
const VIEW = { name: '', arg: null, key: null, dirty: false };
const VIEWS = {};
const RAIL = [['home', 'Heute', 'home'], ['list', 'Rezepte', 'book'], ['plan', 'Planer', 'cal'], ['shop', 'Einkauf', 'cart'], ['settings', 'Mehr', 'gear']];
const APP = {
  stack: [],
  async go(name, arg, opt = {}) {
    if (VIEW.leave && !(await VIEW.leave())) return;
    if (opt.root) APP.stack = [];
    else if (VIEW.name) APP.stack.push({ name: VIEW.name, arg: VIEW.arg, y: scrollY, focus: APP.focusKey() });
    if (!INAPP && !opt.pop) { const hs = '#/' + name + (arg && typeof arg === 'string' ? '/' + encodeURIComponent(arg) : ''); if (opt.root) history.replaceState({ d: 0 }, '', hs); else history.pushState({ d: APP.stack.length }, '', hs); }
    await APP.show(name, arg);
  },
  async show(name, arg, restore) {
    VIEW.name = name; VIEW.arg = arg; VIEW.key = null; VIEW.firstFocus = null; VIEW.leave = null; VIEW.editing = false;
    if (AND && AND.keepOn) AND.keepOn(name === 'cook');
    APP.rail();
    const main = $('#main'); main.innerHTML = '';
    try { await VIEWS[name](main, arg); } catch (e) { main.innerHTML = '<div class="empty"><b>😕</b>' + esc(e.message) + '</div>'; console.error(e); }
    if (restore) { scrollTo(0, restore.y || 0); const e = restore.focus && $('[data-k="' + CSS.escape(restore.focus) + '"]', main); if (e) NAV.set(e, true); else NAV.first(main); }
    else { scrollTo(0, 0); if (!UI.top()) { if (VIEW.firstFocus && VIEW.firstFocus.isConnected) NAV.set(VIEW.firstFocus, true); else NAV.first(main); } }
  },
  focusKey() { return NAV.cur && NAV.cur.dataset && NAV.cur.dataset.k || ''; },
  async back(fromPop) {
    if (UI.top()) { UI.close(); return; }
    if (VIEW.leave && !(await VIEW.leave())) return;
    VIEW.leave = null;
    if (NAV.cur && NAV.cur.closest('#rail') && APP.stack.length === 0 && VIEW.name !== 'home') { APP.go('home', null, { root: true }); return; }
    const p = APP.stack.pop();
    if (p) { if (!INAPP && !fromPop) { APP._skipPop = true; history.back(); } await APP.show(p.name, p.arg, p); return; }
    if (VIEW.name !== 'home') { APP.go('home', null, { root: true }); return; }
    if (AND && AND.exit) AND.exit();
  },
  async refresh() { const y = scrollY, f = APP.focusKey(); await APP.show(VIEW.name, VIEW.arg, { y, focus: f }); },
  rail() {
    const r = $('#rail');
    if (!r.firstChild) {
      r.innerHTML = '<div class="logo"></div>' + RAIL.map(([v, t, i]) => '<button class="it f" data-v="' + v + '" data-k="rail-' + v + '">' + ico(i) + '<span>' + t + '</span></button>').join('') + '<div class="sp"></div>';
      $$('.it', r).forEach(b => b.onclick = () => APP.go(b.dataset.v, null, { root: true }));
    }
    const root = APP.stack.length ? APP.stack[0].name : VIEW.name, map = { recipe: 'list', cook: 'list', edit: 'list' };
    $$('.it', r).forEach(b => b.classList.toggle('on', b.dataset.v === (map[root] || root)));
    const open = DB.shop.items.filter(i => !i.done).length, sb = $('[data-v="shop"]', r);
    let bd = $('.bd', sb); if (open) { if (!bd) { bd = h('span', 'bd'); sb.appendChild(bd); } bd.textContent = open > 99 ? '99+' : open; } else if (bd) bd.remove();
  },
  key(k) { if (VIEW.key && VIEW.key(k)) return; },
  shared(text) { APP.go('edit', { importUrl: text }); },
};
window.APP = APP;
addEventListener('popstate', () => { if (APP._skipPop) { APP._skipPop = false; return; } APP.back(true); });

// Datum
const WD = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'], WDL = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
const MON = ['Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni', 'Juli', 'Aug.', 'Sep.', 'Okt.', 'Nov.', 'Dez.'];
const ymd = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
const addD = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const monday = (d) => { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); return addD(x, -((x.getDay() + 6) % 7)); };
const today = () => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), d.getDate()); };
const dayLabel = (d) => { const t = today(), diff = Math.round((new Date(d.getFullYear(), d.getMonth(), d.getDate()) - t) / 864e5); return diff === 0 ? 'Heute' : diff === 1 ? 'Morgen' : diff === -1 ? 'Gestern' : WDL[d.getDay()]; };
const PLAN = {
  get(date, slot) { return (DB.plan.days[date] || []).find(e => e.slot === slot); },
  set(date, slot, e) {
    const l = (DB.plan.days[date] || []).filter(x => x.slot !== slot);
    if (e) l.push(Object.assign({ id: Math.random().toString(36).slice(2, 9), slot }, e));
    if (l.length) DB.plan.days[date] = l; else delete DB.plan.days[date];
    // alte Tage (> 120 Tage) aufräumen
    const old = ymd(addD(today(), -120)); for (const k of Object.keys(DB.plan.days)) if (k < old) delete DB.plan.days[k];
  },
  label(e) { const r = e && e.rid && DB.byId[e.rid]; return r ? r.title : e && e.t ? e.t : (e && e.rid ? 'Gelöschtes Rezept' : ''); },
};

// ------------------------------------------------------------------ Bausteine
function cardHtml(r, k) {
  return '<div class="card f" data-k="' + esc(k || 'r-' + r.id) + '" data-id="' + esc(r.id) + '">' + pic(r) + (r.fav ? '<div class="fav">❤️</div>' : '') +
    '<div class="bd"><div class="t">' + esc(r.title) + '</div><div class="m">' + (r.time ? '<span>⏱ ' + fmtTime(r.time) + '</span>' : '') +
    (r.store === 'cloud' ? '<span class="badge cloud">☁ midnox.de</span>' : '<span class="badge loc">● lokal</span>') + '</div></div></div>';
}
const fmtTime = (m) => m >= 60 ? Math.floor(m / 60) + ' Std.' + (m % 60 ? ' ' + (m % 60) + ' Min.' : '') : m + ' Min.';
function bindCards(root, fn) { $$('.card[data-id]', root).forEach(c => c.onclick = () => (fn || ((id) => APP.go('recipe', id)))(c.dataset.id)); }

// ------------------------------------------------------------------ Start
VIEWS.home = async (m) => {
  const t = today(), hr = new Date().getHours(), slots = DB.slots();
  const greet = hr < 11 ? 'Guten Morgen' : hr < 17 ? 'Hallo' : 'Guten Abend';
  m.innerHTML = '<div class="head"><div><h1>' + greet + ' <span class="grad">👋</span></h1><div class="sub">' + WDL[t.getDay()] + ', ' + t.getDate() + '. ' + MON[t.getMonth()] + ' · ' + DB.list.length + ' Rezepte</div></div></div>';
  const days = [t, addD(t, 1)];
  for (const d of days) {
    const ds = ymd(d);
    m.appendChild(h('h2', 'sec', dayLabel(d) + ' <small>' + WDL[d.getDay()] + ', ' + d.getDate() + '. ' + MON[d.getMonth()] + '</small>'));
    const g = h('div', 'today');
    for (const s of slots) {
      const e = PLAN.get(ds, s), r = e && e.rid && DB.byId[e.rid];
      const c = h('div', 'meal f' + (e ? '' : ' none'), (r ? pic(r) : '') + '<div class="bd"><div class="sl">' + esc(s) + '</div><div class="t">' + (e ? esc(PLAN.label(e)) : 'Noch nichts geplant') + '</div>' + (r && r.time ? '<div class="mut">⏱ ' + fmtTime(r.time) + (e.p ? ' · ' + e.p + ' Portionen' : '') + '</div>' : '') + '</div>');
      c.dataset.k = 'm-' + ds + s;
      c.onclick = () => r ? APP.go('recipe', r.id + (e.p ? '|' + e.p : '')) : slotMenu(ds, s);
      g.appendChild(c);
    }
    m.appendChild(g);
  }
  const open = DB.shop.items.filter(i => !i.done).length;
  const q = h('div', 'quick');
  const qa = (i, t, s, fn, k) => { const e = h('div', 'qa f', '<div class="ico">' + ico(i) + '</div><div><b>' + t + '</b><small>' + s + '</small></div>'); e.dataset.k = k; e.onclick = fn; q.appendChild(e); };
  qa('cal', 'Woche planen', 'Kochplaner öffnen', () => APP.go('plan', null, { root: true }), 'q-plan');
  qa('cart', 'Einkaufsliste', open ? open + ' Dinge offen' : 'aus dem Plan erstellen', () => APP.go('shop', null, { root: true }), 'q-shop');
  qa('link', 'Rezept übernehmen', 'Link von Chefkoch & Co.', () => importFlow(), 'q-imp');
  qa('plus', 'Neues Rezept', 'selbst eintippen', () => APP.go('edit', null), 'q-new');
  m.appendChild(q);
  const favs = DB.list.filter(r => r.fav), recent = DB.list.slice().sort((a, b) => (b.created || 0) - (a.created || 0)).slice(0, 12);
  for (const [title, list, kp] of [['Favoriten', favs, 'f'], ['Zuletzt hinzugefügt', recent, 'n']]) {
    if (!list.length) continue;
    m.appendChild(h('h2', 'sec', title));
    const row = h('div', 'row', list.slice(0, 16).map(r => cardHtml(r, kp + '-' + r.id)).join('')); bindCards(row); m.appendChild(row);
  }
  if (!DB.list.length) m.appendChild(h('div', 'empty', '<b>🍳</b>Noch keine Rezepte. Übernimm eins per Link oder leg selbst eins an – am bequemsten am Handy oder PC über die Adresse unten.'));
  if (INAPP || TV) m.appendChild(lanBox());
};
function lanBox() {
  const url = DB.st.url || location.origin;
  const b = h('div', 'lan', '<div class="qr">' + qrSvg(url) + '</div><div><div class="mut">Im Heimnetz auf Handy, Tablet oder PC öffnen</div><b class="grad">' + esc(url.replace(/^http:\/\//, '')) + '</b><div class="hint">Rezepte, Kochplaner und Einkaufsliste – alles bleibt hier auf dem Gerät' + (DB.S.bg ? ', auch wenn die App geschlossen ist.' : '. Erreichbar, solange die App läuft (Einstellungen → „Immer im Netzwerk“).') + '</div></div>');
  return b;
}
function qrSvg(url) { try { const q = qrcode(0, 'M'); q.addData(url); q.make(); return q.createSvgTag({ cellSize: 4, margin: 0, scalable: true }); } catch (e) { return ''; } }

// ------------------------------------------------------------------ Rezeptliste
const LIST = { q: '', f: 'all', tag: '' };
VIEWS.list = async (m) => {
  m.innerHTML = '<div class="head"><div><h1>Rezepte</h1><div class="sub">' + DB.list.length + ' Rezepte' + (DB.cloud.on ? ' · ' + DB.list.filter(r => r.store === 'cloud').length + ' auf midnox.de' : '') + '</div></div><div class="btns"><button class="btn f" data-k="l-imp">' + ico('link') + 'Per Link</button><button class="btn pri f" data-k="l-new">' + ico('plus') + 'Neu</button></div></div>' +
    (DB.cloud.err ? '<div class="empty" style="padding:1.4rem;margin-bottom:2rem;text-align:left">☁ midnox.de: ' + esc(DB.cloud.err) + ' – es werden die zuletzt geladenen Rezepte gezeigt.</div>' : '') +
    '<div class="addrow"><input class="in f" data-k="l-q" placeholder="Suchen: Titel, Zutat, Stichwort …"></div><div class="chips" style="margin-bottom:2.4rem"></div><div class="grid"></div>';
  $('[data-k=l-imp]', m).onclick = () => importFlow();
  $('[data-k=l-new]', m).onclick = () => APP.go('edit', null);
  const inp = $('[data-k=l-q]', m); inp.value = LIST.q;
  inp.oninput = () => { LIST.q = inp.value; draw(); };
  const chips = $('.chips', m), grid = $('.grid', m);
  const F = [['all', 'Alle'], ['fav', '❤ Favoriten'], ['local', 'Lokal'], ['cloud', 'midnox.de']].filter(f => DB.cloud.on || f[0] !== 'cloud');
  const tags = DB.tags().slice(0, TV ? 10 : 14);
  chips.innerHTML = F.map(([v, t]) => '<button class="chip f" data-f="' + v + '" data-k="lf-' + v + '">' + t + '</button>').join('') + tags.map(t => '<button class="chip f" data-t="' + esc(t) + '" data-k="lt-' + esc(t) + '">' + esc(t) + '</button>').join('');
  $$('.chip', chips).forEach(c => c.onclick = () => { if (c.dataset.f) { LIST.f = c.dataset.f; LIST.tag = ''; } else LIST.tag = LIST.tag === c.dataset.t ? '' : c.dataset.t; draw(); });
  function draw() {
    $$('.chip', chips).forEach(c => c.classList.toggle('on', c.dataset.f ? (LIST.f === c.dataset.f && !LIST.tag) : LIST.tag === c.dataset.t));
    const q = LIST.q.trim().toLowerCase();
    const l = DB.list.filter(r => (LIST.f === 'all' || (LIST.f === 'fav' ? r.fav : r.store === LIST.f)) && (!LIST.tag || (r.tags || []).includes(LIST.tag)) &&
      (!q || (r.title + ' ' + (r.tags || []).join(' ') + ' ' + (r.ingr || []).join(' ')).toLowerCase().includes(q)));
    grid.innerHTML = l.length ? l.map(r => cardHtml(r)).join('') : '<div class="empty" style="grid-column:1/-1"><b>🔎</b>' + (DB.list.length ? 'Nichts gefunden.' : 'Noch keine Rezepte.') + '</div>';
    bindCards(grid);
  }
  draw();
};

// ------------------------------------------------------------------ Rezept
VIEWS.recipe = async (m, arg) => {
  const [id, pp] = String(arg).split('|');
  const r = DB.byId[id]; if (!r) throw new Error('Rezept nicht gefunden');
  const base = r.portions || 2; let por = +pp || defP(r);
  m.innerHTML = '<div class="rhero">' + pic(r) + '<div><h1>' + esc(r.title) + '</h1><div class="meta">' +
    (r.time ? '<span class="mi">' + ico('clock') + fmtTime(r.time) + '</span>' : '') +
    (r.store === 'cloud' ? '<span class="badge cloud">☁ midnox.de</span>' : '<span class="badge loc">● nur auf diesem Gerät</span>') +
    (r.tags || []).map(t => '<span class="badge">' + esc(t) + '</span>').join('') + '</div>' +
    '<div class="stepper"><button class="btn f mi" data-k="p-">' + ico('minus') + '</button><b class="pn"></b><button class="btn f pl" data-k="p+">' + ico('plus') + '</button></div>' +
    '<div class="btns" style="margin-top:2rem"><button class="btn pri f" data-k="r-cook">' + ico('chef') + 'Jetzt kochen</button><button class="btn f" data-k="r-plan">' + ico('cal') + 'Einplanen</button><button class="btn f ic" data-k="r-fav" title="Favorit">' + (r.fav ? '❤️' : ico('heart')) + '</button><button class="btn f ic" data-k="r-edit" title="Bearbeiten">' + ico('edit') + '</button><button class="btn f ic" data-k="r-more" title="Mehr">⋯</button></div></div></div>' +
    '<div class="cols"><div><h2 class="sec" style="margin-top:0">Zutaten</h2><ul class="ings"></ul><div class="btns noprint" style="margin-top:1.6rem"><button class="btn sm f" data-k="r-shop">' + ico('cart') + 'Auf die Einkaufsliste</button></div></div>' +
    '<div><h2 class="sec" style="margin-top:0">Zubereitung</h2><ol class="steps">' + (r.steps || []).map(s => /^#+\s*/.test(s) ? '<li class="grp">' + esc(s.replace(/^#+\s*/, '')) + '</li>' : '<li>' + esc(s) + '</li>').join('') + '</ol>' +
    (r.notes ? '<div class="notes">' + esc(r.notes) + '</div>' : '') + (r.src ? '<div class="hint" style="margin-top:1.6rem">Quelle: ' + (INAPP ? esc(r.src.replace(/^https?:\/\/(www\.)?/, '').split('/')[0]) : '<a href="' + esc(r.src) + '" target="_blank" rel="noopener" style="color:inherit">' + esc(r.src.replace(/^https?:\/\/(www\.)?/, '').split('/')[0]) + '</a>') + '</div>' : '') + '</div></div>';
  const draw = () => {
    $('.pn', m).textContent = por + (por === 1 ? ' Portion' : ' Portionen');
    const k = por / base;
    $('.ings', m).innerHTML = (r.ingr || []).map(l => { const p = ING.parse(l); return p.grp != null ? '<li class="grp">' + esc(p.grp) + '</li>' : '<li><span class="q">' + esc(ING.qty(p, k)) + '</span><span>' + esc(p.n) + '</span></li>'; }).join('') || '<li class="mut">Keine Zutaten eingetragen</li>';
  };
  draw(); VIEW.firstFocus = $('[data-k=r-cook]', m);
  $('[data-k="p-"]', m).onclick = () => { if (por > 1) { por--; draw(); } };
  $('[data-k="p+"]', m).onclick = () => { if (por < 40) { por++; draw(); } };
  $('[data-k=r-cook]', m).onclick = () => APP.go('cook', r.id + '|' + por);
  $('[data-k=r-plan]', m).onclick = () => planRecipe(r, +pp || defP(r));
  $('[data-k=r-edit]', m).onclick = () => APP.go('edit', r.id);
  $('[data-k=r-shop]', m).onclick = async () => { addToShop([{ r, p: por }], false); await DB.saveShop(); APP.rail(); toast('Zutaten stehen auf der Einkaufsliste'); };
  $('[data-k=r-fav]', m).onclick = async () => { await saveRecipe(Object.assign({}, r, { fav: !r.fav }), true); APP.refresh(); };
  $('[data-k=r-more]', m).onclick = async () => {
    const other = r.store === 'cloud' ? 'local' : 'cloud';
    const v = await UI.choose(r.title, [
      { t: other === 'cloud' ? 'Auf midnox.de speichern' : 'Nur auf diesem Gerät speichern', sub: other === 'cloud' ? (DB.cloud.on ? 'Dann auf allen Geräten verfügbar' : 'Erst in den Einstellungen einrichten') : 'Wird von midnox.de entfernt', i: other === 'cloud' ? 'cloud' : 'phone', v: 'move' },
      { t: 'Rezept teilen', i: 'share', v: 'share' }, ...(INAPP ? [] : [{ t: 'Drucken', i: 'list', v: 'print' }]),
      { t: 'Löschen', i: 'trash', v: 'del', danger: true }]);
    if (v === 'move') { if (other === 'cloud' && !DB.cloud.on) { APP.go('settings', null, { root: true }); return; } await saveRecipe(Object.assign({}, r, { store: other })); toast(other === 'cloud' ? 'Liegt jetzt auf midnox.de' : 'Liegt jetzt nur auf diesem Gerät'); APP.refresh(); }
    if (v === 'share') shareText(r.title, recipeText(r, por));
    if (v === 'print') print();
    if (v === 'del' && await UI.confirm('Rezept löschen?', '„' + esc(r.title) + '“ wird ' + (r.store === 'cloud' ? 'von midnox.de' : 'von diesem Gerät') + ' gelöscht.', 'Löschen', true)) {
      try { await API.post('del?id=' + encodeURIComponent(r.id), {}); await DB.load(); toast('Gelöscht'); APP.back(); } catch (e) { toast(e.message); }
    }
  };
};
function recipeText(r, por) {
  const base = r.portions || 2, k = por / base;
  return r.title + '\n' + por + ' Portionen' + (r.time ? ' · ' + fmtTime(r.time) : '') + '\n\nZutaten:\n' + (r.ingr || []).map(l => { const p = ING.parse(l); return p.grp != null ? '\n' + p.grp + ':' : '• ' + (ING.qty(p, k) + ' ' + p.n).trim(); }).join('\n') +
    '\n\nZubereitung:\n' + (r.steps || []).filter(s => !/^#/.test(s)).map((s, i) => (i + 1) + '. ' + s).join('\n') + (r.src ? '\n\nQuelle: ' + r.src : '');
}
function shareText(title, text) {
  if (AND && AND.shareText) { AND.shareText(title, text); return; }
  if (navigator.share) { navigator.share({ title, text }).catch(() => {}); return; }
  (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()).then(() => toast('In die Zwischenablage kopiert'), () => UI.prompt(title, text, { area: true, ok: 'Fertig' }));
}
async function saveRecipe(r, quiet) {
  try { const res = await API.post('recipe', r); await DB.load(); if (!quiet) toast('Gespeichert'); return res.recipe; }
  catch (e) { toast(e.message, 4500); throw e; }
}

// ------------------------------------------------------------------ Kochmodus
const TIMERS = [];
VIEWS.cook = async (m, arg) => {
  const [id, pp] = String(arg).split('|'); const r = DB.byId[id]; if (!r) throw new Error('Rezept nicht gefunden');
  const base = r.portions || 2, por = +pp || base, k = por / base;
  const steps = []; let grp = '';
  for (const s of r.steps || []) { if (/^#+\s*/.test(s)) grp = s.replace(/^#+\s*/, ''); else steps.push({ s, grp }); }
  if (!steps.length) steps.push({ s: 'Für dieses Rezept sind keine Schritte eingetragen.', grp: '' });
  let i = 0;
  const c = h('div', 'cook', '<div class="stage"><div class="top"><button class="btn f ic" data-k="c-x">' + ico('x') + '</button><div class="t">' + esc(r.title) + ' · ' + por + ' Portionen</div></div><div class="bar"><i></i></div>' +
    '<div class="txt"><div class="num"></div><div class="say"></div></div><div class="ctl"><button class="btn f" data-k="c-prev">' + ico('left') + 'Zurück</button><button class="btn pri f" data-k="c-next">Weiter' + ico('right') + '</button><span class="tb"></span></div></div>' +
    '<div class="side"><div class="timers"></div><h3>Zutaten</h3><ul class="ings">' + (r.ingr || []).map(l => { const p = ING.parse(l); return p.grp != null ? '<li class="grp">' + esc(p.grp) + '</li>' : '<li><span class="q">' + esc(ING.qty(p, k)) + '</span><span>' + esc(p.n) + '</span></li>'; }).join('') + '</ul></div>');
  m.appendChild(c);
  const TRE = /(\d+(?:[.,]\d+)?)(?:\s*(?:-|–|bis)\s*(\d+(?:[.,]\d+)?))?\s*(Stunden|Stunde|Std\.?|Minuten|Minute|Min\.?|Sekunden|Sek\.?)/gi;
  const draw = () => {
    const st = steps[i];
    $('.num', c).textContent = 'Schritt ' + (i + 1) + ' von ' + steps.length + (st.grp ? ' · ' + st.grp : '');
    $('.say', c).innerHTML = esc(st.s).replace(TRE, '<mark>$&</mark>');
    $('.bar i', c).style.width = ((i + 1) / steps.length * 100) + '%';
    $('[data-k=c-prev]', c).classList.toggle('off', i === 0);
    $('[data-k=c-next]', c).innerHTML = i === steps.length - 1 ? ico('check') + 'Fertig' : 'Weiter' + ico('right');
    const tb = $('.tb', c); tb.innerHTML = '';
    let mm; TRE.lastIndex = 0; const seen = {};
    while ((mm = TRE.exec(st.s))) {
      const n = parseFloat(mm[1].replace(',', '.')), u = mm[3].toLowerCase(), sec = Math.round(n * (u.startsWith('st') ? 3600 : u.startsWith('se') ? 1 : 60));
      if (!sec || seen[sec]) continue; seen[sec] = 1;
      const b = h('button', 'btn f', ico('timer') + mm[0].replace(/\s+/g, ' ')); b.dataset.k = 'c-t' + sec;
      b.onclick = () => startTimer(sec, mm ? r.title.slice(0, 30) + ' – Schritt ' + (i + 1) : '', c); tb.appendChild(b);
    }
  };
  $('[data-k=c-x]', c).onclick = () => APP.back();
  $('[data-k=c-prev]', c).onclick = () => { if (i > 0) { i--; draw(); } };
  $('[data-k=c-next]', c).onclick = () => { if (i < steps.length - 1) { i++; draw(); } else { toast('Guten Appetit! 🍽'); APP.back(); } };
  // Fernbedienung: links/rechts blättert, solange der Fokus unten auf den Knöpfen liegt
  VIEW.key = (k) => {
    if (UI.top()) return false;
    if ((k === 'left' || k === 'right') && NAV.cur && NAV.cur.closest('.ctl') && !NAV.cur.closest('.tb')) { $(k === 'left' ? '[data-k=c-prev]' : '[data-k=c-next]', c).click(); NAV.set($('[data-k=c-next]', c), true); return true; }
    return false;
  };
  // Wischen am Handy
  let sx = null; c.addEventListener('touchstart', e => { sx = e.touches[0].clientX; }, { passive: true });
  c.addEventListener('touchend', e => { if (sx == null) return; const dx = e.changedTouches[0].clientX - sx; if (Math.abs(dx) > 70 && e.target.closest('.stage')) $(dx < 0 ? '[data-k=c-next]' : '[data-k=c-prev]', c).click(); sx = null; });
  draw(); drawTimers(c);
  setTimeout(() => NAV.set($('[data-k=c-next]', c), true), 0);
};
function startTimer(sec, label, c) { TIMERS.push({ id: Date.now(), end: Date.now() + sec * 1000, sec, label, ring: false }); toast('Wecker läuft: ' + fmtClock(sec)); drawTimers(c); }
const fmtClock = (s) => { s = Math.max(0, Math.round(s)); const hh = Math.floor(s / 3600), mm = Math.floor(s % 3600 / 60), ss = s % 60; return (hh ? hh + ':' + String(mm).padStart(2, '0') : mm) + ':' + String(ss).padStart(2, '0'); };
function drawTimers(c) {
  const box = c ? $('.timers', c) : $('.cook .timers'); if (!box) return;
  box.innerHTML = '';
  for (const t of TIMERS) {
    const left = (t.end - Date.now()) / 1000;
    const e = h('div', 'tmr f' + (t.ring ? ' ring' : ''), '<b>' + (t.ring ? '⏰' : fmtClock(left)) + '</b><small>' + esc(t.label || 'Wecker') + '<br>' + (t.ring ? 'Fertig! Antippen zum Ausschalten' : 'Antippen zum Abbrechen') + '</small>');
    e.dataset.k = 'tm-' + t.id; e.onclick = () => { TIMERS.splice(TIMERS.indexOf(t), 1); drawTimers(); BEEP.stop(); };
    box.appendChild(e);
  }
}
const BEEP = {
  ctx: null, iv: null,
  start() {
    if (BEEP.iv) return;
    const tone = () => { try { BEEP.ctx = BEEP.ctx || new (window.AudioContext || window.webkitAudioContext)(); const t0 = BEEP.ctx.currentTime; for (let n = 0; n < 3; n++) { const o = BEEP.ctx.createOscillator(), g = BEEP.ctx.createGain(); o.frequency.value = 880; o.connect(g); g.connect(BEEP.ctx.destination); g.gain.setValueAtTime(.0001, t0 + n * .25); g.gain.exponentialRampToValueAtTime(.4, t0 + n * .25 + .02); g.gain.exponentialRampToValueAtTime(.0001, t0 + n * .25 + .2); o.start(t0 + n * .25); o.stop(t0 + n * .25 + .22); } } catch (e) {} if (navigator.vibrate) navigator.vibrate([300, 150, 300]); };
    tone(); BEEP.iv = setInterval(tone, 1500);
  },
  stop() { if (!TIMERS.some(t => t.ring)) { clearInterval(BEEP.iv); BEEP.iv = null; } },
};
setInterval(() => {
  if (!TIMERS.length) return;
  let ring = false;
  for (const t of TIMERS) if (!t.ring && t.end <= Date.now()) { t.ring = true; ring = true; toast('⏰ ' + (t.label || 'Wecker') + ' – fertig!', 6000); }
  if (ring || TIMERS.some(t => t.ring)) BEEP.start();
  const box = $('.cook .timers'); if (box) { const f = APP.focusKey(); drawTimers(); if (f.startsWith('tm-')) { const e = $('[data-k="' + f + '"]'); if (e) NAV.set(e, true); } }
}, 1000);

// ------------------------------------------------------------------ Bearbeiten / Neu / Import
async function importFlow(url) {
  const u = url || await UI.prompt('Rezept per Link übernehmen', '', { text: 'Link zu einem Rezept einfügen (Chefkoch, Lecker, Eat Smarter, Kochbar, Blogs …). Am Handy geht auch: im Browser auf „Teilen“ → midnox Rezepte.', ph: 'https://…', ok: 'Übernehmen', type: 'url' });
  if (!u || !u.trim()) return;
  APP.go('edit', { importUrl: u });
}
VIEWS.edit = async (m, arg) => {
  let r = typeof arg === 'string' ? JSON.parse(JSON.stringify(DB.byId[arg] || null)) : null;
  const isNew = !r;
  if (!r) r = { title: '', portions: DB.S.persons || 2, time: '', tags: [], ingr: [], steps: [], notes: '', store: DB.S.defStore === 'cloud' && DB.cloud.on ? 'cloud' : 'local' };
  if (arg && arg.importUrl) {
    m.innerHTML = '<div class="spin"></div><p class="mut" style="text-align:center">Rezept wird geladen …</p>';
    try { const j = await API.post('import', { url: arg.importUrl }); r = Object.assign(r, j.recipe); if (j.recipe.partial) toast('Auf der Seite stehen keine Rezeptdaten – Titel und Bild übernommen, Rest bitte ergänzen', 5000); }
    catch (e) { toast(e.message, 5000); }
  }
  let newImg = null, imgUrlIn = r.imgUrl || '', drop = false;
  m.innerHTML = '<div class="head"><div><h1>' + (isNew ? 'Neues Rezept' : 'Bearbeiten') + '</h1>' + (r.src ? '<div class="sub">aus ' + esc(r.src.replace(/^https?:\/\/(www\.)?/, '').split('/')[0]) + '</div>' : '') + '</div><div class="btns"><button class="btn f" data-k="e-x">Abbrechen</button><button class="btn pri f" data-k="e-save">' + ico('check') + 'Speichern</button></div></div>' +
    '<div class="cols" style="margin-top:0"><div>' +
    '<div class="pic" style="aspect-ratio:4/3;border-radius:1.8rem" id="eimg"></div><div class="btns" style="margin-top:1rem">' + (TV ? '' : '<label class="btn sm f" data-k="e-file">' + ico('up') + 'Foto wählen<input type="file" accept="image/*" hidden></label>') + '<button class="btn sm f" data-k="e-iurl">' + ico('link') + 'Bild-Link</button><button class="btn sm f ic" data-k="e-idel">' + ico('trash') + '</button></div>' +
    '<label class="lb">Gespeichert</label><div class="seg"><button class="chip f" data-s="local" data-k="e-sl">' + ico('phone') + ' Nur auf diesem Gerät</button><button class="chip f" data-s="cloud" data-k="e-sc">☁ midnox.de</button></div><div class="hint" id="shint"></div>' +
    '<label class="lb">Portionen · Zeit (Minuten)</label><div style="display:flex;gap:1rem"><input class="in f" data-k="e-por" type="number" min="1" max="99"><input class="in f" data-k="e-time" type="number" min="0" placeholder="z. B. 45"></div>' +
    '<label class="lb">Stichworte</label><input class="in f" data-k="e-tags" placeholder="Pasta, Schnell, Vegetarisch"><div class="hint">mit Komma trennen</div>' +
    '</div><div>' +
    '<label class="lb" style="margin-top:0">Titel</label><input class="in f" data-k="e-title" placeholder="z. B. Omas Gulasch">' +
    '<label class="lb">Zutaten</label><textarea class="in f" data-k="e-ingr" rows="9" placeholder="500 g Rinderschulter&#10;2 Zwiebeln&#10;1 EL Paprikapulver&#10;## Für die Soße&#10;200 ml Sahne"></textarea><div class="hint">Eine Zutat pro Zeile, Menge zuerst. „## Überschrift“ für Gruppen. Mengen werden beim Umrechnen und für die Einkaufsliste erkannt.</div>' +
    '<label class="lb">Zubereitung</label><textarea class="in f" data-k="e-steps" rows="10" placeholder="Ein Schritt pro Zeile …"></textarea><div class="hint">Ein Schritt pro Zeile. Zeitangaben wie „20 Minuten“ werden im Kochmodus zum Wecker.</div>' +
    '<label class="lb">Notizen</label><textarea class="in f" data-k="e-notes" rows="3"></textarea>' +
    '<label class="lb">Quelle (Link)</label><input class="in f" data-k="e-src" placeholder="https://…">' +
    '</div></div>';
  const F = (k) => $('[data-k=' + k + ']', m);
  F('e-title').value = r.title || ''; F('e-por').value = r.portions || ''; F('e-time').value = r.time || ''; F('e-tags').value = (r.tags || []).join(', ');
  F('e-ingr').value = (r.ingr || []).join('\n'); F('e-steps').value = (r.steps || []).join('\n'); F('e-notes').value = r.notes || ''; F('e-src').value = r.src || '';
  let store = r.store || 'local';
  const drawStore = () => {
    $$('.seg .chip', m).forEach(c => c.classList.toggle('on', c.dataset.s === store));
    $('#shint', m).textContent = store === 'cloud' ? (DB.cloud.on ? 'Liegt auf midnox.de – auf allen deinen Geräten verfügbar.' : 'midnox.de ist noch nicht eingerichtet (Mehr → midnox.de).') : 'Bleibt nur auf diesem Gerät – im Heimnetz trotzdem über die Netzwerk-Adresse erreichbar.';
  };
  $$('.seg .chip', m).forEach(c => c.onclick = () => { store = c.dataset.s; drawStore(); VIEW.dirty = true; }); drawStore();
  const drawImg = () => {
    const src = newImg ? URL.createObjectURL(newImg) : imgUrlIn ? imgUrlIn : (!drop && r.img ? imgUrl(r) : '');
    $('#eimg', m).innerHTML = (src ? '<img src="' + esc(src) + '" referrerpolicy="no-referrer" onerror="this.remove()">' : '') + '<div class="ph">' + (src ? '' : '📷') + '</div>';
  };
  drawImg();
  const fi = $('input[type=file]', m);
  if (fi) fi.onchange = async () => { if (!fi.files[0]) return; newImg = await shrink(fi.files[0]); imgUrlIn = ''; drop = false; drawImg(); VIEW.dirty = true; };
  F('e-iurl').onclick = async () => { const u = await UI.prompt('Bild-Link', imgUrlIn, { ph: 'https://…/bild.jpg', type: 'url' }); if (u != null) { imgUrlIn = u.trim(); newImg = null; drop = !imgUrlIn && !r.img; drawImg(); VIEW.dirty = true; } };
  F('e-idel').onclick = () => { newImg = null; imgUrlIn = ''; drop = true; drawImg(); VIEW.dirty = true; };
  $$('.in', m).forEach(e => e.addEventListener('input', () => { VIEW.dirty = true; }));
  VIEW.dirty = !!(arg && arg.importUrl); VIEW.editing = true;
  VIEW.leave = async () => !VIEW.dirty || await UI.confirm('Änderungen verwerfen?', 'Das Rezept ist noch nicht gespeichert.', 'Verwerfen', true);
  F('e-x').onclick = () => APP.back();
  F('e-save').onclick = async () => {
    const title = F('e-title').value.trim(); if (!title) { toast('Bitte einen Titel eingeben'); NAV.set(F('e-title')); return; }
    if (store === 'cloud' && !DB.cloud.on) { toast('midnox.de ist noch nicht eingerichtet – speichere lokal oder richte es unter „Mehr“ ein', 5000); return; }
    const lines = (s) => s.split('\n').map(x => x.trim()).filter(Boolean);
    const o = Object.assign({}, r, { title, portions: +F('e-por').value || 2, time: +F('e-time').value || 0, tags: F('e-tags').value.split(',').map(x => x.trim()).filter(Boolean), ingr: lines(F('e-ingr').value), steps: lines(F('e-steps').value), notes: F('e-notes').value.trim(), src: F('e-src').value.trim(), store });
    delete o.partial; delete o.imgUrl;
    if (imgUrlIn && !newImg) o.imgUrl = imgUrlIn; if (drop && !newImg && !imgUrlIn) o.imgDel = true;
    F('e-save').classList.add('off');
    try {
      let saved = await saveRecipe(o, true);
      if (newImg) { const res = await API.post('img?id=' + encodeURIComponent(saved.id), newImg, true); saved = res.recipe; await DB.load(); }
      toast('Gespeichert'); VIEW.dirty = false; VIEW.leave = null;
      if (isNew) { APP.stack.pop(); await APP.show('recipe', saved.id); APP.stack.push({ name: 'list', arg: null }); } else APP.back();
    } catch (e) { F('e-save').classList.remove('off'); }
  };
  if (!TV) setTimeout(() => { if (!r.title) F('e-title').focus(); }, 50);
  NAV.set(r.title ? F('e-save') : F('e-title'), true);
};
/** Foto verkleinern (max. 1600 px, JPEG) bevor es hochgeladen wird */
function shrink(file) {
  return new Promise((res) => {
    const img = new Image(); img.onload = () => {
      const s = Math.min(1, 1600 / Math.max(img.width, img.height)), cv = document.createElement('canvas'); cv.width = Math.round(img.width * s); cv.height = Math.round(img.height * s);
      cv.getContext('2d').drawImage(img, 0, 0, cv.width, cv.height); cv.toBlob(b => res(b || file), 'image/jpeg', .85);
    }; img.onerror = () => res(file); img.src = URL.createObjectURL(file);
  });
}

// ------------------------------------------------------------------ Kochplaner
const PL = { week: null };
VIEWS.plan = async (m) => {
  const mon = PL.week || monday(today()), slots = DB.slots(), t0 = ymd(today());
  const days = [...Array(7)].map((_, i) => addD(mon, i)), kw = isoWeek(mon);
  m.innerHTML = '<div class="head"><div><h1>Kochplaner</h1><div class="sub">KW ' + kw + ' · ' + days[0].getDate() + '. ' + MON[days[0].getMonth()] + ' – ' + days[6].getDate() + '. ' + MON[days[6].getMonth()] + '</div></div>' +
    '<div class="btns"><button class="btn f ic" data-k="w-prev">' + ico('left') + '</button><button class="btn f" data-k="w-now">Diese Woche</button><button class="btn f ic" data-k="w-next">' + ico('right') + '</button><button class="btn f" data-k="w-dice">' + ico('dice') + 'Auffüllen</button><button class="btn pri f" data-k="w-shop">' + ico('cart') + 'Einkaufsliste</button><button class="btn f ic" data-k="w-more">⋯</button></div></div>';
  const wk = h('div', 'week');
  wk.style.gridTemplateRows = 'auto repeat(' + slots.length + ', minmax(15rem, auto))';
  wk.appendChild(h('div', ''));
  for (const d of days) wk.appendChild(h('div', 'dh' + (ymd(d) === t0 ? ' now' : ''), '<b>' + WD[d.getDay()] + '</b><small>' + d.getDate() + '.' + (d.getMonth() + 1) + '.</small>'));
  const dl = h('div', 'daylist');
  const dayBoxes = {};
  for (const d of days) { const ds = ymd(d), b = h('div', 'day' + (ds === t0 ? ' now' : ds < t0 ? ' past' : ''), '<b>' + dayLabel(d) + '<small>' + WD[d.getDay()] + ', ' + d.getDate() + '. ' + MON[d.getMonth()] + '</small></b>'); dayBoxes[ds] = b; dl.appendChild(b); }
  for (const s of slots) {
    wk.appendChild(h('div', 'sh', esc(s)));
    for (const d of days) {
      const ds = ymd(d);
      for (const [box, kp] of [[wk, 'g'], [dayBoxes[ds], 'd']]) {
        const e = PLAN.get(ds, s), r = e && e.rid && DB.byId[e.rid];
        const cls = 'slot f' + (ds < t0 ? ' past' : '') + (ds === t0 ? ' today' : '') + (!e ? ' free' : r ? '' : ' txt');
        const c = h('div', cls, !e ? (kp === 'd' ? '+ ' + esc(s) : '+') : (r ? pic(r) : '') + '<div class="bd">' + (kp === 'd' ? '<small>' + esc(s) + '</small>' : '') + esc(PLAN.label(e)) + (e.p && r ? '<small>' + e.p + ' Portionen</small>' : '') + '</div>');
        c.dataset.k = kp + '-' + ds + '-' + s; c.onclick = () => slotMenu(ds, s);
        box.appendChild(c);
      }
    }
  }
  m.appendChild(wk); m.appendChild(dl);
  const F = (k) => $('[data-k=' + k + ']', m);
  F('w-prev').onclick = () => { PL.week = addD(mon, -7); APP.refresh(); };
  F('w-next').onclick = () => { PL.week = addD(mon, 7); APP.refresh(); };
  F('w-now').onclick = () => { PL.week = null; APP.refresh(); };
  F('w-dice').onclick = () => fillWeek(days);
  F('w-shop').onclick = () => shopFromPlan(days);
  F('w-more').onclick = async () => {
    const v = await UI.choose('Woche KW ' + kw, [{ t: 'Plan teilen', i: 'share', v: 'share' }, { t: 'Woche von letzter Woche übernehmen', i: 'copy', v: 'copy' }, { t: 'Woche leeren', i: 'trash', v: 'clear', danger: true }]);
    if (v === 'share') shareText('Kochplan KW ' + kw, days.map(d => { const l = slots.map(s => { const e = PLAN.get(ymd(d), s); return e ? '  ' + s + ': ' + PLAN.label(e) : ''; }).filter(Boolean); return l.length ? WDL[d.getDay()] + ' ' + d.getDate() + '.' + (d.getMonth() + 1) + '.\n' + l.join('\n') : ''; }).filter(Boolean).join('\n\n') || 'Noch nichts geplant');
    if (v === 'copy') { for (const d of days) for (const s of slots) { const e = PLAN.get(ymd(addD(d, -7)), s); if (e && !PLAN.get(ymd(d), s)) PLAN.set(ymd(d), s, { rid: e.rid, t: e.t, p: e.p }); } await DB.savePlan(); APP.refresh(); }
    if (v === 'clear' && await UI.confirm('Woche leeren?', 'Alle Einträge der KW ' + kw + ' werden entfernt.', 'Leeren', true)) { for (const d of days) delete DB.plan.days[ymd(d)]; await DB.savePlan(); APP.refresh(); }
  };
  const tk = (document.body.classList.contains('narrow') ? 'd-' : 'g-') + (ymd(mon) <= t0 && t0 <= ymd(days[6]) ? t0 : ymd(days[0])) + '-' + slots[slots.length - 1];
  VIEW.firstFocus = $('[data-k="' + CSS.escape(tk) + '"]', m);
};
function isoWeek(d) { const x = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())); const n = x.getUTCDay() || 7; x.setUTCDate(x.getUTCDate() + 4 - n); const y = new Date(Date.UTC(x.getUTCFullYear(), 0, 1)); return Math.ceil(((x - y) / 864e5 + 1) / 7); }
async function slotMenu(ds, s) {
  const e = PLAN.get(ds, s), r = e && e.rid && DB.byId[e.rid], d = new Date(ds + 'T12:00');
  const title = dayLabel(d) + ', ' + d.getDate() + '. ' + MON[d.getMonth()] + ' · ' + s;
  const items = [];
  if (r) items.push({ t: 'Rezept ansehen', sub: r.title, i: 'eye', v: 'open' }, { t: 'Jetzt kochen', i: 'chef', v: 'cook' }, { t: 'Portionen: ' + (e.p || r.portions || 2), i: 'users', v: 'por' });
  items.push({ t: e ? 'Anderes Rezept wählen' : 'Rezept wählen', i: 'book', v: 'pick' }, { t: 'Zufälliges Rezept', i: 'dice', v: 'rand' }, { t: e && !r ? 'Text ändern' : 'Notiz statt Rezept', sub: 'z. B. „Reste“, „Essen gehen“, „Pizza bestellen“', i: 'edit', v: 'text' });
  if (e) items.push({ t: 'Verschieben auf …', i: 'move', v: 'move' }, { t: 'Entfernen', i: 'trash', v: 'del', danger: true });
  const v = await UI.choose(title, items);
  if (!v) return;
  if (v === 'open') return APP.go('recipe', r.id + (e.p ? '|' + e.p : ''));
  if (v === 'cook') return APP.go('cook', r.id + '|' + (e.p || r.portions || 2));
  if (v === 'pick') { const id = await pickRecipe(title); if (!id) return; PLAN.set(ds, s, { rid: id, p: defP(DB.byId[id]) }); }
  if (v === 'rand') { const id = randomRecipe(ds); if (!id) { toast('Erst ein paar Rezepte anlegen'); return; } PLAN.set(ds, s, { rid: id, p: defP(DB.byId[id]) }); toast('🎲 ' + DB.byId[id].title); }
  if (v === 'text') { const t = await UI.prompt('Notiz für ' + s, e && !r ? e.t : '', { ph: 'z. B. Reste von gestern' }); if (t == null) return; if (t.trim()) PLAN.set(ds, s, { t: t.trim() }); else PLAN.set(ds, s, null); }
  if (v === 'por') { const n = await UI.choose('Portionen', [1, 2, 3, 4, 5, 6, 8, 10, 12].map(n => ({ t: n + (n === 1 ? ' Portion' : ' Portionen'), v: n }))); if (!n) return; e.p = n; }
  if (v === 'move') {
    const opts = []; const base = new Date(ds + 'T12:00');
    for (let i = -3; i <= 7; i++) { if (!i) continue; const d2 = addD(base, i); for (const s2 of DB.slots()) opts.push({ t: dayLabel(d2) + ' ' + d2.getDate() + '.' + (d2.getMonth() + 1) + '. · ' + s2 + (PLAN.get(ymd(d2), s2) ? ' (tauschen)' : ''), v: ymd(d2) + '|' + s2 }); }
    const to = await UI.choose('Verschieben auf …', opts); if (!to) return;
    const [d2, s2] = to.split('|'), other = PLAN.get(d2, s2), mine = { rid: e.rid, t: e.t, p: e.p };
    PLAN.set(d2, s2, mine); PLAN.set(ds, s, other ? { rid: other.rid, t: other.t, p: other.p } : null);
  }
  if (v === 'del') PLAN.set(ds, s, null);
  await DB.savePlan(); APP.refresh();
}
function randomRecipe(ds, avoid = new Set()) {
  if (!DB.list.length) return null;
  const used = new Set(); const t = new Date(ds + 'T12:00');
  for (let i = -10; i <= 10; i++) for (const e of DB.plan.days[ymd(addD(t, i))] || []) if (e.rid) used.add(e.rid);
  let pool = DB.list.filter(r => !used.has(r.id) && !avoid.has(r.id) && !(r.tags || []).some(x => /^(dessert|nachtisch|kuchen|getränk|backen|frühstück)$/i.test(x)));
  if (!pool.length) pool = DB.list.filter(r => !avoid.has(r.id)); if (!pool.length) pool = DB.list;
  const w = pool.map(r => r.fav ? 3 : 1), sum = w.reduce((a, b) => a + b, 0); let x = Math.random() * sum;
  for (let i = 0; i < pool.length; i++) { x -= w[i]; if (x <= 0) return pool[i].id; }
  return pool[0].id;
}
async function fillWeek(days) {
  const t0 = ymd(today()), slots = DB.slots();
  const which = slots.length > 1 ? await UI.choose('Leere Plätze zufällig füllen', [...slots.map(s => ({ t: 'Nur ' + s, v: s })), { t: 'Alle Mahlzeiten', v: '*' }], 'Favoriten kommen öfter dran, Rezepte der letzten Tage werden ausgelassen.') : slots[0];
  if (!which) return;
  let n = 0; const avoid = new Set();
  for (const d of days) { const ds = ymd(d); if (ds < t0) continue; for (const s of slots) { if ((which !== '*' && s !== which) || PLAN.get(ds, s)) continue; const id = randomRecipe(ds, avoid); if (!id) break; avoid.add(id); PLAN.set(ds, s, { rid: id, p: defP(DB.byId[id]) }); n++; } }
  if (!n) { toast(DB.list.length ? 'Keine freien Plätze mehr' : 'Erst ein paar Rezepte anlegen'); return; }
  await DB.savePlan(); toast('🎲 ' + n + ' Plätze gefüllt'); APP.refresh();
}
function pickRecipe(title) {
  return new Promise((res) => {
    let done = false;
    const d = UI.open('<h2>Rezept wählen</h2><p>' + esc(title) + '</p><input class="in f" placeholder="Suchen …"><div class="chips" style="margin-top:1.2rem"></div><div class="pick"></div>', 'wide', () => { if (!done) res(null); });
    const inp = $('input', d), box = $('.pick', d), ch = $('.chips', d); let fav = false, tag = '';
    const tags = DB.tags().slice(0, 8);
    ch.innerHTML = '<button class="chip f" data-f="1">❤ Favoriten</button>' + tags.map(t => '<button class="chip f" data-t="' + esc(t) + '">' + esc(t) + '</button>').join('');
    $$('.chip', ch).forEach(c => c.onclick = () => { if (c.dataset.f) fav = !fav; else tag = tag === c.dataset.t ? '' : c.dataset.t; draw(); });
    const draw = () => {
      $$('.chip', ch).forEach(c => c.classList.toggle('on', c.dataset.f ? fav : tag === c.dataset.t));
      const q = inp.value.trim().toLowerCase();
      const l = DB.list.filter(r => (!fav || r.fav) && (!tag || (r.tags || []).includes(tag)) && (!q || (r.title + ' ' + (r.tags || []).join(' ') + ' ' + (r.ingr || []).join(' ')).toLowerCase().includes(q)));
      box.innerHTML = l.length ? l.slice(0, 120).map(r => cardHtml(r)).join('') : '<div class="empty" style="grid-column:1/-1">' + (DB.list.length ? 'Nichts gefunden' : 'Noch keine Rezepte') + '</div>';
      bindCards(box, (id) => { done = true; UI.close(); res(id); });
    };
    inp.oninput = draw; draw();
    setTimeout(() => { const c = $('.card', box); NAV.set(c || inp, true); }, 30);
  });
}
async function planRecipe(r, por) {
  const t = today(), opts = [];
  for (let i = 0; i < 10; i++) { const d = addD(t, i); for (const s of DB.slots()) { const e = PLAN.get(ymd(d), s); opts.push({ t: dayLabel(d) + ' ' + d.getDate() + '.' + (d.getMonth() + 1) + '. · ' + s, sub: e ? 'ersetzt: ' + PLAN.label(e) : '', v: ymd(d) + '|' + s }); } }
  const v = await UI.choose('„' + r.title + '“ einplanen', opts); if (!v) return;
  const [ds, s] = v.split('|'); PLAN.set(ds, s, { rid: r.id, p: por }); await DB.savePlan(); toast('Eingeplant: ' + dayLabel(new Date(ds + 'T12:00')) + ', ' + s);
}

// ------------------------------------------------------------------ Einkaufsliste
/** sources = [{r, p}] → Zutaten zusammenfassen und auf die Liste setzen */
function addToShop(sources, replaceGen) {
  const acc = {};
  for (const { r, p } of sources) {
    const k = (p || r.portions || 2) / (r.portions || 2);
    for (const line of r.ingr || []) {
      const x = ING.parse(line); if (x.grp != null || !x.n) continue;
      const key = ING.key(x.n); if (!key || ING.SKIP.test(key)) continue;
      let u = ING.NORM[x.u] != null ? ING.NORM[x.u] : x.u, a = x.a == null ? null : x.a * k;
      if (u === 'kg' && a != null) { a *= 1000; u = 'g'; } if (u === 'l' && a != null) { a *= 1000; u = 'ml'; }
      const id = key + '|' + u;
      const it = acc[id] || (acc[id] = { key: id, n: x.n.replace(/,.*$/, '').trim(), u, a: null, na: false, src: [] });
      if (a == null) it.na = true; else it.a = (it.a || 0) + a;
      if (!it.src.includes(r.title)) it.src.push(r.title);
    }
  }
  const old = {}; for (const it of DB.shop.items) old[it.key] = it;
  if (replaceGen) DB.shop.items = DB.shop.items.filter(i => i.man);
  for (const it of Object.values(acc)) {
    let a = it.a, u = it.u;
    if (u === 'g' && a >= 1000) { a /= 1000; u = 'kg'; } if (u === 'ml' && a >= 1000) { a /= 1000; u = 'l'; }
    const q = (a != null ? ING.fmt(a, u) + (u ? ' ' + u : '') : (u || '')) + (it.na && a != null ? ' +' : '');
    const ex = DB.shop.items.find(i => i.key === it.key && !i.man);
    if (ex && !replaceGen) { ex.q = ex.q ? ex.q + ' + ' + q : q; ex.src = [...new Set([...(ex.src || []), ...it.src])]; ex.done = false; continue; }
    DB.shop.items.push({ id: Math.random().toString(36).slice(2, 9), key: it.key, n: it.n, q: q.trim(), cat: ING.cat(it.n), src: it.src, done: replaceGen && old[it.key] ? old[it.key].done : false });
  }
}
async function shopFromPlan(days) {
  const t0 = ymd(today());
  const ranges = [{ t: 'Rest dieser Plan-Woche', sub: 'ab heute bis Sonntag', v: 'w' }, { t: 'Nächste 7 Tage', v: '7' }, { t: 'Nächste 3 Tage', v: '3' }];
  const v = await UI.choose('Einkaufsliste aus dem Plan', ranges, 'Die Zutaten aller geplanten Rezepte werden zusammengerechnet. Selbst eingetragene Dinge bleiben stehen.'); if (!v) return;
  const list = v === 'w' ? (days || [...Array(7)].map((_, i) => addD(monday(today()), i))).filter(d => ymd(d) >= t0) : [...Array(+v)].map((_, i) => addD(today(), i));
  const src = [];
  for (const d of list) for (const e of DB.plan.days[ymd(d)] || []) { const r = e.rid && DB.byId[e.rid]; if (r) src.push({ r, p: e.p || r.portions }); }
  if (!src.length) { toast('In diesem Zeitraum ist kein Rezept eingeplant'); return; }
  addToShop(src, true);
  DB.shop.at = Date.now(); DB.shop.range = ymd(list[0]) + '|' + ymd(list[list.length - 1]);
  await DB.saveShop(); toast(src.length + ' Rezepte → Einkaufsliste'); APP.go('shop', null, { root: true });
}
VIEWS.shop = async (m) => {
  const items = DB.shop.items;
  m.innerHTML = '<div class="head"><div><h1>Einkaufsliste</h1><div class="sub">' + (items.length ? items.filter(i => !i.done).length + ' offen · ' + items.filter(i => i.done).length + ' erledigt' : 'leer') + '</div></div><div class="btns"><button class="btn pri f" data-k="s-gen">' + ico('cal') + 'Aus dem Plan</button><button class="btn f ic" data-k="s-share">' + ico('share') + '</button><button class="btn f ic" data-k="s-more">⋯</button></div></div>' +
    '<div class="shop"><div class="addrow"><input class="in f" data-k="s-add" placeholder="Hinzufügen, z. B. 2 l Milch"><button class="btn f" data-k="s-addb">' + ico('plus') + '<span>Dazu</span></button></div><div class="cats"></div></div>';
  const F = (k) => $('[data-k=' + k + ']', m);
  const add = async () => { const v = F('s-add').value.trim(); if (!v) return; const p = ING.parse(v); DB.shop.items.push({ id: Math.random().toString(36).slice(2, 9), key: 'm-' + Date.now(), n: p.n || v, q: p.a != null ? ING.qty(p, 1) : '', cat: ING.cat(p.n || v), man: true, done: false }); F('s-add').value = ''; await DB.saveShop(); APP.refresh(); };
  F('s-add')._enter = add; F('s-add').addEventListener('keydown', e => { if (e.key === 'Enter' && !TV) add(); });
  F('s-addb').onclick = add;
  F('s-gen').onclick = () => shopFromPlan();
  F('s-share').onclick = () => shareText('Einkaufsliste', shopText());
  F('s-more').onclick = async () => {
    const v = await UI.choose('Einkaufsliste', [{ t: 'Erledigte entfernen', i: 'check', v: 'done' }, { t: 'Alles leeren', i: 'trash', v: 'all', danger: true }]);
    if (v === 'done') DB.shop.items = DB.shop.items.filter(i => !i.done);
    if (v === 'all' && await UI.confirm('Liste leeren?', 'Alle Einträge werden entfernt.', 'Leeren', true)) DB.shop.items = []; else if (v !== 'done') return;
    await DB.saveShop(); APP.refresh();
  };
  const box = $('.cats', m);
  if (!items.length) { box.innerHTML = '<div class="empty"><b>🛒</b>Die Liste ist leer. Plane ein paar Rezepte und tippe auf „Aus dem Plan“ – die Zutaten werden zusammengerechnet.</div>'; return; }
  const order = [...ING.CATS.map(c => c[0]), 'Sonstiges'].filter(c => c !== 'Gewürze & Basics').concat(['Gewürze & Basics']);
  const groups = {}; for (const it of items) (groups[it.cat || 'Sonstiges'] || (groups[it.cat || 'Sonstiges'] = [])).push(it);
  for (const c of order) {
    const l = groups[c]; if (!l) continue;
    l.sort((a, b) => (a.done - b.done) || a.n.localeCompare(b.n, 'de'));
    const sec = h('div', 'shopcat', '<h3>' + esc(c === 'Gewürze & Basics' ? 'Gewürze & Basics – vielleicht schon da' : c) + '</h3>');
    for (const it of l) {
      const e = h('div', 'si f' + (it.done ? ' done' : ''), '<div class="ck">' + (it.done ? '✓' : '') + '</div><div class="n">' + esc(it.n) + (it.src && it.src.length ? '<small>' + esc(it.src.join(', ')) + '</small>' : '') + '</div><div class="q">' + esc(it.q || '') + '</div>');
      e.dataset.k = 'si-' + it.id;
      e.onclick = async () => { it.done = !it.done; e.classList.toggle('done', it.done); $('.ck', e).textContent = it.done ? '✓' : ''; APP.rail(); clearTimeout(VIEWS.shop.t); VIEWS.shop.t = setTimeout(() => DB.saveShop(), 400); };
      sec.appendChild(e);
    }
    box.appendChild(sec);
  }
};
function shopText() {
  const g = {}; for (const it of DB.shop.items.filter(i => !i.done)) (g[it.cat] || (g[it.cat] = [])).push(it);
  return Object.keys(g).map(c => c + ':\n' + g[c].map(i => '☐ ' + (i.q ? i.q + ' ' : '') + i.n).join('\n')).join('\n\n') || 'Alles erledigt 🎉';
}

// ------------------------------------------------------------------ Einstellungen
VIEWS.settings = async (m) => {
  const S = DB.S;
  m.innerHTML = '<div class="head"><div><h1>Mehr</h1><div class="sub">Einstellungen · Netzwerk · midnox.de</div></div></div><div class="set">' +
    '<div class="box"><h3>☁ midnox.de</h3><p class="mut">Rezepte, die du auf „midnox.de“ speicherst, liegen auf deinem Webserver und sind auf allen Geräten mit der App da. Planer und Einkaufsliste bleiben immer lokal.</p>' +
    '<div class="status ' + (S.hasKey ? (DB.cloud.err ? 'err' : 'ok') : '') + '"><i></i>' + (!S.hasKey ? 'nicht eingerichtet' : DB.cloud.err ? esc(DB.cloud.err) : 'verbunden · ' + (DB.cloud.n || 0) + ' Rezepte') + '</div>' +
    '<label class="lb">Adresse</label><input class="in f" data-k="c-url"><label class="lb">Schlüssel</label><input class="in f" data-k="c-key" placeholder="steht oben in der index.php"><div class="btns" style="margin-top:1.4rem"><button class="btn pri f" data-k="c-save">Speichern & testen</button></div></div>' +
    '<div class="box"><h3>Kochplaner</h3><label class="lb">Mahlzeiten pro Tag</label><div class="seg" data-k="slots"></div>' +
    '<label class="lb">Portionen (Haushalt)</label><div class="seg" data-k="pers"></div>' +
    '<label class="lb">Neue Rezepte speichern</label><div class="seg" data-k="defs"></div></div>' +
    '<div class="box"><h3>Im Heimnetz</h3><div class="lanw"></div>' + (INAPP ? '<div class="tog f' + (S.bg ? ' on' : '') + '" data-k="bg"><div><b>Immer im Netzwerk erreichbar</b><small>auch wenn die App geschlossen ist (zeigt eine dauerhafte Benachrichtigung) und nach dem Neustart des Geräts</small></div><div class="sw"></div></div>' : '') + '</div>' +
    '<div class="box"><h3>Datensicherung</h3><p class="mut">Lokale Rezepte samt Bildern, Planer und Einkaufsliste als Datei sichern – am besten am PC im Browser.</p><div class="btns">' + (INAPP ? '<span class="hint">Öffne dazu die Netzwerk-Adresse am PC.</span>' : '<a class="btn f" data-k="b-exp" href="/api/export" download>' + ico('down') + 'Sicherung herunterladen</a><label class="btn f" data-k="b-imp">' + ico('up') + 'Sicherung einspielen<input type="file" accept=".json,application/json" hidden></label>') + '</div></div>' +
    '<div class="hint" style="text-align:center;margin-top:2rem">midnox Rezepte</div></div>';
  const F = (k) => $('[data-k=' + k + ']', m);
  F('c-url').value = S.cloudUrl || ''; F('c-key').value = S.cloudKey || '';
  F('c-save').onclick = async () => {
    F('c-save').classList.add('off');
    try { await API.post('settings', { cloudUrl: F('c-url').value.trim(), cloudKey: F('c-key').value.trim() }); const r = await API.get('cloudtest'); await DB.load(true); toast(r.cloud.err ? '✗ ' + r.cloud.err : '✓ Verbunden mit midnox.de', 4000); } catch (e) { toast(e.message); }
    APP.refresh();
  };
  const seg = (k, opts, cur, fn) => { const b = F(k); b.innerHTML = opts.map(([v, t]) => '<button class="chip f' + (String(v) === String(cur) ? ' on' : '') + '" data-v="' + esc(v) + '" data-k="' + k + '-' + esc(v) + '">' + esc(t) + '</button>').join(''); $$('.chip', b).forEach(c => c.onclick = async () => { await fn(c.dataset.v); APP.refresh(); }); };
  const put = async (o) => { await API.post('settings', o); await DB.load(); };
  const SL = { 'Abend': ['Abend'], 'Mittag,Abend': ['Mittag', 'Abend'], 'Frühstück,Mittag,Abend': ['Frühstück', 'Mittag', 'Abend'] };
  seg('slots', [['Abend', 'Nur Abend'], ['Mittag,Abend', 'Mittag + Abend'], ['Frühstück,Mittag,Abend', 'Frühstück, Mittag, Abend']], DB.slots().join(','), (v) => put({ slots: SL[v] }));
  seg('pers', [1, 2, 3, 4, 5, 6].map(n => [n, String(n)]), S.persons || 2, (v) => put({ persons: +v }));
  seg('defs', [['local', 'Nur auf dem Gerät'], ['cloud', 'Auf midnox.de']], S.defStore || 'local', (v) => put({ defStore: v }));
  $('.lanw', m).appendChild(lanBox()); $('.lanw .lan', m).style.marginTop = '0';
  if (F('bg')) F('bg').onclick = async () => { await put({ bg: !S.bg }); if (AND && AND.bg) AND.bg(); toast(!S.bg ? 'Bleibt im Netzwerk erreichbar' : 'Nur erreichbar, solange die App offen ist'); APP.refresh(); };
  const fi = $('[data-k=b-imp] input', m);
  if (fi) fi.onchange = async () => {
    const f = fi.files[0]; if (!f) return;
    if (!await UI.confirm('Sicherung einspielen?', 'Rezepte aus der Datei werden hinzugefügt (gleiche Rezepte überschrieben). Planer und Einkaufsliste werden durch die Sicherung ersetzt.', 'Einspielen')) return;
    try { const r = await API.post('restore', await f.arrayBuffer(), true); await DB.load(); toast(r.n + ' Rezepte eingespielt'); APP.refresh(); } catch (e) { toast(e.message, 5000); }
  };
};

// ------------------------------------------------------------------ Start & Abgleich im Netzwerk
async function boot() {
  document.body.classList.toggle('tv', TV);
  if (TV) { document.body.classList.add('kb'); const fit = () => { document.documentElement.style.fontSize = (innerWidth / 1920 * 10) + 'px'; }; fit(); addEventListener('resize', fit); }
  const narrow = () => document.body.classList.toggle('narrow', !TV && innerWidth < 760); narrow(); addEventListener('resize', narrow);
  $('#main').innerHTML = '<div class="spin"></div>';
  for (let i = 0; ; i++) { try { await DB.load(); break; } catch (e) { if (i > 20) { $('#main').innerHTML = '<div class="empty"><b>📡</b>Keine Verbindung zur App. Läuft midnox Rezepte noch?</div>'; return; } await sleep(700); } }
  const hs = location.hash.replace(/^#\/?/, '').split('/');
  if (!INAPP && hs[0] && VIEWS[hs[0]] && hs[0] !== 'edit' && hs[0] !== 'cook') { await APP.go(hs[0] === 'recipe' ? 'list' : hs[0], null, { root: true }); if (hs[0] === 'recipe' && hs[1]) await APP.go('recipe', decodeURIComponent(hs[1])); }
  else await APP.go('home', null, { root: true });
  // Änderungen von anderen Geräten im Heimnetz übernehmen
  setInterval(async () => {
    if (document.hidden) return;
    try {
      const v = (await API.get('ver')).ver; if (v === DB.ver) return;
      const busy = VIEW.editing || UI.top() || VIEW.name === 'cook' || (document.activeElement && /^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName));
      await DB.load(); if (!busy) APP.refresh(); else APP.rail();
    } catch (e) {}
  }, 4000);
}
boot();
