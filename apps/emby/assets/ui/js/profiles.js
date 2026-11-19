// midnox Media – „Wer guckt?": Profile über mehrere Server (Emby + Jellyfin), PIN, Kinderprofil.
'use strict';

/** PIN-Eingabe mit Zahlenfeld (Fire-TV-Fernbedienungen haben keine Zifferntasten). */
const PIN = {
  ask(title, sub) {
    return UI.modal((box, close) => {
      box.classList.add('pinbox');
      box.appendChild(h('h2', '', esc(title))); if (sub) box.appendChild(h('p', '', esc(sub)));
      let v = '';
      const dots = h('div', 'pindots', '<i></i><i></i><i></i><i></i>'); box.appendChild(dots);
      const paint = () => { $$('i', dots).forEach((d, i) => d.classList.toggle('on', i < v.length)); };
      const pad = h('div', 'pinpad'); pad.setAttribute('data-mem', '');
      const press = (k) => {
        if (k === '⌫') v = v.slice(0, -1); else if (v.length < 4) v += k;
        paint(); if (v.length === 4) setTimeout(() => close(v), 180);
      };
      for (const k of ['1', '2', '3', '4', '5', '6', '7', '8', '9', '✕', '0', '⌫']) {
        const b = onOk(h('div', 'btn pk' + (k === '✕' ? ' x' : ''), k), () => { if (k === '✕') close(null); else press(k); });
        pad.appendChild(b);
      }
      box.appendChild(pad);
    }, { initial: (box) => $$('.pk', box)[4] });
  },
  /** Neue PIN zweimal eingeben lassen. */
  async create(title) {
    const a = await PIN.ask(title || 'Neue PIN', 'Vier Ziffern wählen.'); if (!a) return null;
    const b = await PIN.ask('PIN wiederholen', 'Zur Sicherheit noch einmal.'); if (!b) return null;
    if (a !== b) { UI.toast('Die PINs stimmen nicht überein.', { icon: '⚠️' }); return null; }
    return a;
  },
  async check(pin, title, sub) {
    for (let i = 0; i < 3; i++) { const v = await PIN.ask(title || 'PIN eingeben', i ? 'Falsche PIN – noch einmal.' : sub); if (v == null) return false; if (v === pin) return true; }
    UI.toast('Zu viele Versuche.', { icon: '🔒' }); return false;
  },
};
window.PIN = PIN;

