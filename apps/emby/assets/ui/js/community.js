// midnox Emby – Community: Bewertungen, Empfehlungs-Chat, Admin-Nachrichten, Watch-Party.
'use strict';
const STAR_WORDS = ['', 'Grauenhaft', 'Sehr schwach', 'Schwach', 'Mau', 'Geht so', 'Ganz okay', 'Gut', 'Sehr gut', 'Großartig', 'Meisterwerk'];
const QUICK = ['Muss man gesehen haben!', 'Gute Unterhaltung', 'Spannend bis zum Schluss', 'Zum Abschalten', 'Eher nichts für mich'];
const EMOJI = ['😂', '😱', '❤️', '👏', '🔥', '🍿', '😭', '🤯', '😴'];

const COMM = {
  me: null, ok: false, err: '', cache: {}, cacheAt: 0, feedT: 0, lastFeed: null, invite: null,
  url() { return EMBY.settings().community; },
  hdr() { return { 'X-Emby-Token': EMBY.token, 'X-Emby-User': EMBY.user.id, 'X-Emby-Device': DEVICE.deviceId, 'Content-Type': 'application/json' }; },
  call(a, body, q) { return NET.json(body ? 'POST' : 'GET', COMM.url() + '?a=' + a + (q || ''), COMM.hdr(), body || null, 15000); },
  async start() {
    clearTimeout(COMM.feedT);
    try { const r = await COMM.call('hello'); COMM.me = r.me; COMM.ok = true; COMM.err = ''; }
    catch (e) { COMM.ok = false; COMM.err = e.message; console.warn('Community', e.message); COMM.feedT = setTimeout(COMM.start, 60000); return; }
    COMM.feed();
  },
  async feed() {
    clearTimeout(COMM.feedT);
    try {
      const w = PLAYER.active && PLAYER.item && EMBY.settings().showWatching ? '&w=' + encodeURIComponent(PLAYER.item.Type === 'Episode' ? (PLAYER.item.SeriesName || '') : PLAYER.item.Name) : (EMBY.settings().showWatching ? '&w=' : '');
      const f = await COMM.call('feed', null, w); COMM.lastFeed = f; COMM.ok = true;
      COMM.handleNews(f.news || []);
      const seen = STORE.get('chatSeen', 0);
      const unread = f.chatLast > seen && f.chatLastFrom !== EMBY.user.id ? 1 : 0;
      RAIL.badge('community', ROUTER.top() && ROUTER.top().name === 'community' ? 0 : unread);
      COMM.handleParties(f.parties || []);
    } catch (e) { COMM.ok = false; COMM.err = e.message; }
    COMM.feedT = setTimeout(COMM.feed, 20000);
  },
  handleNews(list) {
    const seen = STORE.get('newsSeen', []);
    for (const n of list) {
      if (seen.indexOf(n.id) >= 0) continue;
      seen.push(n.id);
      if (n.kind === 'popup') UI.modal((box, close) => {
        box.appendChild(h('div', '', '<div style="font-size:1.05rem;letter-spacing:.2em;text-transform:uppercase;color:var(--gold);font-weight:800">📣 Nachricht von ' + esc(n.from) + '</div>'));
        box.appendChild(h('h2', '', esc(n.text))); box.lastChild.style.marginTop = '1rem';
        const b = h('div', 'btns'); b.appendChild(onOk(h('div', 'btn pri', 'Verstanden'), () => close(true))); box.appendChild(b);
      });
      else UI.toast(n.text, { title: 'Nachricht von ' + n.from, icon: '📣', cls: 'admin', ms: 12000 });
    }
    STORE.set('newsSeen', seen.slice(-50));
  },
  handleParties(list) {
    const known = COMM._parties || {}; const now = {};
    for (const p of list) {
      now[p.id] = 1;
      if (!known[p.id] && COMM._partiesInit && p.hostId !== EMBY.user.id && !(PARTY.p && PARTY.p.id === p.id)) {
        COMM.invite = { p, until: Date.now() + 20000 };
        UI.toast((p.meta && p.meta.name) || 'Film', { title: p.host + ' startet eine Watch-Party', icon: '🍿', hint: 'MENU-Taste: jetzt beitreten', ms: 20000 });
      }
    }
    COMM._parties = now; COMM._partiesInit = true; COMM.parties = list;
  },
  menuAction() {
    if (COMM.invite && COMM.invite.until > Date.now()) { const p = COMM.invite.p; COMM.invite = null; PARTY.join(p); return true; }
    return false;
  },

  // ---------- Bewertungen ----------
  async ratings(ids) {
    if (!COMM.ok || !ids.length) return {};
    if (Date.now() - COMM.cacheAt > 90000) { COMM.cache = {}; COMM.cacheAt = Date.now(); }
    const need = ids.filter(id => !(id in COMM.cache));
    for (let i = 0; i < need.length; i += 150) {
      try { const r = await COMM.call('ratings', null, '&ids=' + need.slice(i, i + 150).join(',')); for (const id of need.slice(i, i + 150)) COMM.cache[id] = (r.r || {})[id] || null; } catch (e) { break; }
    }
    const out = {}; for (const id of ids) if (COMM.cache[id]) out[id] = COMM.cache[id]; return out;
  },
  /** Community-Sterne an Karten heften. */
  async decorate(root) {
    if (!COMM.ok) return;
    const cards = $$('.card[data-rid]', root); if (!cards.length) return;
    const r = await COMM.ratings(Array.from(new Set(cards.map(c => c.dataset.rid))));
    for (const c of cards) { const x = r[c.dataset.rid]; let b = $('.mxs', c); if (!x) { if (b) b.remove(); continue; } if (!b) { b = h('div', 'mxs'); c.appendChild(b); } b.innerHTML = '★ <i>' + x.avg.toFixed(1) + '</i>'; }
  },
  meta(item) { return { name: item.Name, type: item.Type, year: item.ProductionYear || 0, series: item.SeriesName || '' }; },
  async rate(item, stars, comment) {
    const r = await COMM.call('rate', { item: item.Id, stars, comment: comment || '', meta: COMM.meta(item) });
    COMM.cache[item.Id] = r.r; return r.r;
  },
  starsHtml(v, max) { max = max || 10; const full = Math.round(v / 2 * 2) / 2; let s = ''; for (let i = 1; i <= 5; i++) s += i <= v / 2 ? '★' : (i - .5 <= v / 2 ? '⯪' : '☆'); return s; },
  /** Der Bewertungsdialog: ←/→ ändert die Sterne (1–10), OK speichert. */
  ratePrompt(item, opts) {
    opts = opts || {};
    if (!COMM.ok) { UI.alert('Community nicht erreichbar', COMM.err || 'Der Community-Server antwortet gerade nicht.'); return Promise.resolve(null); }
    return COMM.ratings([item.Id]).then(r => {
      const cur = r[item.Id]; let v = (cur && cur.mine) || opts.initial || 7; let comment = (cur && cur.myComment) || '';
      return UI.modal((box, close, m) => {
        box.style.minWidth = '64rem';
        box.appendChild(h('div', '', '<div style="font-size:1.05rem;letter-spacing:.2em;text-transform:uppercase;color:var(--mut);font-weight:800">' + (opts.after ? 'Fertig geschaut' : 'Bewerten') + '</div>'));
        box.appendChild(h('h2', '', esc(opts.after ? 'Wie fandest du „' + item.Name + '“?' : item.Name)));
        const big = h('div', '', ''); big.style.cssText = 'display:flex;align-items:flex-end;gap:2rem;margin-top:1.8rem';
        const num = h('div', 'rate-big grad-text'); const word = h('div', 'rate-word');
        const right = h('div'); const stars = h('div', 'rate-stars'); stars.style.margin = '0';
        right.appendChild(stars); right.appendChild(word); big.appendChild(num); big.appendChild(right); box.appendChild(big);
        const draw = () => {
          num.textContent = v; word.textContent = STAR_WORDS[v];
          stars.innerHTML = '';
          for (let i = 1; i <= 5; i++) { const f = v >= i * 2 ? 1 : v >= i * 2 - 1 ? .5 : 0; stars.appendChild(h('div', 's', '<svg viewBox="0 0 24 24"><defs><linearGradient id="g' + i + '"><stop offset="' + (f * 100) + '%" stop-color="#facc15"/><stop offset="' + (f * 100) + '%" stop-color="rgba(255,255,255,.15)"/></linearGradient></defs><path d="m12 2.5 2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.3l-5.9 3.2 1.2-6.5L2.5 9.4l6.6-.9z" fill="url(#g' + i + ')"/></svg>')); }
        };
        draw();
        const hint = h('p', '', '◀ ▶ zum Einstellen' + (cur ? ' · Community: ' + cur.avg.toFixed(1) + ' (' + cur.n + ' Stimme' + (cur.n === 1 ? '' : 'n') + ')' : '')); box.appendChild(hint);
        const chips = h('div', 'btns'); chips.style.marginTop = '1.4rem';
        const cm = h('p', ''); cm.style.minHeight = '2rem';
        const showC = () => { cm.textContent = comment ? '„' + comment + '“' : ''; };
        showC();
        for (const q of QUICK) chips.appendChild(onOk(h('div', 'chip' + (comment === q ? ' sel' : ''), esc(q)), function () { comment = comment === q ? '' : q; $$('.chip', chips).forEach(c => c.classList.toggle('sel', c.textContent === comment)); showC(); }));
        chips.appendChild(onOk(h('div', 'chip', ic('keyboard', ' style="width:1.5rem;height:1.5rem"') + 'Eigener Satz'), async () => { const t = await TEXT.ask('Dein Kommentar', { value: comment, placeholder: 'Kurz und knackig …' }); if (t != null) { comment = t; showC(); } }));
        box.appendChild(chips); box.appendChild(cm);
        const b = h('div', 'btns');
        const save = onOk(h('div', 'btn pri', ic('star') + 'Bewertung speichern'), async () => {
          try { const res = await COMM.rate(item, v, comment); close(res); UI.toast(item.Name + ': ' + v + '/10 – danke!', { icon: '⭐' }); }
          catch (e) { UI.toast(e.message, { icon: '⚠️' }); }
        });
        b.appendChild(save);
        if (cur && cur.mine) b.appendChild(onOk(h('div', 'btn', ic('trash') + 'Löschen'), async () => { try { await COMM.rate(item, 0); close(null); UI.toast('Bewertung gelöscht'); } catch (e) {} }));
        b.appendChild(onOk(h('div', 'btn', opts.after ? 'Später' : 'Abbrechen'), () => close(null)));
        box.appendChild(b); m._save = save;
        m._key = (k) => {
          if ((k === 'left' || k === 'right') && (NAV.cur === save || !box.contains(NAV.cur) || NAV.cur.closest('.btns') === b && NAV.cur === save)) { v = Math.max(1, Math.min(10, v + (k === 'right' ? 1 : -1))); draw(); return true; }
          return false;
        };
      }, { initial: (box) => box.parentElement._save });
    });
  },
  async afterWatch(item) {
    if (!COMM.ok) return;
    let target = item;
    if (item.Type === 'Episode') { if (PLAYER.nextItem) return; try { target = await EMBY.item(item.SeriesId); } catch (e) { return; } }
    const r = await COMM.ratings([target.Id]); if (r[target.Id] && r[target.Id].mine) return;
    COMM.ratePrompt(target, { after: true });
  },
  async recommend(item) {
    if (!COMM.ok) { UI.alert('Community nicht erreichbar', COMM.err); return; }
    const t = await TEXT.ask('„' + item.Name + '“ empfehlen', { sub: 'Erscheint im Community-Chat bei allen. Ein Satz dazu?', placeholder: 'Optional – z. B. „Unbedingt anschauen!“', allowEmpty: true, okLabel: 'Empfehlen' });
    if (t == null) return;
    try { await COMM.call('say', { text: t, item: Object.assign({ id: item.Id }, COMM.meta(item)) }); UI.toast('Empfehlung geteilt', { icon: '💬' }); } catch (e) { UI.toast(e.message, { icon: '⚠️' }); }
  },
};

