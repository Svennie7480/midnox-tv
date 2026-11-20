// midnox Krimi-Abend – Detektiv-Modus: „Team“ (1–2, gemeinsam, Fernbedienung reicht) und „Duell“ (2 Handys, gegeneinander).
// Nutzt die Helfer aus tv.html (stage, G, tell, okWait, guard, sync, SND, esc, shuffle, …).
window.DG = (function(){
  const PER_ACT = { team: 4, duel: 3 };
  const PTS = { cul: 50, mot: 25, ev: 25, left: 2 };
  let F = { list: [], i: 0 };                      // Fokus auf dem Fernseher

  // ---------- Fokus mit dem Steuerkreuz ----------
  function focusables(){ F.list = Array.from(stage.querySelectorAll('.df')); if (F.i >= F.list.length) F.i = Math.max(0, F.list.length - 1); paint(); }
  function curKey(){ const e = F.list[F.i]; return e && e.dataset ? (e.dataset.ev || e.dataset.sus || e.dataset.k) : null; }
  function paint(){ F.list.forEach((e, i) => e.classList.toggle('foc', i === F.i)); }
  function move(dir){
    const cur = F.list[F.i]; if (!cur) return; const r = cur.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    let best = -1, bs = Infinity;
    F.list.forEach((e, i) => { if (i === F.i) return; const q = e.getBoundingClientRect(), x = q.left + q.width / 2, y = q.top + q.height / 2;
      const dx = x - cx, dy = y - cy; let pri, ort;
      if (dir === 'left' || dir === 'right') { pri = dir === 'right' ? dx : -dx; ort = Math.abs(dy); } else { pri = dir === 'down' ? dy : -dy; ort = Math.abs(dx); }
      if (pri <= 4) return; const s = pri + ort * 2.2; if (s < bs) { bs = s; best = i; } });
    if (best >= 0) { F.i = best; paint(); SND.play('select'); }
  }
  function key(k){
    if (!G || !G.mode || !F.list.length) return false;
    if (['up', 'down', 'left', 'right'].includes(k)) { move(k); return true; }
    if (k === 'ok') { const e = F.list[F.i]; if (e && e._ok) { e._ok(); return true; } }
    return false;
  }

  // ---------- Daten ----------
  const D = () => DETECTIVE[G.c.id];
  const recOf = (p) => G.mode === 'team' ? G.rec : G.recs[p.pid];
  function newRec(){ return { left: 0, asked: new Set(), looked: new Set(), notes: [], ready: false, acc: null, step: 0, pick: {}, score: null }; }
  function ask(rec, ci){ const k = ci + '_' + G.act; if (rec.left <= 0 || rec.asked.has(k) || !G.c.chars[ci]) return null;
    rec.left--; rec.asked.add(k); const ch = G.c.chars[ci];
    const n = { ic: ch.emoji, who: ch.name, a: G.act, kind: 'Aussage', text: D().talk[ci][G.act - 1] }; rec.notes.push(n); return n; }
  function look(rec, name){ const e = G.ev.find(x => x.name === name); if (!e || rec.left <= 0 || rec.looked.has(name)) return null;
    rec.left--; rec.looked.add(name); const n = { ic: '🔍', who: e.name, a: G.act, kind: 'Lupe', text: D().look[name] || 'Nichts Auffälliges.' }; rec.notes.push(n); return n; }
  function scoreOf(rec){ const a = rec.acc || {}, cul = G.c.chars.findIndex(c => c.guilty);
    const r = { cul: a.c === cul, mot: a.m === D().motives[0], ev: D().keys.includes(a.e) };
    r.pts = (r.cul ? PTS.cul : 0) + (r.mot ? PTS.mot : 0) + (r.ev ? PTS.ev : 0) + rec.left * PTS.left;
    r.rank = r.pts >= 100 ? 'Meisterdetektiv' : r.pts >= 75 ? 'Kommissar' : r.pts >= 50 ? 'Inspektor' : 'Praktikant'; return r; }

  // ---------- Handy-Ansicht ----------
  function view(p){
    const duel = G.mode === 'duel', inGame = !duel || G.duo.includes(p);
    if (!inGame) return { t: 'wait', emoji: '🍿', title: 'Detektiv-Duell läuft', sub: 'Zwei ermitteln gegeneinander. Schau am Fernseher zu!' };
    const rec = recOf(p), d = D();
    const v = { t: 'det', mode: G.mode, case: G.c.title, tag: G.c.tag, victim: G.c.victim, act: G.act, left: rec.left, phase: G.dph,
      sus: G.c.chars.map((c, i) => ({ i, name: c.name, emoji: c.emoji, role: c.role, asked: rec.asked.has(i + '_' + G.act) })),
      ev: G.ev.map(e => ({ name: e.name, ic: e.ic, text: e.text, a: e.a, looked: rec.looked.has(e.name) })).reverse(),
      notes: rec.notes.slice().reverse(), ready: rec.ready, last: rec.notes.length };
    if (G.dph === 'accuse') { v.acc = duel ? { mine: true, done: !!rec.acc, sus: G.c.chars.map((c, i) => ({ i, name: c.name, emoji: c.emoji })), mot: G.mots, ev: G.ev.map(e => ({ name: e.name, ic: e.ic })) } : { mine: false }; }
    if (G.dph === 'result') { const s = rec.score || {}; v.res = { pts: s.pts || 0, rank: s.rank || '', cul: !!s.cul, mot: !!s.mot, ev: !!s.ev, culprit: G.c.chars.find(c => c.guilty).name, win: G.win == null ? null : G.win === p.pid, draw: G.win === 0 }; }
    return v;
  }

  // ---------- Eingaben von Handys ----------
  function input(p, d){
    if (G.mode === 'duel' && !G.duo.includes(p)) return;
    const rec = recOf(p);
    if (G.dph === 'invest') {
      let n = null;
      if (d.ask != null) n = ask(rec, +d.ask);
      if (d.look != null) n = look(rec, d.look + '');
      if (n) { SND.play(n.kind === 'Lupe' ? 'paper' : 'ask'); if (G.mode === 'team') showNote(n); else board(); sync(); return; }
      if (d.ready && G.mode === 'duel') { rec.ready = true; SND.play('ready'); board(); sync(); if (G.duo.every(q => G.recs[q.pid].ready) && G.res) G.res(); return; }
      if (d.next && G.mode === 'team' && G.res) { G.res(); return; }
    }
    if (G.dph === 'accuse' && G.mode === 'duel' && d.acc && !rec.acc) {
      const a = d.acc; rec.acc = { c: +a.c, m: a.m + '', e: a.e + '' }; SND.play('vote'); accWaitTv(); sync();
      if (G.duo.every(q => G.recs[q.pid].acc) && G.res) setTimeout(() => G.res && G.res(), 800);
    }
  }

  // ---------- Fernseher ----------
  function hudLine(){ return '<div class="hud">' + G.c.icon + ' <b>' + esc(G.c.title) + '</b> · ' + (G.mode === 'team' ? 'Detektiv-Team' : 'Detektiv-Duell') + (G.act ? ' · Akt ' + G.act + ' von 3' : '') + '</div><div class="hudr">Zurück = Fall abbrechen</div>'; }
  function evTile(e, rec){ const l = rec && rec.looked.has(e.name);
    return '<div class="ev dev' + (rec ? ' df' : '') + (l ? ' done' : '') + '" data-ev="' + esc(e.name) + '"><div class="ic">' + e.ic + '</div><h3>' + esc(e.name) + (l ? ' 🔍' : '') + '</h3><p>' + esc(e.text) + '</p></div>'; }
  function board(){
    const team = G.mode === 'team', rec = team ? G.rec : null;
    const keep = curKey() || F.keep;
    let side;
    if (team) side = '<div class="dleft"><div class="big2">' + rec.left + '</div><div class="mut">Aktionen übrig</div><div class="mut" style="font-size:1.7vh;margin-top:1vh">Befragen oder Lupe kostet je 1. Nicht genutzte Aktionen bringen am Ende Bonuspunkte.</div></div>' +
      '<div class="dbtn df" data-k="next">' + (G.act < 3 ? '➜ Weiter zu Akt ' + (G.act + 1) : '⚖️ Anklage erheben') + '</div>' +
      '<div class="dbtn df" data-k="notes">📓 Notizbuch (' + rec.notes.length + ')</div>';
    else side = G.duo.map(p => { const r = G.recs[p.pid]; return '<div class="who"><span class="e">' + p.emoji + '</span><span class="t"><b>' + esc(p.name) + '</b><small>' + r.left + ' Aktionen übrig · ' + r.notes.length + ' Funde</small></span>' + (r.ready ? '<span class="ok">✔</span>' : '') + '</div>'; }).join('') +
      '<div class="mut" style="font-size:1.9vh;margin-top:1vh">Jeder ermittelt heimlich auf seinem Handy. Weiter, wenn beide fertig sind (oder OK).</div>';
    stage.innerHTML = hudLine() + '<div class="board dboard"><div class="wall">' +
      '<div class="serif" style="font-size:3vh">🧑‍⚖️ Verdächtige ' + (team ? '<span class="mut" style="font-size:2vh">· OK = befragen</span>' : '') + '</div>' +
      '<div class="sgrid">' + G.c.chars.map((c, i) => { const done = team && rec.asked.has(i + '_' + G.act);
        return '<div class="stile' + (team ? ' df' : '') + (done ? ' done' : '') + '" data-sus="' + i + '"><span class="e">' + c.emoji + '</span><span class="t"><b>' + esc(c.name) + '</b><small>' + esc(c.role) + '</small></span>' + (done ? '<span class="ok">✔</span>' : '') + '</div>'; }).join('') + '</div>' +
      '<div class="serif" style="font-size:3vh;margin-top:1vh">🗂️ Beweise ' + (team ? '<span class="mut" style="font-size:2vh">· OK = mit der Lupe untersuchen</span>' : '') + '</div>' +
      '<div class="grid">' + G.ev.slice().reverse().map(e => evTile(e, rec)).join('') + '</div></div>' +
      '<div class="side">' + side + '</div></div>';
    if (team) {
      stage.querySelectorAll('[data-sus]').forEach(e => e._ok = () => { const n = ask(rec, +e.dataset.sus); if (n) { SND.play('ask'); showNote(n); sync(); } else toast(rec.left <= 0 ? 'Keine Aktionen mehr – weiter zum nächsten Akt' : 'In diesem Akt schon befragt'); });
      stage.querySelectorAll('[data-ev]').forEach(e => e._ok = () => { const n = look(rec, e.dataset.ev); if (n) { SND.play('paper'); showNote(n); sync(); } else toast(rec.left <= 0 ? 'Keine Aktionen mehr – weiter zum nächsten Akt' : 'Schon untersucht'); });
      const nx = stage.querySelector('[data-k="next"]'); if (nx) nx._ok = () => { if (G.res) G.res(); };
      const nb = stage.querySelector('[data-k="notes"]'); if (nb) nb._ok = () => notebook();
    }
    focusables();
    if (keep) { const j = F.list.findIndex(e => (e.dataset.ev || e.dataset.sus || e.dataset.k) === keep); if (j >= 0) { F.i = j; paint(); } }
    if (team && rec.left <= 0) { const j = F.list.findIndex(e => e.dataset.k === 'next'); if (j >= 0) { F.i = j; paint(); } }   // keine Aktionen mehr: gleich auf „Weiter“
  }
  function showNote(n){
    F.keep = curKey() || F.keep;
    const back = () => board();
    stage.innerHTML = hudLine() + '<div class="spot fade"><div class="em" style="font-size:22vh">' + n.ic + '</div><div class="info"><div class="over mut" style="letter-spacing:.3em;text-transform:uppercase;font-size:2vh">' + (n.kind === 'Lupe' ? 'Unter der Lupe' : 'Verhör · Akt ' + n.a) + '</div><div class="nm" style="font-size:5.4vh">' + esc(n.who) + '</div><div class="q">' + (n.kind === 'Lupe' ? '' : '„') + esc(n.text) + (n.kind === 'Lupe' ? '' : '“') + '</div></div></div>' +
      '<div class="okhint">OK = zurück zur Ermittlung</div>';
    F.list = [{ _ok: back, classList: { toggle(){} } }]; F.i = 0;
  }
  function notebook(){
    F.keep = curKey() || F.keep;
    const rec = G.rec;
    stage.innerHTML = hudLine() + '<div class="story" style="justify-content:flex-start;padding-top:10vh"><div class="over">📓 Notizbuch</div><div class="nbk">' +
      (rec.notes.length ? rec.notes.slice().reverse().map(n => '<div><b>' + n.ic + ' ' + esc(n.who) + '</b> <span class="mut">· Akt ' + n.a + ' · ' + n.kind + '</span><br>' + esc(n.text) + '</div>').join('') : '<div class="mut">Noch nichts notiert.</div>') +
      '</div></div><div class="okhint">OK = zurück</div>';
    F.list = [{ _ok: () => board(), classList: { toggle(){} } }]; F.i = 0;
  }
  function accWaitTv(){
    stage.innerHTML = hudLine() + '<div class="story fade"><div class="over">Die Anklage</div><div class="big">Wer war es – und warum?</div><div class="txt mut" style="font-size:2.8vh">Beide erheben jetzt geheim auf dem Handy Anklage: Täter, Motiv und entscheidender Beweis.</div>' +
      '<div class="chips">' + G.duo.map(p => '<div class="chip' + (G.recs[p.pid].acc ? ' on' : '') + '">' + p.emoji + ' ' + esc(p.name) + (G.recs[p.pid].acc ? ' ✔' : '') + '</div>').join('') + '</div></div><div class="okhint">Auflösung, wenn beide fertig sind</div>';
    F.list = [];
  }
  // Anklage am Fernseher (Team): drei Schritte
  function accuseTv(my){
    return new Promise(res => {
      const pick = {}; let step = 0;
      const steps = [
        { t: 'Wer ist der Täter?', opts: G.c.chars.map((c, i) => ({ v: i, html: '<span class="e">' + c.emoji + '</span><span class="t"><b>' + esc(c.name) + '</b><small>' + esc(c.role) + '</small></span>' })) },
        { t: 'Was war das Motiv?', opts: G.mots.map(m => ({ v: m, html: '<span class="t"><b style="white-space:normal">' + esc(m) + '</b></span>' })) },
        { t: 'Welcher Beweis überführt den Täter?', opts: G.ev.map(e => ({ v: e.name, html: '<span class="e">' + e.ic + '</span><span class="t"><b style="white-space:normal">' + esc(e.name) + '</b></span>' })) } ];
      const draw = () => { const S = steps[step]; const k = ['c', 'm', 'e'][step];
        stage.innerHTML = hudLine() + '<div class="story" style="gap:3vh"><div class="over">Die Anklage · Schritt ' + (step + 1) + ' von 3</div><div class="big" style="font-size:6vh">' + S.t + '</div>' +
          '<div class="agrid">' + S.opts.map((o, i) => '<div class="stile df" data-i="' + i + '">' + o.html + '</div>').join('') + '</div></div><div class="okhint">OK = auswählen · Zurück = Fall abbrechen</div>';
        stage.querySelectorAll('[data-i]').forEach(e => e._ok = () => { pick[k] = S.opts[+e.dataset.i].v; SND.play('vote'); step++; if (step < 3) { F.i = 0; draw(); } else { F.list = []; res(pick); } });
        F.i = 0; focusables(); };
      draw();
    });
  }

  async function evidenceReveal(my, a, A){
    for (let i = 0; i < A.evidence.length; i++) { G.ev.push(Object.assign({ a }, A.evidence[i])); SND.play('paper');
      stage.innerHTML = hudLine() + '<div class="story"><div class="over">Akt ' + a + ' · Neue Beweise</div><div class="evrow">' + A.evidence.slice(0, i + 1).map((e, j) => evHtml(e, j === i ? 'in' : '')).join('') + '</div></div><div class="okhint">OK = weiter</div>';
      sync(); await okWait(i < A.evidence.length - 1 ? 2400 : 4000); guard(my); }
  }

  async function run(my, mode){
    const C = CASES[selCase]; F = { list: [], i: 0 };
    G = { mode, c: C, act: 0, ev: [], dph: 'listen', all: players.slice(), cast: [], det: [], ph: { k: 'listen' } };
    G.mots = shuffle(DETECTIVE[C.id].motives);
    if (mode === 'team') G.rec = newRec(); else { G.duo = players.slice(0, 2); G.recs = {}; G.duo.forEach(p => G.recs[p.pid] = newRec()); }
    SND.amb('mood'); SND.play('start'); flash(); sync();
    for (let i = 0; i < C.intro.length; i++) { await tell(my, C.id + '_intro' + i, C.intro[i], () => storyScreen(C.tag, C.intro[i], i === 0 ? C.title : '')); if (i === 1) flash(); }
    // Statt Vorstellungsrunde: die Verdächtigen
    stage.innerHTML = hudLine() + '<div class="story fade"><div class="over">Die Verdächtigen</div><div class="sgrid" style="width:80vw">' + C.chars.map(c => '<div class="stile"><span class="e">' + c.emoji + '</span><span class="t"><b>' + esc(c.name) + '</b><small>' + esc(c.role) + '</small></span></div>').join('') + '</div>' +
      '<div class="txt mut" style="font-size:2.6vh">' + (mode === 'team' ? 'Ihr habt pro Akt ' + PER_ACT.team + ' Aktionen: Verdächtige befragen oder Beweise mit der Lupe untersuchen.' : 'Jeder hat pro Akt ' + PER_ACT.duel + ' Aktionen – heimlich auf dem eigenen Handy. Wer überführt den Täter besser?') + '</div></div><div class="okhint">OK = Ermittlung beginnen</div>';
    await okWait(15000); guard(my);
    for (let a = 1; a <= 3; a++) { const A = C.acts[a - 1]; G.act = a; G.dph = 'listen'; F.list = []; sync(); SND.amb(a === 3 ? 'tense' : 'mood');
      await tell(my, C.id + '_act' + a, A.text, () => storyScreen('Akt ' + a, A.text, A.title));
      await evidenceReveal(my, a, A);
      if (mode === 'team') G.rec.left += PER_ACT.team; else G.duo.forEach(p => { const r = G.recs[p.pid]; r.left += PER_ACT.duel; r.ready = false; });
      G.dph = 'invest'; F.i = 0; board(); SND.play('clue'); sync();
      await new Promise(res => { G.res = () => { G.res = null; okRes = null; res(); }; if (mode === 'duel') okRes = G.res; });
      guard(my);
    }
    // Anklage
    SND.amb('tense'); G.dph = 'accuse'; F.list = []; sync();
    if (mode === 'team') { G.rec.acc = await accuseTv(my); guard(my); }
    else { accWaitTv(); await new Promise(res => { G.res = () => { G.res = null; okRes = null; res(); }; okRes = G.res; }); guard(my); }
    await result(my);
  }

  async function result(my){
    const C = G.c, cul = C.chars.find(c => c.guilty); SND.amb(null); G.dph = 'reveal'; sync(); F.list = [];
    const recs = G.mode === 'team' ? [{ p: null, r: G.rec }] : G.duo.map(p => ({ p, r: G.recs[p.pid] }));
    recs.forEach(x => { x.r.score = scoreOf(x.r); if (x.p) score[x.p.pid] = (score[x.p.pid] || 0) + x.r.score.pts; });
    if (G.mode === 'duel') { const [a, b] = recs; G.win = a.r.score.pts === b.r.score.pts ? 0 : (a.r.score.pts > b.r.score.pts ? a.p.pid : b.p.pid); }
    stage.innerHTML = hudLine() + '<div class="story"><div class="over">Und der Täter ist …</div></div>'; SND.play('drum'); await sleep(2200); guard(my);
    SND.play('sting'); flash();
    stage.innerHTML = hudLine() + '<div class="story"><div class="culprit"><div class="em">' + cul.emoji + '</div><div class="big">' + esc(cul.name) + '</div><div class="mut" style="font-size:2.6vh">' + esc(DETECTIVE[C.id].motives[0]) + '</div></div></div><div class="okhint">OK = weiter</div>';
    await okWait(7000); guard(my);
    const line = (ok, label, val) => '<div class="rl2' + (ok ? ' ok' : '') + '"><span>' + (ok ? '✔' : '✘') + '</span><b>' + label + '</b><span class="mut">' + esc(val || '–') + '</span></div>';
    const cardOf = (x) => { const s = x.r.score, a = x.r.acc || {};
      return '<div class="rescard' + (G.win && x.p && G.win === x.p.pid ? ' win' : '') + '">' + (x.p ? '<div class="rh">' + x.p.emoji + ' ' + esc(x.p.name) + (G.win && G.win === x.p.pid ? ' 🏆' : '') + '</div>' : '') +
        line(s.cul, 'Täter', C.chars[a.c] ? C.chars[a.c].name : '') + line(s.mot, 'Motiv', a.m) + line(s.ev, 'Beweis', a.e) +
        '<div class="rl2"><span>⏱</span><b>Bonus</b><span class="mut">' + x.r.left + ' Aktionen übrig × ' + PTS.left + '</span></div>' +
        '<div class="rpts">' + s.pts + ' Punkte</div><div class="rrank">' + s.rank + '</div></div>'; };
    G.dph = 'result'; sync();
    stage.innerHTML = hudLine() + '<div class="story fade"><div class="over">' + (G.mode === 'team' ? 'Eure Ermittlung' : (G.win === 0 ? 'Unentschieden!' : 'Das Duell')) + '</div><div class="evrow">' + recs.map(cardOf).join('') + '</div></div><div class="okhint">OK = Auflösung</div>';
    const best = Math.max(...recs.map(x => x.r.score.pts)); setTimeout(() => SND.play(best >= 75 ? 'win' : 'lose'), 600);
    await okWait(25000); guard(my);
    for (let i = 0; i < C.solution.length; i++) { await tell(my, C.id + '_sol' + i, C.solution[i], () => storyScreen('Die Lösung', C.solution[i], i === 0 ? 'Was wirklich geschah' : '')); }
    stage.innerHTML = hudLine() + '<div class="story fade"><div class="over">Die entscheidenden Spuren</div><div class="keys">' + C.keys.map(k => '<div>' + esc(k) + '</div>').join('') + '</div></div><div class="okhint">OK = zurück zur Lobby</div>';
    solved.add(C.id); await okWait(20000); guard(my);
  }

  return { run, view, input, key };
})();