const PROFILES = {
  key(srvId, uid) { return srvId + ':' + uid; },
  list() {
    const out = [];
    for (const s of EMBY.servers()) for (const u of (s.users || [])) if (u.token) out.push({ key: PROFILES.key(s.id, u.id), srv: s, u });
    return out;
  },
  metaAll() { return STORE.get('pmeta', {}); },
  meta(k) { return PROFILES.metaAll()[k] || {}; },
  setMeta(k, m) { const all = PROFILES.metaAll(); all[k] = Object.assign({}, all[k] || {}, m); STORE.set('pmeta', all); },
  current() { const a = STORE.get('active'); return a ? PROFILES.key(a.server, a.user) : ''; },
  curMeta() { return PROFILES.meta(PROFILES.current()); },
  /** Beim Start fragen? Nur wenn es mehrere Profile gibt (abschaltbar). */
  shouldAsk() { return PROFILES.list().length > 1 && EMBY.settings().askProfile !== false; },
  parentPin() { return STORE.get('parentPin', ''); },

  async switchTo(p, skipPin) {
    const m = PROFILES.meta(p.key);
    if (!skipPin && m.pin && !(await PIN.check(m.pin, 'PIN für ' + p.u.name))) return false;
    if (window.PLAYER && PLAYER.active) await PLAYER.stop('user');
    if (window.MUSIC) MUSIC.stop(true);
    EMBY.closeSocket(); if (window.COMM) { clearTimeout(COMM.feedT); COMM.ok = false; }
    if (window.STATS) { STATS.data = null; STATS.got = {}; STATS.at = 0; }
    EMBY.use(p.srv); EMBY.token = p.u.token; EMBY.user = { id: p.u.id, name: p.u.name, admin: false };
    try { EMBY.setUser(await EMBY.get('/Users/' + p.u.id, null, 12000)); }
    catch (e) {
      if (e.status === 401) { UI.alert('Abgemeldet', 'Dieses Profil muss sich neu anmelden.'); ROUTER.go('login', p.srv, { root: true }); return false; }
      UI.alert('Server nicht erreichbar', p.srv.name + ' (' + p.srv.url + ')\n' + (e.message || '')); return false;
    }
    STORE.set('active', { server: p.srv.id, user: p.u.id });
    $$('#toasts .toast').forEach(t => t.remove());
    KIDS.apply();
    ROUTER.drop(99);
    await APP.afterLogin();
    return true;
  },
  /** „Profil wechseln" – aus dem Kinderprofil nur mit Eltern-PIN. */
  async leave() {
    if (KIDS.on && PROFILES.parentPin() && !(await PIN.check(PROFILES.parentPin(), 'Eltern-PIN', 'Das Kinderprofil verlassen.'))) return;
    if (window.PLAYER && PLAYER.active) await PLAYER.stop('user');
    ROUTER.go('who', null, { root: true });
  },
  remove(p) {
    const list = EMBY.servers(); const s = list.find(x => x.id === p.srv.id);
    if (s) { s.users = (s.users || []).filter(u => u.id !== p.u.id); STORE.set('servers', list.filter(x => (x.users || []).length || x.id === (EMBY.server && EMBY.server.id))); }
    const all = PROFILES.metaAll(); delete all[p.key]; STORE.set('pmeta', all);
    if (PROFILES.current() === p.key) STORE.set('active', null);
  },
  color(name) { let h = 0; for (const c of String(name)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return ['#7c3aed,#3b5bf0', '#f43f5e,#f97316', '#0ea5e9,#14b8a6', '#10b981,#84cc16', '#ec4899,#a855f7', '#f59e0b,#ef4444', '#6366f1,#06b6d4'][h % 7]; },
};
window.PROFILES = PROFILES;

/** Kinderprofil: kindgerechte Inhalte (nach Altersfreigabe), eigene Startseite, Ausgang per Eltern-PIN. */
const KIDS = {
  on: false, max: 12,
  apply() {
    const m = PROFILES.curMeta(); KIDS.on = !!m.kids; KIDS.max = m.kidsMax != null ? m.kidsMax : 12; KIDS.unrated = !!m.kidsUnrated;
    document.body.classList.toggle('kids', KIDS.on);
  },
  /** Altersfreigabe aus FSK/MPAA/TV-Angaben lesen. null = unbekannt. */
  age(r) {
    if (!r) return null; r = String(r).toUpperCase().trim();
    let m = r.match(/(?:FSK|DE|AT|CH|USK|PEGI)?[-\s:]?(\d{1,2})\b/); if (m && /FSK|DE-|^\d|AT-|CH-|USK|PEGI/.test(r)) return +m[1];
    const map = { 'G': 0, 'TV-Y': 0, 'TV-G': 0, 'TV-Y7': 7, 'TV-Y7-FV': 7, 'PG': 6, 'TV-PG': 10, 'PG-13': 13, 'TV-14': 14, 'R': 17, 'NC-17': 18, 'TV-MA': 17, 'UNRATED': null, 'NR': null, 'NOT RATED': null, 'U': 0, '12A': 12, '15': 15, '18': 18 };
    if (r in map) return map[r];
    m = r.match(/(\d{1,2})/); return m ? +m[1] : null;
  },
  allow(it) {
    if (!it || !KIDS.on) return true;
    const t = it.Type; if (t === 'CollectionFolder' || t === 'UserView' || t === 'Folder' || t === 'Person' || t === 'MusicArtist' || t === 'Audio' || t === 'MusicAlbum' || t === 'Season') return true;
    const a = KIDS.age(it.OfficialRating || it.CustomRating);
    if (a == null) return t === 'Episode' ? true : KIDS.unrated;
    return a <= KIDS.max;
  },
  /** Antworten des Servers filtern (Listen und Items-Arrays). */
  filter(r) {
    if (!KIDS.on || !r) return r;
    if (Array.isArray(r)) return r.filter(KIDS.allow);
    if (Array.isArray(r.Items)) { const n = r.Items.length; r.Items = r.Items.filter(KIDS.allow); if (r.TotalRecordCount != null) r.TotalRecordCount -= (n - r.Items.length); }
    return r;
  },
};
window.KIDS = KIDS;

// ---------- „Wer guckt?" ----------
ROUTER.reg('who', async (scr) => {
  scr.noRail = true; $('#rail').classList.add('hidden'); BG.set(null); BG.dim(true);
  document.body.classList.remove('kids');
  const el = scr.el; el.className = 'page whopage'; el.style.paddingLeft = '0';
  const render = () => {
    el.innerHTML = '';
    el.appendChild(h('div', 'whohead', '<div class="brand">midnox <b>Media</b></div><h1>Wer <span>guckt?</span></h1>'));
    const grid = h('div', 'whogrid'); grid.setAttribute('data-mem', '');
    const cur = PROFILES.current();
    for (const p of PROFILES.list()) {
      const m = PROFILES.meta(p.key);
      const img = (p.srv.url || '').replace(/\/+$/, '') + (p.srv.kind === 'jellyfin' ? '' : '/emby') + '/Users/' + p.u.id + '/Images/Primary?maxWidth=300';
      const t = h('div', 'who f' + (p.key === cur ? ' last' : '') + (m.kids ? ' kid' : ''));
      const av = h('div', 'wav'); av.style.background = 'linear-gradient(135deg,' + PROFILES.color(p.u.name) + ')';
      av.innerHTML = '<span>' + esc((p.u.name || '?').slice(0, 1).toUpperCase()) + '</span>';
      const im = new Image(); im.onload = () => { if (im.naturalWidth > 2) { av.style.backgroundImage = 'url("' + img + '")'; av.classList.add('img'); } }; im.src = img;
      if (m.kids) av.appendChild(h('em', 'kb', '🧸')); if (m.pin) av.appendChild(h('em', 'lk', '🔒'));
      t.appendChild(av);
      t.appendChild(h('b', '', esc(p.u.name)));
      t.appendChild(h('small', '', (p.srv.kind === 'jellyfin' ? 'Jellyfin' : 'Emby') + ' · ' + esc(p.srv.name || '')));
      t._ok = () => PROFILES.switchTo(p);
      t._menu = () => editProfile(p);
      grid.appendChild(t);
    }
    const add = onOk(h('div', 'who add f', '<div class="wav"><span>+</span></div><b>Profil hinzufügen</b><small>Emby oder Jellyfin</small>'), () => ROUTER.go('connect', { add: true }, { root: true }));
    grid.appendChild(add);
    el.appendChild(grid);
    el.appendChild(h('div', 'whohint', 'OK = Profil öffnen · MENU (☰) = PIN, Kinderprofil, entfernen'));
    const first = $('.who.last', grid) || $('.who', grid); NAV.focus(first); scr.initial = first;
  };
  const editProfile = async (p) => {
    const m = PROFILES.meta(p.key);
    // Profile mit PIN oder Kinderprofile darf nur ändern, wer die PIN kennt
    if (m.pin && !(await PIN.check(m.pin, 'PIN für ' + p.u.name))) return;
    if (m.kids && PROFILES.parentPin() && !(await PIN.check(PROFILES.parentPin(), 'Eltern-PIN'))) return;
    const opts = [
      { label: m.pin ? '🔒 PIN ändern' : '🔒 PIN festlegen', value: 'pin' },
      m.pin ? { label: 'PIN entfernen', value: 'nopin' } : null,
      { label: m.kids ? '🧸 Kinderprofil ausschalten' : '🧸 Als Kinderprofil einrichten', value: 'kids' },
      m.kids ? { label: 'Altersfreigabe: bis ' + (m.kidsMax != null ? m.kidsMax : 12) + ' Jahre', value: 'age' } : null,
      m.kids ? { label: 'Titel ohne Freigabe: ' + (m.kidsUnrated ? 'zeigen' : 'ausblenden'), value: 'unrated' } : null,
      { label: 'Profil von diesem Gerät entfernen', value: 'del' },
    ].filter(Boolean);
    const v = await UI.choose(p.u.name, opts, (p.srv.kind === 'jellyfin' ? 'Jellyfin' : 'Emby') + ' · ' + (p.srv.name || '') + ' · ' + p.srv.url);
    if (v === 'pin') { const n = await PIN.create('Neue PIN für ' + p.u.name); if (n) { PROFILES.setMeta(p.key, { pin: n }); UI.toast('PIN gespeichert', { icon: '🔒' }); } }
    if (v === 'nopin') { PROFILES.setMeta(p.key, { pin: '' }); UI.toast('PIN entfernt'); }
    if (v === 'kids') {
      if (!m.kids && !PROFILES.parentPin()) {
        await UI.alert('Eltern-PIN festlegen', 'Mit dieser PIN verlässt man später das Kinderprofil und öffnet die Einstellungen. Sie gilt für alle Kinderprofile auf diesem Gerät.');
        const n = await PIN.create('Eltern-PIN'); if (!n) return; STORE.set('parentPin', n);
      }
      PROFILES.setMeta(p.key, { kids: !m.kids });
      if (!m.kids) UI.alert('Kinderprofil eingerichtet', 'Es werden nur Titel bis ' + (m.kidsMax != null ? m.kidsMax : 12) + ' Jahre gezeigt. Am sichersten ist es, zusätzlich auf dem ' + (p.srv.kind === 'jellyfin' ? 'Jellyfin' : 'Emby') + '-Server für dieses Konto eine Altersgrenze (Kindersicherung) einzustellen.');
    }
    if (v === 'age') { const a = await UI.choose('Altersfreigabe', [0, 6, 12, 16].map(x => ({ label: x === 0 ? 'Nur ohne Altersbeschränkung (FSK 0)' : 'Bis ' + x + ' Jahre (FSK ' + x + ')', value: String(x), sel: (m.kidsMax != null ? m.kidsMax : 12) === x }))); if (a != null) PROFILES.setMeta(p.key, { kidsMax: +a }); }
    if (v === 'unrated') PROFILES.setMeta(p.key, { kidsUnrated: !m.kidsUnrated });
    if (v === 'del' && await UI.confirm('Profil entfernen?', p.u.name + ' wird von diesem Gerät entfernt. Auf dem Server bleibt alles erhalten.', 'Entfernen')) PROFILES.remove(p);
    render();
  };
  scr.key = (k) => { if (k === 'back') { UI.confirm('midnox Media beenden?', '', 'Beenden').then(ok => { if (ok && HAS_ANDROID) Android.exit(); }); return true; } return false; };
  render();
});

// ---------- Kinder-Startseite ----------
ROUTER.reg('kidshome', async (scr) => {
  BG.set(null); BG.dim(false);
  const el = scr.el; el.className = 'page kidshome'; el.style.paddingLeft = '0';
  el.innerHTML = '';
  const hello = h('div', 'khead', '<div class="sun">🧸</div><div><h1>Hallo ' + esc(EMBY.user.name) + '!</h1><p>Was möchtest du heute gucken?</p></div>');
  el.appendChild(hello);
  const rows = h('div', 'rows'); rows.setAttribute('data-vs', 'anchor'); rows.dataset.at = '15'; rows.setAttribute('data-mem', '');
  el.appendChild(rows);
  const [resume, next, series, movies] = await Promise.all([
    EMBY.resume().catch(() => ({ Items: [] })), EMBY.nextUp().catch(() => ({ Items: [] })),
    EMBY.items({ Recursive: true, IncludeItemTypes: 'Series', SortBy: 'Random', Limit: 30 }).catch(() => ({ Items: [] })),
    EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie', SortBy: 'Random', Limit: 30 }).catch(() => ({ Items: [] })),
  ]);
  const go = (title, items, kind) => { if (items && items.length) rows.appendChild(V.row(title, items, kind, {})); };
  const ids = new Set();
  const cont = (resume.Items || []).concat(next.Items || []).filter(i => !ids.has(i.Id) && ids.add(i.Id));
  go('▶️ Weitergucken', cont, 'land');
  go('📺 Deine Serien', series.Items, 'poster');
  go('🎬 Filme', movies.Items, 'poster');
  if (!rows.querySelector('.card')) rows.appendChild(h('div', 'empty', 'Hier gibt es noch nichts für dich. Frag deine Eltern! 😊'));
  const first = $('.card', rows); scr.initial = first; NAV.focus(first);
  scr.enter = () => BG.dim(false);
});
