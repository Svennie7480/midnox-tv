// midnox Emby – Musik: Bibliothek, Album/Interpret, Warteschlange, Mini-Player, „Läuft gerade".
'use strict';
const MUSIC = {
  active: false, queue: [], idx: 0, playing: false, pos: 0, dur: 0, info: null, lastReport: 0, shuffled: false,
  async play(list, idx, shuffle) {
    if (window.THEMESONG) THEMESONG.stop();
    if (PLAYER.active) await PLAYER.stop('user');
    list = list.filter(x => x.Type === 'Audio'); if (!list.length) return;
    if (shuffle) { list = list.slice(); for (let i = list.length - 1; i > 0; i--) { const j = Math.random() * (i + 1) | 0; [list[i], list[j]] = [list[j], list[i]]; } idx = 0; }
    MUSIC.queue = list; MUSIC.idx = idx || 0; MUSIC.shuffled = !!shuffle;
    await MUSIC.start();
  },
  async start() {
    const it = MUSIC.queue[MUSIC.idx]; if (!it) { MUSIC.stop(); return; }
    if (MUSIC.info) MUSIC.report('stop');
    MUSIC.active = true; MUSIC.pos = 0; MUSIC.dur = (it.RunTimeTicks || 0) / 10000;
    try {
      MUSIC.info = await EMBY.playbackInfo(it, {});
      ADAPTER.play({ url: MUSIC.info.url, video: false, startMs: 0, durMs: MUSIC.dur || 60000 });
      MUSIC.report('start'); WLOG.begin(it);
    } catch (e) { UI.toast('Titel nicht abspielbar: ' + e.message, { icon: '⚠️' }); MUSIC.next(); return; }
    MUSIC.renderMini(); MUSIC.renderNow();
  },
  ev(e) {
    if (e.e === 'tick') { WLOG.tick(e.playing); MUSIC.pos = e.pos; if (e.dur > 0) MUSIC.dur = e.dur; if (MUSIC.playing !== e.playing) { MUSIC.playing = e.playing; MUSIC.renderMini(); MUSIC.renderNow(); } MUSIC.progress(); if (Date.now() - MUSIC.lastReport > 10000) { MUSIC.lastReport = Date.now(); MUSIC.report('progress'); } }
    else if (e.e === 'ended') MUSIC.next(true);
    else if (e.e === 'error') { UI.toast('Titel übersprungen (nicht abspielbar)', { icon: '⚠️' }); MUSIC.next(true); }
  },
  body() { const it = MUSIC.queue[MUSIC.idx]; return { ItemId: it.Id, MediaSourceId: MUSIC.info.src.Id, PlaySessionId: MUSIC.info.playSessionId, PositionTicks: Math.round(MUSIC.pos * 10000), IsPaused: !MUSIC.playing, PlayMethod: MUSIC.info.method, CanSeek: true, QueueableMediaTypes: ['Audio'] }; },
  report(k) { if (MUSIC.info) EMBY.report(k, MUSIC.body()); },
  toggle() { if (!MUSIC.active) return; if (MUSIC.playing) ADAPTER.pause(); else ADAPTER.resume(); },
  next(auto) { if (MUSIC.idx < MUSIC.queue.length - 1) { MUSIC.idx++; MUSIC.start(); } else if (auto) MUSIC.stop(); },
  prev() { if (MUSIC.pos > 4000 || MUSIC.idx === 0) { ADAPTER.seek(0); return; } MUSIC.idx--; MUSIC.start(); },
  stop(silent) { if (!MUSIC.active) return; MUSIC.report('stop'); WLOG.flush(true); if (MUSIC.info && MUSIC.info.method === 'Transcode') EMBY.stopEncoding(MUSIC.info.playSessionId); if (!silent) ADAPTER.stop(); MUSIC.active = false; MUSIC.info = null; MUSIC.playing = false; $('#mini').classList.add('off'); MUSIC.renderNow(); },
  mediaKey(k) { if (!MUSIC.active) return false; if (k === 'playpause' || k === 'play' || k === 'pause') MUSIC.toggle(); else if (k === 'next') MUSIC.next(); else if (k === 'prev') MUSIC.prev(); else if (k === 'stop') MUSIC.stop(); return true; },
  remote(d) { const c = d.Command; if (c === 'PlayPause' || c === 'Pause' || c === 'Unpause') MUSIC.toggle(); else if (c === 'NextTrack') MUSIC.next(); else if (c === 'PreviousTrack') MUSIC.prev(); else if (c === 'Stop') MUSIC.stop(); else if (c === 'Seek') ADAPTER.seek((d.SeekPositionTicks || 0) / 10000); },
  cur() { return MUSIC.queue[MUSIC.idx]; },
  renderMini() {
    const m = $('#mini'); const it = MUSIC.cur();
    if (!MUSIC.active || !it || (ROUTER.top() && ROUTER.top().name === 'nowplaying') || PLAYER.active) { m.classList.add('off'); return; }
    m.classList.remove('off'); m.classList.toggle('paused', !MUSIC.playing);
    m.innerHTML = '<div class="c" style="background-image:url(\'' + EMBY.img(it, 'Primary', 160) + '\')"></div><div style="min-width:0"><div class="t">' + esc(it.Name) + '</div><div class="s">' + esc((it.Artists || [])[0] || it.AlbumArtist || '') + ' · ⏯ Play/Pause · MENU öffnen</div><div class="bar"><i></i></div></div><div class="eq"><i></i><i></i><i></i></div>';
    MUSIC.progress();
  },
  progress() {
    const p = MUSIC.dur ? MUSIC.pos / MUSIC.dur * 100 : 0;
    const b = $('#mini .bar i'); if (b) b.style.width = p + '%';
    const np = ROUTER.top() && ROUTER.top().name === 'nowplaying' ? ROUTER.top().el : null;
    if (np) { const pb = $('.pbar i', np); if (pb) pb.style.width = p + '%'; const t = $('.times', np); if (t) t.innerHTML = '<span>' + fmtTime(MUSIC.pos) + '</span><span>' + fmtTime(MUSIC.dur) + '</span>'; }
  },
  renderNow() { const t = ROUTER.top(); if (t && t.name === 'nowplaying' && t.draw) t.draw(); },
};

