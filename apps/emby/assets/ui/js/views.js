// midnox Media – Seiten: Einrichtung, Start, Bibliotheken, Details, Suche, Einstellungen.
'use strict';
const V = {
  /** Karte für einen Titel. kind: poster | land | square | circle */
  /** Anti-Spoiler: ungesehene Folgen ohne Bild-Details und Beschreibung. */
  spoil(item) { if (!item || item.Type !== 'Episode' || !EMBY.settings().spoiler) return false; const u = item.UserData || {}; return !u.Played && !(u.PlaybackPositionTicks > 0); },
  card(item, kind, o) {
    o = o || {};
    const c = h('div', 'card f ' + (kind || 'poster')); c.dataset.id = item.Id;
    if (item.Type === 'Movie' || item.Type === 'Series') c.dataset.rid = item.Id;
    const im = h('div', 'im'); c.appendChild(im);
    if (V.spoil(item)) c.classList.add('spoil');
    const src = kind === 'land' ? EMBY.land(item, 560) : kind === 'circle' ? EMBY.img(item, 'Primary', 260) : EMBY.img(item, 'Primary', kind === 'square' ? 360 : 320);
    if (src) { const img = new Image(); img.loading = 'lazy'; img.decoding = 'async'; img.onload = () => img.classList.add('ok'); img.onerror = () => img.remove(); img.src = src; im.appendChild(img); }
    im.appendChild(h('div', 'ph', esc(item.Name)));
    const ud = item.UserData || {};
    if (ud.PlayedPercentage > 0 && ud.PlayedPercentage < 100 && kind !== 'circle') c.appendChild(h('div', 'prog', '<i style="width:' + ud.PlayedPercentage + '%"></i>'));
    else if (ud.Played && item.Type !== 'MusicAlbum') c.appendChild(h('div', 'tag seen', ic('check', ' style="width:1.2rem;height:1.2rem"')));
    else if (item.Type === 'Series' && ud.UnplayedItemCount) c.appendChild(h('div', 'tag', String(ud.UnplayedItemCount)));
    if (o.cap !== false && (kind === 'land' || kind === 'square' || kind === 'circle' || o.cap)) {
      let t = item.Name, s = '';
      if (item.Type === 'Episode') { t = item.SeriesName || item.Name; s = 'S' + (item.ParentIndexNumber || '?') + ' F' + (item.IndexNumber || '?') + (V.spoil(item) ? '' : ' · ' + item.Name); }
      else if (item.Type === 'MusicAlbum') s = item.AlbumArtist || (item.Artists || [])[0] || '';
      else if (item.Type === 'Person' || o.role) s = o.role || item.Role || '';
      else if (item.ProductionYear) s = String(item.ProductionYear);
      c.appendChild(h('div', 'cap', '<b>' + esc(t) + '</b>' + esc(s)));
    }
    c._ok = o.ok || (() => APP.open(item));
    c._menu = o.menu || (() => V.itemMenu(item));
    if (o.focus) c._focus = () => o.focus(item, c);
    return c;
  },
  row(title, items, kind, o) {
    o = o || {};
    const r = h('div', 'row' + (kind === 'land' || kind === 'square' || kind === 'circle' || o.cap ? ' capped' : '')); r.setAttribute('data-mem', ''); r.setAttribute('data-vsi', '');
    r.appendChild(h('h2', '', esc(title) + (o.small ? ' <small>' + esc(o.small) + '</small>' : '')));
    const tr = h('div', 'track'); r.appendChild(tr);
    for (const it of items) tr.appendChild(V.card(it, kind, o));
    return r;
  },
  meta(item, opts) {
    const p = [];
    const yr = item.ProductionYear ? (item.Type === 'Series' && item.EndDate && new Date(item.EndDate).getFullYear() !== item.ProductionYear ? item.ProductionYear + '–' + new Date(item.EndDate).getFullYear() : String(item.ProductionYear)) : '';
    if (item.CommunityRating) p.push('<span class="star">★ ' + item.CommunityRating.toFixed(1) + '</span>');
    if (opts && opts.mx) p.push('<span class="mx">midnox <b>' + opts.mx.avg.toFixed(1) + '</b></span>');
    if (yr) p.push('<span>' + yr + '</span>');
    if (item.Type === 'Series' && item.ChildCount) p.push('<span>' + item.ChildCount + ' Staffel' + (item.ChildCount === 1 ? '' : 'n') + '</span>');
    if (item.RunTimeTicks && item.Type !== 'Series') p.push('<span>' + fmtDur(item.RunTimeTicks) + '</span>');
    if (item.OfficialRating) p.push('<span class="pill">' + esc(item.OfficialRating) + '</span>');
    if (item.Type === 'Episode') p.unshift('<span>S' + (item.ParentIndexNumber || '?') + ' · F' + (item.IndexNumber || '?') + '</span>');
    return '<div class="meta">' + p.join('') + '</div>';
  },
  async itemMenu(item) {
    const ud = item.UserData || {}; const playable = ['Movie', 'Episode', 'Video', 'MusicVideo'].includes(item.Type);
    const opts = [];
    if (playable) opts.push({ label: ud.PlaybackPositionTicks ? 'Fortsetzen' : 'Abspielen', value: 'play' });
    if (playable && ud.PlaybackPositionTicks) opts.push({ label: 'Von vorne abspielen', value: 'start' });
    opts.push({ label: 'Details', value: 'open' });
    if (item.Type !== 'MusicAlbum' && item.Type !== 'Audio') opts.push({ label: ud.Played ? 'Als ungesehen markieren' : 'Als gesehen markieren', value: 'played' });
    opts.push({ label: ud.IsFavorite ? 'Aus Favoriten entfernen' : 'Zu Favoriten', value: 'fav' });
    if (item.Type === 'Movie' || item.Type === 'Series') { opts.push({ label: 'Bewerten', value: 'rate' }); opts.push({ label: 'Der Community empfehlen', value: 'rec' }); }
    if (playable && window.TOUCH) opts.push({ label: '📺 Auf dem Fernseher abspielen', value: 'cast' });
    if (playable) opts.push({ label: 'Als Watch-Party starten', value: 'party' });
    const v = await UI.choose(item.Name, opts);
    if (v === 'play') APP.play(item); else if (v === 'start') APP.play(item, { fromStart: true }); else if (v === 'open') APP.open(item);
    else if (v === 'played') { await EMBY.setPlayed(item.Id, !ud.Played); ud.Played = !ud.Played; UI.toast(ud.Played ? 'Als gesehen markiert' : 'Als ungesehen markiert', { icon: '✓' }); APP.dirty = true; ROUTER.refreshTop(); }
    else if (v === 'fav') { await EMBY.setFav(item.Id, !ud.IsFavorite); ud.IsFavorite = !ud.IsFavorite; UI.toast(ud.IsFavorite ? 'Zu Favoriten hinzugefügt' : 'Aus Favoriten entfernt', { icon: '♥' }); }
    else if (v === 'rate') COMM.ratePrompt(item); else if (v === 'rec') COMM.recommend(item);
    else if (v === 'party') PARTY.create(item, ((item.UserData || {}).PlaybackPositionTicks || 0) / 10000);
    else if (v === 'cast') CAST.play(item, (item.UserData || {}).PlaybackPositionTicks || 0);
  },
};

