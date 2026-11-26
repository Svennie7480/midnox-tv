'use strict';
/* midnox Führerschein – Lernen, Prüfung, Statistik. Fragen: data/*.js (handgeschrieben) + gen.js (erzeugt). */

const TOPICS = {
  gefahr: ['Gefahrenlehre', '⚠️', 'G'], verhalten: ['Verhalten im Straßenverkehr', '🚗', 'G'], vorfahrt: ['Vorfahrt & Vorrang', '🔀', 'G'],
  zeichen: ['Verkehrszeichen', '🛑', 'G'], umwelt: ['Umweltschutz', '🌱', 'G'], betrieb: ['Betrieb der Fahrzeuge', '📋', 'G'],
  technik: ['Technik', '🔧', 'G'], eignung: ['Eignung & Befähigung', '🧠', 'G'],
  bgefahr: ['Gefahrenlehre (Klasse B)', '🚙', 'B'], bverhalten: ['Verhalten (Klasse B)', '🛣️', 'B'], btechnik: ['Technik & Ladung (Klasse B)', '🧰', 'B'], bumwelt: ['Umwelt & Energie (Klasse B)', '🔋', 'B'],
};
window.QDATA = window.QDATA || [];

// ------------------------------------------------------------------ Fragen & Lernstand
const Q = {
  all: [], byId: {},
  init() {
    Q.all = [...QDATA, ...GEN.signs(), ...GEN.calc(), ...GEN.vorfahrt(150)].filter(q => TOPICS[q.topic]);
    for (const q of Q.all) { q.cat = TOPICS[q.topic][2]; Q.byId[q.id] = q; if (!q.type) q.type = 'mc'; }
  },
  of(topic) { return Q.all.filter(q => q.topic === topic); },
};
const ST = {
  d: null,
  load() { try { ST.d = JSON.parse(localStorage.getItem('fs_state') || 'null'); } catch (e) {} if (!ST.d) ST.d = { q: {}, exams: [], days: {}, goal: 30 }; if (!ST.d.days) ST.d.days = {}; },
  save() { try { localStorage.setItem('fs_state', JSON.stringify(ST.d)); } catch (e) {} },
  s(id) { return ST.d.q[id] || { b: 0, n: 0, ok: 0, l: 0, w: false }; },
  /** Antwort verbuchen: Box steigt bei richtig, fällt bei falsch (Karteikasten) */
  answer(id, right) {
    const s = ST.s(id); s.n++; s.l = Date.now();
    if (right) { s.ok++; s.b = Math.min(5, s.b + 1); s.w = false; } else { s.b = Math.max(0, s.b - 2); s.w = true; }
    ST.d.q[id] = s;
    const d = today(); ST.d.days[d] = (ST.d.days[d] || 0) + 1;
    ST.save();
  },
  learned(id) { return ST.s(id).b >= 2; },
  mistakes() { return Q.all.filter(q => ST.s(q.id).w); },
  /** Prüfungsreife 0–100: gelernte Fragen (gewichtet nach Punkten) + Ergebnisse der letzten Prüfungen */
  ready() {
    let tot = 0, got = 0; for (const q of Q.all) { tot += q.pts; const b = ST.s(q.id).b; got += q.pts * Math.min(1, b / 3); }
    const learn = tot ? got / tot : 0, ex = ST.d.exams.slice(-5);
    const exr = ex.length ? ex.filter(e => e.ok).length / ex.length : 0;
    return Math.round(100 * (ex.length ? learn * 0.6 + exr * 0.4 : learn * 0.8));
  },
  streak() { let n = 0; const d = new Date(); for (;;) { const k = ymd(d); if (!ST.d.days[k]) { if (n === 0 && k === today()) { d.setDate(d.getDate() - 1); continue; } break; } n++; d.setDate(d.getDate() - 1); } return n; },
};
const ymd = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
const today = () => ymd(new Date());

/** Lernauswahl: erst Fehler, dann Neues, dann Schwaches und lange nicht Gesehenes */
function pickLearn(pool, n) {
  const now = Date.now();
  const score = (q) => { const s = ST.s(q.id); if (s.w) return 0 + Math.random() * .5; if (!s.n) return 1 + Math.random(); const age = (now - s.l) / 864e5; return 2 + s.b * 1.5 - Math.min(age, 10) * 0.3 + Math.random() * .8; };
  return pool.map(q => [score(q), q]).sort((a, b) => a[0] - b[0]).slice(0, n).map(x => x[1]);
}
/** Prüfungsbogen: 20 Fragen Grundstoff + 10 Fragen Klasse B, Punkte gemischt */
function pickExam() {
  const sh = (a) => a.map(q => [Math.random(), q]).sort((x, y) => x[0] - y[0]).map(x => x[1]);
  const G = sh(Q.all.filter(q => q.cat === 'G')), B = sh(Q.all.filter(q => q.cat === 'B'));
  const take = (pool, n) => { const out = [], perTopic = {}; for (const q of pool) { if (out.length >= n) break; const k = q.topic; if ((perTopic[k] || 0) >= Math.ceil(n / 3)) continue; perTopic[k] = (perTopic[k] || 0) + 1; out.push(q); } return out; };
  const g = take(G, 20), b = take(B, 10);
  if (b.length < 10) g.push(...take(G.filter(q => !g.includes(q)), 10 - b.length));
  return g.concat(b);
}

