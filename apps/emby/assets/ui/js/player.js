// midnox Emby – Wiedergabe: Steuerung des nativen Players, Bedienoberfläche, Fortschritt an Emby.
'use strict';

/** Native Wiedergabe (Android) oder Simulation im Testbrowser. */
const ADAPTER = HAS_ANDROID ? {
  play(o) { Android.play(JSON.stringify(o)); }, pause() { Android.pause(); }, resume() { Android.resume(); },
  seek(ms) { Android.seek(ms); }, stop() { Android.stop(); }, track(t, g, i) { Android.track(t, g, i); },
  speed(r) { Android.speed(r); }, volume(v) { Android.volume(v); },
} : {
  // Simulation: läuft in Echtzeit, damit OSD, Fortschritt und Watch-Party im Browser testbar sind.
  t: null, pos: 0, dur: 0, playing: false, rate: 1, last: 0,
  play(o) {
    const A = ADAPTER; clearInterval(A.t); A.pos = o.startMs || 0; A.dur = o.durMs || 600000; A.playing = !o.paused; A.last = performance.now(); A.rate = 1; A.o = o;
    setTimeout(() => { PLAYER.ev({ e: 'tracks', list: (o.fakeTracks || []) }); PLAYER.ev({ e: 'ready' }); }, 250);
    A.t = setInterval(() => {
      const now = performance.now(); if (A.playing) A.pos = Math.min(A.dur, A.pos + (now - A.last) * A.rate); A.last = now;
      PLAYER.ev({ e: 'tick', pos: Math.round(A.pos), dur: A.dur, buf: Math.min(A.dur, A.pos + 20000), playing: A.playing, state: A.pos >= A.dur ? 4 : 3, wantPlay: A.playing });
      if (A.pos >= A.dur && A.playing) { A.playing = false; PLAYER.ev({ e: 'ended' }); }
    }, 250);
  },
  pause() { ADAPTER.playing = false; }, resume() { if (ADAPTER.pos >= ADAPTER.dur) ADAPTER.pos = 0; ADAPTER.playing = true; ADAPTER.last = performance.now(); },
  seek(ms) { ADAPTER.pos = Math.max(0, Math.min(ADAPTER.dur, ms)); }, stop() { clearInterval(ADAPTER.t); ADAPTER.playing = false; },
  track() {}, speed(r) { ADAPTER.rate = r; }, volume() {},
};

/** Spul-Vorschaubilder: Emby liefert eine BIF-Datei (Roku-Format), Jellyfin Bilderkacheln (Trickplay). */
const TRICK = {
  id: null, ts: null, urls: null, cache: {}, jfi: null,
  reset() { TRICK.id = null; TRICK.ts = null; TRICK.jfi = null; TRICK.cache = {}; if (TRICK.urls) TRICK.urls.forEach(u => URL.revokeObjectURL(u)); TRICK.urls = null; try { if (HAS_ANDROID && Android.bifClear) Android.bifClear(); } catch (e) {} },
  load(item, msId) {
    TRICK.reset(); if (!item || EMBY.settings().trick === false) return;
    const id = item.Id; TRICK.id = id;
    if (EMBY.jf) { TRICK.loadJf(item, msId); return; }
    const url = EMBY.api + '/Videos/' + id + '/index.bif?Width=320' + (msId ? '&MediaSourceId=' + msId : '') + '&' + EMBY.tq();
    if (HAS_ANDROID) { if (Android.bifLoad) Android.bifLoad(url, id); return; }
    const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = 'arraybuffer';
    x.onload = () => { if (x.status === 200 && TRICK.id === id) TRICK.parse(x.response, id); }; x.send();
  },
  async loadJf(item, msId) {
    let tp = item.Trickplay;
    if (!tp) { try { tp = (await EMBY.item(item.Id)).Trickplay; } catch (e) {} }
    if (!tp || TRICK.id !== item.Id) return;
    const bySrc = tp[msId] || tp[Object.keys(tp)[0]]; if (!bySrc) return;
    const widths = Object.keys(bySrc).map(Number).sort((a, b) => Math.abs(a - 320) - Math.abs(b - 320));
    const w = widths[0]; const i = bySrc[w]; if (!i || !i.ThumbnailCount) return;
    TRICK.jfi = { id: item.Id, ms: msId, w, cols: i.TileWidth || 10, rows: i.TileHeight || 10, n: i.ThumbnailCount, iv: i.Interval || 10000, ar: (i.Width && i.Height) ? i.Width / i.Height : 16 / 9 };
    TRICK.ts = true;
    // erste Kachel vorladen
    const im = new Image(); im.src = TRICK.tile(0);
  },
  tile(k) { const j = TRICK.jfi; return EMBY.api + '/Videos/' + j.id + '/Trickplay/' + j.w + '/' + k + '.jpg?' + (j.ms ? 'MediaSourceId=' + j.ms + '&' : '') + EMBY.tq(); },
  parse(buf, id) {
    const v = new DataView(buf); if (buf.byteLength < 72 || v.getUint32(0) !== 0x89424946) return;
    const n = v.getUint32(12, true); let m = v.getUint32(16, true) || 1000; if (!n || 64 + (n + 1) * 8 > buf.byteLength) return;
    const ts = [], urls = [];
    for (let i = 0; i < n; i++) { ts.push(v.getUint32(64 + i * 8, true) * m); const a = v.getUint32(68 + i * 8, true), b = v.getUint32(76 + i * 8, true); urls.push(URL.createObjectURL(new Blob([buf.slice(a, b)], { type: 'image/jpeg' }))); }
    TRICK.ts = ts; TRICK.urls = urls;
  },
  frame(ms) {
    const j = TRICK.jfi;
    if (j) {
      const n = Math.max(0, Math.min(j.n - 1, Math.floor(ms / j.iv))); const per = j.cols * j.rows; const k = Math.floor(n / per), r = n % per;
      return { url: TRICK.tile(k), pos: (j.cols > 1 ? (r % j.cols) / (j.cols - 1) * 100 : 0) + '% ' + (j.rows > 1 ? Math.floor(r / j.cols) / (j.rows - 1) * 100 : 0) + '%', size: j.cols * 100 + '% ' + j.rows * 100 + '%' };
    }
    const ts = TRICK.ts; if (!ts || !ts.length) return null;
    let lo = 0, hi = ts.length - 1; while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (ts[mid] <= ms) lo = mid; else hi = mid - 1; }
    if (TRICK.urls) return TRICK.urls[lo];
    if (!TRICK.cache[lo]) { try { const b = Android.bifFrame(lo); if (!b) return null; TRICK.cache[lo] = 'data:image/jpeg;base64,' + b; } catch (e) { return null; } }
    return TRICK.cache[lo];
  },
};
window.TRICK = TRICK;