// ---------- Einrichtung: Server ----------
ROUTER.reg('connect', async (scr, arg) => {
  scr.noRail = true; $('#rail').classList.add('hidden'); BG.set(null); BG.dim(false);
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  const w = h('div', 'welcome'); el.appendChild(w);
  w.appendChild(h('div', 'brand', '<div class="big">midnox<br><b class="grad-text">Media</b></div><p>Deine Filme, Serien und Musik von Emby oder Jellyfin – mit Community-Bewertungen, Empfehlungs-Chat und Watch-Partys zum gemeinsamen Schauen.</p>'));
  const p = h('div', 'panel2'); w.appendChild(p);
  p.appendChild(h('h2', '', 'Mit welchem Server verbinden?'));
  const list = h('div', ''); p.appendChild(list);
  const srvEl = (title, sub, icon, fn) => { const e = h('div', 'srv f', '<div class="ic">' + ic(icon) + '</div><div><b>' + esc(title) + '</b><small>' + esc(sub) + '</small></div>'); e._ok = fn; list.appendChild(e); return e; };
  const pick = async (srv) => { EMBY.use(srv); ROUTER.go('login', srv); };
  if (arg && arg.add && PROFILES.list().length) srvEl('Zurück zu den Profilen', 'Wer guckt?', 'back', () => ROUTER.go('who', null, { root: true }));
  for (const s of EMBY.servers()) srvEl(s.name, (s.kind === 'jellyfin' ? 'Jellyfin' : 'Emby') + ' · ' + s.url + ' · gespeichert', 'server', () => pick(s));
  const found = h('div', ''); list.appendChild(found);
  srvEl('Adresse eingeben', 'z. B. 192.168.178.20 oder media.meinedomain.de', 'keyboard', async () => {
    const a = await TEXT.ask('Adresse deines Emby- oder Jellyfin-Servers', { placeholder: 'IP-Adresse oder Domain', sub: 'Port 8096 wird automatisch ergänzt, https geht auch.' });
    if (!a) return; await connectTo(a);
  });
  if (HAS_ANDROID) srvEl('Mit dem Handy einrichten', 'QR-Code scannen, Adresse und Login bequem am Handy tippen', 'phone', async () => {
    const d = await PHONE.ask({ mode: 'login' }); if (!d || !d.server) return;
    try { const s = await EMBY.probe(d.server); EMBY.use(s); if (d.user) { await EMBY.login(d.user, d.pw || ''); APP.afterLogin(); } else pick(s); }
    catch (e) { UI.alert('Anmeldung fehlgeschlagen', e.message); }
  });
  const connectTo = async (a) => {
    const t = UI.toast('Suche Server …', { icon: '⏳', ms: 15000 });
    try { const s = await EMBY.probe(a); t.close(); pick(s); } catch (e) { t.close(); UI.alert('Nicht gefunden', e.message); }
  };
  scr.found = found;
  APP.discoverInto = (servers) => {
    if (!found.isConnected) return; found.innerHTML = '';
    for (const s of servers) { if (EMBY.servers().some(x => x.id === s.id)) continue; const e = h('div', 'srv f', '<div class="ic">' + ic('server') + '</div><div><b>' + esc(s.name) + '</b><small>' + esc(s.address) + ' · im Heimnetz gefunden</small></div>'); e._ok = () => connectTo(s.address); found.appendChild(e); }
  };
  if (HAS_ANDROID) Android.discover();
});

// ---------- Einrichtung: Benutzer ----------
ROUTER.reg('login', async (scr, srv) => {
  scr.noRail = true; const el = scr.el; el.style.paddingLeft = '0';
  let users = [];
  try { users = await EMBY.publicUsers(); } catch (e) {}
  el.innerHTML = '';
  const w = h('div', 'welcome'); el.appendChild(w);
  w.appendChild(h('div', 'brand', '<div class="big" style="font-size:4rem">' + esc(srv.name || 'Emby') + '</div><p>' + esc(srv.url) + '</p>'));
  const p = h('div', 'panel2'); p.style.maxWidth = '60rem'; w.appendChild(p);
  p.appendChild(h('h2', '', 'Wer schaut?'));
  const u = h('div', 'users'); p.appendChild(u);
  const doLogin = async (name, hasPw) => {
    let pw = '';
    if (hasPw) { pw = await TEXT.ask('Passwort für ' + name, { password: true, placeholder: 'Passwort' }); if (pw == null) return; }
    try { await EMBY.login(name, pw); APP.afterLogin(); }
    catch (e) { UI.alert('Anmeldung fehlgeschlagen', e.status === 401 ? 'Benutzername oder Passwort stimmt nicht.' : e.message); }
  };
  for (const x of users) {
    const c = h('div', 'ucard f'); c.appendChild(avatar(x.Name, x.PrimaryImageTag ? EMBY.api + '/Users/' + x.Id + '/Images/Primary?maxWidth=300&tag=' + x.PrimaryImageTag : ''));
    c.appendChild(h('div', 'n', esc(x.Name))); c._ok = () => doLogin(x.Name, x.HasPassword !== false && x.HasConfiguredPassword !== false); u.appendChild(c);
  }
  const other = h('div', 'ucard f'); const a = avatar('+'); a.innerHTML = ic('plus', ' style="width:4rem;height:4rem"'); a.style.background = 'rgba(255,255,255,.08)'; other.appendChild(a); other.appendChild(h('div', 'n', 'Anderer Name'));
  other._ok = async () => { const n = await TEXT.ask('Benutzername', { placeholder: 'Dein Benutzername' }); if (n) doLogin(n, true); };
  u.appendChild(other);
  if (HAS_ANDROID) { const ph = h('div', 'ucard f'); const a2 = avatar('📱'); a2.innerHTML = ic('phone', ' style="width:4rem;height:4rem"'); a2.style.background = 'rgba(255,255,255,.08)'; ph.appendChild(a2); ph.appendChild(h('div', 'n', 'Per Handy'));
    ph._ok = async () => { const d = await PHONE.ask({ mode: 'login', server: srv.url }); if (d && d.user) { try { await EMBY.login(d.user, d.pw || ''); APP.afterLogin(); } catch (e) { UI.alert('Anmeldung fehlgeschlagen', e.status === 401 ? 'Benutzername oder Passwort stimmt nicht.' : e.message); } } };
    u.appendChild(ph); }
  const back = h('div', 'btns'); back.style.marginTop = '3rem'; back.appendChild(onOk(h('div', 'btn', 'Anderer Server'), () => ROUTER.go('connect', null, { root: true }))); p.appendChild(back);
});

// ---------- Titelmusik auf Detailseiten ----------
const THEMESONG = {
  a: null, id: null, t: 0, f: 0,
  async play(item) {
    clearTimeout(THEMESONG.t);
    if (EMBY.settings().themeSongs === false || (window.MUSIC && MUSIC.active) || PLAYER.active || !item) return;
    const sid = item.Id; if (THEMESONG.id === sid && THEMESONG.a) return;
    THEMESONG.stop(); THEMESONG.id = sid;
    try {
      const r = await EMBY.get('/Items/' + sid + '/ThemeSongs', { UserId: EMBY.user.id, InheritFromParent: true });
      const s = (r.Items || [])[0]; if (!s || THEMESONG.id !== sid || PLAYER.active) return;
      const a = new Audio(EMBY.api + '/Audio/' + s.Id + '/stream?static=true&' + EMBY.tq());
      a.volume = 0; THEMESONG.a = a; a.play().catch(() => {});
      const tgt = 0.35; clearInterval(THEMESONG.f); THEMESONG.f = setInterval(() => { if (THEMESONG.a !== a) return; a.volume = Math.min(tgt, a.volume + 0.02); if (a.volume >= tgt) clearInterval(THEMESONG.f); }, 100);
    } catch (e) {}
  },
  stopSoon() { clearTimeout(THEMESONG.t); THEMESONG.t = setTimeout(() => THEMESONG.stop(true), 250); },
  stop(fade) {
    clearTimeout(THEMESONG.t); clearInterval(THEMESONG.f); const a = THEMESONG.a; THEMESONG.a = null; THEMESONG.id = null; if (!a) return;
    if (!fade) { a.pause(); return; }
    const iv = setInterval(() => { a.volume = Math.max(0, a.volume - 0.03); if (a.volume <= 0.01) { clearInterval(iv); a.pause(); } }, 60);
  },
};
window.THEMESONG = THEMESONG;

