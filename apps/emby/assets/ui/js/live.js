// midnox Media – Live-TV: Stalker-Portale, Xtream und M3U (Datenschicht aus midnox Live, window.Live).
// Senderliste mit Gruppen, „Jetzt/Danach“, Programmführer, Zappen im Vollbild, Einrichtung & Übernahme aus midnox Live.
'use strict';
const HAS_LIVE = typeof window.Live !== 'undefined';
const FAVG = '★ Favoriten', ALLG = 'Alle Sender';

const LIVE = {
  ch: [], byId: {}, groups: [], favs: [], names: {}, st: {}, ver: 0, epgC: {},
  enabled() { return HAS_LIVE && EMBY.settings().liveOn !== false && !(window.KIDS && KIDS.on); },
  byGroup: {}, _jv: -1, _mv: -1, want: false,
  /** midnox-TV-Kanäle (fragen die ganze Emby-Bibliothek ab) erst bauen, wenn Live-TV geöffnet wird */
  wantMx() { LIVE.want = true; if (window.MXTV && !MXTV.ch.length && !MXTV.building && !MXTV.tried && window.EMBY && EMBY.user) { MXTV.tried = true; MXTV.init(); } },
  /** Live-TV wird gleich gebraucht: Daten im Hintergrund vorbereiten */
  open() { LIVE.want = true; if (HAS_LIVE && Live.warm) Live.warm(); LIVE.load(); },
  /** Senderliste holen – nur wenn sie sich geändert hat (große Portale haben zehntausende Sender). */
  load(force) {
    if (!HAS_LIVE) return;
    try { LIVE.st = JSON.parse(Live.status() || '{}'); } catch (e) { LIVE.st = {}; }
    const jv = Live.ver ? Live.ver() : 0, mv = window.MXTV ? MXTV.ver : 0;
    if (LIVE.want) LIVE.wantMx();
    if (!force && jv === LIVE._jv && mv === LIVE._mv && (LIVE.ch.length || !LIVE.st.count)) return;
    LIVE._jv = jv; LIVE._mv = mv;
    let raw = []; try { raw = JSON.parse(Live.channels() || '[]'); } catch (e) {}
    LIVE.ch = raw.map(r => ({ id: r[0], name: r[1], group: r[2], num: r[3], logos: r[4] ? r[4].split('|').filter(Boolean) : [], src: r[5] }));
    if (window.MXTV) LIVE.ch = MXTV.ch.concat(LIVE.ch);   // eigene Kanäle zuerst
    LIVE.byId = {}; LIVE.byGroup = {};
    for (const c of LIVE.ch) { LIVE.byId[c.id] = c; (LIVE.byGroup[c.group] || (LIVE.byGroup[c.group] = [])).push(c); }
    try { LIVE.groups = JSON.parse(Live.groups() || '[]'); } catch (e) { LIVE.groups = []; }
    if (window.MXTV && MXTV.ch.length) LIVE.groups.unshift(MXG);
    try { LIVE.favs = JSON.parse(Live.favs() || '[]'); } catch (e) { LIVE.favs = []; }
    try { LIVE.names = JSON.parse(Live.sourceNames() || '{}'); } catch (e) { LIVE.names = {}; }
    LIVE.epgC = {}; LIVE.ver++;
  },
  ev(e) {
    switch (e.e) {
      case 'cache': case 'channels': LIVE.load(); LIVE.refresh(); { const t = ROUTER.top(); if (t && t.name === 'home' && window.APP) APP.dirty = true; } break;
      case 'loading': try { LIVE.st = JSON.parse(Live.status() || '{}'); } catch (x) {} LIVE.refresh(true); break;
      case 'epg':
        if (e.id) delete LIVE.epgC[e.id]; else { LIVE.epgC = {}; try { LIVE.st = JSON.parse(Live.status() || '{}'); } catch (x) {} if (!e.id && Live.ver && Live.ver() !== LIVE._jv) LIVE.load(); }
        clearTimeout(LIVE._et); LIVE._et = setTimeout(() => { LIVE.refresh(true); if (TVP.active && !TVP.listOn) TVP.banner(false); }, e.id ? 800 : 200);   // gebündelt statt bei jedem Sender
        break;
      case 'tune': TVP.onTune(e); break;
      case 'import': if (LIVE._imp) { const f = LIVE._imp; LIVE._imp = null; f(e); } else UI.toast(e.msg, { icon: e.ok ? '📺' : '⚠️' }); break;
      case 'peers': if (LIVE._peers) { const f = LIVE._peers; LIVE._peers = null; f(e.list || []); } break;
    }
  },
  refresh(soft) {
    const t = ROUTER.top(); if (t && (t.name === 'live' || t.name === 'liveguide') && t.refresh) t.refresh(soft);
    if (t && t.name === 'home' && window.APP) APP.dirty = true;
  },
  /** Startseiten-Reihe nachreichen, wenn die App ruhig läuft (nicht beim Start) */
  later() {
    if (LIVE._lt) return;
    LIVE._lt = setTimeout(() => { if (HAS_LIVE && Live.warm) Live.warm(); }, 40000);
  },
  isFav(id) { return LIVE.favs.indexOf(id) >= 0; },
  toggleFav(id) { const on = Live.fav(id); LIVE.favs = JSON.parse(Live.favs() || '[]'); UI.toast(on ? 'Zu den Favoriten hinzugefügt' : 'Aus den Favoriten entfernt', { icon: on ? '★' : '☆' }); return on; },
  list(group) {
    if (group === FAVG) return LIVE.favs.map(id => LIVE.byId[id]).filter(Boolean);
    if (!group || group === ALLG) return LIVE.ch;
    return LIVE.byGroup[group] || [];
  },
  // ---- Programm (gecacht) ----
  fetchEpg(ids) {
    const now = Date.now();
    for (const id of ids) if (!LIVE.epgC[id] && id.indexOf('mx:') === 0) LIVE.epgC[id] = MXTV.progs(id, now - 4 * 3600e3, now + 30 * 3600e3);
    const miss = ids.filter(id => !LIVE.epgC[id]); if (!miss.length) return;
    let o = {};
    try { o = JSON.parse(Live.epg(JSON.stringify(miss), now - 4 * 3600e3, now + 30 * 3600e3) || '{}'); } catch (e) {}
    for (const id of miss) LIVE.epgC[id] = o[id] || [];
  },
  progs(id) { if (!LIVE.epgC[id]) LIVE.fetchEpg([id]); return LIVE.epgC[id] || []; },
  nowNext(id, t) {
    t = t || Date.now(); let now = null, next = null;
    for (const p of LIVE.progs(id)) { if (p[0] <= t && p[1] > t) now = p; else if (p[0] > t && (!next || p[0] < next[0])) next = p; }
    if (!now && !next && HAS_LIVE && !LIVE['_s' + id]) { LIVE['_s' + id] = 1; try { Live.shortEpg(id); } catch (e) {} }
    return { now, next };
  },
  // ---- Logo mit Ausweich-Adressen ----
  last() { return localStorage.getItem('lv_last') || (HAS_LIVE ? Live.last() : ''); },
  logo(c, cls) {
    const box = h('div', 'lvlogo' + (cls ? ' ' + cls : ''));
    if (c.emoji) { box.classList.add('mx'); box.innerHTML = '<span>' + c.emoji + '</span><i>midnox</i>'; return box; }
    const ini = () => { box.innerHTML = '<span>' + esc((c.name || '?').replace(/[^\wÄÖÜäöü ]/g, '').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase() || '#') + '</span>'; box.classList.add('ini'); };
    const urls = (c.logos || []).slice();
    const next = () => { const u = urls.shift(); if (!u) { ini(); return; } const im = new Image(); im.onload = () => { box.innerHTML = ''; box.appendChild(im); }; im.onerror = next; im.referrerPolicy = 'no-referrer'; im.src = u; };
    ini(); next();
    return box;
  },
  hm(t) { const d = new Date(t); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); },
  pct(p, t) { t = t || Date.now(); return p ? Math.max(0, Math.min(100, (t - p[0]) / (p[1] - p[0]) * 100)) : 0; },
  // ---- Startseite: Reihe „Live-TV · Läuft gerade“ ----
  homeRow(focus) {
    if (!LIVE.enabled()) return null;
    if (!LIVE.ch.length) { LIVE.later(); return null; }
    let list = LIVE.favs.map(id => LIVE.byId[id]).filter(Boolean);
    const last = LIVE.last(); if (last && LIVE.byId[last] && list.indexOf(LIVE.byId[last]) < 0) list.unshift(LIVE.byId[last]);
    if (!list.length) list = LIVE.ch.slice(0, 12);
    list = list.slice(0, 16);
    LIVE.fetchEpg(list.map(c => c.id));
    const r = h('div', 'row capped'); r.setAttribute('data-mem', ''); r.setAttribute('data-vsi', '');
    r.appendChild(h('h2', '', 'Live-TV <small>läuft gerade</small>'));
    const tr = h('div', 'track'); r.appendChild(tr);
    for (const c of list) {
      const nn = LIVE.nowNext(c.id);
      const card = h('div', 'card land lvcard');
      card.appendChild(LIVE.logo(c, 'big'));
      card.appendChild(h('div', 'cap', '<b>' + esc(c.name) + '</b><span>' + esc(nn.now ? nn.now[2] : 'Live') + '</span>' + (nn.now ? '<i><em style="width:' + LIVE.pct(nn.now) + '%"></em></i>' : '')));
      onOk(card, () => TVP.play(c.id, list.map(x => x.id)));
      if (focus) card._focus = () => focus(null);
      tr.appendChild(card);
    }
    return r;
  },
};