// ------------------------------------------------------------------ Bewertung einer Antwort
function isRight(q, ans) {
  if (q.type === 'num') { const v = parseFloat(String(ans || '').replace(',', '.')); return !isNaN(v) && Math.abs(v - q.ans) < 0.051; }
  const sel = ans || []; return q.a.every((a, i) => !!a.ok === sel.includes(i));
}

// ------------------------------------------------------------------ Ansichten & Navigation
const VIEW = { name: '', arg: null, key: null, leave: null };
const VIEWS = {};
const RAIL = [['home', 'Start', 'home'], ['learn', 'Lernen', 'book'], ['exam', 'Prüfung', 'check'], ['signs', 'Zeichen', 'eye'], ['stats', 'Fortschritt', 'list']];
const APP = {
  stack: [],
  async go(name, arg, opt = {}) {
    if (VIEW.leave && !(await VIEW.leave())) return;
    if (opt.root) APP.stack = []; else if (VIEW.name) APP.stack.push({ name: VIEW.name, arg: VIEW.arg, y: scrollY, focus: APP.focusKey() });
    await APP.show(name, arg);
  },
  async show(name, arg, restore) {
    VIEW.name = name; VIEW.arg = arg; VIEW.key = null; VIEW.leave = null; VIEW.firstFocus = null;
    if (AND && AND.keepOn) AND.keepOn(name === 'quiz');
    APP.rail();
    const main = $('#main'); main.innerHTML = ''; document.body.classList.toggle('inquiz', name === 'quiz');
    try { await VIEWS[name](main, arg); } catch (e) { main.innerHTML = '<div class="empty"><b>😕</b>' + esc(e.message) + '</div>'; console.error(e); }
    if (restore) { scrollTo(0, restore.y || 0); const e = restore.focus && $('[data-k="' + CSS.escape(restore.focus) + '"]', main); if (e) NAV.set(e, true); else NAV.first(main); }
    else { scrollTo(0, 0); if (!UI.top()) { if (VIEW.firstFocus && VIEW.firstFocus.isConnected) NAV.set(VIEW.firstFocus, true); else NAV.first(main); } }
  },
  focusKey() { return NAV.cur && NAV.cur.dataset && NAV.cur.dataset.k || ''; },
  async back() {
    if (UI.top()) { UI.close(); return; }
    if (VIEW.leave && !(await VIEW.leave())) return;
    VIEW.leave = null;
    const p = APP.stack.pop();
    if (p) { await APP.show(p.name, p.arg, p); return; }
    if (VIEW.name !== 'home') { APP.go('home', null, { root: true }); return; }
    if (AND && AND.exit) AND.exit();
  },
  rail() {
    const r = $('#rail');
    if (!r.firstChild) {
      r.innerHTML = '<div class="logo"></div>' + RAIL.map(([v, t, i]) => '<button class="it f" data-v="' + v + '" data-k="rail-' + v + '">' + ico(i) + '<span>' + t + '</span></button>').join('') + '<div class="sp"></div>';
      $$('.it', r).forEach(b => b.onclick = () => APP.go(b.dataset.v, null, { root: true }));
    }
    const root = APP.stack.length ? APP.stack[0].name : VIEW.name, map = { quiz: VIEW.arg && VIEW.arg.exam ? 'exam' : 'learn', topic: 'learn', result: 'exam', formeln: 'home' };
    $$('.it', r).forEach(b => b.classList.toggle('on', b.dataset.v === (map[root] || root)));
    r.classList.toggle('hide', VIEW.name === 'quiz' && document.body.classList.contains('narrow'));
  },
  key(k) { if (VIEW.key) VIEW.key(k); },
  pause() { ST.save(); },
};
window.APP = APP;

// ------------------------------------------------------------------ Bausteine
const pct = (a, b) => b ? Math.round(a / b * 100) : 0;
function ringSvg(p, size, label) {
  const r = 52, c = 2 * Math.PI * r;
  return '<svg viewBox="0 0 120 120" class="ring" style="width:' + size + 'rem;height:' + size + 'rem"><defs><linearGradient id="rg" x1="0" x2="1"><stop offset="0" stop-color="rgb(var(--a1))"/><stop offset="1" stop-color="rgb(var(--a2))"/></linearGradient></defs><circle cx="60" cy="60" r="' + r + '" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="12"/><circle cx="60" cy="60" r="' + r + '" fill="none" stroke="url(#rg)" stroke-width="12" stroke-linecap="round" stroke-dasharray="' + (c * p / 100) + ' ' + c + '" transform="rotate(-90 60 60)"/><text x="60" y="' + (label ? 62 : 70) + '" text-anchor="middle" font-size="' + (label ? 28 : 30) + '" font-weight="800" fill="#fff">' + p + '%</text>' + (label ? '<text x="60" y="84" text-anchor="middle" font-size="11" fill="#a19db3">' + label + '</text>' : '') + '</svg>';
}
function imgHtml(img) {
  if (!img) return '';
  if (img.sign) return '<div class="qimg signimg">' + DRAW.sign(img.sign) + '</div>';
  if (img.signs) return '<div class="qimg signrow">' + img.signs.map(s => DRAW.sign(s)).join('') + '</div>';
  if (img.scene) return '<div class="qimg sceneimg">' + DRAW.scene(img.scene) + '</div>';
  if (img.svg) return '<div class="qimg sceneimg">' + img.svg + '</div>';
  return '';
}

