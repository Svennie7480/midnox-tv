// midnox Emby – „Was gucken wir heute?": alle wischen gleichzeitig auf dem Handy (und am TV), Treffer werden gefeiert.
'use strict';
const MATCH = {
  r: null, code: '', key: '', p: '', view: null, poll: 0, items: [], byId: {}, votes: {}, seen: new Set(), opts: null,
  call(a, body, q) { return COMM.call(a, body ? Object.assign({ c: MATCH.code, p: MATCH.p }, body) : null, '&c=' + MATCH.code + (q || '')); },
  phoneUrl() {
    let u = COMM.url() || ''; u = /\.php(\?.*)?$/i.test(u) ? u.replace(/[^/]*$/, '') : u.replace(/\/?$/, '/');
    return u + 'match.html?c=' + MATCH.code;
  },
  meta(it) {
    const min = it.RunTimeTicks ? Math.round(it.RunTimeTicks / 600000000) : 0;
    return { id: it.Id, name: it.Name, year: it.ProductionYear || 0, type: it.Type, ov: (it.Overview || '').slice(0, 600), genres: (it.Genres || []).slice(0, 3).join(' · '),
      rating: it.CommunityRating || 0, min, fsk: it.OfficialRating || '', tag: (it.ImageTags || {}).Primary || '' };
  },
  stopPoll() { clearTimeout(MATCH.poll); MATCH.poll = 0; },
};
window.MATCH = MATCH;