// ---------- Watch-Party ----------
const PARTY = {
  p: null, timer: 0, since: 0, offset: null, rtt: 9999, coolUntil: 0, started: false, readySent: false, lastVer: 0,
  isHost() { return !!(PARTY.p && PARTY.p.isHost); },
  serverNow() { return Date.now() + (PARTY.offset || 0); },
  async create(item, startMs) {
    if (!COMM.ok) { UI.alert('Community nicht erreichbar', COMM.err || 'Für die Watch-Party wird der Community-Server gebraucht.'); return; }
    try {
      const r = await COMM.call('party_new', { item: item.Id, meta: COMM.meta(item), pos: (startMs || 0) / 1000 });
      PARTY.begin(r.party);
      await PLAYER.open(item, { startMs: startMs || 0, paused: true, party: true });
      PARTY.renderLobby();
    } catch (e) { UI.alert('Watch-Party', e.message); }
  },
  async startFromPlayer() {
    if (!COMM.ok) { UI.alert('Community nicht erreichbar', COMM.err); return; }
    const ok = await UI.confirm('Watch-Party starten?', 'Alle midnox-Nutzer bekommen eine Einladung und schauen ab hier synchron mit. Du steuerst Pause und Spulen für alle.', 'Starten');
    if (!ok) return;
    try {
      ADAPTER.pause();
      const r = await COMM.call('party_new', { item: PLAYER.item.Id, meta: COMM.meta(PLAYER.item), pos: PLAYER.pos / 1000 });
      PARTY.begin(r.party); PLAYER.party = true; PLAYER.renderControls(); PARTY.renderLobby();
    } catch (e) { UI.alert('Watch-Party', e.message); }
  },
  async join(p) {
    try {
      const r = await COMM.call('party_join', { id: p.id });
      PARTY.begin(r.party);
      const item = await EMBY.item(p.item);
      const st = r.party.state;
      await PLAYER.open(item, { startMs: Math.max(0, st.pos * 1000), paused: true, party: true });
      PARTY.renderLobby();
    } catch (e) { UI.alert('Watch-Party', e.status === 404 ? 'Diese Watch-Party ist schon vorbei.' : e.message); }
  },
  begin(p) { PARTY.p = p; PARTY.since = p.seq || 0; PARTY.started = !!p.state.playing; PARTY.readySent = false; PARTY.offset = null; PARTY.rtt = 9999; PARTY.lastVer = 0; clearTimeout(PARTY.timer); PARTY.poll(); },
  async poll() {
    clearTimeout(PARTY.timer); if (!PARTY.p) return;
    const t0 = Date.now();
    try {
      const q = '&id=' + PARTY.p.id + '&since=' + PARTY.since + '&ready=' + (PLAYER.ready ? 1 : 0) + '&buf=' + (PLAYER.state === 2 ? 1 : 0) + '&pos=' + (PLAYER.pos / 1000).toFixed(1);
      const r = await COMM.call('party', null, q); const t1 = Date.now();
      if (!PARTY.p) return;
      const rtt = t1 - t0, off = r.party.now - (t0 + t1) / 2;
      if (PARTY.offset == null || rtt < PARTY.rtt * 1.5) { PARTY.offset = PARTY.offset == null ? off : PARTY.offset * .7 + off * .3; PARTY.rtt = Math.min(PARTY.rtt * 1.1, rtt); }
      PARTY.update(r.party);
    } catch (e) {
      if (e.status === 404) { PARTY.ended('Die Watch-Party ist zu Ende.'); return; }
    }
    PARTY.timer = setTimeout(PARTY.poll, 1000);
  },
  update(p) {
    const wasHost = PARTY.isHost(); PARTY.p = p;
    for (const r of p.react || []) { PARTY.since = Math.max(PARTY.since, r.seq); if (r.uid === EMBY.user.id && r.e !== 'join') continue; PARTY.showReact(r); }
    if (!wasHost && p.isHost) UI.toast('Du bist jetzt Gastgeber der Watch-Party', { icon: '👑' });
    if (p.state.playing && !PARTY.started) { PARTY.started = true; const l = $('.plobby'); if (l) { l.remove(); NAV.cur = null; PLAYER.hideOsdSoon(1500); } }
    PARTY.renderMembers();
    if (!PARTY.started) PARTY.renderLobby();
    PARTY.apply(p.state);
  },
  expected(st) { return st.pos * 1000 + (st.playing ? Math.max(0, PARTY.serverNow() - st.at) : 0); },
  apply(st) {
    if (!PLAYER.active || !PLAYER.ready || PARTY.offset == null) return;
    if (Date.now() < PARTY.coolUntil) return;
    const exp = PARTY.expected(st);
    if (st.playing && !PLAYER.playing && !PLAYER.ended) { ADAPTER.seek(exp + 250); ADAPTER.resume(); PLAYER.hideOsdSoon(1500); return; }
    if (!st.playing && PLAYER.playing) { ADAPTER.pause(); ADAPTER.seek(exp); PLAYER.showOsd(); if (st.by && st.by !== EMBY.user.name) PARTY.note(st.by + ' hat pausiert'); return; }
    const drift = PLAYER.pos - exp;
    if (Math.abs(drift) > 1500) { ADAPTER.seek(exp + (st.playing ? 300 : 0)); if (st.by && st.by !== EMBY.user.name && Math.abs(drift) > 8000) PARTY.note(st.by + ' hat gespult'); }
  },
  async localAction(playing, posMs) {
    if (!PARTY.p) return;
    if (!PARTY.isHost() && !PARTY.p.open) {
      UI.toast('Nur ' + PARTY.p.hostName + ' steuert diese Watch-Party', { icon: '👑' });
      PARTY.coolUntil = 0; setTimeout(() => PARTY.p && PARTY.apply(PARTY.p.state), 150); return;
    }
    PARTY.coolUntil = Date.now() + 1500;
    if (playing && !PARTY.started) { PARTY.started = true; const l = $('.plobby'); if (l) l.remove(); }
    try { const r = await COMM.call('party_state', { id: PARTY.p.id, playing, pos: posMs / 1000, since: PARTY.since }); if (r.party) PARTY.p = r.party; } catch (e) {}
  },
  localTick() {},
  localReady() { PARTY.renderLobby(); },
  renderMembers() {
    const o = $('#osd'); if (!o || !PARTY.p) return;
    let box = $('.pbox', o); if (!box) { box = h('div', 'pbox'); o.appendChild(box); }
    box.innerHTML = '';
    for (const m of PARTY.p.members) {
      const e = h('div', 'pm' + (m.host ? ' host' : '')); e.appendChild(avatar(m.name));
      e.appendChild(h('span', '', esc(m.name) + ' <span class="st">' + (m.host ? '👑' : '') + (m.buf ? ' lädt…' : (!PARTY.started ? (m.ready ? ' bereit' : ' …') : '')) + '</span>'));
      box.appendChild(e);
    }
  },
  renderLobby() {
    if (!PARTY.p || PARTY.started || !PLAYER.active) return;
    const o = $('#osd'); let l = $('.plobby', o); const fresh = !l;
    if (!l) { l = h('div', 'plobby'); o.appendChild(l); }
    const p = PARTY.p; const all = p.members.length, ready = p.members.filter(m => m.ready).length;
    const keep = NAV.cur && l.contains(NAV.cur) ? NAV.cur.dataset.k : null;
    l.innerHTML = '<div style="font-size:3.4rem">🍿</div><h2>Watch-Party: ' + esc((p.meta && p.meta.name) || '') + '</h2><p>' + (p.isHost ? 'Die anderen bekommen gerade eine Einladung. Starte, sobald alle da sind.' : 'Warte auf ' + esc(p.hostName) + ' – gleich geht es los.') + '</p>';
    const mem = h('div', 'mem');
    for (const m of p.members) { const d = h('div'); d.appendChild(avatar(m.name)); d.appendChild(h('div', '', esc(m.name))); d.appendChild(h('div', m.ready ? 'ok' : 'wait', m.ready ? '✓ bereit' : 'lädt …')); mem.appendChild(d); }
    l.appendChild(mem);
    const b = h('div', 'btns');
    if (p.isHost) {
      const go = onOk(h('div', 'btn pri', ic('play') + 'Für alle starten (' + ready + '/' + all + ' bereit)'), () => { PLAYER.play(true); }); go.dataset.k = 'go'; b.appendChild(go);
      const op = onOk(h('div', 'btn' + (p.open ? ' on' : ''), (p.open ? '✓ ' : '') + 'Alle dürfen steuern'), async () => { try { const r = await COMM.call('party_open', { id: p.id, open: !p.open }); PARTY.p = r.party; PARTY.renderLobby(); } catch (e) {} }); op.dataset.k = 'open'; b.appendChild(op);
    }
    const lv = onOk(h('div', 'btn', 'Verlassen'), () => PLAYER.stop('user')); lv.dataset.k = 'leave'; b.appendChild(lv);
    l.appendChild(b);
    PLAYER.showOsd(); clearTimeout(PLAYER.osdTimer);
    const want = keep ? $('[data-k="' + keep + '"]', l) : null;
    if (want) NAV.focus(want); else if (fresh || !NAV.cur || !l.contains(NAV.cur)) NAV.focus($('.btn', l));
  },
  key(k) {
    const bar = $('.reactbar');
    if (bar) {
      if (k === 'left' || k === 'right') { NAV.move(k); return true; }
      if (k === 'ok') { if (NAV.cur && NAV.cur._ok) NAV.cur._ok(); return true; }
      if (k === 'back' || k === 'menu' || k === 'up' || k === 'down') { bar.remove(); NAV.cur = null; PLAYER.hideOsdSoon(800); return true; }
    }
    if ($('.plobby') && (k === 'back')) { return false; }
    return false;
  },
  reactBar() {
    const o = $('#osd'); let bar = $('.reactbar', o); if (bar) { bar.remove(); return; }
    bar = h('div', 'reactbar');
    for (const e of EMOJI) bar.appendChild(onOk(h('div', 'r', e), () => { PARTY.react(e); clearTimeout(PARTY._rb); PARTY._rb = setTimeout(() => { bar.remove(); NAV.cur = null; }, 2500); }));
    o.appendChild(bar); NAV.focus($('.r', bar));
    clearTimeout(PARTY._rb); PARTY._rb = setTimeout(() => { if (bar.isConnected) { bar.remove(); NAV.cur = null; } }, 6000);
  },
  async react(e) {
    PARTY.showReact({ name: EMBY.user.name, e });
    try { await COMM.call('party_react', { id: PARTY.p.id, e, since: PARTY.since }); } catch (x) {}
  },
  showReact(r) {
    if (r.e === 'join') { if (r.uid !== EMBY.user.id) PARTY.note(r.name + ' ist dabei 🎉'); return; }
    if (r.e === 'leave') { PARTY.note(r.name + ' ist gegangen'); return; }
    const f = h('div', 'fly', esc(r.e) + '<small>' + esc(r.name) + '</small>');
    f.style.setProperty('--dx', (Math.random() * 8 - 6) + 'rem'); f.style.right = (Math.random() * 6) + 'rem';
    $('#reacts').appendChild(f); setTimeout(() => f.remove(), 3300);
  },
  note(t) {
    const o = $('#osd'); if (!o) return; let n = $('.pnote', o); if (!n) { n = h('div', 'pnote'); o.appendChild(n); }
    n.textContent = t; n.style.opacity = 1; clearTimeout(PARTY._nt); PARTY._nt = setTimeout(() => { n.style.opacity = 0; }, 3500);
  },
  ended(msg) {
    clearTimeout(PARTY.timer); PARTY.p = null; PLAYER.party = null;
    const l = $('.plobby'); if (l) l.remove(); const b = $('#osd .pbox'); if (b) b.remove();
    if (PLAYER.active) { UI.toast(msg + ' Du schaust allein weiter.', { icon: '🍿' }); PLAYER.renderControls(); }
  },
  leave() {
    const p = PARTY.p; clearTimeout(PARTY.timer); PARTY.p = null;
    if (p) COMM.call('party_leave', { id: p.id }).catch(() => {});
  },
};