ROUTER.reg('music', async (scr, arg) => {
  BG.set(null); BG.dim(true);
  const views = EMBY.viewsOf('music');
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  if (!views.length) { el.innerHTML = '<div class="empty">Keine Musik-Bibliothek gefunden.</div>'; return; }
  const head = h('div', 'libhead', '<h1>Musik</h1><span class="count"></span>'); el.appendChild(head);
  const gw = h('div', 'grid-wrap'); const grid = h('div', 'grid square'); grid.setAttribute('data-vs', 'keep'); grid.setAttribute('data-mem', ''); gw.appendChild(grid); el.appendChild(gw);
  const parent = views[0].Id;
  let tab = scr.tab || 'albums';
  const tabs = [['albums', 'Alben'], ['artists', 'Interpreten'], ['playlists', 'Playlists']];
  const show = async (t) => {
    tab = scr.tab = t; $$('.chip[data-t]', head).forEach(c => c.classList.toggle('sel', c.dataset.t === t));
    grid.innerHTML = ''; grid._ty = 0; grid.style.transform = '';
    let r;
    if (t === 'albums') r = await EMBY.items({ ParentId: parent, Recursive: true, IncludeItemTypes: 'MusicAlbum', SortBy: 'DateCreated,SortName', SortOrder: 'Descending', Limit: 300 });
    else if (t === 'artists') r = await EMBY.get('/Artists/AlbumArtists', { ParentId: parent, UserId: EMBY.user.id, Fields: 'PrimaryImageAspectRatio', Limit: 300, SortBy: 'SortName' });
    else r = await EMBY.items({ Recursive: true, IncludeItemTypes: 'Playlist', MediaTypes: 'Audio', SortBy: 'SortName', Limit: 200 });
    const items = (r && r.Items) || [];
    $('.count', head).textContent = items.length + ' ' + (t === 'albums' ? 'Alben' : t === 'artists' ? 'Interpreten' : 'Playlists');
    for (const it of items) grid.appendChild(V.card(it, t === 'artists' ? 'circle' : 'square', { cap: true, ok: () => ROUTER.go(t === 'artists' ? 'artist' : 'album', { id: it.Id, name: it.Name }), focus: (i) => BG.set(EMBY.backdrop(i) || '') }));
    if (!items.length) grid.appendChild(h('div', 'empty', 'Nichts da.'));
  };
  for (const [t, l] of tabs) { const c = onOk(h('div', 'chip', l), async () => { await show(t); const f = $('.card', grid); if (f) NAV.focus(f); }); c.dataset.t = t; head.appendChild(c); }
  head.appendChild(onOk(h('div', 'chip', ic('shuffle', ' style="width:1.5rem;height:1.5rem"') + 'Alles mischen'), async () => { const r = await EMBY.items({ ParentId: parent, Recursive: true, IncludeItemTypes: 'Audio', SortBy: 'Random', Limit: 200 }); MUSIC.play(r.Items || [], 0); ROUTER.go('nowplaying'); }));
  if (MUSIC.active) head.appendChild(onOk(h('div', 'chip sel', '♫ Läuft gerade'), () => ROUTER.go('nowplaying')));
  await show(tab);
  scr.initial = $('.card', grid);
  scr.leave = () => BG.dim(false); scr.enter = () => { BG.dim(true); MUSIC.renderMini(); };
});