// ------------------------------------------------------------------ Start
VIEWS.home = async (m) => {
  const ready = ST.ready(), learned = Q.all.filter(q => ST.learned(q.id)).length, mis = ST.mistakes().length, todayN = ST.d.days[today()] || 0, streak = ST.streak();
  const last = ST.d.exams[ST.d.exams.length - 1];
  m.innerHTML = '<div class="hero"><div class="hl">' + ringSvg(ready, TV ? 22 : 15, 'prüfungsreif') + '</div><div class="hr"><h1>Führerschein <span class="grad">Klasse B</span></h1>' +
    '<div class="sub">' + learned + ' von ' + Q.all.length + ' Fragen sicher · ' + (streak ? '🔥 ' + streak + (streak === 1 ? ' Tag' : ' Tage') + ' in Folge' : 'Heute noch nicht gelernt') + '</div>' +
    '<div class="goal"><div class="gb"><i style="width:' + Math.min(100, pct(todayN, ST.d.goal)) + '%"></i></div><small>Heute ' + todayN + ' von ' + ST.d.goal + ' Fragen' + (todayN >= ST.d.goal ? ' – Tagesziel geschafft! 🎉' : '') + '</small></div>' +
    '<div class="btns"><button class="btn pri f" data-k="h-go">' + ico('play') + 'Weiterlernen</button><button class="btn f" data-k="h-exam">' + ico('check') + 'Prüfung simulieren</button></div></div></div>';
  const q = h('div', 'quick');
  const qa = (i, t, s, fn, k) => { const e = h('div', 'qa f', '<div class="ico">' + i + '</div><div><b>' + t + '</b><small>' + s + '</small></div>'); e.dataset.k = k; e.onclick = fn; q.appendChild(e); };
  qa('📚', 'Themen', '12 Themen mit Fortschritt', () => APP.go('learn', null, { root: true }), 'q-top');
  qa('❌', 'Fehler wiederholen', mis ? mis + ' Fragen falsch beantwortet' : 'Keine offenen Fehler', () => mis ? startQuiz(pickLearn(ST.mistakes(), 20), 'Fehler wiederholen') : toast('Keine offenen Fehler 👍'), 'q-mis');
  qa('🔀', 'Vorfahrt-Training', 'Kreuzungen mit Reihenfolge', () => startQuiz(pickLearn(Q.of('vorfahrt'), 15), 'Vorfahrt-Training'), 'q-vf');
  qa('🧮', 'Formeln & Merkhilfen', 'Anhalteweg, Abstände, Promille …', () => APP.go('formeln'), 'q-fo');
  qa('🛑', 'Verkehrszeichen', Object.keys(DRAW.SIGNS).length + ' Zeichen zum Nachschlagen', () => APP.go('signs', null, { root: true }), 'q-zs');
  qa('📈', 'Fortschritt', last ? 'Letzte Prüfung: ' + (last.ok ? 'bestanden' : 'nicht bestanden') + ' (' + last.fp + ' Fehlerpunkte)' : 'Statistik & Prüfungen', () => APP.go('stats', null, { root: true }), 'q-st');
  m.appendChild(q);
  $('[data-k=h-go]', m).onclick = () => startQuiz(pickLearn(Q.all, 20), 'Weiterlernen');
  $('[data-k=h-exam]', m).onclick = () => APP.go('exam', null, { root: true });
  m.appendChild(h('p', 'hint center', 'Übungsfragen zu allen Themen der theoretischen Prüfung Klasse B – selbst geschrieben und gezeichnet, nicht der amtliche Fragenkatalog.'));
};

// ------------------------------------------------------------------ Themen
VIEWS.learn = async (m) => {
  m.innerHTML = '<div class="head"><div><h1>Lernen</h1><div class="sub">Wähle ein Thema – oder lerne gemischt. Eine Frage gilt als sicher, wenn du sie zweimal hintereinander richtig hattest.</div></div><div class="btns"><button class="btn pri f" data-k="l-mix">' + ico('play') + 'Gemischt lernen</button></div></div>';
  $('[data-k=l-mix]', m).onclick = () => startQuiz(pickLearn(Q.all, 20), 'Gemischt');
  for (const cat of ['G', 'B']) {
    m.appendChild(h('h2', 'sec', cat === 'G' ? 'Grundstoff <small>für alle Klassen</small>' : 'Zusatzstoff <small>Klasse B</small>'));
    const g = h('div', 'topics');
    for (const [k, [name, emo, c]] of Object.entries(TOPICS)) {
      if (c !== cat) continue;
      const qs = Q.of(k); if (!qs.length) continue;
      const l = qs.filter(q => ST.learned(q.id)).length, w = qs.filter(q => ST.s(q.id).w).length;
      const e = h('div', 'tp f', '<div class="em">' + emo + '</div><div class="tx"><b>' + esc(name) + '</b><small>' + l + ' / ' + qs.length + ' sicher' + (w ? ' · ' + w + ' Fehler' : '') + '</small><div class="gb"><i style="width:' + pct(l, qs.length) + '%"></i></div></div>');
      e.dataset.k = 't-' + k; e.onclick = () => startQuiz(pickLearn(qs, 15), name);
      g.appendChild(e);
    }
    m.appendChild(g);
  }
};

