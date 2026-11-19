// midnox Emby – „Mein midnox“ (Errungenschaften, Rangliste) und der Jahresrückblick als Story-Folien.
'use strict';
const fmtN = (n) => Math.round(n).toLocaleString('de-DE');
const wDur = (secs) => secs < 3600 ? fmtN(secs / 60) + ' Minuten' : fmtN(secs / 3600) + ' Stunden';
const WD = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
const fmtDay = (k) => { if (!k) return ''; const d = new Date(k + 'T12:00:00'); return d.toLocaleDateString('de-DE', { day: 'numeric', month: 'long' }); };
const medal = (a, l, small) => '<div class="medal lv' + l + (small ? ' sm' : '') + '"><span>' + a.icon + '</span></div>';

// ---------- Mein midnox ----------
ROUTER.reg('me', async (scr) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.className = 'page mepage';
  await STATS.check();
  const D = STATS.data; if (!D) { el.appendChild(h('div', 'empty', 'Statistik konnte nicht geladen werden.')); return; }
  const y = STATS.calc(D.year), all = STATS.calc(0);
  const rows = h('div', 'rows'); rows.setAttribute('data-vs', 'anchor'); rows.dataset.at = '6'; rows.setAttribute('data-mem', '');
  // Kopf
  const pts = Object.values(STATS.got).reduce((a, g) => a + g.lv, 0);
  const head = h('div', 'row mehead'); head.setAttribute('data-vsi', ''); head.setAttribute('data-mem', '');
  const hl = h('div', 'who'); hl.appendChild(avatar(EMBY.user.name, EMBY.userImg()));
  hl.appendChild(h('div', '', '<div class="kicker"><i></i>Mein midnox</div><h1>' + esc(EMBY.user.name) + '</h1><p>' + y.persona.e + ' ' + esc(y.persona.t) + ' · ' + Object.keys(STATS.got).length + ' Errungenschaften · ' + pts + ' Punkte</p>'));
  head.appendChild(hl);
  const tiles = h('div', 'tiles');
  for (const [n, l] of [[y.hours, 'Stunden ' + D.year], [y.movies, 'Filme'], [y.episodes, 'Folgen'], [y.streak, 'Tage am Stück'], [y.binge, 'Folgen an einem Tag']]) tiles.appendChild(h('div', 'tile', '<b>' + fmtN(n) + '</b><small>' + l + '</small>'));
  head.appendChild(tiles);
  const bs = h('div', 'btns');
  const wb = onOk(h('div', 'btn pri f wrapbtn', '✨ Dein Rückblick ' + D.year + (new Date().getMonth() < 11 ? ' (bisher)' : '')), () => ROUTER.go('wrapped', { year: D.year }));
  bs.appendChild(wb);
  if (new Date().getMonth() < 2 && D.myPrev) bs.appendChild(onOk(h('div', 'btn f', 'Rückblick ' + (D.year - 1)), () => ROUTER.go('wrapped', { year: D.year - 1 })));
  head.appendChild(bs); rows.appendChild(head);
  // Errungenschaften
  const ar = h('div', 'row'); ar.setAttribute('data-vsi', ''); ar.setAttribute('data-mem', '');
  ar.appendChild(h('h2', '', 'Errungenschaften <small>' + Object.keys(STATS.got).length + ' von ' + ACH.length + ' · Stufen Bronze, Silber, Gold, Platin</small>'));
  const grid = h('div', 'achgrid'); ar.appendChild(grid);
  const sorted = ACH.slice().sort((a, b) => ((STATS.got[b.id] || {}).lv || 0) - ((STATS.got[a.id] || {}).lv || 0));
  for (const a of sorted) {
    const l = (STATS.got[a.id] || {}).lv || 0, v = a.v(all) || 0;
    const nxt = a.lv[l] || null; const base = l ? a.lv[l - 1] : 0;
    const pct = nxt ? Math.max(3, Math.min(100, (v - base) / (nxt - base) * 100)) : 100;
    const c = h('div', 'ach f lv' + l, medal(a, l) + '<div class="tx"><b>' + esc(a.name) + '</b><small>' + (l ? LEVELS[l - 1] : 'Noch gesperrt') + '</small><div class="bar"><i style="width:' + pct + '%"></i></div><div class="nx">' + (nxt ? esc(a.d.replace('{n}', fmtN(v) + ' / ' + fmtN(nxt))) : 'Höchste Stufe erreicht! ' + esc(a.d.replace('{n}', fmtN(v)))) + '</div></div>');
    c._ok = () => UI.alert(a.icon + ' ' + a.name, a.lv.map((x, i) => (i < l ? '✅ ' : '◻️ ') + LEVELS[i] + ': ' + a.d.replace('{n}', fmtN(x))).join('\n') + '\n\nAktuell: ' + fmtN(v));
    grid.appendChild(c);
  }
  rows.appendChild(ar);
  // Rangliste
  if (COMM.ok) {
    try {
      const b = await COMM.call('board', null, '&year=' + D.year);
      if (b.list.length) {
        const lr = h('div', 'row'); lr.setAttribute('data-vsi', ''); lr.setAttribute('data-mem', '');
        lr.appendChild(h('h2', '', 'Rangliste der Runde <small>nach Errungenschafts-Punkten</small>'));
        const tr = h('div', 'track');
        b.list.slice(0, 12).forEach((p, i) => {
          const c = h('div', 'card rank f' + (p.me ? ' me' : ''));
          c.appendChild(h('div', 'pl', ['🥇', '🥈', '🥉'][i] || (i + 1) + '.'));
          c.appendChild(avatar(p.name)); c.appendChild(h('div', 'tx', '<b>' + esc(p.name) + '</b><small>' + p.pts + ' Punkte · ' + p.n + ' Abzeichen</small><small>' + (p.sum.hours != null ? fmtN(p.sum.hours) + ' Std. in ' + D.year : '') + '</small>'));
          tr.appendChild(c);
        });
        lr.appendChild(tr); rows.appendChild(lr);
      }
    } catch (e) {}
  }
  el.appendChild(rows);
  scr.initial = wb; NAV.focus(wb);
});