ROUTER.reg('album', async (scr, arg) => {
  BG.dim(true);
  const al = await EMBY.item(arg.id);
  const tr = await EMBY.items(al.Type === 'Playlist' ? { ParentId: al.Id, Limit: 500 } : { ParentId: al.Id, SortBy: 'ParentIndexNumber,IndexNumber,SortName', Limit: 500 });
  const tracks = (tr.Items || []).filter(x => x.Type === 'Audio');
  BG.set(EMBY.backdrop(al) || EMBY.img(al, 'Primary', 1400));
  const el = scr.el; el.innerHTML = '';
  const sc = h('div', ''); sc.style.cssText = 'position:absolute;left:9rem;right:0;top:0'; sc.setAttribute('data-vs', 'anchor'); sc.dataset.at = '4'; el.appendChild(sc);
  const top = h('div', 'album-top'); top.setAttribute('data-vsi', ''); top.setAttribute('data-vtop', ''); sc.appendChild(top);
  const cv = h('div', 'cover'); cv.style.backgroundImage = 'url("' + EMBY.img(al, 'Primary', 600) + '")'; top.appendChild(cv);
  const info = h('div', ''); top.appendChild(info);
  const total = tracks.reduce((s, t) => s + (t.RunTimeTicks || 0), 0);
  info.innerHTML = '<div style="font-size:1.05rem;letter-spacing:.2em;text-transform:uppercase;color:var(--mut);font-weight:800">' + (al.Type === 'Playlist' ? 'Playlist' : 'Album') + '</div><h1>' + esc(al.Name) + '</h1><div class="by">' + esc(al.AlbumArtist || (al.Artists || [])[0] || '') + '</div><div class="meta" style="margin-top:1rem"><span>' + (al.ProductionYear || '') + '</span><span>' + tracks.length + ' Titel</span><span>' + fmtDur(total) + '</span></div>';
  const b = h('div', 'btns'); b.style.marginTop = '2rem'; info.appendChild(b);
  b.appendChild(onOk(h('div', 'btn pri', ic('play') + 'Abspielen'), () => { MUSIC.play(tracks, 0); ROUTER.go('nowplaying'); }));
  b.appendChild(onOk(h('div', 'btn', ic('shuffle') + 'Zufall'), () => { MUSIC.play(tracks, 0, true); ROUTER.go('nowplaying'); }));
  const fav = (al.UserData || {}).IsFavorite;
  const fb = onOk(h('div', 'btn icon' + (fav ? ' on' : ''), ic(fav ? 'heartf' : 'heart')), async () => { const on = !fb.classList.contains('on'); await EMBY.setFav(al.Id, on); fb.classList.toggle('on', on); fb.innerHTML = ic(on ? 'heartf' : 'heart'); }); b.appendChild(fb);
  const list = h('div', 'tracks'); list.setAttribute('data-mem', ''); sc.appendChild(list);
  tracks.forEach((t, i) => {
    const e = h('div', 'trk f' + (MUSIC.active && MUSIC.cur() && MUSIC.cur().Id === t.Id ? ' cur' : ''), '<span class="n">' + (t.IndexNumber || i + 1) + '</span><span class="t">' + esc(t.Name) + '</span><span class="a">' + esc((t.Artists || []).join(', ')) + '</span><span class="d">' + fmtTime((t.RunTimeTicks || 0) / 10000) + '</span>');
    e.setAttribute('data-vsi', ''); e._ok = () => { MUSIC.play(tracks, i); $$('.trk', list).forEach(x => x.classList.toggle('cur', x === e)); }; e._menu = () => V.itemMenu(t); list.appendChild(e);
  });
  scr.initial = $('.btn', b);
  scr.leave = () => BG.dim(false); scr.enter = () => { BG.dim(true); MUSIC.renderMini(); };
});