// ======================================================================= Live-Seite
ROUTER.reg('live', async (scr, arg) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.innerHTML = ''; el.classList.add('livepage');
  if (!HAS_LIVE) { el.innerHTML = '<div class="empty">Live-TV gibt es in der midnox-Media-App auf Fernseher, Handy und Tablet.</div>'; return; }
  LIVE.open();
  let grp = localStorage.getItem('lv_grp') || '';
  let shown = 0, cur = [], lastVer = -1;

  const head = h('div', 'libhead lvhead'); el.appendChild(head);
  const body = h('div', 'lvbody'); el.appendChild(body);
  const gcol = h('div', 'lvgroups'); const gl = h('div', 'lvgl'); gl.setAttribute('data-vs', 'keep'); gl.setAttribute('data-mem', ''); gl.dataset.margin = '1'; gcol.appendChild(gl); body.appendChild(gcol);
  const lcol = h('div', 'lvchans'); const ll = h('div', 'lvcl'); ll.setAttribute('data-vs', 'keep'); ll.setAttribute('data-mem', ''); ll.dataset.margin = '1'; lcol.appendChild(ll); body.appendChild(lcol);

  const drawHead = () => {
    const st = LIVE.st || {};
    const errs = (st.sources || []).filter(s => s.err).length;
    head.innerHTML = '<h1>Live-TV</h1><span class="count">' + (LIVE.ch.length ? LIVE.ch.length + ' Sender' : '') + (st.loading ? ' · ' + esc(st.status || 'lädt …') : st.epgLoading ? ' · Programm wird geladen …' : '') + (errs ? ' · <span class="warn">' + errs + ' Quelle' + (errs > 1 ? 'n' : '') + ' mit Fehler</span>' : '') + '</span>';
    const btns = h('div', 'lvbtns');
    btns.appendChild(onOk(h('div', 'btn', ic('grid') + 'Programm'), () => ROUTER.go('liveguide', { group: grp })));
    btns.appendChild(onOk(h('div', 'btn', ic('search') + 'Suchen'), async () => {
      const q = await TEXT.ask('Sender suchen', { placeholder: 'z. B. ZDF, Sky Sport, Kika …' }); if (!q) return;
      const ql = q.toLowerCase(); const res = LIVE.ch.filter(c => c.name.toLowerCase().indexOf(ql) >= 0);
      if (!res.length) { UI.toast('Kein Sender gefunden', { icon: '🔎' }); return; }
      grp = '🔎 ' + q; cur = res; drawGroups(); drawList(true);
    }));
    btns.appendChild(onOk(h('div', 'btn', ic('gear') + 'Einrichten'), () => LIVESET.open()));
    head.appendChild(btns);
  };
  const groupsList = () => { const g = []; if (LIVE.favs.length) g.push(FAVG); const gs = LIVE.groups.slice(); if (gs[0] === MXG) g.push(gs.shift()); g.push(ALLG); return g.concat(gs); };
  const drawGroups = () => {
    gl.innerHTML = '';
    const gs = groupsList(); if (grp.indexOf('🔎') === 0) gs.unshift(grp);
    if (!gs.includes(grp)) grp = gs[0];
    let selEl = null;
    for (const g of gs) {
      const n = g.indexOf('🔎') === 0 ? cur.length : LIVE.list(g).length;
      const e = h('div', 'lvg' + (g === grp ? ' sel' : ''), '<span>' + esc(g) + '</span><small>' + n + '</small>'); e.setAttribute('data-vsi', '');
      onOk(e, () => { pick(g); const f = ll.querySelector('.f'); if (f) NAV.focus(f); });
      e._focus = () => { clearTimeout(scr._gt); scr._gt = setTimeout(() => { if (NAV.cur === e && grp !== g) pick(g); }, 600); };
      gl.appendChild(e); if (g === grp) selEl = e;
    }
    scr.gsel = selEl;
  };
  const pick = (g) => {
    if (g.indexOf('🔎') !== 0) { grp = g; localStorage.setItem('lv_grp', g); cur = LIVE.list(g); }
    else grp = g;
    $$('.lvg', gl).forEach(e => e.classList.toggle('sel', e.querySelector('span').textContent === g));
    drawList(true);
  };
  const rowOf = (c) => {
    const nn = LIVE.nowNext(c.id);
    const r = h('div', 'lvrow'); r.setAttribute('data-vsi', ''); r.dataset.id = c.id;
    r.appendChild(h('div', 'num', c.num ? String(c.num) : ''));
    r.appendChild(LIVE.logo(c));
    const t = h('div', 'txt');
    t.innerHTML = '<b>' + esc(c.name) + (LIVE.isFav(c.id) ? ' <span class="star">★</span>' : '') + '</b>' +
      (nn.now ? '<div class="now"><span class="tm">' + LIVE.hm(nn.now[0]) + '</span>' + esc(nn.now[2]) + '</div><i class="pb"><em style="width:' + LIVE.pct(nn.now) + '%"></em></i>' : '<div class="now mut">' + (LIVE.st.epgLoading ? 'Programm wird geladen …' : 'Keine Programminfos') + '</div>') +
      (nn.next ? '<div class="nx">' + LIVE.hm(nn.next[0]) + ' · ' + esc(nn.next[2]) + '</div>' : '');
    r.appendChild(t);
    onOk(r, () => TVP.play(c.id, cur.map(x => x.id)));
    r._menu = () => { LIVE.toggleFav(c.id); const b = $('b', t); b.innerHTML = esc(c.name) + (LIVE.isFav(c.id) ? ' <span class="star">★</span>' : ''); };
    return r;
  };
  const CHUNK = 60;
  const more = () => {
    const part = cur.slice(shown, shown + CHUNK); if (!part.length) return;
    LIVE.fetchEpg(part.map(c => c.id));
    const sent = $('.lvmore', ll); if (sent) sent.remove();
    for (const c of part) ll.appendChild(rowOf(c));
    shown += part.length;
    if (shown < cur.length) { const m = h('div', 'lvmore', 'Weitere ' + (cur.length - shown) + ' Sender …'); ll.appendChild(m); }
  };
  ll._onScroll = (item) => { if (shown < cur.length && item && ll.lastElementChild && Array.prototype.indexOf.call(ll.children, item) > ll.children.length - 12) more(); };
  lcol.addEventListener('scroll', () => { if (shown < cur.length && lcol.scrollTop + lcol.clientHeight > lcol.scrollHeight - 800) more(); }, { passive: true });
  const drawList = (reset) => {
    if (reset) { ll.innerHTML = ''; shown = 0; ll._ty = 0; ll.style.transform = ''; lcol.scrollTop = 0; }
    if (!cur.length) {
      const st = LIVE.st || {};
      ll.innerHTML = '<div class="empty small">' + (st.loading ? esc(st.status || 'Sender werden geladen …') : grp === FAVG ? 'Noch keine Favoriten. Auf einem Sender MENU drücken (oder lange tippen).' : 'Keine Sender in dieser Gruppe.') + '</div>';
      return;
    }
    more();
  };
  const softUpdate = () => { const rows = $$('.lvrow', ll); LIVE.fetchEpg(rows.map(r => r.dataset.id)); rows.forEach(r => { const c = LIVE.byId[r.dataset.id]; if (!c) return; const nn = LIVE.nowNext(c.id); const n = $('.now', r); if (n && nn.now) { n.innerHTML = '<span class="tm">' + LIVE.hm(nn.now[0]) + '</span>' + esc(nn.now[2]); n.classList.remove('mut'); let pb = $('.pb', r); if (!pb) { pb = h('i', 'pb', '<em></em>'); n.after(pb); } $('em', pb).style.width = LIVE.pct(nn.now) + '%'; } const x = $('.nx', r); if (nn.next) { if (x) x.textContent = LIVE.hm(nn.next[0]) + ' · ' + nn.next[2]; else $('.txt', r).appendChild(h('div', 'nx', LIVE.hm(nn.next[0]) + ' · ' + esc(nn.next[2]))); } }); };

  const build = () => {
    lastVer = LIVE.ver;
    drawHead();
    const st = LIVE.st || {};
    if (!(st.sources || []).length && !LIVE.ch.length) {
      body.style.display = 'none';
      let s = $('.lvsetup', el); if (s) s.remove();
      s = h('div', 'lvsetup');
      s.innerHTML = '<div class="big">📺</div><h2>Live-TV einrichten</h2><p>Hier laufen deine Sender aus Stalker-Portalen, Xtream-Codes und M3U-Listen – mit Programmführer und Zappen wie im Fernsehen.</p>';
      const b = h('div', 'btns');
      if (st.liveInstalled) b.appendChild(onOk(h('div', 'btn pri', ic('download') + 'Aus midnox Live übernehmen'), () => LIVESET.importLocal()));
      b.appendChild(onOk(h('div', 'btn' + (st.liveInstalled ? '' : ' pri'), ic('wifi') + 'Von anderem Gerät'), () => LIVESET.fromPeer()));
      b.appendChild(onOk(h('div', 'btn', ic('plus') + 'Quelle hinzufügen'), () => LIVESET.add()));
      s.appendChild(b); el.appendChild(s);
      NAV.focus($('.btn', s));
      return;
    }
    const s0 = $('.lvsetup', el); if (s0) s0.remove();
    body.style.display = '';
    cur = grp.indexOf('🔎') === 0 ? cur : LIVE.list(grp || (LIVE.favs.length ? FAVG : ALLG));
    if (!grp) grp = LIVE.favs.length ? FAVG : ALLG;
    drawGroups(); cur = grp.indexOf('🔎') === 0 ? cur : LIVE.list(grp); drawList(true);
    // Fokus: zuletzt gesehener Sender, sonst erster
    const last = arg && arg.focus ? arg.focus : LIVE.last();
    const r = last ? ll.querySelector('.lvrow[data-id="' + CSS.escape(last) + '"]') : null;
    scr.initial = r || ll.querySelector('.f') || (scr.gsel);
  };
  build();
  scr.refresh = (soft) => { if (soft || lastVer === LIVE.ver) { drawHead(); softUpdate(); return; } const f = NAV.cur && NAV.cur.dataset ? NAV.cur.dataset.id : null; build(); if (f) { const r = ll.querySelector('.lvrow[data-id="' + CSS.escape(f) + '"]'); if (r) NAV.focus(r); } else if (scr.initial) NAV.focus(scr.initial); };
  scr.enter = () => { softUpdate(); };
  const tick = setInterval(() => { if (ROUTER.top() === scr) softUpdate(); }, 60000);
  scr.destroy = () => clearInterval(tick);
  scr.key = (k) => {
    if (k === 'menu' && NAV.cur && NAV.cur._menu) { NAV.cur._menu(); return true; }
    if ((k === 'up' || k === 'down') && NAV.cur && (ll.contains(NAV.cur) || gl.contains(NAV.cur))) {   // in der eigenen Spalte bleiben
      let n = k === 'down' ? NAV.cur.nextElementSibling : NAV.cur.previousElementSibling;
      while (n && !n.classList.contains('f')) { if (n.classList.contains('lvmore')) { more(); n = k === 'down' ? NAV.cur.nextElementSibling : null; continue; } n = k === 'down' ? n.nextElementSibling : n.previousElementSibling; }
      if (n) NAV.focus(n); else if (k === 'up') { const b = $('.lvbtns .btn', head); if (b) NAV.focus(b); }
      return true;
    }
    if (k === 'left' && ll.contains(NAV.cur)) { NAV.focus(scr.gsel || gl.querySelector('.f')); return true; }
    if (k === 'right' && gl.contains(NAV.cur)) { const f = ll.querySelector('.f'); if (f) NAV.focus(f); return true; }
    return false;
  };
});