const PLAYER = {
  active: false, item: null, info: null, pos: 0, dur: 0, buf: 0, playing: false, state: 1, ready: false,
  audioIndex: null, subIndex: -1, tracks: [], osdOn: false, osdTimer: 0, scrub: null, lastReport: 0, nextItem: null, upNextShown: false,
  party: null, ended: false,

  async open(item, opts) {
    if (window.THEMESONG) THEMESONG.stop();
    opts = opts || {};
    if (window.MUSIC) MUSIC.stop(true);
    if (PLAYER.active) await PLAYER.stop('switch');
    PLAYER.active = true; PLAYER.ended = false; PLAYER.ready = false; PLAYER.upNextShown = false; PLAYER.nextItem = null; PLAYER.fallback = false;
    PLAYER.party = opts.party || null; PLAYER.isTrailer = !!opts.trailer; PLAYER.pos = opts.startMs || 0; PLAYER.dur = (item.RunTimeTicks || 0) / 10000;
    document.body.classList.add('playing'); document.documentElement.classList.add('playing');
    PLAYER.buildOsd(); PLAYER.showOsd(true);
    PLAYER.loading(true);
    try {
      if (!item.MediaSources || !item.MediaStreams) item = await EMBY.item(item.Id);
      PLAYER.item = item;
      const s = EMBY.settings();
      PLAYER.audioIndex = opts.audioIndex != null ? opts.audioIndex : null;
      PLAYER.subIndex = opts.subIndex != null ? opts.subIndex : null;
      PLAYER.renderHead();
      await PLAYER.load(opts.startMs || 0, { paused: !!opts.paused });
      if (item.Type === 'Episode') PLAYER.findNext();
    } catch (e) {
      PLAYER.loading(false);
      await UI.alert('Wiedergabe nicht möglich', e.message || String(e));
      PLAYER.close();
    }
  },
  /** PlaybackInfo holen und Stream starten (auch für Ton-/Untertitel-Wechsel). */
  async load(startMs, o) {
    o = o || {};
    const it = PLAYER.item; const s = EMBY.settings();
    const first = !PLAYER.info;
    const info = await EMBY.playbackInfo(it, { startMs, audioIndex: PLAYER.audioIndex, subIndex: PLAYER.subIndex == null ? undefined : PLAYER.subIndex, mediaSourceId: PLAYER.mediaSourceId, forceTranscode: PLAYER.fallback });
    if (PLAYER.info && PLAYER.info.method === 'Transcode' && PLAYER.info.playSessionId !== info.playSessionId) EMBY.stopEncoding(PLAYER.info.playSessionId);
    PLAYER.info = info; PLAYER.mediaSourceId = info.src.Id;
    if (!TRICK.id || TRICK.id !== it.Id) TRICK.load(it, info.src.Id);
    if (EMBY.jf && !it._seg) {   // Jellyfin-Segmente als Intro-/Abspann-Marken
      it._seg = true;
      EMBY.segments(it.Id).then(seg => {
        if (!seg.length) return; const ch = it.Chapters = (it.Chapters || []).filter(c => !c.MarkerType);
        for (const x of seg) {
          if (x.type === 'Intro') ch.push({ MarkerType: 'IntroStart', StartPositionTicks: x.start * 10000 }, { MarkerType: 'IntroEnd', StartPositionTicks: x.end * 10000 });
          if (x.type === 'Outro' && !ch.some(c => c.MarkerType === 'CreditsStart')) ch.push({ MarkerType: 'CreditsStart', StartPositionTicks: x.start * 10000 });
        }
      });
    }
    if (!PLAYER.isTrailer) WLOG.begin(it, { party: !!PLAYER.party });
    const streams = info.src.MediaStreams || [];
    if (PLAYER.audioIndex == null) PLAYER.audioIndex = info.src.DefaultAudioStreamIndex != null ? info.src.DefaultAudioStreamIndex : ((streams.find(x => x.Type === 'Audio') || {}).Index);
    if (PLAYER.subIndex == null) PLAYER.subIndex = PLAYER.pickDefaultSub(info.src);
    const subs = [];
    const sub = streams.find(x => x.Type === 'Subtitle' && x.Index === PLAYER.subIndex);
    if (sub && (sub.IsTextSubtitleStream || sub.DeliveryMethod === 'External') && sub.DeliveryMethod !== 'Encode') {
      let u = EMBY.subUrl(it, info.src, sub).replace(/\/Stream\.\w+(\?|$)/, '/Stream.vtt$1');
      subs.push({ url: u, lang: sub.Language || '', label: sub.DisplayTitle || '', mime: 'vtt', index: sub.Index });
    }
    $('#subs').innerHTML = '';
    const fakeTracks = streams.filter(x => x.Type === 'Audio').map((x, i) => ({ type: 'audio', g: i, i: 0, lang: x.Language || '', label: x.DisplayTitle || '', sel: x.Index === PLAYER.audioIndex, ok: true }));
    ADAPTER.play({ url: info.url, hls: info.hls, startMs: startMs, paused: !!o.paused, video: true, textOn: subs.length > 0, textLang: sub ? (sub.Language || 'und') : 'de', audioLang: s.audioLang, subs, durMs: PLAYER.dur || 600000, fakeTracks });
    PLAYER.reportBody = () => ({ ItemId: it.Id, MediaSourceId: info.src.Id, PlaySessionId: info.playSessionId, PositionTicks: Math.round(PLAYER.pos * 10000), IsPaused: !PLAYER.playing, IsMuted: false,
      PlayMethod: info.method, AudioStreamIndex: PLAYER.audioIndex, SubtitleStreamIndex: PLAYER.subIndex == null ? -1 : PLAYER.subIndex, CanSeek: true, PlaybackRate: 1, VolumeLevel: 100,
      RunTimeTicks: it.RunTimeTicks, QueueableMediaTypes: ['Video', 'Audio'], RepeatMode: 'RepeatNone' });
    if (first) EMBY.report('start', PLAYER.reportBody());
    PLAYER.lastReport = Date.now();
    PLAYER.renderControls();
  },
  pickDefaultSub(src) {
    const s = EMBY.settings(); const subs = (src.MediaStreams || []).filter(x => x.Type === 'Subtitle');
    if (!subs.length || s.subMode === 'off') return -1;
    const L = (x) => (x.Language || '').toLowerCase();
    const want = s.subLang || 'ger'; const isWant = (x) => [want, want.slice(0, 2), want === 'ger' ? 'deu' : want, want === 'ger' ? 'de' : want].indexOf(L(x)) >= 0;
    if (s.subMode === 'always') { const m = subs.find(isWant) || (src.DefaultSubtitleStreamIndex != null ? subs.find(x => x.Index === src.DefaultSubtitleStreamIndex) : null); return m ? m.Index : -1; }
    // „smart": nur erzwungene Untertitel – oder wenn die Tonspur nicht in der Wunschsprache ist
    const forced = subs.find(x => x.IsForced && isWant(x)); if (forced) return forced.Index;
    const aud = (src.MediaStreams || []).find(x => x.Index === PLAYER.audioIndex);
    if (aud && aud.Language && !isWant(aud) && ['ger', 'deu', 'de'].indexOf(aud.Language.toLowerCase()) < 0) { const m = subs.find(isWant); if (m) return m.Index; }
    return src.DefaultSubtitleStreamIndex != null && src.DefaultSubtitleStreamIndex >= 0 ? src.DefaultSubtitleStreamIndex : -1;
  },
  async findNext() {
    try {
      const it = PLAYER.item; const eps = await EMBY.episodes(it.SeriesId);
      const list = (eps.Items || []).filter(e => e.LocationType !== 'Virtual');
      const i = list.findIndex(e => e.Id === it.Id);
      PLAYER.nextItem = i >= 0 && i < list.length - 1 ? list[i + 1] : null;
      PLAYER.renderControls();
    } catch (e) {}
  },

  // ---------- Ereignisse vom nativen Player ----------
  ev(e) {
    if (!PLAYER.active && !(window.MUSIC && MUSIC.active)) return;
    if (window.MUSIC && MUSIC.active && !PLAYER.active) { MUSIC.ev(e); return; }
    switch (e.e) {
      case 'tick':
        PLAYER.pos = e.pos; if (e.dur > 0) PLAYER.dur = e.dur; PLAYER.buf = e.buf; PLAYER.state = e.state;
        if (PLAYER.playing !== e.playing) { PLAYER.playing = e.playing; PLAYER.renderControls(); if (PLAYER.ready) EMBY.report('progress', Object.assign(PLAYER.reportBody(), { EventName: e.playing ? 'unpause' : 'pause' })); }
        PLAYER.loading(e.state === 2 && e.wantPlay);
        PLAYER.renderTime();
        PLAYER.checkMarkers();
        if (Date.now() - PLAYER.lastReport > 10000 && PLAYER.ready) { PLAYER.lastReport = Date.now(); EMBY.report('progress', Object.assign(PLAYER.reportBody(), { EventName: 'timeupdate' })); }
        if (PLAYER.party) PARTY.localTick();
        PLAYER.checkSleep();
        WLOG.tick(e.playing);
        break;
      case 'ready':
        if (!PLAYER.ready) { PLAYER.ready = true; PLAYER.loading(false); if (!PLAYER.party) setTimeout(() => PLAYER.hideOsdSoon(), 400); if (PLAYER.party) PARTY.localReady(); }
        break;
      case 'bif': if (e.id === TRICK.id) TRICK.ts = e.ts; break;
      case 'tracks': PLAYER.tracks = e.list || []; PLAYER.applyAudioTrack(); break;
      case 'cues': { const sEl = $('#subs'); sEl.innerHTML = e.text ? '<span>' + esc(e.text).replace(/&lt;\/?(i|b|u)&gt;/g, '') + '</span>' : ''; break; }
      case 'ended': PLAYER.onEnded(); break;
      case 'error': PLAYER.onError(e); break;
    }
  },
  async onError(e) {
    if (!PLAYER.active) return;
    if (PLAYER.info && PLAYER.info.method !== 'Transcode' && !PLAYER.fallback) {
      PLAYER.fallback = true; UI.toast('Direkte Wiedergabe klappt nicht – der Server wandelt um …', { icon: '⚙️' });
      try { await PLAYER.load(PLAYER.pos); return; } catch (x) { e = { msg: x.message }; }
    }
    PLAYER.loading(false);
    await UI.alert('Wiedergabe abgebrochen', (e.msg || 'Unbekannter Fehler').replace(/^ERROR_CODE_/, ''));
    PLAYER.stop('error');
  },
  applyAudioTrack() {
    if (!PLAYER.info || PLAYER.info.method === 'Transcode') return;
    const auds = (PLAYER.info.src.MediaStreams || []).filter(x => x.Type === 'Audio' && !x.IsExternal);
    const k = auds.findIndex(x => x.Index === PLAYER.audioIndex);
    const nat = PLAYER.tracks.filter(t => t.type === 'audio');
    if (k < 0 || !nat[k] || nat[k].sel) return;
    ADAPTER.track('audio', nat[k].g, nat[k].i);
  },

  // ---------- Steuerung ----------
  toggle() { if (PLAYER.playing) PLAYER.pause(true); else PLAYER.play(true); },
  pause(local) { ADAPTER.pause(); PLAYER.flash('pause'); if (local && PLAYER.party) PARTY.localAction(false, PLAYER.pos); PLAYER.showOsd(); },
  play(local) { if (PLAYER.ended) { PLAYER.ended = false; } ADAPTER.resume(); PLAYER.flash('play'); if (local && PLAYER.party) PARTY.localAction(true, PLAYER.pos); PLAYER.hideOsdSoon(); },
  seekTo(ms, local) {
    ms = Math.max(0, Math.min(PLAYER.dur ? PLAYER.dur - 1000 : ms, ms)); PLAYER.pos = ms; ADAPTER.seek(ms); PLAYER.renderTime();
    if (local && PLAYER.party) PARTY.localAction(PLAYER.playing, ms);
    EMBY.report('progress', Object.assign(PLAYER.reportBody ? PLAYER.reportBody() : {}, { EventName: 'timeupdate' }));
  },
  volume(v) { ADAPTER.volume(Math.max(0, Math.min(1, v))); },
  remote(d) {
    const c = d.Command;
    if (c === 'Pause') PLAYER.pause(true); else if (c === 'Unpause') PLAYER.play(true); else if (c === 'PlayPause') PLAYER.toggle();
    else if (c === 'Stop') PLAYER.stop('remote'); else if (c === 'Seek') PLAYER.seekTo((d.SeekPositionTicks || 0) / 10000, true);
    else if (c === 'NextTrack' && PLAYER.nextItem) PLAYER.playNext(); else if (c === 'FastForward') PLAYER.seekTo(PLAYER.pos + 30000, true); else if (c === 'Rewind') PLAYER.seekTo(PLAYER.pos - 10000, true);
  },
  async stop(reason) {
    if (!PLAYER.active) return;
    const it = PLAYER.item, pos = PLAYER.pos, dur = PLAYER.dur, info = PLAYER.info;
    if (info) { EMBY.report('stop', Object.assign(PLAYER.reportBody(), { PositionTicks: Math.round(pos * 10000) })); if (info.method === 'Transcode') EMBY.stopEncoding(info.playSessionId); }
    if (PLAYER.party && reason !== 'switch') PARTY.leave();
    ADAPTER.stop();
    if (!PLAYER.isTrailer) WLOG.flush(!!(dur && pos / dur > .85) || reason === 'ended');
    TRICK.reset(); PLAYER.active = false; PLAYER.info = null; PLAYER.mediaSourceId = null; PLAYER.tracks = [];
    clearTimeout(PLAYER.osdTimer); clearTimeout(PLAYER.scrubT); clearInterval(PLAYER.upNextT);
    if (reason !== 'switch') PLAYER.close();
    APP.dirty = true;
    if (reason === 'user' || reason === 'error') { PLAYER.sleep = null; }
    if (reason !== 'switch' && reason !== 'sleep' && !PLAYER.isTrailer && it && dur && (pos / dur > .85 || reason === 'ended') && (it.Type === 'Movie' || it.Type === 'Episode')) setTimeout(() => COMM.afterWatch(it), 400);
  },
  close() {
    document.body.classList.remove('playing'); document.documentElement.classList.remove('playing');
    const o = $('#osd'); o.className = 'off'; o.innerHTML = ''; $('#subs').innerHTML = ''; $('#reacts').innerHTML = '';
    PLAYER.active = false;
    const t = ROUTER.top(); if (t) { if (t.refresh) t.refresh(); NAV.focus(t._focus && t._focus.isConnected ? t._focus : (NAV.cur && NAV.cur.isConnected && t.el.contains(NAV.cur) ? NAV.cur : NAV.first(t.el))); }
  },
  onEnded() {
    if (PLAYER.ended) return; PLAYER.ended = true;
    if (PLAYER.party && !PARTY.isHost()) return;   // Gäste folgen dem Gastgeber
    if (PLAYER.sleep && PLAYER.sleep.end) { PLAYER.sleep = null; UI.toast('Gute Nacht! 🌙', { icon: '💤' }); PLAYER.stop('sleep'); return; }
    if (PLAYER.nextItem && EMBY.settings().autoNext && !PLAYER.party) { PLAYER.autoNext(); return; }
    PLAYER.stop('ended');
  },
  /** Automatisch zur nächsten Folge – nach einigen Folgen ohne Tastendruck erst nachfragen. */
  autoNext() {
    const v = EMBY.settings().still; const lim = v === undefined ? 3 : +v;
    if (window.SAVER && SAVER.last > (PLAYER._chainT || 0)) PLAYER._chain = 0;   // seit dem letzten Wechsel wurde eine Taste gedrückt
    PLAYER._chain = (PLAYER._chain || 0) + 1; PLAYER._chainT = Date.now();
    if (lim && PLAYER._chain > lim) { PLAYER._chain = 0; PLAYER.askStill(); return; }
    PLAYER.playNext();
  },
  askStill() {
    if (PLAYER.playing) ADAPTER.pause();
    let t = 0;
    const p = UI.modal((box, close) => {
      box.classList.add('stillbox');
      box.appendChild(h('div', 'still-ic', '🛋️'));
      box.appendChild(h('h2', '', 'Schaust du noch?'));
      box.appendChild(h('p', '', 'Es liefen ' + ((+EMBY.settings().still || 3) + ' Folgen') + ' hintereinander ohne Tastendruck. Als Nächstes: ' + esc((PLAYER.nextItem && (PLAYER.nextItem.SeriesName ? 'S' + PLAYER.nextItem.ParentIndexNumber + ' F' + PLAYER.nextItem.IndexNumber : PLAYER.nextItem.Name)) || '')));
      const b = h('div', 'btns');
      b.appendChild(onOk(h('div', 'btn pri', ic('play') + 'Weiter gucken'), () => close(true)));
      b.appendChild(onOk(h('div', 'btn', 'Aufhören'), () => close(false)));
      box.appendChild(b);
      t = setTimeout(() => close(false), 90000);
    });
    p.then(v => { clearTimeout(t); if (v) PLAYER.playNext(); else PLAYER.stop('user'); });
  },
  async playNext() {
    const n = PLAYER.nextItem; if (!n) return;
    clearInterval(PLAYER.upNextT);
    await PLAYER.stop('switch'); PLAYER.info = null;
    PLAYER.open(n, { startMs: 0 });
  },

  // ---------- Marken (Intro / Abspann) ----------
  checkMarkers() {
    const it = PLAYER.item; if (!it || !PLAYER.ready) return;
    const ch = it.Chapters || []; const p = PLAYER.pos * 10000;
    const iS = ch.find(c => c.MarkerType === 'IntroStart'), iE = ch.find(c => c.MarkerType === 'IntroEnd');
    const inIntro = iS && iE && p >= iS.StartPositionTicks && p < iE.StartPositionTicks - 20000000;
    const sk = $('#osd .skipper');
    if (inIntro) {
      if (EMBY.settings().autoSkip && !PLAYER._skipped) { PLAYER._skipped = true; PLAYER.seekTo(iE.StartPositionTicks / 10000, true); UI.toast('Intro übersprungen', { icon: '⏭' }); }
      else if (!sk) { const b = onOk(h('div', 'btn pri skipper', ic('next') + 'Intro überspringen'), () => { PLAYER.seekTo(iE.StartPositionTicks / 10000, true); b.remove(); NAV.focus(null); }); $('#osd').appendChild(b); if (!PLAYER.osdOn) NAV.focus(b); }
    } else if (sk) { sk.remove(); if (NAV.cur === sk) NAV.cur = null; }
    // „Als Nächstes"
    if (PLAYER.nextItem && !PLAYER.upNextShown && !PLAYER.party && PLAYER.dur > 60000) {
      const cS = ch.find(c => c.MarkerType === 'CreditsStart');
      const at = cS ? cS.StartPositionTicks / 10000 : PLAYER.dur - 25000;
      if (PLAYER.pos >= at && PLAYER.pos < PLAYER.dur - 1500) PLAYER.showUpNext();
    }
  },
  showUpNext() {
    PLAYER.upNextShown = true; const n = PLAYER.nextItem; const auto = EMBY.settings().autoNext;
    const box = h('div', 'upnext' + (V.spoil(n) ? ' spoil' : ''));
    const im = h('div', 'im'); im.style.backgroundImage = 'url("' + EMBY.land(n, 400) + '")'; const bar = h('i'); im.appendChild(bar); box.appendChild(im);
    const t = h('div', '', '<small>Als Nächstes</small><b>S' + (n.ParentIndexNumber || '?') + ' · F' + (n.IndexNumber || '?') + (V.spoil(n) ? '' : ' ' + esc(n.Name)) + '</b>');
    const b = h('div', 'btns'); const go = onOk(h('div', 'btn pri', ic('play') + 'Jetzt'), () => PLAYER.playNext());
    const no = onOk(h('div', 'btn', 'Abspann'), () => { clearInterval(PLAYER.upNextT); box.remove(); NAV.cur = null; });
    b.appendChild(go); b.appendChild(no); t.appendChild(b); box.appendChild(t);
    $('#osd').appendChild(box); NAV.focus(go);
    if (auto) { let left = 15; bar.style.width = '0%'; PLAYER.upNextT = setInterval(() => { left -= .25; bar.style.width = ((15 - left) / 15 * 100) + '%'; if (left <= 0) { clearInterval(PLAYER.upNextT); PLAYER.autoNext(); } }, 250); }
  },

  // ---------- Bedienoberfläche ----------
  buildOsd() {
    const o = $('#osd'); o.className = ''; o.innerHTML =
      '<div class="scrim"></div><div class="head"><div><div class="t1"></div><div class="t2"></div></div><div class="clock"></div></div>' +
      '<div class="big-state"></div><div class="buffer hidden"><div class="spin"></div></div>' +
      '<div class="foot"><div class="seek f"><div class="b"></div><div class="p"></div><div class="k"></div><div class="thumb"><div class="ti"></div><div class="tip"></div></div></div><div class="times"><span class="tp">0:00</span><span class="tr"></span></div><div class="ctrl"></div></div>';
    const seek = $('.seek', o);
    seek._focus = () => {}; seek._ok = () => PLAYER.toggle();
    PLAYER.seekEl = seek;
  },
  renderHead() {
    const it = PLAYER.item; const o = $('#osd'); if (!it || !o) return;
    $('.t1', o).textContent = it.Type === 'Episode' ? (it.SeriesName || '') : (it.ProductionYear ? 'Film · ' + it.ProductionYear : 'Film');
    $('.t2', o).textContent = it.Type === 'Episode' ? 'S' + (it.ParentIndexNumber || '?') + ' · F' + (it.IndexNumber || '?') + '  ' + it.Name : it.Name;
    PLAYER.renderTime();
  },
  renderControls() {
    const c = $('#osd .ctrl'); if (!c) return;
    const keep = NAV.cur && c.contains(NAV.cur) ? NAV.cur.dataset.k : null;
    c.innerHTML = '';
    const add = (k, html, fn, cls) => { const b = onOk(h('div', 'btn' + (cls ? ' ' + cls : '') + (html.indexOf('<span') < 0 && html.indexOf('svg') >= 0 && html.indexOf('</svg>') === html.length - 6 ? ' icon' : ''), html), fn); b.dataset.k = k; c.appendChild(b); return b; };
    add('pp', ic(PLAYER.playing ? 'pause' : 'play'), () => PLAYER.toggle(), 'pri');
    add('back', ic('back10'), () => PLAYER.seekTo(PLAYER.pos - 10000, true));
    add('fwd', ic('fwd30'), () => PLAYER.seekTo(PLAYER.pos + 30000, true));
    const streams = PLAYER.info ? (PLAYER.info.src.MediaStreams || []) : [];
    const auds = streams.filter(x => x.Type === 'Audio'), subs = streams.filter(x => x.Type === 'Subtitle');
    const a = auds.find(x => x.Index === PLAYER.audioIndex), s = subs.find(x => x.Index === PLAYER.subIndex);
    add('audio', ic('audio') + '<span>' + esc(a ? langName(a.Language) || 'Ton' : 'Ton') + '</span>', () => PLAYER.chooseAudio());
    add('sub', ic('sub') + '<span>' + esc(s ? langName(s.Language) || 'Untertitel' : 'Aus') + '</span>', () => PLAYER.chooseSub());
    if (PLAYER.nextItem && !PLAYER.party) add('next', ic('next') + '<span>Nächste Folge</span>', () => PLAYER.playNext());
    c.appendChild(h('div', 'spacer'));
    const sl = PLAYER.sleep; add('sleep', ic('moon') + (sl ? '<span>' + (sl.end ? 'Ende' : Math.max(1, Math.ceil((sl.at - Date.now()) / 60000)) + ' Min.') + '</span>' : ''), () => PLAYER.chooseSleep(), sl ? 'on' : '');
    if (!PLAYER.party && !PLAYER.isTrailer) add('party', ic('party') + '<span>Watch-Party</span>', () => PARTY.startFromPlayer());
    else add('react', '<span>😂 Reagieren</span>', () => PARTY.reactBar());
    add('info', ic('info'), () => PLAYER.showInfo());
    if (keep) { const k = $('[data-k="' + keep + '"]', c); if (k && PLAYER.osdOn) NAV.focus(k); }
  },
  renderTime() {
    const o = $('#osd'); if (!o || !PLAYER.seekEl) return;
    const pos = PLAYER.scrub != null ? PLAYER.scrub : PLAYER.pos, dur = PLAYER.dur || 1;
    const pct = Math.max(0, Math.min(100, pos / dur * 100));
    $('.p', PLAYER.seekEl).style.width = (PLAYER.pos / dur * 100) + '%';
    $('.b', PLAYER.seekEl).style.width = Math.min(100, PLAYER.buf / dur * 100) + '%';
    $('.k', PLAYER.seekEl).style.left = pct + '%';
    const th = $('.thumb', PLAYER.seekEl); $('.tip', th).textContent = fmtTime(pos);
    const fu = PLAYER.scrub != null ? TRICK.frame(pos) : null; th.classList.toggle('has', !!fu); th.style.left = (fu ? Math.max(11, Math.min(89, pct)) : pct) + '%';
    if (fu) { const ti = $('.ti', th); const f = typeof fu === 'string' ? { url: fu, pos: 'center', size: 'cover' } : fu; const bg = 'url("' + f.url + '")'; if (ti._bg !== bg) { ti.style.backgroundImage = bg; ti._bg = bg; } ti.style.backgroundPosition = f.pos; ti.style.backgroundSize = f.size; }
    PLAYER.seekEl.classList.toggle('scrub', PLAYER.scrub != null);
    $('.tp', o).textContent = fmtTime(PLAYER.pos);
    $('.tr', o).textContent = PLAYER.dur ? '−' + fmtTime(PLAYER.dur - PLAYER.pos) : '';
    const end = new Date(Date.now() + Math.max(0, PLAYER.dur - PLAYER.pos));
    $('.clock', o).innerHTML = clock() + (PLAYER.dur ? '<small>Ende um ' + clock(end) + '</small>' : '');
  },
  loading(on) { const b = $('#osd .buffer'); if (b) b.classList.toggle('hidden', !on); },
  flash(what) { const b = $('#osd .big-state'); if (!b) return; b.innerHTML = ic(what); b.classList.add('on'); clearTimeout(PLAYER._fl); PLAYER._fl = setTimeout(() => b.classList.remove('on'), 700); },
  showOsd(focusSeek) {
    const o = $('#osd'); if (!o) return;
    o.classList.add('show'); PLAYER.osdOn = true;
    if (focusSeek || !NAV.cur || !o.contains(NAV.cur)) NAV.focus(PLAYER.seekEl);
    PLAYER.hideOsdSoon();
  },
  hideOsdSoon(ms) { clearTimeout(PLAYER.osdTimer); PLAYER.osdTimer = setTimeout(() => { if (PLAYER.playing && PLAYER.scrub == null && !$('#layer .modal') && !$('.plobby') && !$('.reactbar')) PLAYER.hideOsd(); }, ms || 4500); },
  hideOsd() { const o = $('#osd'); if (!o) return; o.classList.remove('show'); PLAYER.osdOn = false; const keepUi = $('#osd .upnext .f, #osd .skipper'); if (keepUi) NAV.focus(keepUi); else { if (NAV.cur) NAV.cur.classList.remove('focused'); NAV.cur = null; } },
  navRoot() { return $('#osd'); },
  chooseAudio() {
    const auds = (PLAYER.info.src.MediaStreams || []).filter(x => x.Type === 'Audio');
    UI.choose('Tonspur', auds.map(a => ({ label: a.DisplayTitle || langName(a.Language) || ('Spur ' + a.Index), sub: (a.Codec || '').toUpperCase(), value: a.Index, sel: a.Index === PLAYER.audioIndex }))).then(async (v) => {
      if (v == null || v === PLAYER.audioIndex) return;
      PLAYER.audioIndex = v;
      if (PLAYER.info.method === 'Transcode') { PLAYER.loading(true); await PLAYER.load(PLAYER.pos); } else PLAYER.applyAudioTrack();
      PLAYER.renderControls();
    });
  },
  chooseSub() {
    const subs = (PLAYER.info.src.MediaStreams || []).filter(x => x.Type === 'Subtitle');
    const opts = [{ label: 'Aus', value: -1, sel: PLAYER.subIndex == null || PLAYER.subIndex < 0 }].concat(subs.map(s => ({ label: s.DisplayTitle || langName(s.Language) || ('Spur ' + s.Index), sub: (s.IsExternal ? 'extern · ' : '') + (s.Codec || '').toUpperCase(), value: s.Index, sel: s.Index === PLAYER.subIndex })));
    UI.choose('Untertitel', opts).then(async (v) => {
      if (v == null || v === PLAYER.subIndex) return;
      PLAYER.subIndex = v; PLAYER.loading(true);
      const wasPlaying = PLAYER.playing;
      await PLAYER.load(PLAYER.pos, { paused: !wasPlaying });
    });
  },
  chooseSleep() {
    const sl = PLAYER.sleep;
    UI.choose('Einschlaf-Timer', [{ label: 'Aus', value: 0, sel: !sl }, { label: '15 Minuten', value: 15 }, { label: '30 Minuten', value: 30 }, { label: '45 Minuten', value: 45 }, { label: '60 Minuten', value: 60 }, { label: '90 Minuten', value: 90 },
      { label: PLAYER.item && PLAYER.item.Type === 'Episode' ? 'Am Ende dieser Folge' : 'Am Ende des Films', value: -1, sel: !!(sl && sl.end) }], 'Danach wird es leiser und die Wiedergabe stoppt.').then(v => {
      if (v == null) return;
      PLAYER.sleep = v === 0 ? null : v < 0 ? { end: true } : { at: Date.now() + v * 60000 };
      if (v) UI.toast(v < 0 ? 'Stoppt am Ende' : 'Stoppt in ' + v + ' Minuten', { icon: '💤' });
      PLAYER.renderControls();
    });
  },
  checkSleep() {
    const sl = PLAYER.sleep; if (!sl || !sl.at) return;
    const left = sl.at - Date.now();
    if (left <= 0) { PLAYER.sleep = null; ADAPTER.volume(1); UI.toast('Gute Nacht! 🌙', { icon: '💤' }); PLAYER.stop('sleep'); return; }
    if (left < 10000) ADAPTER.volume(Math.max(0.05, left / 10000));
    if (PLAYER.osdOn && Math.floor(left / 60000) !== PLAYER._sleepMin) { PLAYER._sleepMin = Math.floor(left / 60000); PLAYER.renderControls(); }
  },
  showInfo() {
    const i = PLAYER.info; if (!i) return; const st = i.src.MediaStreams || [];
    const v = st.find(x => x.Type === 'Video') || {}; const a = st.find(x => x.Index === PLAYER.audioIndex) || {};
    const m = { DirectPlay: 'Direkt (Originaldatei)', DirectStream: 'Direkt-Stream', Transcode: 'Umwandlung durch den Server' }[i.method];
    UI.alert(PLAYER.item.Name, 'Wiedergabe: ' + m + '\nVideo: ' + (v.DisplayTitle || v.Codec || '–') + '\nTon: ' + (a.DisplayTitle || a.Codec || '–') + '\nContainer: ' + (i.src.Container || '–') + (i.src.Bitrate ? '\nBitrate: ' + Math.round(i.src.Bitrate / 100000) / 10 + ' Mbit/s' : ''));
  },

  // ---------- Tasten im Player ----------
  key(k, repeat) {
    if (PLAYER.party && PARTY.key(k)) return;
    const o = $('#osd'); const inOsd = PLAYER.osdOn;
    const focusInOverlay = NAV.cur && o.contains(NAV.cur) && !NAV.cur.closest('.foot');
    if (k === 'back') {
      if (PLAYER.scrub != null) { PLAYER.scrub = null; PLAYER.renderTime(); return; }
      if (focusInOverlay && NAV.cur.closest('.upnext')) { clearInterval(PLAYER.upNextT); NAV.cur.closest('.upnext').remove(); NAV.cur = null; return; }
      if (inOsd && PLAYER.playing) { PLAYER.hideOsd(); return; }
      PLAYER.stop('user'); return;
    }
    if (k === 'playpause' || k === 'play' || k === 'pause') { if (k === 'play') PLAYER.play(true); else if (k === 'pause') PLAYER.pause(true); else PLAYER.toggle(); return; }
    if (k === 'stop') { PLAYER.stop('user'); return; }
    if (k === 'ff' || k === 'rew') { PLAYER.showOsd(true); PLAYER.scrubBy(k === 'ff' ? 1 : -1, repeat, true); return; }
    if (k === 'next' && PLAYER.nextItem) { PLAYER.playNext(); return; }
    if (!inOsd && !focusInOverlay) {
      if (k === 'left' || k === 'right') { PLAYER.showOsd(true); PLAYER.scrubBy(k === 'right' ? 1 : -1, repeat); return; }
      if (k === 'ok') { PLAYER.toggle(); return; }
      if (k === 'menu' && PLAYER.party) { PARTY.reactBar(); return; }
      PLAYER.showOsd(true); return;
    }
    PLAYER.hideOsdSoon(6000);
    if (NAV.cur === PLAYER.seekEl && (k === 'left' || k === 'right')) { PLAYER.scrubBy(k === 'right' ? 1 : -1, repeat); return; }
    if (k === 'ok') { if (PLAYER.scrub != null) { PLAYER.commitScrub(); return; } if (NAV.cur && NAV.cur._ok) NAV.cur._ok(); return; }
    if (k === 'up' && NAV.cur === PLAYER.seekEl) { const sk = $('#osd .skipper'); if (sk) NAV.focus(sk); return; }
    if (['up', 'down', 'left', 'right'].includes(k)) NAV.move(k);
  },
  scrubBy(dir, repeat, big) {
    const step = big ? 30000 : (repeat > 24 ? 120000 : repeat > 10 ? 60000 : repeat > 3 ? 30000 : 10000);
    PLAYER.scrub = Math.max(0, Math.min(PLAYER.dur - 1000, (PLAYER.scrub != null ? PLAYER.scrub : PLAYER.pos) + dir * step));
    PLAYER.renderTime();
    clearTimeout(PLAYER.scrubT); PLAYER.scrubT = setTimeout(() => PLAYER.commitScrub(), TRICK.ts ? 1600 : 900);
  },
  commitScrub() { clearTimeout(PLAYER.scrubT); if (PLAYER.scrub == null) return; const t = PLAYER.scrub; PLAYER.scrub = null; PLAYER.seekTo(t, true); PLAYER.hideOsdSoon(); },
};

const LANGS = { ger: 'Deutsch', deu: 'Deutsch', de: 'Deutsch', eng: 'Englisch', en: 'Englisch', fre: 'Französisch', fra: 'Französisch', fr: 'Französisch', spa: 'Spanisch', es: 'Spanisch', ita: 'Italienisch', it: 'Italienisch',
  tur: 'Türkisch', tr: 'Türkisch', pol: 'Polnisch', rus: 'Russisch', jpn: 'Japanisch', ja: 'Japanisch', kor: 'Koreanisch', dut: 'Niederländisch', nld: 'Niederländisch', dan: 'Dänisch', swe: 'Schwedisch', nor: 'Norwegisch', und: 'Unbekannt' };
function langName(l) { return l ? (LANGS[String(l).toLowerCase()] || String(l).toUpperCase()) : ''; }
window.PLAYER = PLAYER; window.ADAPTER = ADAPTER;