ROUTER.reg('artist', async (scr, arg) => {
  BG.dim(true);
  const r = await EMBY.items({ ArtistIds: arg.id, Recursive: true, IncludeItemTypes: 'MusicAlbum', SortBy: 'ProductionYear,SortName', SortOrder: 'Descending' });
  const el = scr.el; el.style.paddingLeft = '0'; el.innerHTML = '';
  const head = h('div', 'libhead', '<h1>' + esc(arg.name) + '</h1><span class="count">' + (r.TotalRecordCount || 0) + ' Alben</span>'); el.appendChild(head);
  head.appendChild(onOk(h('div', 'chip', ic('shuffle', ' style="width:1.5rem;height:1.5rem"') + 'Alle Titel mischen'), async () => { const t = await EMBY.items({ ArtistIds: arg.id, Recursive: true, IncludeItemTypes: 'Audio', Limit: 300 }); MUSIC.play(t.Items || [], 0, true); ROUTER.go('nowplaying'); }));
  const gw = h('div', 'grid-wrap'); const grid = h('div', 'grid square'); grid.setAttribute('data-vs', 'keep'); gw.appendChild(grid); el.appendChild(gw);
  for (const it of r.Items || []) grid.appendChild(V.card(it, 'square', { cap: true, ok: () => ROUTER.go('album', { id: it.Id }) }));
  scr.initial = $('.card', grid);
  scr.leave = () => BG.dim(false); scr.enter = () => BG.dim(true);
});

ROUTER.reg('nowplaying', async (scr) => {
  BG.dim(false); $('#mini').classList.add('off');
  const el = scr.el; el.innerHTML = '';
  const np = h('div', 'np'); el.appendChild(np);
  const art = h('div', 'art'); np.appendChild(art); const right = h('div'); np.appendChild(right);
  const draw = () => {
    const it = MUSIC.cur();
    if (!MUSIC.active || !it) { right.innerHTML = '<h1>Gerade läuft nichts</h1>'; art.style.backgroundImage = ''; return; }
    art.style.backgroundImage = 'url("' + EMBY.img(it, 'Primary', 700) + '")'; BG.set(EMBY.img(it, 'Primary', 1400));
    const keep = NAV.cur && right.contains(NAV.cur) ? NAV.cur.dataset.k : 'pp';
    right.innerHTML = '<div style="font-size:1.05rem;letter-spacing:.2em;text-transform:uppercase;color:var(--mut);font-weight:800">Läuft gerade</div><h1>' + esc(it.Name) + '</h1><div class="by">' + esc((it.Artists || [])[0] || it.AlbumArtist || '') + '</div><div class="al">' + esc(it.Album || '') + '</div><div class="pbar"><i></i></div><div class="times"></div>';
    const b = h('div', 'btns'); right.appendChild(b);
    const add = (k, html, fn, cls) => { const x = onOk(h('div', 'btn icon' + (cls ? ' ' + cls : ''), html), fn); x.dataset.k = k; b.appendChild(x); };
    add('prev', ic('prev'), () => MUSIC.prev()); add('pp', ic(MUSIC.playing ? 'pause' : 'play'), () => MUSIC.toggle(), 'pri'); add('next', ic('next'), () => MUSIC.next());
    add('stop', '<span style="font-size:1.6rem">■</span>', () => { MUSIC.stop(); ROUTER.back(); });
    const n = MUSIC.queue[MUSIC.idx + 1]; right.appendChild(h('div', 'queue', 'Titel ' + (MUSIC.idx + 1) + ' von ' + MUSIC.queue.length + (n ? ' · danach: ' + esc(n.Name) : '') + (MUSIC.shuffled ? ' · gemischt' : '')));
    MUSIC.progress();
    const f = $('[data-k="' + keep + '"]', b); if (f && (!NAV.cur || !NAV.cur.isConnected || right.contains(NAV.cur) || NAV.cur === null)) NAV.focus(f);
  };
  scr.draw = draw; draw();
  scr.initial = $('[data-k="pp"]', right);
  scr.key = (k) => { if (k === 'left' || k === 'right') { if (NAV.cur && NAV.cur.closest('.btns')) return false; } if (k === 'ff') { ADAPTER.seek(MUSIC.pos + 15000); return true; } if (k === 'rew') { ADAPTER.seek(MUSIC.pos - 15000); return true; } return false; };
  scr.leave = () => MUSIC.renderMini(); scr.destroy = () => setTimeout(() => MUSIC.renderMini(), 50);
  scr.enter = () => { $('#mini').classList.add('off'); draw(); };
});
window.MUSIC = MUSIC;