// ------------------------------------------------------------------ Quiz / Prüfung
function startQuiz(list, title, exam) {
  if (!list.length) { toast('Keine Fragen vorhanden'); return; }
  APP.go('quiz', { list: list.map(q => q.id), title, exam: !!exam, i: 0, ans: {}, done: {}, mark: {}, t0: Date.now() });
}
VIEWS.quiz = async (m, S) => {
  const list = S.list.map(id => Q.byId[id]);
  const draw = () => {
    const q = list[S.i], st = S.done[S.i], ans = S.ans[S.i];
    m.innerHTML = '';
    const top = h('div', 'qtop', '<button class="btn f ic" data-k="x">' + ico('x') + '</button><div class="qt"><b>' + esc(S.title) + '</b><small>Frage ' + (S.i + 1) + ' von ' + list.length + ' · ' + esc(TOPICS[q.topic][0]) + '</small></div>' +
      (S.exam ? '<button class="btn f" data-k="grid">' + ico('list') + '<span class="xs">Übersicht</span></button>' : '') + '<div class="pts">' + q.pts + ' Punkte</div>');
    m.appendChild(top);
    const bar = h('div', 'qbar', '<i style="width:' + ((S.i + (st ? 1 : 0)) / list.length * 100) + '%"></i>'); m.appendChild(bar);
    const body = h('div', 'qbody' + (q.img ? ' hasimg' : ''));
    if (q.img) body.insertAdjacentHTML('beforeend', imgHtml(q.img));
    const right = h('div', 'qr');
    right.appendChild(h('div', 'qq', esc(q.q)));
    if (q.type === 'num') {
      const box = h('div', 'numbox f' + (st ? (st.ok ? ' ok' : ' bad') : ''), '<span class="v">' + esc(ans || '') + '</span><span class="u">' + esc(q.unit || '') + '</span>');
      box.dataset.k = 'num';
      box.onclick = () => { if (!st) numPad(ans || '', (v) => { S.ans[S.i] = v; draw(); NAV.set($('[data-k=ok]', m) || $('[data-k=next]', m)); }); };
      right.appendChild(h('div', 'hint', 'Zahl eingeben' + (q.unit ? ' (in ' + q.unit + ')' : '')));
      right.appendChild(box);
    } else {
      right.appendChild(h('div', 'hint', 'Eine oder mehrere Antworten können richtig sein.'));
      const as = h('div', 'answers');
      q.a.forEach((a, i) => {
        const sel = (ans || []).includes(i);
        let cls = 'ans f' + (sel ? ' sel' : '');
        if (st) cls += a.ok ? ' right' : sel ? ' wrong' : '';
        const e = h('div', cls, '<span class="ck">' + (sel ? '✓' : '') + '</span><span class="t">' + esc(a.t) + '</span>');
        e.dataset.k = 'a' + i;
        e.onclick = () => { if (st) return; const cur = S.ans[S.i] || []; S.ans[S.i] = cur.includes(i) ? cur.filter(x => x !== i) : cur.concat(i); e.classList.toggle('sel'); $('.ck', e).textContent = S.ans[S.i].includes(i) ? '✓' : ''; };
        as.appendChild(e);
      });
      right.appendChild(as);
    }
    if (st && !S.exam) right.appendChild(h('div', 'expl ' + (st.ok ? 'ok' : 'bad'), '<b>' + (st.ok ? '✓ Richtig!' : '✗ Leider falsch.') + '</b>' + (q.type === 'num' ? ' Richtig: ' + String(q.ans).replace('.', ',') + ' ' + esc(q.unit || '') + '.' : '') + '<p>' + esc(q.ex || '') + '</p>'));
    body.appendChild(right); m.appendChild(body);
    const foot = h('div', 'qfoot');
    if (S.exam) {
      foot.innerHTML = '<button class="btn f" data-k="prev"' + (S.i ? '' : ' disabled') + '>' + ico('left') + 'Zurück</button><button class="btn f" data-k="mark">' + (S.mark[S.i] ? '★ Markiert' : '☆ Markieren') + '</button>' +
        (S.i < list.length - 1 ? '<button class="btn pri f" data-k="next">Weiter' + ico('right') + '</button>' : '<button class="btn pri f" data-k="give">' + ico('check') + 'Abgeben</button>');
    } else foot.innerHTML = st ? '<button class="btn pri f" data-k="next">' + (S.i < list.length - 1 ? 'Weiter' + ico('right') : ico('check') + 'Auswertung') + '</button>' : '<button class="btn pri f" data-k="ok">' + ico('check') + 'Prüfen</button>';
    m.appendChild(foot);
    const F = (k) => $('[data-k=' + k + ']', m);
    F('x').onclick = () => APP.back();
    if (F('ok')) F('ok').onclick = () => {
      const cur = S.ans[S.i];
      if (q.type === 'num' ? !cur : !(cur || []).length) { toast('Bitte zuerst antworten'); return; }
      const ok = isRight(q, cur); S.done[S.i] = { ok }; ST.answer(q.id, ok); draw(); NAV.set(F('next'), true);
    };
    if (F('next')) F('next').onclick = () => {
      if (!S.exam && S.i === list.length - 1) { finishLearn(); return; }
      S.i++; draw(); NAV.first($('.qbody', m));
    };
    if (F('prev')) F('prev').onclick = () => { if (S.i) { S.i--; draw(); } };
    if (F('mark')) F('mark').onclick = () => { S.mark[S.i] = !S.mark[S.i]; F('mark').textContent = S.mark[S.i] ? '★ Markiert' : '☆ Markieren'; };
    if (F('give')) F('give').onclick = () => giveExam();
    if (F('grid')) F('grid').onclick = () => examGrid();
    const ff = st ? F('next') : ($('.answers .ans', m) || $('.numbox', m));
    VIEW.firstFocus = ff; if (ff) NAV.set(ff, true);
  };
  const finishLearn = () => {
    const n = Object.values(S.done).length, ok = Object.values(S.done).filter(x => x.ok).length;
    APP.stack.pop();
    APP.show('result', { learn: true, n, ok, title: S.title, wrong: list.filter((q, i) => S.done[i] && !S.done[i].ok).map(q => q.id) });
  };
  const examGrid = () => {
    const d = UI.open('<h2>Übersicht</h2><p>Beantwortete Fragen sind hell, markierte haben einen Stern.</p><div class="egrid"></div>', 'wide');
    const g = $('.egrid', d);
    list.forEach((q, i) => {
      const a = S.ans[i], has = q.type === 'num' ? !!a : !!(a && a.length);
      const e = h('button', 'eg f' + (has ? ' has' : '') + (i === S.i ? ' cur' : ''), (i + 1) + (S.mark[i] ? '★' : '') + '<small>' + q.pts + ' P</small>');
      e.onclick = () => { UI.close(); S.i = i; draw(); }; g.appendChild(e);
    });
  };
  const giveExam = async () => {
    const open = list.filter((q, i) => q.type === 'num' ? !S.ans[i] : !(S.ans[i] && S.ans[i].length)).length;
    if (!(await UI.confirm('Prüfung abgeben?', open ? open + ' Fragen sind noch unbeantwortet – sie zählen als falsch.' : 'Alle Fragen sind beantwortet.', 'Abgeben'))) return;
    let fp = 0, five = 0; const wrong = [];
    list.forEach((q, i) => { const ok = isRight(q, S.ans[i]); ST.answer(q.id, ok); if (!ok) { fp += q.pts; if (q.pts === 5) five++; wrong.push({ id: q.id, ans: S.ans[i] }); } });
    const ok = fp <= 10 && five < 2;
    ST.d.exams.push({ t: Date.now(), fp, ok, min: Math.round((Date.now() - S.t0) / 60000) }); ST.save();
    VIEW.leave = null; APP.stack.pop();
    APP.show('result', { exam: true, fp, ok, five, wrong, n: list.length, min: Math.round((Date.now() - S.t0) / 60000) });
  };
  VIEW.leave = async () => Object.keys(S.ans).length === 0 || Object.keys(S.done).length === list.length || await UI.confirm(S.exam ? 'Prüfung abbrechen?' : 'Lernrunde beenden?', S.exam ? 'Die Antworten gehen verloren.' : 'Bisherige Antworten sind gespeichert.', 'Beenden', true);
  VIEW.key = (k) => {
    if (UI.top()) return false;
    // Zifferntasten der Fernbedienung / Tastatur: Antwort 1–4 umschalten
    return false;
  };
  draw();
};
/** Zahlenfeld (Fire-TV-Fernbedienungen haben keine Ziffern) */
function numPad(val, done) {
  let v = String(val || '');
  const d = UI.open('<h2>Zahl eingeben</h2><div class="npv"></div><div class="np"></div><div class="btns"><button class="btn f no">Abbrechen</button><button class="btn pri f yes">Übernehmen</button></div>');
  const show = () => { $('.npv', d).textContent = v || '–'; };
  const np = $('.np', d);
  for (const k of ['1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0', '⌫']) {
    const b = h('button', 'nk f', k); b.onclick = () => { if (k === '⌫') v = v.slice(0, -1); else if (k === ',') { if (!v.includes(',')) v += v ? ',' : '0,'; } else if (v.length < 6) v += k; show(); }; np.appendChild(b);
  }
  $('.no', d).onclick = () => UI.close();
  $('.yes', d).onclick = () => { UI.close(); done(v); };
  d.addEventListener('keydown', (e) => { if (/^[0-9]$/.test(e.key)) { if (v.length < 6) v += e.key; show(); } if (e.key === 'Backspace') { v = v.slice(0, -1); show(); } if (e.key === ',' || e.key === '.') { if (!v.includes(',')) v += v ? ',' : '0,'; show(); } });
  show(); setTimeout(() => NAV.set($('.nk', d), true), 0);
}
VIEWS.result = async (m, R) => {
  if (R.learn) {
    const p = pct(R.ok, R.n);
    m.innerHTML = '<div class="res"><div class="big">' + (p >= 90 ? '🏆' : p >= 70 ? '👍' : '💪') + '</div><h1>' + R.ok + ' von ' + R.n + ' richtig</h1><div class="sub">' + esc(R.title) + ' · ' + p + ' %</div>' +
      '<div class="btns"><button class="btn pri f" data-k="again">' + ico('play') + 'Weiterlernen</button>' + (R.wrong.length ? '<button class="btn f" data-k="wr">❌ Fehler wiederholen (' + R.wrong.length + ')</button>' : '') + '<button class="btn f" data-k="home">Zur Übersicht</button></div></div>';
    $('[data-k=again]', m).onclick = () => startQuiz(pickLearn(Q.all, 20), 'Weiterlernen');
    if ($('[data-k=wr]', m)) $('[data-k=wr]', m).onclick = () => startQuiz(R.wrong.map(id => Q.byId[id]), 'Fehler aus dieser Runde');
    $('[data-k=home]', m).onclick = () => APP.go('home', null, { root: true });
    return;
  }
  m.innerHTML = '<div class="res ' + (R.ok ? 'pass' : 'fail') + '"><div class="big">' + (R.ok ? '🎉' : '😬') + '</div><h1>' + (R.ok ? 'Bestanden!' : 'Nicht bestanden') + '</h1>' +
    '<div class="sub">' + R.fp + ' Fehlerpunkte von höchstens 10' + (R.five >= 2 ? ' · zwei 5-Punkte-Fragen falsch' : '') + ' · ' + R.min + ' Min.</div>' +
    '<div class="btns"><button class="btn pri f" data-k="new">' + ico('refresh') + 'Neue Prüfung</button>' + (R.wrong.length ? '<button class="btn f" data-k="wr">❌ Fehler üben (' + R.wrong.length + ')</button>' : '') + '<button class="btn f" data-k="home">Zur Übersicht</button></div></div>';
  if (R.wrong.length) {
    m.appendChild(h('h2', 'sec', 'Deine Fehler'));
    const l = h('div', 'wlist');
    for (const w of R.wrong) {
      const q = Q.byId[w.id];
      const e = h('div', 'wi f', '<div class="wp">' + q.pts + '</div><div><b>' + esc(q.q) + '</b><small>' + (q.type === 'num' ? 'Richtig: ' + String(q.ans).replace('.', ',') + ' ' + esc(q.unit || '') : 'Richtig: ' + q.a.filter(a => a.ok).map(a => esc(a.t)).join(' · ')) + '</small></div>');
      e.onclick = () => UI.open('<h2>' + esc(q.q) + '</h2>' + imgHtml(q.img) + '<p><b>Richtig:</b> ' + (q.type === 'num' ? String(q.ans).replace('.', ',') + ' ' + esc(q.unit || '') : q.a.filter(a => a.ok).map(a => esc(a.t)).join(' · ')) + '</p><p>' + esc(q.ex || '') + '</p><div class="btns"><button class="btn pri f" onclick="UI.close()">OK</button></div>', 'wide');
      l.appendChild(e);
    }
    m.appendChild(l);
  }
  $('[data-k=new]', m).onclick = () => startQuiz(pickExam(), 'Prüfungssimulation', true);
  if ($('[data-k=wr]', m)) $('[data-k=wr]', m).onclick = () => startQuiz(R.wrong.map(w => Q.byId[w.id]), 'Fehler aus der Prüfung');
  $('[data-k=home]', m).onclick = () => APP.go('home', null, { root: true });
};
VIEWS.exam = async (m) => {
  const ex = ST.d.exams.slice(-8).reverse();
  m.innerHTML = '<div class="head"><div><h1>Prüfungssimulation</h1><div class="sub">Wie in der echten Theorieprüfung Klasse B</div></div></div>' +
    '<div class="box"><ul class="rules"><li><b>30 Fragen:</b> 20 aus dem Grundstoff, 10 aus dem Zusatzstoff Klasse B</li><li>Jede Frage ist <b>2 bis 5 Fehlerpunkte</b> wert</li><li><b>Bestanden</b> mit höchstens <b>10 Fehlerpunkten</b> – aber nicht, wenn zwei Fragen mit 5 Punkten falsch sind</li><li>Bei mehreren Antworten müssen <b>alle richtigen</b> angekreuzt sein</li><li>Du kannst vor- und zurückspringen und Fragen markieren</li></ul>' +
    '<div class="btns"><button class="btn pri f" data-k="go">' + ico('play') + 'Prüfung starten</button></div></div>';
  $('[data-k=go]', m).onclick = () => startQuiz(pickExam(), 'Prüfungssimulation', true);
  if (ex.length) {
    m.appendChild(h('h2', 'sec', 'Letzte Prüfungen <small>' + ST.d.exams.filter(e => e.ok).length + ' von ' + ST.d.exams.length + ' bestanden</small>'));
    m.appendChild(h('div', 'exlist', ex.map(e => '<div class="ex ' + (e.ok ? 'ok' : 'bad') + '"><b>' + (e.ok ? '✓ Bestanden' : '✗ Nicht bestanden') + '</b><span>' + e.fp + ' Fehlerpunkte</span><small>' + new Date(e.t).toLocaleDateString('de-DE', { day: 'numeric', month: 'short' }) + ' · ' + e.min + ' Min.</small></div>').join('')));
  }
};