// ======================================================================= Programmführer
ROUTER.reg('liveguide', async (scr, arg) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.innerHTML = ''; el.classList.add('guidepage');
  if (!HAS_LIVE) return;
  LIVE.open();
  const SPAN = 3 * 3600e3; let PX = 0.3;   // 3 Stunden sichtbar; PX = rem pro Minute (passt sich der Breite an)
  let t0 = Math.floor((Date.now() - 30 * 60e3) / 1800e3) * 1800e3;
  const grp = (arg && arg.group) || localStorage.getItem('lv_grp') || ALLG;
  const list = (grp.indexOf('🔎') === 0 ? LIVE.ch : LIVE.list(grp)).slice(0, 400);
  const head = h('div', 'gdhead'); el.appendChild(head);
  const grid = h('div', 'gdgrid'); const rows = h('div', 'gdrows'); rows.setAttribute('data-vs', 'keep'); rows.setAttribute('data-mem', ''); rows.dataset.margin = '1'; grid.appendChild(rows); el.appendChild(grid);
  const nowLine = h('div', 'gdnow'); grid.appendChild(nowLine);
  { const R = parseFloat(getComputedStyle(document.documentElement).fontSize) || 10, chw = parseFloat(getComputedStyle(el).getPropertyValue('--gdch')) || 22;
    const avail = (grid.clientWidth || el.clientWidth - 11 * R) / R - chw - 1; if (avail > 20) PX = avail / 180; }
  const W = (ms) => (ms / 60000 * PX) + 'rem';
  let built = 0;
  const drawHead = () => {
    let s = '<div class="gdt"><b>' + esc(grp) + '</b><small>' + new Date(t0).toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) + '</small></div><div class="gdtimes">';
    for (let t = t0; t < t0 + SPAN; t += 1800e3) s += '<span style="width:' + W(1800e3) + '">' + LIVE.hm(t) + '</span>';
    head.innerHTML = s + '</div>';
    const off = (Date.now() - t0); nowLine.style.left = 'calc(var(--gdch) + ' + W(off) + ')'; nowLine.style.display = off > 0 && off < SPAN ? '' : 'none';
  };
  const rowOf = (c) => {
    const r = h('div', 'gdrow'); r.setAttribute('data-vsi', ''); r.dataset.id = c.id;
    const cc = h('div', 'gdch'); cc.appendChild(LIVE.logo(c)); cc.appendChild(h('span', '', (c.num ? '<i>' + c.num + '</i>' : '') + esc(c.name))); r.appendChild(cc);
    const tl = h('div', 'gdtl'); r.appendChild(tl);
    const ps = LIVE.progs(c.id).filter(p => p[1] > t0 && p[0] < t0 + SPAN).sort((a, b) => a[0] - b[0]);
    const play = () => TVP.play(c.id, list.map(x => x.id));
    if (!ps.length) { const b = h('div', 'gdp empty', '<b>' + esc(c.name) + '</b><small>Keine Programminfos</small>'); b.style.width = W(SPAN); onOk(b, play); tl.appendChild(b); }
    let x = t0;
    for (const p of ps) {
      const a = Math.max(p[0], t0), e = Math.min(p[1], t0 + SPAN);
      if (a > x + 60e3) { const gap = h('div', 'gdgap'); gap.style.width = W(a - x); tl.appendChild(gap); }
      const live = p[0] <= Date.now() && p[1] > Date.now();
      const b = h('div', 'gdp' + (live ? ' live' : ''), '<b>' + esc(p[2]) + '</b><small>' + LIVE.hm(p[0]) + '–' + LIVE.hm(p[1]) + '</small>'); b.style.width = 'calc(' + W(e - a) + ' - 0.3rem)';
      onOk(b, live || p[0] > Date.now() ? (live ? play : () => info(c, p)) : () => info(c, p));
      b._menu = () => info(c, p);
      tl.appendChild(b); x = e;
    }
    return r;
  };
  const info = (c, p) => UI.modal((box, close) => {
    box.appendChild(h('h2', '', esc(p[2])));
    box.appendChild(h('p', '', esc(c.name) + ' · ' + new Date(p[0]).toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'numeric' }) + ' · ' + LIVE.hm(p[0]) + '–' + LIVE.hm(p[1])));
    if (p[3]) box.appendChild(h('p', 'desc', esc(p[3])));
    const b = h('div', 'btns');
    b.appendChild(onOk(h('div', 'btn pri', ic('play') + 'Sender einschalten'), () => { close(); TVP.play(c.id, list.map(x => x.id)); }));
    b.appendChild(onOk(h('div', 'btn', 'Schließen'), () => close()));
    box.appendChild(b);
  });
  const more = () => { const part = list.slice(built, built + 40); if (!part.length) return; LIVE.fetchEpg(part.map(c => c.id)); for (const c of part) rows.appendChild(rowOf(c)); built += part.length; };
  rows._onScroll = (item) => { if (built < list.length && Array.prototype.indexOf.call(rows.children, item) > rows.children.length - 8) more(); };
  grid.addEventListener('scroll', () => { if (built < list.length && grid.scrollTop + grid.clientHeight > grid.scrollHeight - 600) more(); }, { passive: true });
  const build = () => { rows.innerHTML = ''; built = 0; rows._ty = 0; rows.style.transform = ''; drawHead(); if (!list.length) { rows.innerHTML = '<div class="empty small">Keine Sender.</div>'; return; } more(); };
  build();
  const live0 = rows.querySelector('.gdp.live') || rows.querySelector('.gdp'); scr.initial = live0;
  scr.refresh = () => { const f = NAV.cur && NAV.cur.closest('.gdrow'); const id = f && f.dataset.id; build(); const r = id && rows.querySelector('.gdrow[data-id="' + CSS.escape(id) + '"] .gdp.live, .gdrow[data-id="' + CSS.escape(id) + '"] .gdp'); NAV.focus(r || rows.querySelector('.gdp')); };
  scr.key = (k) => {
    if (k === 'menu' && NAV.cur && NAV.cur._menu) { NAV.cur._menu(); return true; }
    if ((k === 'right' || k === 'left') && NAV.cur && rows.contains(NAV.cur)) {
      const sib = k === 'right' ? NAV.cur.nextElementSibling : NAV.cur.previousElementSibling;
      const nx = sib && sib.classList.contains('gdgap') ? (k === 'right' ? sib.nextElementSibling : sib.previousElementSibling) : sib;
      if (nx && nx.classList.contains('f')) { NAV.focus(nx); return true; }
      t0 += (k === 'right' ? 1 : -1) * 3600e3; const id = NAV.cur.closest('.gdrow').dataset.id; build();
      const r = rows.querySelector('.gdrow[data-id="' + CSS.escape(id) + '"]'); if (r) NAV.focus(k === 'right' ? r.querySelector('.gdp') : $$('.gdp', r).pop());
      return true;
    }
    return false;
  };
  const tick = setInterval(() => drawHead(), 60000); scr.destroy = () => clearInterval(tick);
});