// ---------- Startseite ----------
const GENRE_COLORS = [['#7c3aed', '#3b5bf0'], ['#f43f5e', '#f97316'], ['#0ea5e9', '#14b8a6'], ['#10b981', '#84cc16'], ['#ec4899', '#a855f7'], ['#f59e0b', '#ef4444'], ['#6366f1', '#06b6d4'], ['#64748b', '#334155']];
ROUTER.reg('home', async (scr) => {
  BG.dim(false);
  const el = scr.el; el.style.paddingLeft = '0';
  const hero = h('div', 'hero'); const info = h('div', 'hinfo'); hero.appendChild(info);
  const spot = h('div', 'spot'); spot.setAttribute('data-mem', ''); hero.appendChild(spot);
  const sbtns = h('div', 'btns'); const dots = h('div', 'dots'); spot.appendChild(sbtns); spot.appendChild(dots);
  const rows = h('div', 'rows'); rows.setAttribute('data-vs', 'anchor'); rows.dataset.at = '27.5'; rows.setAttribute('data-mem', '');
  const rowsWrap = h('div', ''); rowsWrap.style.cssText = 'position:absolute;left:0;right:0;top:0;bottom:0'; rowsWrap.appendChild(rows);
  let spots = [], si = 0, spotMode = false, rotT = 0;
  const paint = (it, kickerOverride) => {
    const logo = EMBY.img(it.Type === 'Episode' ? Object.assign({}, it, { ImageTags: {} }) : it, 'Logo', 700);
    const kicker = kickerOverride || (it.Type === 'Episode' ? 'Folge' : it.Type === 'Series' ? 'Serie' : it.Type === 'MusicAlbum' ? 'Album' : it.Type === 'Movie' ? 'Film' : '');
    const title = it.Type === 'Episode' ? (it.SeriesName || it.Name) : it.Name;
    info.innerHTML = (kicker ? '<div class="kicker"><i></i>' + kicker + '</div>' : '') + (logo ? '<img class="logoimg" src="' + logo + '" onerror="this.outerHTML=\'<h1>' + esc(title).replace(/'/g, '') + '</h1>\'">' : '<h1>' + esc(title) + '</h1>')
      + V.meta(it, { mx: COMM.cache[it.Id] || (it.SeriesId ? COMM.cache[it.SeriesId] : null) }) + (V.spoil(it) ? '<div class="ov">S' + (it.ParentIndexNumber || '?') + ' F' + (it.IndexNumber || '?') + ' · 🙈 ohne Spoiler</div>' : it.Overview ? '<div class="ov">' + esc(it.Type === 'Episode' ? it.Name + ' – ' + it.Overview : it.Overview) + '</div>' : '');
    info.classList.remove('swap'); void info.offsetWidth; info.classList.add('swap');
    BG.set(EMBY.backdrop(it) || (it.Type === 'MusicAlbum' ? EMBY.img(it, 'Primary', 1200) : ''));
  };
  const leaveSpot = () => { spotMode = false; hero.classList.remove('spotmode'); for (const b of sbtns.children) b.classList.add('dis'); };
  const setHero = debounce((it) => { if (it && !spotMode) paint(it); }, 180);
  const focus = (it) => { leaveSpot(); clearTimeout(rotT); setHero(it); };
  // --- Spotlight ---
  const showSpot = (i, keepFocus) => {
    if (!spots.length) return; si = (i + spots.length) % spots.length; const it = spots[si];
    paint(it, 'Spotlight · ' + (it.Type === 'Series' ? 'Serie' : 'Film'));
    const pos = (it.UserData || {}).PlaybackPositionTicks;
    const had = NAV.cur && sbtns.contains(NAV.cur) ? Array.from(sbtns.children).indexOf(NAV.cur) : -1;
    sbtns.innerHTML = '';
    const b1 = onOk(h('div', 'btn pri', ic('play') + (pos ? 'Fortsetzen' : 'Abspielen')), () => APP.play(it));
    const b2 = onOk(h('div', 'btn', ic('info') + 'Details'), () => APP.open(it));
    const b3 = onOk(h('div', 'btn icon', ic('next')), () => { showSpot(si + 1, true); });
    for (const b of [b1, b2, b3]) { b._focus = enterSpot; sbtns.appendChild(b); }
    dots.innerHTML = spots.map((_, k) => '<i class="' + (k === si ? 'on' : '') + '"></i>').join('');
    if (keepFocus && had >= 0) NAV.focus(sbtns.children[had]);
    clearTimeout(rotT); if (spotMode) rotT = setTimeout(() => { if (spotMode && ROUTER.top() === scr && !$('#layer .modal') && !SAVER.on) showSpot(si + 1, true); }, 9000);
  };
  const enterSpot = () => {
    if (!spotMode) { spotMode = true; hero.classList.add('spotmode'); showSpot(si, true); }
    for (const b of sbtns.children) b.classList.remove('dis');
    rows._ty = 34 * rem(); rows.style.transform = 'translateY(' + rows._ty + 'px)'; for (const r of rows.children) r.classList.remove('gone');
  };
  const rowList = () => Array.from(rows.children).filter(r => r.classList.contains('row') && r.querySelector('.f'));
  const pick = (r) => (r._last && r._last.isConnected && r.contains(r._last)) ? r._last : r.querySelector('.f');
  scr.key = (k) => {
    const cur = NAV.cur; if (!cur) return false;
    const inSpot = sbtns.contains(cur);
    if (inSpot && (k === 'ff' || k === 'rew')) { showSpot(si + (k === 'ff' ? 1 : -1), true); return true; }
    if (inSpot && k === 'down') { const r = rowList()[0]; if (r) NAV.focus(pick(r)); return true; }
    if (inSpot && k === 'up') return true;
    if (inSpot && k === 'left' && cur === sbtns.firstChild) return false;
    const row = rows.contains(cur) ? cur.closest('.row') : null;
    if (row && (k === 'up' || k === 'down')) {
      const list = rowList(); const i = list.indexOf(row); const to = list[i + (k === 'up' ? -1 : 1)];
      if (to) NAV.focus(pick(to)); else if (k === 'up' && spots.length) { for (const b of sbtns.children) b.classList.remove('dis'); NAV.focus(sbtns.firstChild); }
      return true;
    }
    return false;
  };
  rows._onScroll = (row) => { let after = false; for (const r of rows.children) { if (r === row) after = true; r.classList.toggle('gone', !after); } };
  const actCard = (a) => {
    const c = h('div', 'card act f'); const p = h('div', 'p'); p.style.backgroundImage = 'url("' + EMBY.api + '/Items/' + a.id + '/Images/Primary?maxWidth=200")'; c.appendChild(p);
    const t = h('div', 'tx'); const w = h('div', 'who'); w.appendChild(avatar(a.name)); w.appendChild(h('span', '', esc(a.name) + (a.stars ? ' <b class="st">★ ' + a.stars + '</b>' : ' <b class="st">empfiehlt</b>')));
    t.appendChild(w); t.appendChild(h('div', 'ti', esc(a.title))); t.appendChild(h('div', 'cm', esc(a.text || (a.stars ? STAR_WORDS[a.stars] : '')) + ' · ' + ago(a.t)));
    c.appendChild(t); c._ok = () => APP.openId(a.id);
    c._focus = async () => { leaveSpot(); clearTimeout(rotT);
      try { if (!c._it) c._it = ((await EMBY.items({ Ids: a.id, Fields: FIELDS })).Items || [])[0]; if (c._it && NAV.cur === c) setHero(c._it); } catch (e) {} };
    return c;
  };
  const load = async () => {
    const U = EMBY.user.id;
    const tasks = [EMBY.resume().catch(() => ({ Items: [] })), EMBY.nextUp().catch(() => ({ Items: [] })),
      EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie,Series', SortBy: 'DateCreated', SortOrder: 'Descending', ImageTypes: 'Backdrop', Limit: 14 }).catch(() => ({ Items: [] })),
      EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie,Series', Filters: 'IsFavorite', SortBy: 'DateCreated', SortOrder: 'Descending', Limit: 24 }).catch(() => ({ Items: [] })),
      EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie', Filters: 'IsPlayed', SortBy: 'DatePlayed', SortOrder: 'Descending', Limit: 1 }).catch(() => ({ Items: [] }))];
    const libs = EMBY.views.filter(v => ['movies', 'tvshows', 'music', 'homevideos', 'musicvideos', 'boxsets', ''].indexOf(v.CollectionType || '') >= 0);
    for (const v of libs) tasks.push(EMBY.latest(v.Id, 20).catch(() => []));
    const [resume, next, spotR, favs, lastPlayed, ...latest] = await Promise.all(tasks);
    spots = (spotR.Items || []).filter(i => !(i.UserData || {}).Played).slice(0, 8);
    if (spots.length < 3) spots = (spotR.Items || []).slice(0, 8);
    rows.innerHTML = '';
    const resumeIds = new Set((resume.Items || []).map(i => i.Id));
    if ((resume.Items || []).length) rows.appendChild(V.row('Weiterschauen', resume.Items, 'land', { focus }));
    const nx = (next.Items || []).filter(i => !resumeIds.has(i.Id));
    if (nx.length) rows.appendChild(V.row('Nächste Folgen', nx, 'land', { focus }));
    const mo = new Date().getMonth(), dd = new Date().getDate();
    if (mo === 11 || (mo === 0 && dd <= 20)) {
      const wy = mo === 11 ? new Date().getFullYear() : new Date().getFullYear() - 1;
      const wr = h('div', 'row'); wr.setAttribute('data-mem', ''); wr.setAttribute('data-vsi', ''); wr.appendChild(h('h2', '', '✨ Dein Jahr ' + wy)); const tr = h('div', 'track'); wr.appendChild(tr);
      const c = h('div', 'card wrapcard f', '<div class="yr">' + wy + '</div><div><b>Dein midnox-Rückblick</b><small>Stunden, Lieblingsserie, Gucker-Typ, Errungenschaften – und wie die Runde geschaut hat.</small></div>');
      c._ok = () => ROUTER.go('wrapped', { year: wy }); c._focus = () => focus(null); tr.appendChild(c);
      const c2 = h('div', 'card wrapcard alt f', '<div class="yr">🏆</div><div><b>Errungenschaften</b><small>Deine Abzeichen und die Rangliste der Runde.</small></div>');
      c2._ok = () => ROUTER.go('me', null, { root: true }); c2._focus = () => focus(null); tr.appendChild(c2);
      rows.appendChild(wr);
    }
    if (COMM.parties && COMM.parties.length) {
      const pr = h('div', 'row'); pr.setAttribute('data-mem', ''); pr.setAttribute('data-vsi', ''); pr.appendChild(h('h2', '', '🍿 Watch-Partys jetzt')); const tr = h('div', 'track'); pr.appendChild(tr);
      for (const p of COMM.parties) { const c = h('div', 'party-card f'); c.style.width = '40rem'; const im = h('div', 'p'); im.style.backgroundImage = 'url("' + EMBY.api + '/Items/' + p.item + '/Images/Primary?maxWidth=220")'; c.appendChild(im); c.appendChild(h('div', '', '<b>' + esc((p.meta && p.meta.name) || '') + '</b><small>👑 ' + esc(p.host) + ' · ' + p.n + ' dabei</small><small>OK = beitreten</small>')); c._ok = () => PARTY.join(p); c._focus = () => focus(null); tr.appendChild(c); }
      rows.appendChild(pr);
    }
    if ((favs.Items || []).length) rows.appendChild(V.row('Meine Liste', favs.Items, 'poster', { focus, small: 'deine Favoriten' }));
    const addLatest = (types) => libs.forEach((v, i) => {
      if (types.indexOf(v.CollectionType || '') < 0) return;
      const items = latest[i] || []; if (!items.length) return;
      const kind = v.CollectionType === 'music' ? 'square' : 'poster';
      rows.appendChild(V.row((v.CollectionType === 'music' ? 'Neue Musik' : 'Neu bei ' + v.Name), items, kind, { focus }));
    });
    addLatest(['movies', 'homevideos', 'musicvideos', 'boxsets', '']);
    const lp = (lastPlayed.Items || [])[0];
    let commRow = null;
    if (COMM.ok) {
      try {
        const t = await COMM.call('top');
        const ids = t.top.slice(0, 20).map(x => x.id);
        if (ids.length) {
          const r = await EMBY.items({ Ids: ids.join(','), Fields: FIELDS });
          const by = {}; for (const it of r.Items || []) by[it.Id] = it;
          const its = ids.map(id => by[id]).filter(Boolean);
          if (its.length) rows.appendChild(V.row('Community-Favoriten', its, 'poster', { focus, small: 'von euch am besten bewertet' }));
        }
        const acts = t.recent.map(x => ({ id: x.id, name: x.name, stars: x.stars, text: x.comment, title: x.meta.name, t: x.t }));
        try { const ch = await COMM.call('chat'); for (const m of ch.msgs) if (m.item && !m.del) acts.push({ id: m.item.id, name: m.name, stars: 0, text: m.text, title: m.item.name, t: m.t }); } catch (e) {}
        acts.sort((x, y) => y.t - x.t);
        if (acts.length) { commRow = h('div', 'row'); commRow.setAttribute('data-mem', ''); commRow.setAttribute('data-vsi', ''); commRow.appendChild(h('h2', '', 'Neu in der Community <small>Bewertungen & Empfehlungen</small>')); const tr = h('div', 'track'); commRow.appendChild(tr); for (const a of acts.slice(0, 16)) tr.appendChild(actCard(a)); }
      } catch (e) {}
    }
    if (lp) {
      try { const sim = ((await EMBY.similar(lp.Id)).Items || []).filter(i => !(i.UserData || {}).Played); if (sim.length) rows.appendChild(V.row('Weil du „' + lp.Name + '“ geschaut hast', sim, 'poster', { focus })); } catch (e) {}
    }
    addLatest(['tvshows']);
    if (commRow) rows.appendChild(commRow);
    addLatest(['music']);
    const mv = EMBY.viewsOf('movies')[0];
    if (mv) {
      try {
        const g = ((await EMBY.genres(mv.Id)).Items || []).slice(0, 14);
        if (g.length) {
          const gr = h('div', 'row'); gr.setAttribute('data-mem', ''); gr.setAttribute('data-vsi', ''); gr.appendChild(h('h2', '', 'Genres')); const tr = h('div', 'track'); gr.appendChild(tr);
          g.forEach((x, i) => { const c = h('div', 'card genre f', '<span>' + esc(x.Name) + '</span>'); const col = GENRE_COLORS[i % GENRE_COLORS.length]; c.style.background = 'linear-gradient(135deg,' + col[0] + ',' + col[1] + ')'; c._ok = () => ROUTER.go('movies', { genre: x.Name }); c._focus = () => focus(null); tr.appendChild(c); });
          rows.appendChild(gr);
        }
      } catch (e) {}
    }
    const lr = h('div', 'row'); lr.setAttribute('data-mem', ''); lr.setAttribute('data-vsi', ''); lr.appendChild(h('h2', '', 'Deine Bibliotheken')); const lt = h('div', 'track'); lr.appendChild(lt);
    for (const v of EMBY.views) {
      const c = V.card(v, 'lib', { cap: false, focus: () => { leaveSpot(); paint({ Name: v.Name, Id: v.Id, Type: 'Folder', ImageTags: {} }); } });
      c.classList.add('lib'); c.appendChild(h('div', 'lbl', esc(v.Name)));
      c._ok = () => APP.openLibrary(v); c._menu = null; lt.appendChild(c);
    }
    rows.appendChild(lr);
    if (!rows.querySelector('.card')) rows.appendChild(h('div', 'empty', 'Noch nichts da. Füge auf deinem Server Bibliotheken hinzu.'));
    COMM.decorate(rows);
    if (spots.length) showSpot(0); else spot.classList.add('hidden');
  };
  el.innerHTML = ''; el.appendChild(hero); el.appendChild(rowsWrap);
  await load();
  scr.initial = spots.length ? sbtns.firstChild : ($('.card', rows) || $('.f', rows));
  scr.refresh = async () => { if (!APP.dirty) return; APP.dirty = false; const keepId = NAV.cur && NAV.cur.dataset ? NAV.cur.dataset.id : null; const inSpot = NAV.cur && spot.contains(NAV.cur); await load(); const k = !inSpot && keepId && $('.card[data-id="' + keepId + '"]', rows); NAV.focus(k || sbtns.firstChild || $('.card', rows)); };
  scr.enter = () => { BG.dim(false); if (APP.dirty) scr.refresh(); else if (spotMode) showSpot(si, true); };
  scr.leave = () => clearTimeout(rotT);
});

// ---------- Bibliothek (Filme / Serien / beliebiger Ordner) ----------
function libraryPage(type) {
  return async (scr, arg) => {
    const el = scr.el; BG.set(null); BG.dim(true);
    let views = arg && arg.view ? [arg.view] : EMBY.viewsOf(type === 'Movie' ? 'movies' : 'tvshows');
    if (!views.length && !arg) { el.innerHTML = '<div class="empty">Keine ' + (type === 'Movie' ? 'Film' : 'Serien') + '-Bibliothek gefunden.</div>'; return; }
    const st = Object.assign({ sort: 'DateCreated', filter: '', genre: '', view: views[0] ? views[0].Id : '' }, STORE.get('lib_' + type, {}));
    if (arg && arg.view) st.view = arg.view.Id;
    if (arg && arg.genre) { st.genre = arg.genre; st.filter = ''; }
    if (!views.some(v => v.Id === st.view) && views[0]) st.view = views[0].Id;
    const head = h('div', 'libhead'); const title = (arg && arg.view) ? arg.view.Name : type === 'Movie' ? 'Filme' : 'Serien';
    head.innerHTML = '<h1>' + esc(title) + '</h1><span class="count"></span>';
    const gw = h('div', 'grid-wrap'); const grid = h('div', 'grid'); grid.setAttribute('data-vs', 'keep'); grid.setAttribute('data-mem', ''); gw.appendChild(grid);
    el.innerHTML = ''; el.style.paddingLeft = '0'; el.appendChild(head); el.appendChild(gw);
    const SORTS = [['DateCreated', 'Neu hinzugefügt'], ['SortName', 'Name'], ['PremiereDate', 'Erscheinungsjahr'], ['CommunityRating', 'Bewertung'], ['Random', 'Zufall']];
    const FILT = [['', 'Alle'], ['IsUnplayed', 'Ungesehen'], ['IsResumable', 'Angefangen'], ['IsFavorite', 'Favoriten']];
    const chip = (label, fn) => onOk(h('div', 'chip', label), fn);
    const sortChip = chip('', async () => { const v = await UI.choose('Sortieren nach', SORTS.map(s => ({ label: s[1], value: s[0], sel: s[0] === st.sort }))); if (v) { st.sort = v; reload(); } });
    const filtChip = chip('', async () => { const v = await UI.choose('Anzeigen', FILT.map(s => ({ label: s[1], value: s[0], sel: s[0] === st.filter }))); if (v != null) { st.filter = v; reload(); } });
    const genreChip = chip('', async () => {
      const g = await EMBY.genres(st.view); const opts = [{ label: 'Alle Genres', value: '', sel: !st.genre }].concat((g.Items || []).map(x => ({ label: x.Name, value: x.Name, sel: x.Name === st.genre })));
      const v = await UI.choose('Genre', opts); if (v != null) { st.genre = v; reload(); }
    });
    const viewChip = views.length > 1 ? chip('', async () => { const v = await UI.choose('Bibliothek', views.map(x => ({ label: x.Name, value: x.Id, sel: x.Id === st.view }))); if (v) { st.view = v; reload(); } }) : null;
    if (viewChip) head.appendChild(viewChip); head.appendChild(sortChip); head.appendChild(filtChip); head.appendChild(genreChip);
    if (type === 'Movie' && !arg) head.appendChild(chip(ic('shuffle', ' style="width:1.5rem;height:1.5rem"') + 'Überrasch mich', async () => { const r = await EMBY.items(q(0, 1, 'Random')); if (r.Items && r.Items[0]) APP.open(r.Items[0]); }));
    const labels = () => {
      sortChip.innerHTML = '↕ ' + SORTS.find(s => s[0] === st.sort)[1]; filtChip.innerHTML = FILT.find(s => s[0] === st.filter)[1]; genreChip.innerHTML = st.genre || 'Alle Genres';
      if (viewChip) viewChip.innerHTML = (views.find(v => v.Id === st.view) || {}).Name || '';
      [filtChip, genreChip].forEach((c, i) => c.classList.toggle('sel', !!(i ? st.genre : st.filter)));
    };
    let total = 0, loaded = 0, busy = false;
    const q = (start, limit, sort) => ({ ParentId: st.view || (arg && arg.view && arg.view.Id), Recursive: true, IncludeItemTypes: arg && arg.types ? arg.types : type, SortBy: (sort || st.sort) + (st.sort === 'SortName' ? '' : ',SortName'), SortOrder: (sort || st.sort) === 'SortName' ? 'Ascending' : 'Descending', Filters: st.filter, Genres: st.genre, StartIndex: start, Limit: limit });
    const more = async () => {
      if (busy || (loaded >= total && loaded)) return; busy = true;
      try {
        const r = await EMBY.items(q(loaded, 84)); total = r.TotalRecordCount || 0;
        for (const it of r.Items || []) grid.appendChild(V.card(it, 'poster', { cap: true, focus: (i) => { BG.set(EMBY.backdrop(i)); if (grid.lastChild && NAV.cur && Array.from(grid.children).indexOf(NAV.cur) > grid.children.length - 15) more(); } }));
        loaded += (r.Items || []).length; $('.count', head).textContent = total + ' Titel';
        if (!total) grid.appendChild(h('div', 'empty', 'Nichts gefunden.'));
        COMM.decorate(grid);
      } finally { busy = false; }
    };
    const reload = async () => { STORE.set('lib_' + type, { sort: st.sort, filter: st.filter, genre: st.genre, view: st.view }); labels(); grid.innerHTML = ''; grid._ty = 0; grid.style.transform = ''; total = loaded = 0; await more(); const f = $('.card', grid); if (f) NAV.focus(f); };
    labels(); await more();
    scr.initial = $('.card', grid) || sortChip;
    scr.enter = () => BG.dim(true); scr.leave = () => BG.dim(false);
    scr.refresh = () => {};
  };
}
ROUTER.reg('movies', libraryPage('Movie'));
ROUTER.reg('shows', libraryPage('Series'));
ROUTER.reg('folder', async (scr, arg) => {
  const ct = arg.view.CollectionType;
  const types = ct === 'movies' ? 'Movie' : ct === 'tvshows' ? 'Series' : ct === 'boxsets' ? 'BoxSet' : ct === 'homevideos' ? 'Video,Photo' : 'Movie,Series,Video,BoxSet';
  return libraryPage(types)(scr, { view: arg.view, types });
});

// ---------- Detailseite ----------
ROUTER.reg('detail', async (scr, arg) => {
  BG.dim(false);
  let item = await EMBY.item(arg.id);
  if (item.Type === 'Episode' || item.Type === 'Season') { const focusEp = item.Type === 'Episode' ? item.Id : null; const season = item.Type === 'Season' ? item.Id : item.SeasonId; item = await EMBY.item(item.SeriesId); scr.focusEp = focusEp; scr.season = season; }
  scr.item = item;
  const el = scr.el; el.className = 'page detail'; el.innerHTML = '';
  const sc = h('div', 'scroller'); sc.setAttribute('data-vs', 'anchor'); sc.dataset.at = '4'; el.appendChild(sc);
  BG.set(EMBY.backdrop(item));
  const top = h('div', 'top'); top.setAttribute('data-vsi', ''); top.setAttribute('data-vtop', ''); sc.appendChild(top);
  const logo = EMBY.img(item, 'Logo', 800);
  const mx = COMM.ok ? (await COMM.ratings([item.Id]))[item.Id] : null;
  top.innerHTML = (logo ? '<img class="logoimg" src="' + logo + '">' : '<h1>' + esc(item.Name) + '</h1>') + (item.Taglines && item.Taglines[0] ? '<div class="tagline">' + esc(item.Taglines[0]) + '</div>' : '')
    + '<div style="margin-top:1.4rem">' + V.meta(item, { mx }) + '</div>' + (item.Genres && item.Genres.length ? '<div class="genres">' + esc(item.Genres.join(' · ')) + '</div>' : '')
    + (item.Overview ? '<div class="ov">' + esc(item.Overview) + '</div>' : '');
  if (logo) { const li = $('.logoimg', top); li.onerror = () => { li.outerHTML = '<h1>' + esc(item.Name) + '</h1>'; }; }
  if (item.Type === 'Movie' || item.Type === 'Video') {
    const src = (item.MediaSources || [])[0]; const st = (src && src.MediaStreams) || item.MediaStreams || [];
    const v = st.find(x => x.Type === 'Video'); const aud = st.filter(x => x.Type === 'Audio'); const sub = st.filter(x => x.Type === 'Subtitle');
    const info = []; if (v) info.push((v.DisplayTitle || '').replace(/ ?\(?SDR\)?/, '') || (v.Height ? v.Height + 'p' : ''));
    if (aud.length) info.push('Ton: ' + Array.from(new Set(aud.map(a => langName(a.Language) || 'unbekannt'))).join(', '));
    if (sub.length) info.push('UT: ' + Array.from(new Set(sub.map(a => langName(a.Language) || '?'))).join(', '));
    if (info.length) top.appendChild(h('div', 'streaminfo', info.map(esc).map(s => '<span>' + s + '</span>').join('')));
  }
  const btns = h('div', 'btns'); top.appendChild(btns);
  const ud = item.UserData || {};
  const pos = ud.PlaybackPositionTicks || 0;
  if (item.Type === 'Series') {
    let nx = null; try { const n = await EMBY.nextUp(item.Id); nx = (n.Items || [])[0]; } catch (e) {}
    if (!nx) { try { const e = await EMBY.episodes(item.Id); nx = (e.Items || []).find(x => !(x.UserData || {}).Played) || (e.Items || [])[0]; } catch (e) {} }
    if (nx) { const p2 = (nx.UserData || {}).PlaybackPositionTicks; btns.appendChild(onOk(h('div', 'btn pri', ic('play') + (p2 ? 'Fortsetzen' : 'Abspielen') + ' · S' + nx.ParentIndexNumber + ' F' + nx.IndexNumber), () => APP.play(nx))); scr.nextEp = nx; }
  } else {
    btns.appendChild(onOk(h('div', 'btn pri', ic('play') + (pos ? 'Fortsetzen ab ' + fmtTime(pos / 10000) : 'Abspielen')), () => APP.play(item)));
    if (pos) btns.appendChild(onOk(h('div', 'btn', ic('restart') + 'Von vorne'), () => APP.play(item, { fromStart: true })));
  }
  if (window.TOUCH && (item.Type !== 'Series' || scr.nextEp)) btns.appendChild(onOk(h('div', 'btn', ic('tv') + 'Auf dem TV'), () => { const t = item.Type === 'Series' ? scr.nextEp : item; CAST.play(t, ((t.UserData || {}).PlaybackPositionTicks || 0)); }));
  if (item.Type !== 'Series' || scr.nextEp) btns.appendChild(onOk(h('div', 'btn', ic('party') + 'Watch-Party'), () => { const t = item.Type === 'Series' ? scr.nextEp : item; PARTY.create(t, ((t.UserData || {}).PlaybackPositionTicks || 0) / 10000); }));
  if (item.LocalTrailerCount || (item.RemoteTrailers || []).length) btns.appendChild(onOk(h('div', 'btn', ic('trailer') + 'Trailer'), () => APP.trailer(item)));
  const rateBtn = onOk(h('div', 'btn' + (mx && mx.mine ? ' on' : ''), ic(mx && mx.mine ? 'starf' : 'star') + '<span>' + (mx && mx.mine ? 'Deine ' + mx.mine + '/10' : 'Bewerten') + '</span>'), async () => { const r = await COMM.ratePrompt(item); if (r !== undefined) ROUTER.go('detail', { id: item.Id }, { replace: true }); });
  btns.appendChild(rateBtn);
  btns.appendChild(onOk(h('div', 'btn icon', ic('share')), () => COMM.recommend(item)));
  const seen = onOk(h('div', 'btn icon' + (ud.Played ? ' on' : ''), ic('check')), async () => { await EMBY.setPlayed(item.Id, !ud.Played); ud.Played = !ud.Played; seen.classList.toggle('on', ud.Played); APP.dirty = true; UI.toast(ud.Played ? 'Als gesehen markiert' : 'Als ungesehen markiert', { icon: '✓' }); });
  const fav = onOk(h('div', 'btn icon' + (ud.IsFavorite ? ' on' : ''), ic(ud.IsFavorite ? 'heartf' : 'heart')), async () => { await EMBY.setFav(item.Id, !ud.IsFavorite); ud.IsFavorite = !ud.IsFavorite; fav.classList.toggle('on', ud.IsFavorite); fav.innerHTML = ic(ud.IsFavorite ? 'heartf' : 'heart'); });
  btns.appendChild(seen); btns.appendChild(fav);
  if ((item.MediaSources || []).length > 1) btns.appendChild(onOk(h('div', 'btn', '<span>Version</span>'), async () => { const v = await UI.choose('Version', item.MediaSources.map(m => ({ label: m.Name || m.Container, value: m.Id }))); if (v) APP.play(item, { mediaSourceId: v }); }));
  if (mx) top.appendChild(h('div', 'community', '<span class="score grad-text">' + mx.avg.toFixed(1) + '</span><span><span class="stars">' + COMM.starsHtml(mx.avg) + '</span><br><span style="color:var(--mut)">midnox-Community · ' + mx.n + ' Stimme' + (mx.n === 1 ? '' : 'n') + '</span></span>'));

  // Staffeln & Folgen
  if (item.Type === 'Series') {
    const seasons = ((await EMBY.seasons(item.Id)).Items || []);
    const sec = h('div', 'row capped'); sec.setAttribute('data-vsi', ''); sec.setAttribute('data-mem', ''); sc.appendChild(sec);
    const sbar = h('div', 'seasons'); sec.appendChild(sbar);
    const tr = h('div', 'track'); tr.style.paddingLeft = '0'; const trWrap = h('div', ''); trWrap.style.overflow = 'visible'; trWrap.appendChild(tr); sec.appendChild(trWrap);
    const epinfo = h('div', 'epinfo'); sec.appendChild(epinfo);
    const showSeason = async (s, focusId) => {
      $$('.chip', sbar).forEach(c => c.classList.toggle('sel', c.dataset.id === s.Id));
      const eps = ((await EMBY.episodes(item.Id, s.Id)).Items || []); tr.innerHTML = ''; tr.style.transform = '';
      for (const ep of eps) {
        const c = V.card(ep, 'land', { cap: false, ok: () => APP.play(ep), focus: (e) => { epinfo.innerHTML = V.spoil(e) ? '<h3>' + (e.IndexNumber ? 'Folge ' + e.IndexNumber : 'Folge') + '</h3>' + V.meta(e) + '<p class="spoiltx">🙈 Titel und Beschreibung versteckt (Anti-Spoiler)</p>' : '<h3>' + (e.IndexNumber ? e.IndexNumber + '. ' : '') + esc(e.Name) + '</h3>' + V.meta(e) + '<p>' + esc(e.Overview || '') + '</p>'; } });
        c.classList.add('ep'); c.appendChild(h('div', 'num', 'F' + (ep.IndexNumber || '?')));
        if (window.TOUCH) c.appendChild(h('div', 'eptitle', esc(V.spoil(ep) ? 'Folge ' + (ep.IndexNumber || '') : ep.Name) + (ep.RunTimeTicks ? '<small>' + Math.round(ep.RunTimeTicks / 6e8) + ' Min.</small>' : '')));
        tr.appendChild(c);
      }
      const want = focusId && $('.card[data-id="' + focusId + '"]', tr);
      return want || tr.firstChild;
    };
    for (const s of seasons) { const c = onOk(h('div', 'chip', esc(s.Name)), async () => { const f = await showSeason(s); if (f) NAV.focus(f); }); c.dataset.id = s.Id; sbar.appendChild(c); }
    const startSeason = seasons.find(s => s.Id === scr.season) || (scr.nextEp && seasons.find(s => s.Id === scr.nextEp.SeasonId)) || seasons[0];
    if (startSeason) { const f = await showSeason(startSeason, scr.focusEp || (scr.nextEp && scr.nextEp.Id)); if (scr.focusEp && f) scr.initial = f; else if (f) sec._last = f; }
  }
  // Besetzung
  const cast = (item.People || []).filter(p => p.Type === 'Actor' || p.Type === 'Director').slice(0, 20);
  if (cast.length) {
    const r = h('div', 'row capped'); r.setAttribute('data-vsi', ''); r.setAttribute('data-mem', ''); r.appendChild(h('h2', '', 'Besetzung')); const tr = h('div', 'track'); r.appendChild(tr);
    for (const p of cast) { const c = V.card({ Id: p.Id, Name: p.Name, Type: 'Person', ImageTags: p.PrimaryImageTag ? { Primary: p.PrimaryImageTag } : {} }, 'circle', { role: p.Type === 'Director' ? 'Regie' : p.Role, ok: () => ROUTER.go('person', { id: p.Id, name: p.Name }), menu: null }); c.classList.add('person'); tr.appendChild(c); }
    sc.appendChild(r);
  }
  // Community-Stimmen
  if (COMM.ok) {
    try {
      const rv = await COMM.call('item', null, '&id=' + item.Id);
      if (rv.list.length) {
        const r = h('div', 'row'); r.setAttribute('data-vsi', ''); r.setAttribute('data-mem', ''); r.appendChild(h('h2', '', 'Was die Community sagt')); const tr = h('div', 'track'); r.appendChild(tr);
        for (const x of rv.list) { const c = h('div', 'review f'); const w = h('div', 'who'); w.appendChild(avatar(x.name)); w.appendChild(h('span', '', esc(x.name))); w.appendChild(h('span', 'st', '★ ' + x.stars + '/10')); c.appendChild(w); c.appendChild(h('p', '', esc(x.comment || STAR_WORDS[x.stars]) + '<br><span style="color:var(--dim);font-size:1rem">' + ago(x.t) + '</span>')); c._ok = () => {}; tr.appendChild(c); }
        sc.appendChild(r);
      }
    } catch (e) {}
  }
  // Ähnliches
  try { const sim = await EMBY.similar(item.Id); if ((sim.Items || []).length) { const r = V.row('Ähnliches', sim.Items, 'poster', { cap: true }); sc.appendChild(r); COMM.decorate(r); } } catch (e) {}
  scr.initial = scr.initial || $('.btn', btns);
  scr.refresh = async () => { if (APP.dirty) { APP.dirty = false; const f = NAV.cur && NAV.cur.dataset ? NAV.cur.dataset.id : null; await ROUTER.go('detail', { id: arg.id }, { replace: true }); if (f) { const c = $('.card[data-id="' + f + '"]', ROUTER.top().el); if (c) NAV.focus(c); } } };
  scr.enter = () => { BG.set(EMBY.backdrop(item)); BG.dim(false); THEMESONG.play(item); };
  scr.leave = () => THEMESONG.stopSoon();
  THEMESONG.play(item);
});

ROUTER.reg('person', async (scr, arg) => {
  BG.set(null); BG.dim(true);
  const r = await EMBY.items({ PersonIds: arg.id, Recursive: true, IncludeItemTypes: 'Movie,Series', SortBy: 'PremiereDate', SortOrder: 'Descending', Limit: 100 });
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  const head = h('div', 'libhead', '<h1>' + esc(arg.name) + '</h1><span class="count">' + (r.TotalRecordCount || 0) + ' Titel</span>'); el.appendChild(head);
  const gw = h('div', 'grid-wrap'); const grid = h('div', 'grid'); grid.setAttribute('data-vs', 'keep'); gw.appendChild(grid); el.appendChild(gw);
  for (const it of r.Items || []) grid.appendChild(V.card(it, 'poster', { cap: true }));
  COMM.decorate(grid);
  scr.leave = () => BG.dim(false); scr.enter = () => BG.dim(true);
});

ROUTER.reg('boxset', async (scr, arg) => {
  BG.dim(true);
  const it = await EMBY.item(arg.id); BG.set(EMBY.backdrop(it));
  const r = await EMBY.items({ ParentId: arg.id, SortBy: 'PremiereDate', Limit: 200 });
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  el.appendChild(h('div', 'libhead', '<h1>' + esc(it.Name) + '</h1><span class="count">' + (r.TotalRecordCount || 0) + ' Titel</span>'));
  const gw = h('div', 'grid-wrap'); const grid = h('div', 'grid'); grid.setAttribute('data-vs', 'keep'); gw.appendChild(grid); el.appendChild(gw);
  for (const x of r.Items || []) grid.appendChild(V.card(x, 'poster', { cap: true }));
  scr.leave = () => BG.dim(false); scr.enter = () => BG.dim(true);
});

// ---------- Suche ----------
ROUTER.reg('search', async (scr) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  const sc = h('div', ''); sc.style.cssText = 'position:absolute;inset:0'; sc.setAttribute('data-vs', 'anchor'); sc.dataset.at = '3'; el.appendChild(sc);
  const top = h('div', ''); top.style.cssText = 'padding:4rem 0 0 9rem;display:flex;gap:4rem'; top.setAttribute('data-vsi', ''); top.setAttribute('data-vtop', ''); sc.appendChild(top);
  const left = h('div'); top.appendChild(left);
  const field = h('div', 'sfield'); left.appendChild(field);
  const kb = h('div', 'kbd'); kb.style.cssText = 'grid-template-columns:repeat(10,3.6rem);margin-top:2rem'; kb.setAttribute('data-mem', ''); left.appendChild(kb);
  const right = h('div'); right.style.cssText = 'padding-top:1rem;max-width:40rem'; top.appendChild(right);
  let term = '';
  const draw = () => { field.innerHTML = term ? esc(term) + '<span class="cur"></span>' : '<span class="ph">Titel, Serie, Person …</span><span class="cur"></span>'; };
  const key = (label, fn, cls) => { const k = onOk(h('div', 'k' + (cls ? ' ' + cls : ''), label), fn); kb.appendChild(k); return k; };
  const type = (c) => { term += c; draw(); go(); };
  for (const c of 'ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜß1234567890') key(c, () => type(c.toLowerCase() === c ? c : c.toLowerCase()));
  key('Leerz.', () => type(' '), 'wide'); key('⌫', () => { term = term.slice(0, -1); draw(); go(); }, 'wide'); key('Löschen', () => { term = ''; draw(); go(); }, 'w3');
  if (HAS_ANDROID) key(ic('keyboard', ' style="width:1.8rem;height:1.8rem"'), async () => { const t = await TEXT.ask('Suchen', { value: term }); if (t != null) { term = t; draw(); go(); } }, '');
  key(ic('phone', ' style="width:1.8rem;height:1.8rem"'), async () => { const d = await PHONE.ask({ mode: 'text', title: 'Suchen', value: term }); if (d && typeof d.text === 'string') { term = d.text; draw(); go(); } }, '');
  let draw0 = draw;
  if (window.TOUCH) {   // Handy/Tablet: echtes Eingabefeld mit Handytastatur statt Bildschirmtastatur
    kb.style.display = 'none'; field.innerHTML = '';
    const inp = document.createElement('input'); inp.className = 'tin sin'; inp.placeholder = 'Titel, Serie, Person …'; inp.setAttribute('autocapitalize', 'off'); inp.autocomplete = 'off'; inp.spellcheck = false;
    inp.addEventListener('input', () => { term = inp.value; go(); });
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { inp.blur(); if (HAS_ANDROID && Android.showKeyboard) Android.showKeyboard(false); } });
    field.appendChild(inp); draw0 = () => { if (inp.value !== term) inp.value = term; };
    setTimeout(() => inp.focus(), 300);
  }
  const draw1 = draw0; draw0();
  const recent = STORE.get('recentSearch', []);
  if (recent.length) { right.appendChild(h('div', '', '<div style="font-size:1.05rem;letter-spacing:.18em;text-transform:uppercase;color:var(--mut);font-weight:700">Zuletzt gesucht</div>')); const rr = h('div', 'recent'); for (const r of recent) rr.appendChild(onOk(h('div', 'chip', esc(r)), () => { term = r; draw1(); go(); })); right.appendChild(rr); }
  const results = h('div', ''); results.style.marginTop = '2rem'; sc.appendChild(results);
  let seq = 0;
  const go = debounce(async () => {
    const my = ++seq; const t = term.trim();
    if (t.length < 2) { results.innerHTML = ''; return; }
    const [r, p] = await Promise.all([EMBY.search(t).catch(() => ({ Items: [] })), EMBY.persons(t).catch(() => ({ Items: [] }))]);
    if (my !== seq) return;
    results.innerHTML = '';
    const its = r.Items || []; const by = (tp) => its.filter(i => tp.indexOf(i.Type) >= 0);
    const add = (title, list, kind) => { if (list.length) { const row = V.row(title, list, kind, { cap: kind === 'poster' }); results.appendChild(row); } };
    add('Filme', by(['Movie', 'BoxSet']), 'poster'); add('Serien', by(['Series']), 'poster'); add('Folgen', by(['Episode']), 'land');
    add('Alben', by(['MusicAlbum']), 'square'); add('Interpreten', by(['MusicArtist']), 'circle'); add('Titel', by(['Audio']), 'square');
    add('Personen', (p.Items || []).map(x => Object.assign(x, { Type: 'Person' })), 'circle');
    if (!results.children.length) results.appendChild(h('div', 'empty', 'Nichts gefunden für „' + esc(t) + '“.'));
    else { COMM.decorate(results); const rec = [t].concat(STORE.get('recentSearch', []).filter(x => x !== t)).slice(0, 6); STORE.set('recentSearch', rec); }
  }, 450);
  scr.initial = $('.k', kb);
  scr.leave = () => BG.dim(false); scr.enter = () => BG.dim(true);
});