// ------------------------------------------------------------------ Verkehrszeichen-Lexikon
VIEWS.signs = async (m) => {
  const groups = [['Gefahrzeichen', GEN.SIGN_FAM.gefahr], ['Vorfahrt & Vorrang', GEN.SIGN_FAM.vorfahrt], ['Gebote & Wege', GEN.SIGN_FAM.gebot], ['Verbote', GEN.SIGN_FAM.verbot], ['Geschwindigkeit & Streckenverbote', GEN.SIGN_FAM.tempo], ['Richtzeichen', GEN.SIGN_FAM.richt]];
  m.innerHTML = '<div class="head"><div><h1>Verkehrszeichen</h1><div class="sub">Zum Nachschlagen – OK für Details. Lernen kannst du sie im Thema „Verkehrszeichen“.</div></div><div class="btns"><button class="btn pri f" data-k="zl">' + ico('play') + 'Zeichen üben</button></div></div>';
  $('[data-k=zl]', m).onclick = () => startQuiz(pickLearn(Q.of('zeichen'), 20), 'Verkehrszeichen');
  const seen = new Set();
  for (const [t, codes] of groups) {
    m.appendChild(h('h2', 'sec', t));
    const g = h('div', 'sgrid');
    for (const c of codes) {
      if (seen.has(c)) continue; seen.add(c);
      const e = h('div', 'sg f', DRAW.sign(c) + '<small>' + esc(DRAW.signName(c)) + '</small>');
      e.dataset.k = 'sg-' + c;
      e.onclick = () => UI.open('<div class="sgbig">' + DRAW.sign(c) + '</div><h2>' + esc(DRAW.signName(c)) + '</h2><p>Zeichen ' + esc(c.replace(/-\d+$/, '')) + '</p>' + (GEN.SIGN_TIPS[c] ? '<p>' + esc(GEN.SIGN_TIPS[c]) + '</p>' : '') + '<div class="btns"><button class="btn pri f" onclick="UI.close()">OK</button></div>');
      g.appendChild(e);
    }
    m.appendChild(g);
  }
};