// ======================================================================= Einrichtung
const LIVESET = {
  open() {
    LIVE.open();
    return UI.modal((box, close) => {
      box.style.minWidth = '64rem';
      box.appendChild(h('h2', '', 'Live-TV einrichten'));
      const st = LIVE.st || {};
      box.appendChild(h('p', '', (LIVE.ch.length ? LIVE.ch.length + ' Sender' : 'Noch keine Sender') + (st.epgAt ? ' · Programm vom ' + new Date(st.epgAt).toLocaleString('de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' }) : '')));
      const l = h('div', 'opts');
      for (const s of st.sources || []) {
        const e = h('div', 'opt f lvsrc', '<b>' + esc(s.name) + (s.on ? '' : ' <small>(aus)</small>') + '</b><small>' + esc(s.label) + (s.macs ? ' · ' + s.macs + ' MAC' + (s.macs > 1 ? 's' : '') : '') + (s.err ? ' · ⚠️ ' + esc(s.err) : '') + '</small>');
        e._ok = async () => {
          const v = await UI.choose(s.name, [{ label: s.on ? 'Ausschalten' : 'Einschalten', value: 'tog' }, { label: 'Entfernen', value: 'del' }]);
          if (v === 'tog') { Live.toggleSource(s.id); close(); UI.toast('Sender werden neu geladen …', { icon: '📺' }); }
          if (v === 'del' && await UI.confirm('„' + s.name + '“ entfernen?', 'Die Sender dieser Quelle verschwinden aus midnox Media. midnox Live selbst bleibt unverändert.', 'Entfernen')) { Live.removeSource(s.id); close(); }
        };
        l.appendChild(e);
      }
      box.appendChild(l);
      const b = h('div', 'btns'); b.style.flexWrap = 'wrap';
      if (st.liveInstalled) b.appendChild(onOk(h('div', 'btn pri', ic('download') + 'Aus midnox Live übernehmen'), () => { close(); LIVESET.importLocal(); }));
      b.appendChild(onOk(h('div', 'btn', ic('wifi') + 'Von anderem Gerät'), () => { close(); LIVESET.fromPeer(); }));
      b.appendChild(onOk(h('div', 'btn', ic('plus') + 'Quelle hinzufügen'), () => { close(); LIVESET.add(); }));
      b.appendChild(onOk(h('div', 'btn', ic('refresh') + 'Neu laden'), () => { close(); Live.reload(); UI.toast('Sender und Programm werden neu geladen …', { icon: '📺' }); }));
      b.appendChild(onOk(h('div', 'btn', 'Schließen'), () => close()));
      box.appendChild(b);
    });
  },
  importLocal() {
    UI.toast('Übernehme Einstellungen aus midnox Live …', { icon: '📺' });
    LIVE._imp = (e) => { UI.toast(e.msg, { icon: e.ok ? '✅' : '⚠️', ms: 5000 }); };
    Live.importLocal();
  },
  async fromPeer() {
    UI.toast('Suche midnox-Live-Geräte im Netzwerk …', { icon: '📡' });
    const list = await new Promise(res => { LIVE._peers = res; Live.peers(); setTimeout(() => { if (LIVE._peers === res) { LIVE._peers = null; res([]); } }, 6000); });
    if (!list.length) { await UI.alert('Kein Gerät gefunden', 'Öffne midnox Live auf einem anderen Fernseher im selben WLAN und lege dort unter Einstellungen → Quickübertragen einen Netzwerk-Code fest.'); return; }
    const p = await UI.choose('Von welchem Gerät?', list.map(x => ({ label: x.name, sub: x.count + ' Sender · ' + x.ip, value: x })));
    if (!p) return;
    const pin = await TEXT.ask('Netzwerk-Code von „' + p.name + '“', { sub: 'Steht in midnox Live unter Einstellungen → Quickübertragen.', autoKeyboard: true });
    if (pin == null) return;
    UI.toast('Übernehme …', { icon: '📡' });
    LIVE._imp = (e) => UI.toast(e.msg, { icon: e.ok ? '✅' : '⚠️', ms: 5000 });
    Live.pull(p.ip, p.port, pin);
  },
  async add() {
    const type = await UI.choose('Welche Art von Quelle?', [{ label: 'Stalker-Portal (MAC-Adresse)', value: 'stalker' }, { label: 'Xtream Codes (Benutzer & Passwort)', value: 'xtream' }, { label: 'M3U-Liste (Adresse)', value: 'm3u' }]);
    if (!type) return;
    const url = await TEXT.ask(type === 'stalker' ? 'Portal-Adresse' : type === 'xtream' ? 'Server-Adresse' : 'Adresse der M3U-Liste', { placeholder: type === 'stalker' ? 'http://portal.beispiel.tv:8080/c/' : 'http://…', sub: type === 'stalker' ? 'Tipp: Du kannst hier auch einen ganzen Text mit Portal und MACs einfügen – „Per Handy tippen“.' : '' });
    if (!url) return;
    const o = { type, url };
    if (type === 'stalker') { const mac = await TEXT.ask('MAC-Adresse(n)', { placeholder: '00:1A:79:…', sub: 'Mehrere MACs mit Komma trennen – die App wechselt automatisch, wenn eine nicht geht.', allowEmpty: true }); if (mac == null) return; o.mac = mac; }
    if (type === 'xtream') { const u = await TEXT.ask('Benutzername'); if (u == null) return; const p = await TEXT.ask('Passwort', { password: true }); if (p == null) return; o.user = u; o.pass = p; }
    const name = await TEXT.ask('Name der Quelle', { placeholder: 'z. B. Mein Portal', allowEmpty: true });
    o.name = name || '';
    const r = JSON.parse(Live.addSource(JSON.stringify(o)));
    UI.toast(r.ok ? 'Quelle hinzugefügt – Sender werden geladen …' : r.msg, { icon: r.ok ? '✅' : '⚠️', ms: 4000 });
  },
};