// ---------- Rückblick ----------
ROUTER.reg('wrapped', async (scr, arg) => {
  const year = (arg && arg.year) || new Date().getFullYear();
  const el = scr.el; el.className = 'page wrapped';
  document.body.classList.add('wrapmode');
  el.appendChild(h('div', 'wload', '<div class="spin"></div>Dein Jahr wird zusammengestellt …'));
  await STATS.load(true);
  const s = STATS.calc(year); const D = STATS.data;
  let board = null; if (COMM.ok) { try { board = await COMM.call('board', null, '&year=' + year); } catch (e) {} }
  el.innerHTML = '';
  const img = (it, t, w) => it ? (t === 'Backdrop' ? EMBY.backdrop(it) : EMBY.img(it, t || 'Primary', w || 400)) : '';
  const slides = [];
  const add = (cls, html, bg, after) => slides.push({ cls, html, bg, after });
  const count = (n, suf) => '<b class="cnt" data-n="' + n + '">0</b>' + (suf || '');
  const name = EMBY.user.name;

  add('s-intro', '<div class="big yr">' + year + '</div><h1>Dein Jahr bei <span>midnox</span>, ' + esc(name) + '.</h1><p>Lehn dich zurück – wir schauen, was du so geschaut hast.</p><div class="hint">▶ weiter · ◀ zurück</div>');
  if (s.hours || s.movies || s.episodes) {
    const days = (s.movieSecs + s.epSecs) / 86400;
    add('s-time', '<div class="kick">Deine Zeit vor dem Bildschirm</div><h1>Du hast ' + (s.hours >= 1 ? count(s.hours) + ' Stunden' : count(Math.round((s.movieSecs + s.epSecs) / 60)) + ' Minuten') + ' geschaut.</h1><p>Das sind ' + (days >= 1 ? 'gut ' + days.toFixed(1).replace('.', ',') + ' Tage am Stück' : fmtN((s.movieSecs + s.epSecs) / 60) + ' Minuten') + ((s.movieSecs + s.epSecs) >= 5400 ? ' – oder ' + fmtN((s.movieSecs + s.epSecs) / 5400) + ' Fußballspiele ohne Nachspielzeit' : '') + '.</p>'
      + '<div class="split"><div><b class="cnt" data-n="' + s.movies + '">0</b><small>Filme · ' + wDur(s.movieSecs) + '</small></div><div><b class="cnt" data-n="' + s.episodes + '">0</b><small>Folgen · ' + wDur(s.epSecs) + '</small></div>' + (s.musicH ? '<div><b class="cnt" data-n="' + s.musicH + '">0</b><small>Std. Musik</small></div>' : '') + '</div>');
  } else add('s-time', '<h1>Noch ganz <span>frisch</span> hier.</h1><p>Für ' + year + ' gibt es noch nicht viel zu erzählen. Schau ein paar Filme und Serien – dein Rückblick wächst mit.</p>');
  if (s.topSeries.length) {
    const t = s.topSeries[0];
    add('s-series', '<div class="kick">Deine Serie des Jahres</div><div class="hero2">' + (t.item && img(t.item, 'Logo') ? '<img class="logo" src="' + img(t.item, 'Logo', 700) + '" onerror="this.remove()">' : '') + '<h1>' + esc(t.name) + '</h1></div><p>' + t.n + ' Folgen, ' + wDur(t.secs) + '. Ihr zwei seid ein gutes Team.</p>'
      + (s.topSeries.length > 1 ? '<div class="podium">' + s.topSeries.slice(1, 5).map((x, i) => '<div><i style="background-image:url(\'' + img(x.item || { Id: x.id, ImageTags: { Primary: 1 } }, 'Primary', 220) + '\')"></i><b>' + (i + 2) + '. ' + esc(x.name) + '</b><small>' + x.n + ' Folgen</small></div>').join('') + '</div>' : ''), t.item ? img(t.item, 'Backdrop') : '');
  }
  if (s.topMovies.length) {
    add('s-movies', '<div class="kick">Großes Kino</div><h1>' + count(s.movies) + ' Filme ' + (s.movies === 1 ? 'hat' : 'haben') + ' es auf deinen Bildschirm geschafft.</h1>'
      + '<div class="posters">' + s.topMovies.slice(0, 6).map(m => '<i style="background-image:url(\'' + img(m, 'Primary', 300) + '\')"><b>' + esc(m.Name) + '</b></i>').join('') + '</div>'
      + (s.longest ? '<p>Dein längster: <b>' + esc(s.longest.Name) + '</b> mit ' + fmtN(s.longest.RunTimeTicks / 6e8) + ' Minuten' + (s.oldest && s.oldest.ProductionYear < 2000 ? ' · der älteste: <b>' + esc(s.oldest.Name) + '</b> (' + s.oldest.ProductionYear + ')' : '') + '.</p>' : ''), img(s.topMovies[0], 'Backdrop'));
  }
  if (s.genres.length) {
    const top = s.genres.slice(0, 5), max = top[0].n;
    add('s-genre', '<div class="kick">Dein Geschmack</div><h1>Am liebsten: <span>' + esc(top[0].g) + '</span></h1><div class="bars">' + top.map((g, i) => '<div class="br"><span>' + esc(g.g) + '</span><i style="--w:' + Math.max(6, g.n / max * 100) + '%;--d:' + i * .12 + 's"></i></div>').join('') + '</div><p>' + s.genreCount + ' Genres hast du ausprobiert.</p>');
  }
  if (s.hoursOf.some(Boolean)) {
    const mx = Math.max(...s.hoursOf), peak = s.hoursOf.indexOf(mx), wd = s.wdOf.indexOf(Math.max(...s.wdOf));
    const label = peak < 5 ? 'mitten in der Nacht' : peak < 11 ? 'morgens' : peak < 17 ? 'tagsüber' : peak < 22 ? 'zum Feierabend' : 'spät abends';
    add('s-clock', '<div class="kick">Wann du schaust</div><h1>Deine Lieblingszeit: <span>' + peak + ' Uhr</span></h1><p>Meistens ' + label + ', am liebsten am ' + WD[wd] + '.' + (s.night ? ' ' + s.night + '× warst du noch nach Mitternacht wach.' : '') + '</p><div class="clock">' + s.hoursOf.map((n, i) => '<i style="--h:' + (n / mx * 100) + '%;--d:' + i * .03 + 's" class="' + (i === peak ? 'pk' : '') + '"><em>' + (i % 6 === 0 ? i : '') + '</em></i>').join('') + '</div>');
  }
  if (s.binge > 1 || s.streak > 1) {
    add('s-binge', '<div class="kick">Durchhaltevermögen</div>' + (s.binge > 1 ? '<h1>' + count(s.binge) + ' Folgen an einem Tag.</h1><p>Am ' + fmtDay(s.bingeDay) + ' gab es kein Halten mehr. 🔥</p>' : '') + (s.streak > 1 ? '<div class="split"><div><b class="cnt" data-n="' + s.streak + '">0</b><small>Tage am Stück geschaut</small></div><div><b class="cnt" data-n="' + s.activeDays + '">0</b><small>Tage mit midnox</small></div></div>' : ''));
  }
  const comm = s.ratings + s.chat + s.partyHost + s.partyJoin + s.matches;
  if (comm) {
    add('s-comm', '<div class="kick">Du und die Runde</div><h1>Allein gucken ist <span>nur halb so schön.</span></h1><div class="grid4">'
      + [[s.ratings, 'Bewertungen', '⭐'], [s.chat, 'Chat-Nachrichten', '💬'], [s.partyHost + s.partyJoin, 'Watch-Partys', '🍿'], [s.matches, 'Matches ausgesucht', '💜']].map(x => '<div><span>' + x[2] + '</span><b class="cnt" data-n="' + x[0] + '">0</b><small>' + x[1] + '</small></div>').join('') + '</div>'
      + (s.bestRating && s.bestRating.name ? '<p>Deine Top-Bewertung: <b>' + esc(s.bestRating.name) + '</b> mit ' + '★'.repeat(Math.round(s.bestRating.s / 2)) + (s.bestRating.c ? ' – „' + esc(s.bestRating.c) + '“' : '') + '</p>' : ''));
  }
  add('s-persona', '<div class="kick">Dein Gucker-Typ ' + year + '</div><div class="pe">' + s.persona.e + '</div><h1><span>' + esc(s.persona.t) + '</span></h1><p>' + esc(s.persona.d) + '</p>');
  const gotY = Object.keys(STATS.got).filter(k => new Date(STATS.got[k].t * 1000).getFullYear() === year).map(k => ({ a: ACH.find(a => a.id === k), l: STATS.got[k].lv })).filter(x => x.a).sort((a, b) => b.l - a.l);
  if (gotY.length) add('s-ach', '<div class="kick">Errungenschaften</div><h1>' + count(gotY.length) + ' Abzeichen gesammelt.</h1><div class="medals">' + gotY.slice(0, 10).map(x => '<div>' + medal(x.a, x.l) + '<b>' + esc(x.a.name) + '</b><small>' + LEVELS[x.l - 1] + '</small></div>').join('') + '</div>');
  if (board && board.list.length > 1) {
    const L = board.list.filter(p => p.sum && p.sum.hours != null).sort((a, b) => b.sum.hours - a.sum.hours);
    const tot = L.reduce((a, p) => a + (+p.sum.hours || 0), 0);
    if (L.length > 1) add('s-group', '<div class="kick">Eure Runde ' + year + '</div><h1>Zusammen ' + count(tot) + ' Stunden.</h1><div class="lb">' + L.slice(0, 6).map((p, i) => '<div class="' + (p.me ? 'me' : '') + '"><em>' + (['🥇', '🥈', '🥉'][i] || i + 1) + '</em><b>' + esc(p.name) + '</b><small>' + esc(p.sum.persona || '') + (p.sum.topSeries ? ' · ' + esc(p.sum.topSeries) : '') + '</small><span>' + fmtN(p.sum.hours) + ' Std.</span></div>').join('') + '</div>');
  }
  add('s-outro', '<div class="big">🍿</div><h1>Danke fürs Gucken, ' + esc(name) + '!</h1><p>Auf ein neues Jahr mit großen Filmen, langen Serienabenden und vielen Matches.</p><div class="btns"><div class="btn f again">Nochmal ansehen</div><div class="btn pri f done">Fertig</div></div>');

  // --- Folien-Steuerung ---
  const prog = h('div', 'wprog', slides.map(() => '<i><b></b></i>').join(''));
  const bgA = h('div', 'wbg'), bgB = h('div', 'wbg');
  const stage = h('div', 'wstage');
  el.appendChild(bgA); el.appendChild(bgB); el.appendChild(h('div', 'wblobs', '<i></i><i></i><i></i>')); el.appendChild(stage); el.appendChild(prog);
  let i = -1, t = 0, front = bgA;
  const DUR = 9000;
  const show = (n) => {
    if (n < 0) n = 0; if (n >= slides.length) { done(); return; }
    i = n; const sl = slides[i];
    stage.innerHTML = ''; const d = h('div', 'wslide ' + sl.cls, sl.html); stage.appendChild(d);
    el.dataset.slide = sl.cls;
    const nb = front === bgA ? bgB : bgA; nb.style.backgroundImage = sl.bg ? 'url("' + sl.bg + '")' : ''; nb.classList.toggle('on', !!sl.bg); front.classList.remove('on'); front = nb;
    $$('.wprog i', el).forEach((p, k) => { p.className = k < i ? 'full' : k === i ? 'run' : ''; });
    // Zahlen hochzählen
    for (const c of $$('.cnt', d)) { const to = +c.dataset.n || 0; const t0 = performance.now(); const step = (now) => { const k = Math.min(1, (now - t0) / 1400); c.textContent = fmtN(to * (1 - Math.pow(1 - k, 3))); if (k < 1 && c.isConnected) requestAnimationFrame(step); }; requestAnimationFrame(step); }
    clearTimeout(t);
    const btns = $$('.btn', d);
    if (btns.length) { onOk(btns[0], () => show(0)); onOk(btns[1], () => done()); NAV.focus(btns[1]); }
    else { NAV.focus(stageFocus); t = setTimeout(() => show(i + 1), DUR); }
  };
  const stageFocus = h('div', 'f wfocus'); el.appendChild(stageFocus); stageFocus._ok = () => show(i + 1);
  const done = () => { clearTimeout(t); ROUTER.back(); };
  scr.key = (k) => {
    if (k === 'right' || k === 'ff') { if (i < slides.length - 1) show(i + 1); return true; }
    if (k === 'left' || k === 'rew') { if (NAV.cur && NAV.cur.classList.contains('again')) return false; show(i - 1); return true; }
    if (k === 'playpause' || k === 'pause') { if (t) { clearTimeout(t); t = 0; UI.toast('Angehalten – ▶ für weiter', { icon: '⏸' }); } else show(i); return true; }
    return false;
  };
  scr.leave = scr.destroy = () => { clearTimeout(t); document.body.classList.remove('wrapmode'); };
  scr.enter = () => document.body.classList.add('wrapmode');
  show(0);
});