ROUTER.reg('match', async (scr) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.className = 'page mx'; el.style.paddingLeft = '0';
  const o = MATCH.opts = Object.assign({ kind: 'Movie', unplayed: true, genre: '', n: 30, tv: true }, STORE.get('match_opts', {}));
  let phase = 'setup', celebrateT = 0;
  const leave = () => MATCH.stopPoll();
  scr.leave = leave; scr.destroy = leave;
  scr.enter = () => { BG.dim(phase !== 'vote'); if (phase !== 'setup') tick(); };

  // ---------- 1. Einrichtung ----------
  const setup = () => {
    phase = 'setup'; MATCH.stopPoll(); BG.set(null); BG.dim(true);
    el.innerHTML = '';
    const w = h('div', 'mx-setup');
    w.appendChild(h('div', 'mx-hero', '<div class="kicker"><i></i>Gemeinsam aussuchen</div><h1>Was gucken wir <span>heute?</span></h1><p>Alle scannen den QR-Code und wischen auf dem Handy durch Vorschläge aus eurer Bibliothek – nach rechts heißt „Ja!“. Wollen alle denselben Titel sehen, gibt es ein Match.</p>'));
    const form = h('div', 'mx-form'); form.setAttribute('data-mem', '');
    const line = (label, opts, key) => {
      const r = h('div', 'mx-line'); r.appendChild(h('label', '', label)); const cs = h('div', 'chips'); r.appendChild(cs);
      for (const [v, t] of opts) { const c = onOk(h('div', 'chip f' + (o[key] === v ? ' sel' : ''), t), () => { o[key] = v; for (const x of cs.children) x.classList.toggle('sel', x === c); STORE.set('match_opts', o); }); cs.appendChild(c); }
      form.appendChild(r); return cs;
    };
    line('Was?', [['Movie', 'Filme'], ['Series', 'Serien'], ['Movie,Series', 'Beides']], 'kind');
    line('Auswahl', [[true, 'Nur Ungesehenes'], [false, 'Alles']], 'unplayed');
    const gr = h('div', 'mx-line'); gr.appendChild(h('label', '', 'Genre')); const gcs = h('div', 'chips'); gr.appendChild(gcs);
    const gchip = onOk(h('div', 'chip f sel', esc(o.genre || 'Alle Genres') + ' ▾'), async () => {
      try {
        const views = EMBY.viewsOf('movies').concat(EMBY.viewsOf('tvshows'));
        const seen = new Set(); let list = [];
        for (const v of views) for (const g of ((await EMBY.genres(v.Id)).Items || [])) if (!seen.has(g.Name)) { seen.add(g.Name); list.push(g.Name); }
        list.sort((a, b) => a.localeCompare(b, 'de'));
        const v = await UI.choose('Genre', [{ label: 'Alle Genres', value: '', sel: !o.genre }].concat(list.map(n => ({ label: n, value: n, sel: n === o.genre }))));
        if (v != null) { o.genre = v; gchip.innerHTML = esc(v || 'Alle Genres') + ' ▾'; STORE.set('match_opts', o); }
      } catch (e) {}
    });
    gcs.appendChild(gchip); form.appendChild(gr);
    line('Wie viele?', [[20, '20 Titel'], [30, '30 Titel'], [45, '45 Titel']], 'n');
    line('Fernseher', [[true, 'Ich wische hier mit'], [false, 'Nur Handys']], 'tv');
    w.appendChild(form);
    const go = onOk(h('div', 'btn pri mx-go f', ic('play') + 'Runde starten'), () => start());
    w.appendChild(go); el.appendChild(w);
    scr.initial = go; NAV.focus(go);
  };

  // ---------- 2. Runde anlegen ----------
  const start = async () => {
    if (!COMM.ok) { UI.alert('Community nicht erreichbar', COMM.err || 'Für „Was gucken wir heute?" wird der Community-Server gebraucht.'); return; }
    UI.toast('Suche Vorschläge …', { icon: '🎲' });
    try {
      const q = { Recursive: true, IncludeItemTypes: o.kind, SortBy: 'Random', Limit: o.n, Fields: FIELDS, ImageTypes: 'Primary', ExcludeItemTypes: 'BoxSet' };
      if (o.unplayed) q.Filters = 'IsUnplayed';
      if (o.genre) q.Genres = o.genre;
      let r = await EMBY.items(q);
      let items = r.Items || [];
      if (items.length < 6 && o.unplayed) { delete q.Filters; items = ((await EMBY.items(q)).Items || []); }
      if (items.length < 3) { UI.alert('Zu wenig Auswahl', 'Mit diesen Einstellungen finde ich nicht genug Titel. Nimm „Alles“ oder ein anderes Genre.'); return; }
      MATCH.items = items; MATCH.byId = {}; for (const it of items) MATCH.byId[it.Id] = it;
      const res = await COMM.call('match_new', { items: items.map(MATCH.meta), tv: o.tv, tvName: EMBY.user.name + ' (TV)' });
      MATCH.code = res.code; MATCH.key = res.key; MATCH.p = res.p || ''; MATCH.votes = {}; MATCH.seen = new Set(); MATCH.view = res.view;
      lobby();
    } catch (e) { UI.alert('Was gucken wir heute?', e.message); }
  };

  // ---------- 3. Lobby mit QR-Code ----------
  const lobby = () => {
    phase = 'lobby'; el.innerHTML = '';
    const url = MATCH.phoneUrl();
    const w = h('div', 'mx-lobby');
    const qr = h('div', 'mx-qr', '<div class="code">' + UI.qr(url) + '</div><div class="cd">' + esc(MATCH.code.split('').join(' ')) + '</div><small>' + esc(url.replace(/^https?:\/\//, '').replace(/\?c=.*/, '')) + '</small>');
    const side = h('div', 'mx-side');
    side.appendChild(h('div', '', '<div class="kicker"><i></i>Runde ' + esc(MATCH.code) + '</div><h1>Handys <span>raus!</span></h1><p>QR-Code scannen oder Code eingeben, Namen tippen – fertig. Wer von unterwegs mitmacht, braucht nur den Code.</p>'));
    side.appendChild(h('div', 'mx-players'));
    const bs = h('div', 'btns'); bs.setAttribute('data-mem', '');
    const go = onOk(h('div', 'btn pri f', ic('play') + 'Los geht\'s'), async () => { try { await MATCH.call('m_ctl', { key: MATCH.key, state: 'vote' }); vote(); } catch (e) { UI.toast(e.message); } });
    const back = onOk(h('div', 'btn f', 'Einstellungen'), () => setup());
    bs.appendChild(go); bs.appendChild(back); side.appendChild(bs);
    w.appendChild(qr); w.appendChild(side); el.appendChild(w);
    scr.initial = go; NAV.focus(go);
    renderPlayers(); tick();
  };
  const renderPlayers = () => {
    const v = MATCH.view; if (!v) return;
    for (const box of $$('.mx-players', el)) {
      box.innerHTML = '';
      if (!v.players.length) { box.appendChild(h('div', 'mx-empty', 'Noch niemand da …')); continue; }
      for (const p of v.players) {
        const row = h('div', 'mx-pl' + (p.on ? '' : ' off')); row.appendChild(p.tv ? h('div', 'av tvav', '📺') : avatar(p.name));
        const pct = Math.round(p.n / Math.max(1, v.total) * 100);
        row.appendChild(h('div', 'nm', '<b>' + esc(p.name) + '</b>' + (v.state === 'lobby' ? '<small>ist dabei</small>' : '<div class="bar"><i style="width:' + pct + '%"></i></div>')));
        if (v.state !== 'lobby') row.appendChild(h('div', 'ct', p.n + '/' + v.total));
        box.appendChild(row);
      }
    }
  };

  // ---------- Stand abholen ----------
  const tick = async () => {
    MATCH.stopPoll(); if (ROUTER.top() !== scr || phase === 'setup') return;
    try {
      const v = await MATCH.call('m_state', null, MATCH.p ? '&p=' + MATCH.p : '');
      onView(v);
    } catch (e) { if (/vorbei|gibt es nicht/.test(e.message || '')) { UI.toast('Die Runde ist abgelaufen.'); setup(); return; } }
    if (ROUTER.top() === scr && phase !== 'setup') MATCH.poll = setTimeout(tick, 1500);
  };
  const onView = (v) => {
    const prev = MATCH.view; MATCH.view = v;
    const fresh = v.matches.filter(id => !MATCH.seen.has(id)); v.matches.forEach(id => MATCH.seen.add(id));
    if (prev && prev.state === 'vote' && fresh.length && v.players.length > 1) celebrate(fresh[fresh.length - 1]);
    if (v.state === 'done' && phase !== 'result') { result(); return; }
    if (v.state === 'vote' && phase === 'lobby') { vote(); return; }
    renderPlayers(); if (phase === 'vote' || phase === 'watch') renderHits();
  };

  // ---------- 4. Abstimmen ----------
  const left = () => MATCH.items.filter(it => !(it.Id in MATCH.votes));
  const vote = () => {
    phase = MATCH.p && left().length ? 'vote' : 'watch'; el.innerHTML = '';
    const w = h('div', 'mx-vote');
    const main = h('div', 'mx-main'); const side = h('div', 'mx-side2');
    side.appendChild(h('h3', '', 'Wer wischt')); side.appendChild(h('div', 'mx-players'));
    side.appendChild(h('h3', '', 'Treffer')); side.appendChild(h('div', 'mx-hits'));
    w.appendChild(main); w.appendChild(side); el.appendChild(w);
    if (phase === 'vote') showCard(main); else watchView(main);
    renderPlayers(); renderHits(); tick();
  };
  const showCard = (main) => {
    const it = left()[0]; if (!it) { phase = 'watch'; watchView(main); return; }
    BG.set(EMBY.backdrop(it) || EMBY.img(it, 'Primary', 1200)); BG.dim(false);
    const done = MATCH.items.length - left().length;
    main.innerHTML = '';
    const card = h('div', 'mx-card');
    const p = h('div', 'pst'); const img = EMBY.img(it, 'Primary', 500); if (img) p.style.backgroundImage = 'url("' + img + '")'; card.appendChild(p);
    const tx = h('div', 'tx');
    tx.innerHTML = '<div class="kicker"><i></i>' + (it.Type === 'Series' ? 'Serie' : 'Film') + ' · ' + (done + 1) + ' von ' + MATCH.items.length + '</div><h1>' + esc(it.Name) + '</h1>' + V.meta(it, {}) + (it.Genres && it.Genres.length ? '<div class="genres">' + esc(it.Genres.slice(0, 3).join(' · ')) + '</div>' : '') + (it.Overview ? '<div class="ov">' + esc(it.Overview) + '</div>' : '');
    card.appendChild(tx); main.appendChild(card);
    const bs = h('div', 'mx-yn'); bs.setAttribute('data-mem', '');
    const no = onOk(h('div', 'btn f mx-no', '<b>✕</b>Nö'), () => cast(0));
    const yes = onOk(h('div', 'btn pri f mx-yes', '<b>♥</b>Ja!'), () => cast(1));
    const skip = onOk(h('div', 'btn f', 'Ergebnis zeigen'), () => finish());
    bs.appendChild(no); bs.appendChild(yes); bs.appendChild(skip); main.appendChild(bs);
    main.appendChild(h('div', 'mx-hint', '◀ Nö  ·  Ja! ▶  ·  oder einfach auf dem Handy wischen'));
    NAV.focus(yes);
  };
  const cast = (v) => {
    const it = left()[0]; if (!it) return;
    MATCH.votes[it.Id] = v;
    const card = $('.mx-card', el); if (card) card.classList.add(v ? 'goyes' : 'gono');
    MATCH.call('m_vote', { id: it.Id, v }).then(onView).catch(() => {});
    setTimeout(() => { const main = $('.mx-main', el); if (!main) return; if (left().length) showCard(main); else { phase = 'watch'; watchView(main); } }, 260);
  };
  scr.key = (k) => {
    if (phase === 'vote' && (k === 'left' || k === 'right') && NAV.cur && NAV.cur.closest('.mx-yn') && (NAV.cur.classList.contains('mx-no') || NAV.cur.classList.contains('mx-yes'))) { cast(k === 'right' ? 1 : 0); return true; }
    if (k === 'back' && phase !== 'setup') {
      UI.choose('Runde verlassen?', [{ label: 'Weiter abstimmen', value: 0 }, { label: 'Ergebnis zeigen', value: 2 }, { label: 'Runde beenden', value: 1 }]).then(v => { if (v === 1) { MATCH.call('m_ctl', { key: MATCH.key, state: 'done' }).catch(() => {}); MATCH.stopPoll(); ROUTER.back(); } if (v === 2) finish(); });
      return true;
    }
    return false;
  };
  const watchView = (main) => {
    BG.set(null); BG.dim(true); main.innerHTML = '';
    main.appendChild(h('div', 'mx-watch', '<div class="pulse">🍿</div><h1>' + (MATCH.p ? 'Du bist <span>durch!</span>' : 'Die Handys <span>wischen …</span>') + '</h1><p>Neue Treffer erscheinen rechts. Wenn alle fertig sind – oder ihr keine Lust mehr habt – zeig das Ergebnis.</p>'));
    const bs = h('div', 'btns'); const b = onOk(h('div', 'btn pri f', ic('star') + 'Ergebnis zeigen'), () => finish()); bs.appendChild(b); main.appendChild(bs); NAV.focus(b);
  };
  const renderHits = () => {
    const box = $('.mx-hits', el); const v = MATCH.view; if (!box || !v) return;
    const np = v.players.length;
    const top = Object.keys(v.likes).map(id => ({ id, n: v.likes[id] })).filter(x => MATCH.byId[x.id]).sort((a, b) => b.n - a.n).slice(0, 6);
    box.innerHTML = top.length ? '' : '<div class="mx-empty">Noch keine Stimmen</div>';
    for (const x of top) { const it = MATCH.byId[x.id]; const r = h('div', 'mx-hit' + (x.n >= np ? ' all' : ''), '<div class="p" style="background-image:url(\'' + EMBY.img(it, 'Primary', 120) + '\')"></div><div><b>' + esc(it.Name) + '</b><small>' + (x.n >= np ? '💜 alle wollen' : x.n + ' von ' + np) + '</small></div>'); box.appendChild(r); }
    // Wenn alle fertig sind: automatisch zum Ergebnis
    if (v.state === 'vote' && np && v.players.every(p => p.n >= v.total) && phase !== 'result') finish();
  };
  const celebrate = (id) => {
    const it = MATCH.byId[id]; if (!it) return; clearTimeout(celebrateT);
    let ov = $('#mxparty'); if (ov) ov.remove();
    ov = h('div', '', '<div class="p" style="background-image:url(\'' + EMBY.img(it, 'Primary', 500) + '\')"></div><h2>Match!</h2><div class="who">Alle ' + MATCH.view.players.length + ' wollen „' + esc(it.Name) + '“ sehen</div>');
    ov.id = 'mxparty';
    const cols = ['var(--c1)', 'var(--c2)', 'var(--c3)', '#facc15', '#f43f5e', '#10b981'];
    for (let i = 0; i < 80; i++) { const d = h('i', 'conf'); d.style.left = Math.random() * 100 + '%'; d.style.background = cols[i % cols.length]; d.style.animationDuration = 1.8 + Math.random() * 2 + 's'; d.style.animationDelay = Math.random() * .5 + 's'; ov.appendChild(d); }
    document.body.appendChild(ov);
    celebrateT = setTimeout(() => ov.remove(), 4200);
  };

  // ---------- 5. Ergebnis ----------
  const finish = async () => { try { onView(await MATCH.call('m_ctl', { key: MATCH.key, state: 'done' })); } catch (e) { result(); } };
  const result = () => {
    phase = 'result'; MATCH.stopPoll(); BG.dim(true);
    const v = MATCH.view; const np = Math.max(1, v.players.length);
    const ranked = Object.keys(v.likes).map(id => ({ id, n: v.likes[id] })).filter(x => MATCH.byId[x.id]).sort((a, b) => b.n - a.n);
    const hits = ranked.filter(x => x.n >= np), near = ranked.filter(x => x.n < np).slice(0, 12);
    el.innerHTML = '';
    const w = h('div', 'mx-result'); const rows = h('div', 'rows'); rows.setAttribute('data-vs', 'anchor'); rows.dataset.at = '19'; rows.setAttribute('data-mem', '');
    const head = h('div', 'mx-rhead', '<div class="kicker"><i></i>Runde ' + esc(MATCH.code) + ' · ' + np + ' Leute</div><h1>' + (hits.length ? (hits.length === 1 ? 'Ihr habt <span>ein Match!</span>' : 'Ihr habt <span>' + hits.length + ' Matches!</span>') : 'Kein <span>Volltreffer</span>') + '</h1><p>' + (hits.length ? 'Wählt einen Titel – als Watch-Party läuft er auf allen Geräten gleichzeitig.' : 'Kein Titel hat alle überzeugt. Hier sind eure Favoriten.') + '</p>');
    w.appendChild(head);
    const mkRow = (title, list, cls) => {
      const r = h('div', 'row'); r.setAttribute('data-mem', ''); r.setAttribute('data-vsi', ''); r.appendChild(h('h2', '', title)); const tr = h('div', 'track'); r.appendChild(tr);
      for (const x of list) {
        const it = MATCH.byId[x.id]; const c = V.card(it, 'poster', {}); c.classList.add(cls);
        c.appendChild(h('div', 'mx-badge', x.n >= np ? '💜 ' + x.n + '/' + np : x.n + '/' + np));
        c._ok = () => pick(it); c._menu = null; tr.appendChild(c);
      }
      rows.appendChild(r);
    };
    if (hits.length) mkRow('Das wollt ihr alle sehen', hits, 'hit');
    if (near.length) mkRow(hits.length ? 'Knapp dahinter' : 'Eure Favoriten', near, 'near');
    const br = h('div', 'row'); br.setAttribute('data-vsi', ''); const bs = h('div', 'btns'); bs.setAttribute('data-mem', '');
    bs.appendChild(onOk(h('div', 'btn f', ic('refresh') + 'Neue Runde'), () => setup()));
    bs.appendChild(onOk(h('div', 'btn f', 'Fertig'), () => ROUTER.back()));
    br.appendChild(bs); rows.appendChild(br);
    w.appendChild(rows); el.appendChild(w);
    const first = $('.card', rows) || $('.btn', rows); NAV.focus(first);
    if (hits.length) { const it = MATCH.byId[hits[0].id]; BG.set(EMBY.backdrop(it)); BG.dim(false); }
  };
  const pick = async (it) => {
    const v = await UI.choose(it.Name, [{ label: '🍿 Als Watch-Party für alle starten', value: 'party' }, { label: '▶ Hier abspielen', value: 'play' }, { label: 'Details ansehen', value: 'info' }]);
    if (!v) return;
    if (v === 'info') { APP.open(it); return; }
    let full = it; try { full = await EMBY.item(it.Id); } catch (e) {}
    if (full.Type === 'Series') { try { const nu = (await EMBY.nextUp(full.Id)).Items || []; let ep = nu[0]; if (!ep) { const eps = await EMBY.get('/Shows/' + full.Id + '/Episodes', { UserId: EMBY.user.id, Limit: 1, Fields: FIELDS }); ep = (eps.Items || [])[0]; } if (ep) full = await EMBY.item(ep.Id); } catch (e) {} }
    if (v === 'party') PARTY.create(full, ((full.UserData || {}).PlaybackPositionTicks || 0) / 10000);
    else APP.play(full);
  };

  setup();
});