// ---------- Einstellungen ----------
ROUTER.reg('settings', async (scr) => {
  BG.set(null); BG.dim(true);
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  el.appendChild(h('div', 'libhead', '<h1>Einstellungen</h1>'));
  const wrap = h('div', 'grid-wrap'); const list = h('div', ''); list.style.cssText = 'padding:1rem 0 6rem 9rem;width:76rem'; list.setAttribute('data-vs', 'keep'); wrap.appendChild(list); el.appendChild(wrap);
  const s = EMBY.settings();
  const save = () => EMBY.saveSettings(s);
  const sect = (t) => list.appendChild(h('div', '', '<div style="font-size:1.05rem;letter-spacing:.18em;text-transform:uppercase;color:var(--mut);font-weight:700;margin:2rem 0 .8rem">' + t + '</div>'));
  const row = (title, val, fn, icon) => { const e = h('div', 'srv f', '<div class="ic">' + ic(icon || 'gear') + '</div><div style="flex:1"><b>' + esc(title) + '</b><small>' + esc(val) + '</small></div>'); e._ok = () => fn(e); list.appendChild(e); return e; };
  const setVal = (e, v) => { $('small', e).textContent = v; };
  const choice = (title, key, opts, icon) => {
    const name = () => (opts.find(o => o[0] === s[key]) || opts[0])[1];
    row(title, name(), async (e) => { const v = await UI.choose(title, opts.map(o => ({ label: o[1], value: o[0], sel: o[0] === s[key] }))); if (v !== null && v !== undefined) { s[key] = v; save(); setVal(e, name()); } }, icon);
  };
  sect('Wiedergabe');
  choice('Maximale Qualität', 'bitrate', [[0, 'Automatisch (beste)'], [80000000, '80 Mbit/s – 4K'], [40000000, '40 Mbit/s – 1080p hoch'], [20000000, '20 Mbit/s – 1080p'], [10000000, '10 Mbit/s – 720p'], [4000000, '4 Mbit/s – sparsam unterwegs'], [1500000, '1,5 Mbit/s – sehr langsam']], 'film');
  choice('Bevorzugte Tonspur', 'audioLang', [['ger', 'Deutsch'], ['eng', 'Englisch'], ['', 'Originalton (Server-Vorgabe)']], 'audio');
  choice('Untertitel', 'subMode', [['smart', 'Clever (nur wenn nötig, z. B. Fremdsprache)'], ['always', 'Immer anzeigen'], ['off', 'Aus']], 'sub');
  choice('Untertitel-Sprache', 'subLang', [['ger', 'Deutsch'], ['eng', 'Englisch']], 'sub');
  choice('Nächste Folge automatisch', 'autoNext', [[true, 'An (mit 15 s Countdown)'], [false, 'Aus']], 'next');
  choice('Intro automatisch überspringen', 'autoSkip', [[false, 'Aus (Knopf wird angezeigt)'], [true, 'An']], 'skip');
  sect('Darstellung');
  row('Farbthema', (THEME.list.find(t => t[0] === (s.theme || 'auto')) || THEME.list[0])[1] + ((s.theme || 'auto') === 'auto' ? ' · gerade: ' + THEME.names[THEME.seasonal()] : ''), async (e) => { const v = await UI.choose('Farbthema', THEME.list.map(t => ({ label: t[1], value: t[0], sel: t[0] === (s.theme || 'auto') })), 'Saisonal wechselt automatisch – z. B. Advent, Halloween, Frühling, Sommer, Tag der Deutschen Einheit.'); if (v) { s.theme = v; save(); THEME.apply(v); setVal(e, THEME.list.find(t => t[0] === v)[1]); } }, 'gear');
  row('Glas-Effekte', (GLASS.list.find(t => t[0] === (s.glass || 'full')) || GLASS.list[0])[1], async (e) => { const v = await UI.choose('Glas-Effekte', GLASS.list.map(t => ({ label: t[1], value: t[0], sel: t[0] === (s.glass || 'full') })), 'Wenn es auf deinem Gerät ruckelt, nimm „Sparsam“ – der Look bleibt, nur die Unschärfe fällt weg.'); if (v) { s.glass = v; save(); GLASS.apply(v); setVal(e, GLASS.list.find(t => t[0] === v)[1]); } }, 'tv');
  if (window.TOUCH) choice('Qualität über mobile Daten', 'mobileBitrate', [[4000000, 'Sparsam (4 Mbit/s, ca. 1,8 GB/h)'], [1500000, 'Sehr sparsam (1,5 Mbit/s, ca. 0,7 GB/h)'], [10000000, 'Gut (10 Mbit/s, ca. 4,5 GB/h)'], [0, 'Keine Begrenzung']], 'tv');
  choice('Anti-Spoiler', 'spoiler', [[false, 'Aus'], [true, 'An – ungesehene Folgen ohne Bild-Details und Beschreibung']], 'eye');
  choice('Schaust du noch?', 'still', [[3, 'Nach 3 Folgen ohne Tastendruck fragen'], [2, 'Nach 2 Folgen'], [5, 'Nach 5 Folgen'], [0, 'Nie fragen']], 'moon');
  choice('Titelmusik auf Detailseiten', 'themeSongs', [[true, 'An (leise im Hintergrund)'], [false, 'Aus']], 'music');
  choice('Vorschaubilder beim Spulen', 'trick', [[true, 'An (wenn der Server sie erzeugt hat)'], [false, 'Aus']], 'tv');
  choice('Bildschirmschoner', 'saver', [[5, 'Nach 5 Minuten'], [3, 'Nach 3 Minuten'], [10, 'Nach 10 Minuten'], [20, 'Nach 20 Minuten'], [0, 'Aus (Fire-TV-eigener)']], 'tv');
  sect('Community');
  choice('Anderen zeigen, was ich schaue', 'showWatching', [[true, 'Ja'], [false, 'Nein']], 'user');
  row('Community-Server', s.community + (COMM.ok ? ' · verbunden' : ' · nicht verbunden'), async (e) => { const v = await TEXT.ask('Adresse des Community-Servers', { value: s.community }); if (v) { s.community = v.trim(); save(); await COMM.start(); setVal(e, s.community + (COMM.ok ? ' · verbunden' : ' · nicht verbunden')); } }, 'chat');
  sect('Konto');
  row('Angemeldet als ' + EMBY.user.name + (EMBY.user.admin ? ' (Administrator)' : ''), EMBY.server.name + ' · ' + EMBY.server.url, () => {}, 'user');
  row('Benutzer wechseln', 'Anderes Profil auf diesem Server', () => { EMBY.closeSocket(); ROUTER.go('login', EMBY.server, { root: true }); $('#rail').classList.add('hidden'); }, 'user');
  row('Profil wechseln', 'Wer guckt? – alle Profile auf diesem Gerät', () => PROFILES.leave(), 'user');
  choice('Beim Start Profil wählen', 'askProfile', [[true, 'Ja, wenn es mehrere Profile gibt'], [false, 'Nein, zuletzt genutztes öffnen']], 'user');
  row('Server wechseln', 'Mit einem anderen Emby- oder Jellyfin-Server verbinden', () => { EMBY.closeSocket(); $('#rail').classList.add('hidden'); ROUTER.go('connect', null, { root: true }); }, 'server');
  row('Abmelden', 'Meldet dieses Gerät beim Server ab', async () => { if (await UI.confirm('Abmelden?', 'Danach musst du dich neu anmelden.', 'Abmelden')) APP.logout(); }, 'logout');
  sect('Über');
  row('midnox Media ' + DEVICE.version, 'Nach Updates suchen', () => { if (HAS_ANDROID) { Android.checkUpdate(); UI.toast('Suche nach Updates …', { icon: '⏳' }); } }, 'info');
  scr.leave = () => BG.dim(false); scr.enter = () => BG.dim(true);
});