// ------------------------------------------------------------------ Formeln & Merkhilfen
VIEWS.formeln = async (m) => {
  const card = (t, body) => '<div class="box fo f"><h3>' + t + '</h3>' + body + '</div>';
  m.innerHTML = '<div class="head"><div><h1>Formeln & Merkhilfen</h1><div class="sub">Das Wichtigste auf einen Blick</div></div></div><div class="fogrid">' +
    card('⏱️ Reaktionsweg', '<p class="f1">(Geschwindigkeit ÷ 10) × 3</p><p>Beispiel 50 km/h: 5 × 3 = <b>15 m</b></p>') +
    card('🛑 Bremsweg (normal)', '<p class="f1">(Geschwindigkeit ÷ 10) × (Geschwindigkeit ÷ 10)</p><p>Beispiel 50 km/h: 5 × 5 = <b>25 m</b></p>') +
    card('🚨 Bremsweg (Gefahrbremsung)', '<p class="f1">normaler Bremsweg ÷ 2</p><p>Beispiel 50 km/h: 25 ÷ 2 = <b>12,5 m</b></p>') +
    card('📏 Anhalteweg', '<p class="f1">Reaktionsweg + Bremsweg</p><p>Beispiel 50 km/h: 15 + 25 = <b>40 m</b> (Gefahrbremsung: 27,5 m)</p>') +
    card('↔️ Sicherheitsabstand', '<p class="f1">außerorts: „halber Tacho“ in Metern</p><p>100 km/h → <b>50 m</b> (entspricht etwa 2 Sekunden).<br>Innerorts mindestens der Weg, den du in <b>1 Sekunde</b> fährst (bei 50 km/h ca. 15 m) – mehr ist besser.</p>') +
    card('📈 Doppelt so schnell', '<p>Reaktionsweg: <b>doppelt</b> so lang<br>Bremsweg: <b>viermal</b> so lang<br>Aufprallwucht: <b>viermal</b> so groß</p>') +
    card('🍺 Alkohol', '<p><b>0,0 ‰</b> in der Probezeit und unter 21 Jahren<br><b>0,5 ‰</b> Ordnungswidrigkeit<br><b>0,3 ‰</b> mit Ausfallerscheinungen: Straftat möglich<br><b>1,1 ‰</b> absolute Fahruntüchtigkeit (Straftat)<br><b>1,6 ‰</b> Medizinisch-Psychologische Untersuchung (MPU)</p>') +
    card('🏎️ Höchstgeschwindigkeiten Pkw', '<p>innerorts <b>50 km/h</b><br>außerorts <b>100 km/h</b><br>Autobahn: Richtgeschwindigkeit <b>130 km/h</b><br>Sicht unter 50 m (Nebel): höchstens <b>50 km/h</b></p>') +
    card('🚲 Seitenabstand beim Überholen', '<p>Radfahrer, Fußgänger, E-Scooter: innerorts mindestens <b>1,5 m</b>, außerorts mindestens <b>2 m</b></p>') +
    card('🔺 Warndreieck aufstellen', '<p>innerorts ca. <b>50 m</b><br>Landstraße ca. <b>100 m</b><br>Autobahn ca. <b>200 m</b> oder mehr – vor Kurven und Kuppen früher</p>') +
    card('🅿️ Halten & Parken', '<p>Kein Halten: bis <b>5 m</b> vor Fußgängerüberwegen, <b>10 m</b> vor Ampeln und Stoppschildern (wenn sie verdeckt würden)<br>Kein Parken: bis <b>5 m</b> vor/hinter Kreuzungen, bis <b>15 m</b> vor/hinter Haltestellenschildern<br>Halten: bis 3 Minuten</p>') +
    card('🛞 Reifen', '<p>Mindestprofil gesetzlich <b>1,6 mm</b> – empfohlen Sommer 3 mm, Winter 4 mm<br>Winterreifen bei Glätte (Schneeflocke + Berg-Symbol)</p>') +
    '</div>';
};

