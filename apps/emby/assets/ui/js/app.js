// midnox Emby – Start und globale Ereignisse.
'use strict';
const APP = {
  dirty: false,
  async boot() {
    THEME.apply(EMBY.settings().theme); SAVER.keepAwake(EMBY.settings().saver !== 0);
    try {
      if (await EMBY.restore()) { await APP.afterLogin(); return; }
    } catch (e) {
      if (e.status === 401) { STORE.set('active', null); }
      else if (EMBY.server) { APP.offline(e); return; }
    }
    ROUTER.go('connect', null, { root: true });
  },
  offline(e) {
    $('#rail').classList.add('hidden');
    ROUTER.reg('offline', async (scr) => {
      scr.noRail = true; scr.el.style.paddingLeft = '0';
      scr.el.innerHTML = '<div class="welcome"><div class="brand"><div class="big">midnox<br><b class="grad-text">Emby</b></div></div><div class="panel2"><h2>Server nicht erreichbar</h2><p style="font-size:1.35rem;color:var(--mut);line-height:1.5">' + esc(EMBY.server.name + ' (' + EMBY.server.url + ')') + '<br>' + esc(e.message || '') + '</p><div class="btns" style="margin-top:2rem"></div></div></div>';
      const b = $('.btns', scr.el);
      b.appendChild(onOk(h('div', 'btn pri', 'Erneut versuchen'), () => { ROUTER.drop(9); APP.boot(); }));
      b.appendChild(onOk(h('div', 'btn', 'Anderer Server'), () => ROUTER.go('connect', null, { root: true })));
    });
    ROUTER.go('offline', null, { root: true });
  },
  async afterLogin() {
    await EMBY.loadViews();
    RAIL.build();
    EMBY.capabilities(); EMBY.openSocket();
    await Promise.race([COMM.start(), new Promise(r => setTimeout(r, 3000))]);
    await ROUTER.go('home', null, { root: true });
    setTimeout(() => { if (STORE.get('welcomed') !== EMBY.user.id) { STORE.set('welcomed', EMBY.user.id); UI.toast('Willkommen, ' + EMBY.user.name + '! ◀ öffnet das Menü, MENU zeigt Optionen zu einem Titel.', { icon: '👋', ms: 8000 }); } }, 800);
  },
  async logout(silent) {
    if (PLAYER.active) await PLAYER.stop('user'); MUSIC.stop();
    await EMBY.logoutUser(); clearTimeout(COMM.feedT);
    $('#rail').classList.add('hidden');
    ROUTER.go(EMBY.server ? 'login' : 'connect', EMBY.server, { root: true });
  },
  open(item) {
    switch (item.Type) {
      case 'Movie': case 'Series': case 'Season': case 'Episode': case 'Video': case 'MusicVideo': return ROUTER.go('detail', { id: item.Id });
      case 'BoxSet': return ROUTER.go('boxset', { id: item.Id });
      case 'Person': return ROUTER.go('person', { id: item.Id, name: item.Name });
      case 'MusicAlbum': case 'Playlist': return ROUTER.go('album', { id: item.Id });
      case 'MusicArtist': return ROUTER.go('artist', { id: item.Id, name: item.Name });
      case 'Audio': return MUSIC.play([item], 0);
      case 'CollectionFolder': case 'UserView': case 'Folder': return APP.openLibrary(item);
    }
    return ROUTER.go('detail', { id: item.Id });
  },
  async openId(id) { try { const it = await EMBY.item(id); APP.open(it); } catch (e) { UI.toast('Titel nicht gefunden – vielleicht nicht in deiner Bibliothek.', { icon: '🔎' }); } },
  openLibrary(v) {
    if (v.CollectionType === 'movies' && EMBY.viewsOf('movies').length === 1) return ROUTER.go('movies');
    if (v.CollectionType === 'tvshows' && EMBY.viewsOf('tvshows').length === 1) return ROUTER.go('shows');
    if (v.CollectionType === 'music') return ROUTER.go('music');
    return ROUTER.go('folder', { view: v });
  },
  async play(item, o) {
    o = o || {};
    if (item.Type === 'Series') { const n = await EMBY.nextUp(item.Id); if (n.Items && n.Items[0]) item = n.Items[0]; else return APP.open(item); }
    if (item.Type === 'MusicAlbum') return ROUTER.go('album', { id: item.Id });
    if (item.Type === 'Audio') return MUSIC.play([item], 0);
    const pos = o.fromStart ? 0 : ((item.UserData || {}).PlaybackPositionTicks || 0) / 10000;
    PLAYER.open(item, { startMs: pos, mediaSourceId: o.mediaSourceId });
  },
  async trailer(item) {
    try {
      if (item.LocalTrailerCount) { const t = await EMBY.get('/Users/' + EMBY.user.id + '/Items/' + item.Id + '/LocalTrailers'); if (t && t[0]) { PLAYER.open(t[0], { startMs: 0, trailer: true }); return; } }
      const r = (item.RemoteTrailers || [])[0];
      if (r && r.Url) { const ok = HAS_ANDROID && Android.openUrl ? Android.openUrl(r.Url) : (window.open(r.Url), true); if (!ok) UI.alert('Trailer', 'Für YouTube-Trailer brauchst du die YouTube-App oder SmartTube auf dem Fire TV.'); }
    } catch (e) { UI.toast('Trailer nicht verfügbar', { icon: '🎬' }); }
  },
  async remotePlay(ids, cmd, ticks) {
    try {
      const r = await EMBY.items({ Ids: ids.join(',') }); const list = r.Items || []; if (!list.length) return;
      if (list[0].MediaType === 'Audio') { MUSIC.play(list, 0); ROUTER.go('nowplaying'); return; }
      UI.toast('Fernsteuerung: ' + list[0].Name, { icon: '📲' });
      PLAYER.open(list[0], { startMs: (ticks || 0) / 10000 });
    } catch (e) {}
  },
  toast(t) { UI.toast(t); },
  onDiscover(list) { if (APP.discoverInto) APP.discoverInto(list || []); },
  onPhone(d) { PHONE.got(d); },
  onUpdate(u, manual) {
    if (!u) { if (manual) UI.toast('midnox Emby ist aktuell (' + DEVICE.version + ')', { icon: '✓' }); return; }
    UI.confirm('Update ' + u.versionName + ' verfügbar', u.notes || '', 'Jetzt installieren', 'Später').then(ok => { if (ok) { UI.toast('Lade Update …', { icon: '⬇️', ms: 15000 }); Android.installUpdate(); } });
  },
  onPause() { if (MUSIC.active) {} },
  onResume() { if (EMBY.user) { COMM.feed(); if (!EMBY.ws) EMBY.openSocket(); } },
};
window.APP = APP;
window.addEventListener('error', (e) => console.error('JS', e.message, e.filename, e.lineno));
document.fonts && document.fonts.ready.then(() => APP.boot()).catch(() => APP.boot());
if (!document.fonts) APP.boot();