// ======================================================================= Live-Player (Zappen)
const TVP = {
  active: false, id: null, ids: [], k: 0, req: '', tries: null, ready: false, wd: 0, zapT: 0, pend: null, cid: null, mac: -1, label: '', listOn: false,
  play(id, ids) {
    if (!HAS_LIVE || !LIVE.byId[id]) return;
    if (window.THEMESONG) THEMESONG.stop();
    if (window.MUSIC && MUSIC.active) MUSIC.stop();
    if (window.PLAYER && PLAYER.active) PLAYER.stop('user');
    TVP.ids = ids && ids.length ? ids : [id];
    if (!TVP.active) {
      TVP.active = true;
      document.body.classList.add('playing', 'tvlive'); document.documentElement.classList.add('playing');
      const o = $('#osd'); o.className = 'show tv'; o.innerHTML = '';
      if (HAS_ANDROID) try { Android.keepAwake(true); } catch (e) {}
    }
    TVP.tune(id);
  },
  tune(id) {
    TVP.id = id; TVP.k = 0; TVP.ready = false; TVP.req = 'r' + Date.now() + Math.random().toString(36).slice(2, 6);
    clearTimeout(TVP.wd);
    TVP.loading(true); TVP.banner(true);
    TVP.mxStopEnc();
    if (id.indexOf('mx:') === 0) { TVP.tuneMx(id, TVP.req); return; }
    Live.tune(TVP.req, id, 0);
  },
  // ---- midnox TV (eigene Sammlung) ----
  mx: null,
  async tuneMx(id, req, t) {
    try {
      const r = await MXTV.tune(id, t);
      if (req !== TVP.req || !TVP.active) { if (r.info.method === 'Transcode') EMBY.stopEncoding(r.info.playSessionId); return; }
      TVP.mx = r; TVP.cid = id; TVP.mac = -1; TVP.label = 'midnox TV' + (r.it.s ? ' · ' + r.it.s : '');
      ADAPTER.play({ url: r.info.url, hls: r.info.hls, startMs: r.off, video: true, durMs: r.it.d, audioLang: EMBY.settings().audioLang });
      TVP.banner(false);
      clearTimeout(TVP.wd); TVP.wd = setTimeout(() => { if (!TVP.ready && TVP.active && req === TVP.req) TVP.mxFail('keine Antwort'); }, 25000);
    } catch (e) { if (req === TVP.req) TVP.mxFail(e.message || String(e)); }
  },
  mxFail(why) {
    if (!TVP.active) return;
    const stop = TVP.mx ? TVP.mx.stop : Date.now();
    UI.toast('Sendung nicht abspielbar (' + why + ') – nächste Sendung kommt …', { icon: '⏭️' });
    TVP.mxStopEnc();
    TVP.ready = false; TVP.req = 'r' + Date.now(); const req = TVP.req;
    setTimeout(() => { if (TVP.active && req === TVP.req) TVP.tuneMx(TVP.id, req, stop + 1000); }, 2500);
  },
  mxStopEnc() { if (TVP.mx && TVP.mx.info && TVP.mx.info.method === 'Transcode') EMBY.stopEncoding(TVP.mx.info.playSessionId); TVP.mx = null; },
  onTune(e) {
    if (!TVP.active || e.req !== TVP.req) return;
    if (e.end) { TVP.loading(false); TVP.fail('Sender nicht erreichbar', 'Alle Wege (MACs und gleichnamige Sender) wurden probiert.'); return; }
    if (e.err) { TVP.next(e.err); return; }
    TVP.k = e.k; TVP.cid = e.cid; TVP.mac = e.mac; TVP.label = e.label || '';
    TVP.ready = false;
    ADAPTER.play({ url: e.url, ua: e.ua, ref: e.ref, video: true, startMs: 0, durMs: 0, live: true });
    TVP.banner(false);
    clearTimeout(TVP.wd); TVP.wd = setTimeout(() => { if (!TVP.ready && TVP.active) TVP.next('keine Antwort'); }, 15000);
  },
  next(why) {
    if (!TVP.active) return;
    clearTimeout(TVP.wd);
    const k = TVP.k + 1; TVP.k = k;
    TVP.status('Versuche anderen Weg … (' + why + ')');
    Live.tune(TVP.req, TVP.id, k);
  },
  ev(e) {
    if (e.e === 'ready') { if (!TVP.ready) { TVP.ready = true; clearTimeout(TVP.wd); TVP.loading(false); TVP.status(''); localStorage.setItem('lv_last', TVP.id); if (!TVP.mx) Live.ok(TVP.cid || TVP.id, TVP.mac); TVP.hideSoon(); } }
    else if (TVP.mx && (e.e === 'ended' || (e.e === 'tick' && Date.now() > TVP.mx.stop + 3000))) { const req = TVP.req = 'r' + Date.now(); TVP.ready = false; TVP.mxStopEnc(); TVP.tuneMx(TVP.id, req); TVP.banner(false); }
    else if (TVP.mx && e.e === 'error') TVP.mxFail((e.msg || 'Fehler').replace(/^ERROR_CODE_/, ''));
    else if (e.e === 'error' || e.e === 'ended') { if (TVP.active) TVP.next(e.e === 'ended' ? 'Stream beendet' : (e.msg || 'Fehler').replace(/^ERROR_CODE_/, '')); }
    else if (e.e === 'tick') { if (e.state === 3 && !TVP.ready) TVP.ev({ e: 'ready' }); TVP.loading(e.state === 2 && e.wantPlay); }
  },
  async fail(title, text) { await UI.alert(title, text); if (TVP.active && !TVP.ready) TVP.stop(); },
  zap(d) {
    const i = TVP.ids.indexOf(TVP.pend || TVP.id); if (i < 0) return;
    const n = TVP.ids[(i + d + TVP.ids.length) % TVP.ids.length];
    TVP.pend = n; TVP.banner(true, n);
    clearTimeout(TVP.zapT); TVP.zapT = setTimeout(() => { const t = TVP.pend; TVP.pend = null; if (t && t !== TVP.id) TVP.tune(t); }, 650);
  },
  stop(silent) {
    if (!TVP.active) return;
    clearTimeout(TVP.wd); clearTimeout(TVP.zapT); clearTimeout(TVP.hideT);
    TVP.active = false; TVP.req = ''; TVP.listOn = false;
    ADAPTER.stop(); TVP.mxStopEnc();
    document.body.classList.remove('playing', 'tvlive'); document.documentElement.classList.remove('playing');
    const o = $('#osd'); o.className = 'off'; o.innerHTML = '';
    if (HAS_ANDROID) try { Android.keepAwake(false); } catch (e) {}
    if (silent) return;
    const t = ROUTER.top();
    if (t && t.name === 'live' && t.refresh) { const r = t.el.querySelector('.lvrow[data-id="' + CSS.escape(TVP.id) + '"]'); t.refresh(true); NAV.focus(r || NAV.first(t.el)); }
    else if (t) NAV.focus(t._focus && t._focus.isConnected ? t._focus : NAV.first(t.el));
  },
  // ---- Einblendungen ----
  loading(on) { const o = $('#osd'); if (o) o.classList.toggle('loading', !!on); },
  status(t) { const s = $('#osd .tvstat'); if (s) s.textContent = t; },
  banner(show, previewId) {
    const o = $('#osd'); if (!o || !TVP.active) return;
    const c = LIVE.byId[previewId || TVP.id]; if (!c) return;
    const nn = LIVE.nowNext(c.id);
    let b = $('.tvb', o); if (!b) { b = h('div', 'tvb'); o.appendChild(b); }
    if (!$('.tvspin', o)) o.appendChild(h('div', 'tvspin', '<i></i>'));
    b.innerHTML = '';
    b.appendChild(LIVE.logo(c, 'big'));
    const t = h('div', 'tvt');
    t.innerHTML = '<div class="l1"><span class="no">' + (c.num || '') + '</span><b>' + esc(c.name) + '</b>' + (LIVE.isFav(c.id) ? '<span class="star">★</span>' : '') + '<span class="clock">' + LIVE.hm(Date.now()) + '</span></div>' +
      (nn.now ? '<div class="l2"><span class="tm">' + LIVE.hm(nn.now[0]) + '–' + LIVE.hm(nn.now[1]) + '</span>' + esc(nn.now[2]) + '</div><i class="pb"><em style="width:' + LIVE.pct(nn.now) + '%"></em></i>' : '<div class="l2 mut">Keine Programminfos</div>') +
      (nn.next ? '<div class="l3">Danach ' + LIVE.hm(nn.next[0]) + ' · ' + esc(nn.next[2]) + '</div>' : '') +
      '<div class="l4"><span class="tvstat"></span><span class="src">' + esc(previewId ? '' : TVP.label) + '</span><span class="hint">' + (document.body.classList.contains('touch') ? '' : '▲▼ Sender · OK Senderliste · MENU Favorit · ◀▶ Info') + '</span></div>';
    b.appendChild(t);
    if (document.body.classList.contains('touch')) {
      const tb = h('div', 'tvtouch');
      tb.appendChild(onOk(h('div', 'btn', '▲'), () => TVP.zap(-1)));
      tb.appendChild(onOk(h('div', 'btn', '▼'), () => TVP.zap(1)));
      tb.appendChild(onOk(h('div', 'btn', '☰'), () => TVP.chanList()));
      tb.appendChild(onOk(h('div', 'btn', '✕'), () => TVP.stop()));
      b.appendChild(tb);
    }
    o.classList.add('show'); o.classList.remove('hid');
    if (!show) TVP.hideSoon();
  },
  hideSoon() { clearTimeout(TVP.hideT); TVP.hideT = setTimeout(() => { const o = $('#osd'); if (o && TVP.ready && !TVP.listOn && !TVP.pend) o.classList.add('hid'); }, 5000); },
  showInfo() {
    const c = LIVE.byId[TVP.id]; if (!c) return;
    const nn = LIVE.nowNext(c.id);
    if (!nn.now) { TVP.banner(false); return; }
    UI.modal((box, close) => {
      box.appendChild(h('h2', '', esc(nn.now[2])));
      box.appendChild(h('p', '', esc(c.name) + ' · ' + LIVE.hm(nn.now[0]) + '–' + LIVE.hm(nn.now[1])));
      if (nn.now[3]) box.appendChild(h('p', 'desc', esc(nn.now[3])));
      if (nn.next) box.appendChild(h('p', 'mut', 'Danach: ' + LIVE.hm(nn.next[0]) + ' · ' + esc(nn.next[2])));
      const b = h('div', 'btns'); b.appendChild(onOk(h('div', 'btn pri', 'OK'), () => close())); box.appendChild(b);
    });
  },
  chanList() {
    const o = $('#osd'); if (!o) return;
    let p = $('.tvlist', o);
    if (p) { p.remove(); TVP.listOn = false; o.classList.remove('listing'); NAV.cur = null; TVP.banner(false); return; }
    TVP.listOn = true; clearTimeout(TVP.hideT); o.classList.add('listing');
    p = h('div', 'tvlist'); const l = h('div', 'tvll'); l.setAttribute('data-vs', 'keep'); l.dataset.margin = '1'; p.appendChild(l); o.appendChild(p);
    const ids = TVP.ids.slice(0, 500); LIVE.fetchEpg(ids.slice(0, 80));
    let focusEl = null;
    ids.forEach((id, i) => {
      const c = LIVE.byId[id]; if (!c) return;
      const nn = i < 80 ? LIVE.nowNext(id) : {};
      const r = h('div', 'tvli' + (id === TVP.id ? ' cur' : '')); r.setAttribute('data-vsi', '');
      r.appendChild(LIVE.logo(c));
      r.appendChild(h('div', 'txt', '<b>' + (c.num ? '<i>' + c.num + '</i>' : '') + esc(c.name) + '</b>' + (nn.now ? '<small>' + esc(nn.now[2]) + '</small>' : '')));
      onOk(r, () => { p.remove(); TVP.listOn = false; o.classList.remove('listing'); if (id !== TVP.id) TVP.tune(id); else TVP.banner(false); });
      r._menu = () => { LIVE.toggleFav(id); };
      l.appendChild(r); if (id === TVP.id) focusEl = r;
    });
    NAV.focus(focusEl || $('.f', l));
  },
  key(k, repeat) {
    if (TVP.listOn) {
      if (k === 'back' || k === 'left') { TVP.chanList(); return; }
      if (k === 'up' || k === 'down') { NAV.move(k); return; }
      if (k === 'ok' && NAV.cur && NAV.cur._ok) { NAV.cur._ok(); return; }
      if (k === 'menu' && NAV.cur && NAV.cur._menu) { NAV.cur._menu(); return; }
      return;
    }
    if (k === 'up' || k === 'next' || k === 'chup') { TVP.zap(-1); return; }
    if (k === 'down' || k === 'prev' || k === 'chdown') { TVP.zap(1); return; }
    if (k === 'ok') { if ($('#osd').classList.contains('hid')) { TVP.banner(false); return; } TVP.chanList(); return; }
    if (k === 'left' || k === 'right' || k === 'info') { TVP.showInfo(); return; }
    if (k === 'menu') { LIVE.toggleFav(TVP.id); TVP.banner(false); return; }
    if (k === 'back' || k === 'stop') { TVP.stop(); return; }
    if (k === 'playpause' || k === 'pause' || k === 'play') { if (TVP._paused) { ADAPTER.resume(); TVP._paused = false; } else { ADAPTER.pause(); TVP._paused = true; } TVP.banner(false); return; }
  },
};
window.LIVE = LIVE; window.TVP = TVP; window.LIVESET = LIVESET;