// ------------------------------------------------------------------ Fortschritt
VIEWS.stats = async (m) => {
  let n = 0, ok = 0; for (const id in ST.d.q) { n += ST.d.q[id].n; ok += ST.d.q[id].ok; }
  const days = Object.keys(ST.d.days).sort().slice(-14);
  m.innerHTML = '<div class="head"><div><h1>Fortschritt</h1><div class="sub">' + n + ' Antworten · ' + pct(ok, n) + ' % richtig · ' + ST.d.exams.length + ' Prüfungen</div></div><div class="btns"><button class="btn f" data-k="goal">🎯 Tagesziel: ' + ST.d.goal + '</button><button class="btn f danger" data-k="reset">' + ico('trash') + 'Zurücksetzen</button></div></div>';
  m.appendChild(h('div', 'statrow', '<div class="box">' + ringSvg(ST.ready(), 12, 'prüfungsreif') + '</div><div class="box"><h3>Letzte 14 Tage</h3><div class="days">' + (days.length ? days.map(d => '<div class="dy"><i style="height:' + Math.min(100, (ST.d.days[d] || 0) / Math.max(ST.d.goal, 1) * 100) + '%"></i><small>' + d.slice(8) + '.</small></div>').join('') : '<p class="mut">Noch keine Daten</p>') + '</div></div>'));
  m.appendChild(h('h2', 'sec', 'Themen'));
  const g = h('div', 'topics');
  for (const [k, [name, emo]] of Object.entries(TOPICS)) {
    const qs = Q.of(k); if (!qs.length) continue;
    const l = qs.filter(q => ST.learned(q.id)).length;
    const e = h('div', 'tp f', '<div class="em">' + emo + '</div><div class="tx"><b>' + esc(name) + '</b><small>' + pct(l, qs.length) + ' % sicher · ' + qs.length + ' Fragen</small><div class="gb"><i style="width:' + pct(l, qs.length) + '%"></i></div></div>');
    e.onclick = () => startQuiz(pickLearn(qs, 15), name); g.appendChild(e);
  }
  m.appendChild(g);
  $('[data-k=goal]', m).onclick = async () => { const v = await UI.choose('Tagesziel', [10, 20, 30, 50, 80].map(n => ({ t: n + ' Fragen pro Tag', v: n }))); if (v) { ST.d.goal = v; ST.save(); APP.show('stats'); } };
  $('[data-k=reset]', m).onclick = async () => { if (await UI.confirm('Alles zurücksetzen?', 'Dein gesamter Lernstand und alle Prüfungen werden gelöscht.', 'Zurücksetzen', true)) { localStorage.removeItem('fs_state'); ST.load(); APP.show('stats'); toast('Zurückgesetzt'); } };
};

// ------------------------------------------------------------------ Start
function boot() {
  document.body.classList.toggle('tv', TV);
  if (TV) { document.body.classList.add('kb'); const fit = () => { document.documentElement.style.fontSize = (innerWidth / 1920 * 10) + 'px'; }; fit(); addEventListener('resize', fit); }
  const narrow = () => document.body.classList.toggle('narrow', !TV && innerWidth < 760); narrow(); addEventListener('resize', narrow);
  ST.load(); Q.init();
  APP.go('home', null, { root: true });
}
boot();