// ---------- Community-Seite ----------
ROUTER.reg('community', async (scr) => {
  const el = scr.el; el.innerHTML = ''; BG.set(null); BG.dim(true);
  STORE.set('chatSeen', COMM.lastFeed ? COMM.lastFeed.chatLast : STORE.get('chatSeen', 0)); RAIL.badge('community', 0);
  const tabs = h('div', 'ctabs'); tabs.innerHTML = '<h1>Community</h1>'; el.appendChild(tabs);
  const body = h('div', 'cbody'); el.appendChild(body);
  if (!COMM.ok) {
    body.innerHTML = '<div class="panel" style="width:60rem"><h3>Nicht verbunden</h3><p style="font-size:1.35rem;line-height:1.5">Der Community-Server ist gerade nicht erreichbar.<br><span style="color:var(--mut)">' + esc(COMM.err || '') + '</span></p></div>';
    const b = onOk(h('div', 'btn pri', 'Erneut versuchen'), async () => { await COMM.start(); ROUTER.go('community', null, { replace: true }); }); b.style.marginTop = '2rem'; body.appendChild(b);
    return;
  }
  const T = [['chat', 'Chat & Tipps'], ['top', 'Bestenliste'], ['rate', 'Bewertungsmodus'], ['party', 'Watch-Partys'], ['online', 'Wer ist da']];
  if (COMM.me && COMM.me.admin) T.push(['admin', 'Admin']);
  let cur = scr.arg || 'chat';
  const show = async (id) => {
    cur = id; $$('.chip', tabs).forEach(c => c.classList.toggle('sel', c.dataset.id === id));
    clearTimeout(scr.chatT);
    // Jeder Reiter bekommt einen eigenen Bereich: Lädt ein alter Reiter noch nach, landet er nicht mehr auf dem Bildschirm
    const pane = h('div', 'cpane'); pane.style.display = 'contents'; pane.innerHTML = '<div class="loading"><div class="spin"></div></div>';
    body.innerHTML = ''; body.appendChild(pane);
    try { await COMM_TABS[id](pane, scr); } catch (e) { pane.innerHTML = '<div class="empty" style="padding:2rem 0">' + esc(e.message) + '</div>'; }
  };
  for (const [id, label] of T) { const c = onOk(h('div', 'chip', esc(label)), () => show(id)); c.dataset.id = id; tabs.appendChild(c); }
  scr.leave = () => { clearTimeout(scr.chatT); BG.dim(false); };
  scr.destroy = scr.leave;
  scr.enter = () => { BG.dim(true); };
  await show(cur);
  scr.initial = $('.chip.sel', tabs);
});