// ---------------------------------------------------------------- Live-TV-Statistik & Errungenschaften
/** Zählt nur auf dem Gerät mit, was live geschaut wird (je Jahr): Sender, Uhrzeit, Sparte, Umschalten. */
const LVSTAT = {
  CATS: {
    sport: ['sport', 'bundesliga', 'fußball', 'fussball', 'dazn', 'eurosport', 'tennis', 'formel 1', 'formel1', 'f1 ', 'motogp', 'handball', 'basketball', 'eishockey', 'nfl', 'nba', 'nhl', 'champions league', 'europa league', 'dfb', 'pokal', 'boxen', 'ufc', 'darts', 'biathlon', 'skispringen', 'olympia', 'wm ', 'em ', 'liga', 'radsport', 'tour de france', 'magenta sport', 'sportschau', 'sportstudio'],
    news: ['nachrichten', 'news', 'tagesschau', 'tagesthemen', 'heute journal', 'heute-journal', 'n-tv', 'ntv', 'welt', 'phoenix', 'cnn', 'bbc', 'euronews', 'brennpunkt', 'pressekonferenz', 'wetter', 'rtl aktuell', 'sat.1 nachrichten'],
    kids: ['kinder', 'kids', 'kika', 'nick', 'disney', 'toggo', 'super rtl', 'cartoon', 'junior', 'maus', 'sesamstraße', 'peppa', 'paw patrol', 'boomerang', 'baby'],
    doc: ['doku', 'documentary', 'discovery', 'dmax', 'n24', 'history', 'terra x', 'national geographic', 'nat geo', 'natgeo', 'galileo', 'planet', 'universum', 'wissen', 'animal', 'spiegel tv', 'zdfinfo', 'ard-alpha', 'curiosity'],
    music: ['musik', 'music', 'mtv', 'viva', 'deluxe', 'konzert', 'schlager', 'radio', 'hits', 'vh1'],
    movie: ['film', 'kino', 'cinema', 'movie', 'spielfilm', 'tnt', 'sky cinema', 'filmabend', 'tatort'],
  },
  NAMES: { sport: 'Sport', news: 'Nachrichten', kids: 'Kinder', doc: 'Doku & Wissen', music: 'Musik', movie: 'Filme', show: 'Shows & Serien' },
  d: null, key: '', sessId: '', sessS: 0, t: 0,
  store() {
    const k = 'lvst_' + ((window.EMBY && EMBY.user && EMBY.user.id) || 'x');
    if (LVSTAT.key !== k) { LVSTAT.key = k; try { LVSTAT.d = JSON.parse(localStorage.getItem(k) || 'null'); } catch (e) { LVSTAT.d = null; } if (!LVSTAT.d || !LVSTAT.d.y) LVSTAT.d = { y: {}, maxSess: 0 }; }
    return LVSTAT.d;
  },
  save() { try { localStorage.setItem(LVSTAT.key, JSON.stringify(LVSTAT.d)); } catch (e) {} },
  year(d) { const y = String(new Date().getFullYear()); return d.y[y] || (d.y[y] = { s: 0, ch: {}, h: new Array(24).fill(0), cat: {}, zaps: 0, pt: {}, am: {}, mx: 0 }); },
  cat(c, p) {
    const has = (txt, list) => { txt = ' ' + (txt || '').toLowerCase() + ' '; return list.some(w => txt.indexOf(w) >= 0); };
    for (const src of [c.group + ' ' + c.name, p ? p[2] + ' ' + (p[3] || '').slice(0, 120) : ''])   // Spartensender zuerst, sonst die laufende Sendung
      for (const k of ['sport', 'news', 'kids', 'doc', 'music', 'movie']) if (src.trim() && has(src, LVSTAT.CATS[k])) return k;
    return 'show';
  },
  /** alle 10 s während Live-TV läuft */
  tick() {
    const now = Date.now(), dt = Math.min(15, (now - (LVSTAT.t || now)) / 1000); LVSTAT.t = now;
    if (!TVP.active || !TVP.ready || document.hidden || (window.ADAPTER && ADAPTER.playing === false) || !EMBY.user || !dt) return;
    const id = TVP.id, c = LIVE.byId[id]; if (!c) return;
    const d = LVSTAT.store(), y = LVSTAT.year(d), dt0 = new Date(), hr = dt0.getHours(), day = STATS.dayKey(dt0);
    const p = LIVE.nowNext(id).now, k = LVSTAT.cat(c, p);
    y.s += dt; y.h[hr] += dt; y.cat[k] = (y.cat[k] || 0) + dt;
    const e = y.ch[id] || (y.ch[id] = { n: c.name, s: 0 }); e.s += dt; e.n = c.name;
    if (c.src === 'mx') y.mx += dt;
    if (hr >= 20 && hr < 23) y.pt[day] = (y.pt[day] || 0) + dt;
    if (hr >= 6 && hr < 9) y.am[day] = (y.am[day] || 0) + dt;
    if (LVSTAT.sessId !== id) { LVSTAT.sessId = id; LVSTAT.sessS = 0; }
    LVSTAT.sessS += dt; if (LVSTAT.sessS > d.maxSess) d.maxSess = LVSTAT.sessS;
    if ((LVSTAT._n = (LVSTAT._n || 0) + 1) % 3 === 0) { LVSTAT.save(); STATS.soon(); }
  },
  zap() { const d = LVSTAT.store(); LVSTAT.year(d).zaps++; },
  /** Kennzahlen für ein Jahr (0 = insgesamt) */
  calc(year) {
    const d = LVSTAT.store(), ys = year ? [d.y[String(year)]].filter(Boolean) : Object.values(d.y);
    const r = { s: 0, ch: {}, h: new Array(24).fill(0), cat: {}, zaps: 0, pt: 0, am: 0, mx: 0 };
    for (const y of ys) {
      r.s += y.s; r.zaps += y.zaps; r.mx += y.mx || 0;
      for (const i in y.ch) { const e = r.ch[i] || (r.ch[i] = { id: i, n: y.ch[i].n, s: 0 }); e.s += y.ch[i].s; }
      y.h.forEach((v, i) => { r.h[i] += v; }); for (const k in y.cat) r.cat[k] = (r.cat[k] || 0) + y.cat[k];
      r.pt += Object.values(y.pt).filter(v => v >= 900).length; r.am += Object.values(y.am).filter(v => v >= 600).length;
    }
    r.top = Object.values(r.ch).sort((a, b) => b.s - a.s);
    r.cats = Object.keys(r.cat).map(k => ({ k, n: LVSTAT.NAMES[k] || k, s: r.cat[k] })).sort((a, b) => b.s - a.s);
    let ph = 0; r.h.forEach((v, i) => { if (v > r.h[ph]) ph = i; }); r.peak = r.s ? ph : -1;
    r.H = (x) => Math.floor(x / 3600);
    r.hours = r.H(r.s); r.chans = r.top.filter(c => c.s >= 300).length; r.fav = r.top[0] ? r.H(r.top[0].s) : 0;
    r.maxSess = d.maxSess || 0;
    return r;
  },
  /** Uhrzeit als Wort */
  when(h) { return h < 0 ? '–' : h < 6 ? 'nachts' : h < 11 ? 'morgens' : h < 14 ? 'mittags' : h < 18 ? 'nachmittags' : h < 23 ? 'abends' : 'spätabends'; },
};
window.LVSTAT = LVSTAT;
if (HAS_LIVE) {
  setInterval(LVSTAT.tick, 10000);
  const z0 = TVP.zap; TVP.zap = function (d) { LVSTAT.zap(); return z0.apply(this, arguments); };
  const L = (y) => LVSTAT.calc(y);
  ACH.push(
    { id: 'live', live: 1, icon: '📡', name: 'Fernsehgucker', d: '{n} Stunden Live-TV geschaut', lv: [2, 10, 50, 150], v: () => L(0).hours },
    { id: 'livesport', live: 1, icon: '⚽', name: 'Stadionstimme', d: '{n} Stunden Sport live', lv: [1, 5, 20, 60], v: () => L(0).H(L(0).cat.sport || 0) },
    { id: 'livenews', live: 1, icon: '📰', name: 'Bestens informiert', d: '{n} Stunden Nachrichten live', lv: [1, 5, 15, 40], v: () => L(0).H(L(0).cat.news || 0) },
    { id: 'livedoku', live: 1, icon: '🌍', name: 'Wissensdurst', d: '{n} Stunden Dokus & Wissen live', lv: [1, 5, 15, 40], v: () => L(0).H(L(0).cat.doc || 0) },
    { id: 'livesender', live: 1, icon: '🗺️', name: 'Senderjäger', d: '{n} verschiedene Sender geschaut (je 5 Min.)', lv: [5, 20, 50, 100], v: () => L(0).chans },
    { id: 'stammsender', live: 1, icon: '❤️', name: 'Stammsender', d: '{n} Stunden auf deinem Lieblingssender', lv: [3, 15, 50, 120], v: () => L(0).fav },
    { id: 'zapper', live: 1, icon: '🔀', name: 'Zapper', d: '{n}× umgeschaltet', lv: [25, 150, 500, 1500], v: () => L(0).zaps },
    { id: 'primetime', live: 1, icon: '🌙', name: 'Primetime', d: '{n} Abende live zwischen 20 und 23 Uhr', lv: [3, 10, 30, 80], v: () => L(0).pt },
    { id: 'fruehstueck', live: 1, icon: '☕', name: 'Frühstücksfernsehen', d: '{n} Morgen live zwischen 6 und 9 Uhr', lv: [1, 5, 15, 40], v: () => L(0).am },
    { id: 'sitzfleisch', live: 1, icon: '🪑', name: 'Sitzfleisch', d: '{n} Stunden am Stück auf einem Sender', lv: [1, 2, 3, 5], v: () => Math.floor(L(0).maxSess / 3600) },
    { id: 'mxtv', live: 1, icon: '📼', name: 'Dauersender', d: '{n} Stunden auf den midnox TV-Kanälen', lv: [1, 5, 20, 50], v: () => L(0).H(L(0).mx) },
  );
}