const COMM_TABS = {
  async chat(body, scr) {
    body.innerHTML = '';
    const wrap = h('div', 'chat'); const list = h('div', 'list'); list.setAttribute('data-mem', ''); wrap.appendChild(list); body.appendChild(wrap);
    list.style.top = '0'; list.style.bottom = 'auto'; list.setAttribute('data-vs', 'keep'); list.dataset.margin = '1';
    const bar = h('div', 'chatbar');
    bar.appendChild(onOk(h('div', 'btn pri', ic('keyboard') + 'Schreiben'), async () => { const t = await TEXT.ask('Nachricht an alle', { placeholder: 'Was schaut ihr gerade? Tipps?' }); if (t) send(t); }));
    for (const q of ['👍', '🍿 Heute Abend jemand?', 'Kann ich empfehlen!']) bar.appendChild(onOk(h('div', 'chip', esc(q)), () => send(q)));
    body.appendChild(bar);
    const side = h('div', 'side'); body.appendChild(side);
    let last = 0; const seenIds = {};
    const send = async (t) => { try { await COMM.call('say', { text: t }); await load(); } catch (e) { UI.toast(e.message, { icon: '⚠️' }); } };
    const fit = () => { const ch = wrap.clientHeight, lh = list.scrollHeight; if (!NAV.cur || !list.contains(NAV.cur)) { list._ty = Math.min(0, ch - lh); list.style.transform = 'translateY(' + list._ty + 'px)'; } };
    const msgEl = (m) => {
      const mine = m.uid === EMBY.user.id;
      const e = h('div', 'msg' + (mine ? ' mine' : '') + (m.del ? ' del' : '')); e.dataset.id = m.id;
      e.appendChild(avatar(m.name));
      const b = h('div', 'bub'); b.innerHTML = '<div class="nm">' + esc(m.name) + (m.admin ? '<span class="adm">ADMIN</span>' : '') + '<time>' + ago(m.t) + '</time></div><div class="tx">' + (m.del ? 'Nachricht gelöscht' : esc(m.text)) + '</div>';
      if (m.item && !m.del) {
        const r = h('div', 'rec'); const p = h('div', 'p'); p.style.backgroundImage = 'url("' + EMBY.api + '/Items/' + m.item.id + '/Images/Primary?maxWidth=200")';
        r.appendChild(p); r.appendChild(h('div', '', '<b>' + esc(m.item.name) + '</b><small>' + esc([m.item.type === 'Series' ? 'Serie' : m.item.type === 'Movie' ? 'Film' : m.item.type, m.item.year || ''].filter(Boolean).join(' · ')) + '</small><span class="go">OK = ansehen</span>'));
        b.appendChild(r);
        onOk(e, () => APP.openId(m.item.id));
      } else if (!m.del && (mine || COMM.me.admin)) e.classList.add('f');
      if (!m.del && (mine || COMM.me.admin)) { e.classList.add('f'); e._menu = async () => { if (await UI.confirm('Nachricht löschen?', m.text || (m.item && m.item.name), 'Löschen')) { await COMM.call('chat_del', { id: m.id }); load(true); } }; if (!e._ok) e._ok = e._menu; }
      b.lastChild && null; e.appendChild(b); return e;
    };
    const load = async (full) => {
      const r = await COMM.call('chat', null, '&since=' + (full ? 0 : last));
      if (full) { list.innerHTML = ''; }
      for (const m of r.msgs) { if (seenIds[m.id] && !full) continue; seenIds[m.id] = 1; list.appendChild(msgEl(m)); last = Math.max(last, m.id); }
      if (!list.children.length) list.appendChild(h('div', 'empty', 'Noch keine Nachrichten. Empfiehl einen Film über die Detailseite oder schreib die erste Nachricht!'));
      STORE.set('chatSeen', last); fit();
    };
    await load(true);
    const tick = async () => { if (ROUTER.top() !== scr) return; try { await load(); } catch (e) {} scr.chatT = setTimeout(tick, 4000); };
    scr.chatT = setTimeout(tick, 4000);
    // Rechts: wer ist online
    const on = h('div', 'panel'); on.innerHTML = '<h3>Gerade online</h3>'; side.appendChild(on);
    const ol = (COMM.lastFeed && COMM.lastFeed.online) || [];
    if (!ol.length) on.appendChild(h('div', '', '<span style="color:var(--mut);font-size:1.2rem">Nur du.</span>'));
    for (const o of ol.slice(0, 8)) { const w = h('div', 'who'); w.appendChild(avatar(o.name)); w.appendChild(h('div', '', esc(o.name) + (o.w ? '<small>schaut ' + esc(o.w) + '</small>' : ''))); on.appendChild(w); }
    const tip = h('div', 'panel'); tip.innerHTML = '<h3>Tipp</h3><div style="font-size:1.15rem;line-height:1.5;color:#c9cbe0">Auf jeder Detailseite gibt es „Empfehlen“ – dann erscheint der Titel hier als Karte, die alle direkt öffnen können.</div>'; side.appendChild(tip);
  },
  async top(body) {
    const r = await COMM.call('top'); body.innerHTML = '';
    if (!r.top.length) { body.innerHTML = '<div class="empty" style="padding:2rem 0">Noch keine Bewertungen. Starte den Bewertungsmodus und leg los!</div>'; return; }
    const g = h('div', 'toplist'); g.setAttribute('data-vs', 'keep'); g.setAttribute('data-mem', '');
    r.top.slice(0, 30).forEach((t, i) => {
      const e = h('div', 'topi'); const p = h('div', 'p'); p.style.backgroundImage = 'url("' + EMBY.api + '/Items/' + t.id + '/Images/Primary?maxWidth=220")';
      e.appendChild(h('div', 'rk' + (i < 3 ? ' grad-text' : ''), String(i + 1))); e.appendChild(p);
      e.appendChild(h('div', '', '<b>' + esc(t.meta.name) + '</b><small>' + esc([t.meta.type === 'Series' ? 'Serie' : 'Film', t.meta.year || ''].filter(Boolean).join(' · ')) + ' · ' + t.n + ' Stimme' + (t.n === 1 ? '' : 'n') + (t.mine ? ' · du: ' + t.mine : '') + '</small>'));
      e.appendChild(h('div', 'sc grad-text', t.avg.toFixed(1)));
      onOk(e, () => APP.openId(t.id)); g.appendChild(e);
    });
    const wrap = h('div', ''); wrap.style.cssText = 'position:absolute;inset:0;overflow:hidden'; wrap.appendChild(g); body.appendChild(wrap);
  },
  async rate(body) {
    body.innerHTML = '';
    const res = await EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie,Series', Filters: 'IsPlayed', SortBy: 'DatePlayed', SortOrder: 'Descending', Limit: 200 });
    let list = res.Items || [];
    if (!list.length) {   // Serien gelten erst als gesehen, wenn alle Folgen gesehen sind – dann teilweise gesehene dazunehmen
      const r2 = await EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie,Series', Filters: 'IsResumable', Limit: 100 }); list = r2.Items || [];
    }
    const rated = await COMM.ratings(list.map(i => i.Id));
    list = list.filter(i => !(rated[i.Id] && rated[i.Id].mine));
    if (!list.length) { body.innerHTML = '<div class="empty" style="padding:2rem 0">Alles bewertet, was du gesehen hast. Stark! 🎉<br><br>Neues erscheint hier automatisch, sobald du etwas fertig geschaut hast.</div>'; return; }
    let i = 0, v = 7;
    const flow = h('div', 'rateflow'); flow.style.paddingLeft = '0'; body.appendChild(flow);
    const render = () => {
      const it = list[i]; if (!it) { body.innerHTML = '<div class="empty" style="padding:2rem 0">Fertig – alles bewertet! 🎉</div>'; return; }
      v = 7;
      flow.innerHTML = '';
      const p = h('div', 'poster'); p.style.backgroundImage = 'url("' + EMBY.img(it, 'Primary', 500) + '")'; flow.appendChild(p);
      const r = h('div'); r.innerHTML = '<div style="font-size:1.05rem;letter-spacing:.2em;text-transform:uppercase;color:var(--mut);font-weight:800">Bewertungsmodus · ' + (i + 1) + ' von ' + list.length + '</div><h1>' + esc(it.Name) + '</h1><div class="sub">' + esc([it.Type === 'Series' ? 'Serie' : 'Film', it.ProductionYear || ''].filter(Boolean).join(' · ')) + '</div>';
      const big = h('div', ''); big.style.cssText = 'display:flex;align-items:flex-end;gap:2rem;margin-top:2.4rem';
      const num = h('div', 'rate-big grad-text'); const word = h('div', 'rate-word'); const st = h('div', 'rate-stars'); st.style.margin = '0';
      const rr = h('div'); rr.appendChild(st); rr.appendChild(word); big.appendChild(num); big.appendChild(rr); r.appendChild(big);
      const draw = () => { num.textContent = v; word.textContent = STAR_WORDS[v]; st.innerHTML = ''; for (let k = 1; k <= 5; k++) st.appendChild(h('div', 's', ic(v >= k * 2 ? 'starf' : 'star', ' style="color:' + (v >= k * 2 - 1 ? '#facc15' : 'rgba(255,255,255,.2)') + '"'))); };
      draw();
      r.appendChild(h('p', '', '<span style="font-size:1.25rem;color:var(--mut)">◀ ▶ Sterne · OK speichern · ▼ überspringen</span>')); r.lastChild.style.marginTop = '1.4rem';
      const b = h('div', 'btns'); b.style.marginTop = '2rem';
      const save = onOk(h('div', 'btn pri', ic('star') + 'Speichern & weiter'), async () => { try { await COMM.rate(it, v, ''); } catch (e) { UI.toast(e.message); return; } p.classList.add('out'); setTimeout(() => { i++; render(); }, 280); });
      const skip = onOk(h('div', 'btn', 'Überspringen'), () => { i++; render(); });
      b.appendChild(save); b.appendChild(skip); r.appendChild(b); flow.appendChild(r);
      save._key = true; NAV.focus(save);
      save.__draw = (d) => { v = Math.max(1, Math.min(10, v + d)); draw(); };
    };
    render();
    const scr = ROUTER.top(); const prevKey = scr.key;
    scr.key = (k) => { if ((k === 'left' || k === 'right') && NAV.cur && NAV.cur.__draw) { NAV.cur.__draw(k === 'right' ? 1 : -1); return true; } if (k === 'down' && NAV.cur && NAV.cur.__draw) { NAV.move('right'); return true; } return prevKey ? prevKey(k) : false; };
  },
  async party(body) {
    await COMM.feed(); body.innerHTML = '';
    const list = COMM.parties || [];
    if (!list.length) body.appendChild(h('div', 'panel', '<h3>Gerade keine Watch-Party</h3><div style="font-size:1.3rem;line-height:1.55;color:#c9cbe0">Öffne einen Film oder eine Folge und wähle <b>Watch-Party</b>. Alle anderen bekommen eine Einladung und schauen synchron mit – Pause und Spulen gelten für alle, dazu Emoji-Reaktionen.</div>'));
    for (const p of list) {
      const c = h('div', 'party-card'); const im = h('div', 'p'); im.style.backgroundImage = 'url("' + EMBY.api + '/Items/' + p.item + '/Images/Primary?maxWidth=220")'; c.appendChild(im);
      c.appendChild(h('div', '', '<b>' + esc((p.meta && p.meta.name) || 'Titel') + '</b><small>👑 ' + esc(p.host) + ' · ' + p.n + ' dabei: ' + esc(p.names.join(', ')) + '</small><small>OK = beitreten</small>'));
      onOk(c, () => PARTY.join(p)); body.appendChild(c);
    }
  },
  async online(body) {
    const r = await COMM.call('online'); body.innerHTML = '';
    const p = h('div', 'panel'); p.style.width = '52rem'; p.innerHTML = '<h3>midnox-Nutzer</h3>'; body.appendChild(p);
    for (const o of r.list) { const w = h('div', 'who'); w.appendChild(avatar(o.name)); w.appendChild(h('div', '', esc(o.name) + (o.admin ? ' 👑' : '') + '<small>' + (o.ago < 120 ? 'online' + (o.w ? ' · schaut ' + esc(o.w) : '') : 'zuletzt ' + ago(Date.now() / 1000 - o.ago)) + '</small>')); if (o.ago < 120) w.appendChild(h('i', 'dot')); p.appendChild(w); }
    if (EMBY.user.admin) {
      try {
        const s = await EMBY.get('/Sessions', { ActiveWithinSeconds: 900 });
        const q = h('div', 'panel'); q.style.cssText = 'position:absolute;right:0;top:0;width:26rem'; q.innerHTML = '<h3>Emby-Sitzungen</h3>'; body.appendChild(q);
        for (const x of s.filter(x => x.UserName)) { const w = h('div', 'who'); w.appendChild(avatar(x.UserName)); w.appendChild(h('div', '', esc(x.UserName) + '<small>' + esc(x.Client + ' · ' + x.DeviceName) + (x.NowPlayingItem ? '<br>▶ ' + esc(x.NowPlayingItem.Name) : '') + '</small>')); q.appendChild(w); }
      } catch (e) {}
    }
  },
  async admin(body) {
    body.innerHTML = '';
    const f = h('div', 'adm-form'); body.appendChild(f);
    let text = '', kind = 'banner', minutes = 60;
    f.appendChild(h('div', 'lbl', 'Nachricht an alle midnox-Nutzer'));
    const field = h('div', 'field f ph', 'Text eingeben …'); f.appendChild(field);
    field._ok = async () => { const t = await TEXT.ask('Rundnachricht', { value: text, placeholder: 'z. B. „Heute ab 22 Uhr Wartung – Server kurz weg“' }); if (t != null) { text = t; field.textContent = t || 'Text eingeben …'; field.classList.toggle('ph', !t); } };
    f.appendChild(h('div', 'lbl', 'Art'));
    const k = h('div', 'btns'); f.appendChild(k);
    for (const [v, l] of [['banner', 'Hinweis oben rechts'], ['popup', 'Fenster mit „Verstanden“']]) { const c = onOk(h('div', 'chip' + (v === kind ? ' sel' : ''), l), () => { kind = v; $$('.chip', k).forEach(x => x.classList.toggle('sel', x === c)); }); k.appendChild(c); }
    f.appendChild(h('div', 'lbl', 'Sichtbar für Geräte, die sich melden, innerhalb von'));
    const d = h('div', 'btns'); f.appendChild(d);
    for (const [v, l] of [[15, '15 Min.'], [60, '1 Stunde'], [1440, '1 Tag'], [10080, '1 Woche']]) { const c = onOk(h('div', 'chip' + (v === minutes ? ' sel' : ''), l), () => { minutes = v; $$('.chip', d).forEach(x => x.classList.toggle('sel', x === c)); }); d.appendChild(c); }
    const b = h('div', 'btns'); b.style.marginTop = '2.2rem'; f.appendChild(b);
    b.appendChild(onOk(h('div', 'btn pri', ic('bell') + 'An alle senden'), async () => {
      if (!text) { field._ok(); return; }
      try { const sent = await COMM.call('news', { text, kind, minutes }); STORE.set('newsSeen', STORE.get('newsSeen', []).concat([sent.news.id]).slice(-50)); UI.toast('Gesendet – erscheint bei allen innerhalb von ~20 Sekunden', { icon: '📣' }); text = ''; field.textContent = 'Text eingeben …'; field.classList.add('ph'); COMM_TABS.adminList(list); } catch (e) { UI.toast(e.message, { icon: '⚠️' }); }
    }));
    const list = h('div', 'panel'); list.style.cssText = 'position:absolute;right:0;top:0;width:24rem'; body.appendChild(list);
    COMM_TABS.adminList(list);
  },
  async adminList(list) {
    list.innerHTML = '<h3>Aktive Nachrichten</h3>';
    const f = await COMM.call('feed');
    if (!f.news.length) list.appendChild(h('div', '', '<span style="color:var(--mut);font-size:1.2rem">Keine.</span>'));
    for (const n of f.news) {
      const e = h('div', 'opt', '<span>' + esc(n.text.slice(0, 40)) + '</span><small>bis ' + clock(new Date(n.until * 1000)) + '</small>');
      onOk(e, async () => { if (await UI.confirm('Nachricht zurückziehen?', n.text, 'Zurückziehen')) { await COMM.call('news_del', { id: n.id }); COMM_TABS.adminList(list); } });
      list.appendChild(e);
    }
  },
};
window.COMM = COMM; window.PARTY = PARTY;
