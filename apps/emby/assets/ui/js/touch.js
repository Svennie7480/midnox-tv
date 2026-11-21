// midnox Media – Touch-Modus für Handy und Tablet: gleiche Oberfläche, aber Antippen, Wischen, natives Scrollen und Tab-Leiste.
'use strict';
const TOUCH = (() => {
  let t = false;
  try { t = HAS_ANDROID ? (Android.isTv ? !Android.isTv() : false) : (/[?&]touch=1/.test(location.search) || (matchMedia('(pointer: coarse)').matches && !/[?&]tv=1/.test(location.search))); } catch (e) {}
  return t;
})();
window.TOUCH = TOUCH;

if (TOUCH) {
  document.body.classList.add('touch');
  const sizeCls = () => {
    const w = innerWidth, hh = innerHeight, small = Math.min(w, hh);
    document.body.classList.toggle('phone', small < 600);
    document.body.classList.toggle('tablet', small >= 600);
    document.body.classList.toggle('portrait', hh > w);
    document.body.classList.toggle('landscape', w >= hh);
    // Grundgröße: Handy hoch ~13 px, Handy quer ~11 px, Tablet bis 18 px
    document.documentElement.style.fontSize = Math.max(11, Math.min(18, Math.min(w / 30, hh / 34))) + 'px';
  };
  sizeCls(); addEventListener('resize', sizeCls);

  // ---------- Fokus-Steuerung entschärfen: kein Verschieben per Transform, kein Fokusrahmen ----------
  NAV.focus = function (el) {
    if (!el) return;
    if (NAV.cur && NAV.cur !== el && NAV.cur._blur) NAV.cur._blur();
    NAV.cur = el;
    let p = el.parentElement; while (p) { if (p.hasAttribute && p.hasAttribute('data-mem')) p._last = el; p = p.parentElement; }
  };
  NAV.scrollTo = function () {};
  NAV.vscroll = function () {};

  // ---------- Antippen & langes Drücken ----------
  let lp = 0, lpFired = false, sx = 0, sy = 0, moved = false;
  const target = (e) => { const el = e.target.closest && e.target.closest('.f, .ri'); return el && !el.classList.contains('dis') ? el : null; };
  document.addEventListener('touchstart', (e) => {
    const el = target(e); moved = false; lpFired = false; const tt = e.touches[0]; sx = tt.clientX; sy = tt.clientY;
    clearTimeout(lp);
    if (el && el._menu) lp = setTimeout(() => { lpFired = true; if (navigator.vibrate) navigator.vibrate(15); NAV.cur = el; el._menu(); }, 520);
    if (el) el.classList.add('pressed');
  }, { passive: true });
  document.addEventListener('touchmove', (e) => { const tt = e.touches[0]; if (Math.abs(tt.clientX - sx) > 10 || Math.abs(tt.clientY - sy) > 10) { moved = true; clearTimeout(lp); $$('.pressed').forEach(x => x.classList.remove('pressed')); } }, { passive: true });
  document.addEventListener('touchend', () => { clearTimeout(lp); setTimeout(() => $$('.pressed').forEach(x => x.classList.remove('pressed')), 120); }, { passive: true });
  document.addEventListener('click', (e) => {
    if (lpFired) { e.preventDefault(); lpFired = false; return; }
    if (e.target.closest('input, textarea, select, a[href]')) return;
    const el = target(e); if (!el) return;
    e.preventDefault(); e.stopPropagation();
    if (window.SAVER) SAVER.poke();
    NAV.focus(el);
    if (el._focus && !el._ok) { el._focus(); return; }
    if (el._ok) el._ok(); else if (el.dataset.id && el.classList.contains('ri')) ROUTER.go(el.dataset.id, null, { root: true });
  }, true);
  // Kontextmenü (z. B. lange gedrückt im Browser) als MENU-Taste
  document.addEventListener('contextmenu', (e) => { const el = target(e); if (el && el._menu) { e.preventDefault(); NAV.cur = el; el._menu(); } });

  // ---------- Tab-Leiste unten (statt Seitenleiste) ----------
  const TABS = {
    build() {
      let bar = $('#tabbar'); if (!bar) { bar = h('nav', ''); bar.id = 'tabbar'; document.body.appendChild(bar); }
      bar.innerHTML = '';
      const items = KIDS.on ? [['kidshome', 'Start', 'home'], ['movies', 'Filme', 'film'], ['shows', 'Serien', 'tv'], ['music', 'Musik', 'music'], ['search', 'Suche', 'search']]
        : (window.LIVE && LIVE.enabled() ? [['home', 'Start', 'home'], ['movies', 'Filme', 'film'], ['shows', 'Serien', 'tv'], ['live', 'Live-TV', 'live'], ['more', 'Mehr', 'menu']] : [['home', 'Start', 'home'], ['movies', 'Filme', 'film'], ['shows', 'Serien', 'tv'], ['search', 'Suche', 'search'], ['more', 'Mehr', 'menu']]);
      for (const [id, label, icon] of items) {
        const b = h('div', 'tab', ic(icon) + '<span>' + label + '</span>'); b.dataset.id = id;
        b.addEventListener('click', (e) => { e.stopPropagation(); if (id === 'more') TABS.more(); else ROUTER.go(id, null, { root: true }); });
        bar.appendChild(b);
      }
      TABS.mark();
    },
    async more() {
      const v = await UI.choose('Mehr', [
        { label: '🔎 Suche', value: 'search' }, { label: '🎵 Musik', value: 'music' }, { label: '💬 Community', value: 'community' }, { label: '💜 Was gucken wir heute?', value: 'match' },
        { label: '🏆 Mein midnox', value: 'me' }, { label: '📺 Auf dem Fernseher abspielen', value: 'cast' }, { label: '⚙️ Einstellungen', value: 'settings' }, { label: '👥 Profil wechseln', value: 'who' },
      ]);
      if (!v) return;
      if (v === 'who') PROFILES.leave(); else if (v === 'cast') CAST.pickTv(); else ROUTER.go(v, null, { root: true });
    },
    mark() {
      const top = ROUTER.top(); const name = top ? (ROUTER.stack[0] || top).name : '';
      $$('#tabbar .tab').forEach(t => t.classList.toggle('cur', t.dataset.id === name || (t.dataset.id === 'more' && ['music', 'community', 'match', 'me', 'settings', 'search'].indexOf(name) >= 0)));
      const hide = !EMBY.user || !top || top.noRail || (window.PLAYER && PLAYER.active) || (window.TVP && TVP.active) || ['connect', 'login', 'who', 'wrapped'].indexOf(top.name) >= 0;
      document.body.classList.toggle('notabs', !!hide);
    },
  };
  window.TABS = TABS;
  const origBuild = RAIL.build.bind(RAIL); RAIL.build = function () { origBuild(); TABS.build(); };
  const origMark = RAIL.mark.bind(RAIL); RAIL.mark = function (n) { origMark(n); TABS.mark(); };
  // Rückmeldung, ob Tabs gezeigt werden, auch nach dem Player
  setInterval(() => { if ($('#tabbar')) TABS.mark(); }, 800);

  // ---------- Texteingabe: echte Handytastatur ----------
  TEXT.ask = function (title, opts) {
    opts = opts || {};
    return UI.modal((box, close) => {
      box.classList.add('tinbox');
      box.appendChild(h('h2', '', esc(title))); if (opts.sub) box.appendChild(h('p', '', esc(opts.sub)));
      const inp = document.createElement('input'); inp.className = 'tin'; inp.type = opts.password ? 'password' : 'text';
      inp.value = opts.value || ''; inp.placeholder = opts.placeholder || ''; inp.autocomplete = 'off'; inp.setAttribute('autocapitalize', 'off'); inp.spellcheck = false;
      inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); close(inp.value.trim() || (opts.allowEmpty ? '' : null)); } });
      box.appendChild(inp);
      const b = h('div', 'btns');
      b.appendChild(onOk(h('div', 'btn pri f', esc(opts.okLabel || 'Fertig')), () => close(inp.value.trim() || (opts.allowEmpty ? '' : null))));
      b.appendChild(onOk(h('div', 'btn f', 'Abbrechen'), () => close(null)));
      box.appendChild(b);
      setTimeout(() => { inp.focus(); if (HAS_ANDROID && Android.showKeyboard) Android.showKeyboard(true); }, 120);
    }, { onClose: () => { if (HAS_ANDROID && Android.showKeyboard) Android.showKeyboard(false); } });
  };
}

// ---------- Touch-Player (erst wenn alle Skripte geladen sind) ----------
if (TOUCH) document.addEventListener('DOMContentLoaded', () => {
  const A = (fn, ...a) => { try { return HAS_ANDROID && Android[fn] ? Android[fn](...a) : undefined; } catch (e) { return undefined; } };
  const oOpen = PLAYER.open.bind(PLAYER);
  PLAYER.open = function (item, opts) { A('orientation', 'landscape'); A('immersive', true); return oOpen(item, opts); };
  const oClose = PLAYER.close.bind(PLAYER);
  PLAYER.close = function () { A('orientation', 'auto'); A('immersive', false); document.body.classList.remove('pip'); return oClose(); };
  PLAYER.pip = (on) => { document.body.classList.toggle('pip', !!on); if (on) PLAYER.hideOsd(); };

  const oBuild = PLAYER.buildOsd.bind(PLAYER);
  PLAYER.buildOsd = function () {
    oBuild();
    const o = $('#osd');
    // Kopf: Schließen + Bild-im-Bild
    const head = $('.head', o);
    const close = h('div', 'tbtn tclose', ic('back')); close.addEventListener('click', (e) => { e.stopPropagation(); PLAYER.stop('user'); });
    head.insertBefore(close, head.firstChild);
    if (HAS_ANDROID) { const pip = h('div', 'tbtn tpip', '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2.5" y="4.5" width="19" height="15" rx="2.5"/><rect x="12" y="11" width="7" height="6" rx="1" fill="currentColor"/></svg>'); pip.addEventListener('click', (e) => { e.stopPropagation(); A('pip'); }); head.appendChild(pip); }
    // Fläche für Gesten
    const layer = h('div', 'tlayer'); o.insertBefore(layer, o.firstChild);
    const ind = h('div', 'tind'); o.appendChild(ind);
    const rip = h('div', 'trip'); o.appendChild(rip);
    let sx = 0, sy = 0, mode = null, base = 0, taps = 0, tapT = 0, lastX = 0;
    layer.addEventListener('touchstart', (e) => { const t = e.touches[0]; sx = t.clientX; sy = t.clientY; mode = null; }, { passive: true });
    layer.addEventListener('touchmove', (e) => {
      const t = e.touches[0], dx = t.clientX - sx, dy = t.clientY - sy;
      if (!mode && Math.abs(dy) > 14 && Math.abs(dy) > Math.abs(dx) * 1.4 && HAS_ANDROID) { mode = sx < innerWidth / 2 ? 'bright' : 'vol'; base = mode === 'bright' ? (A('brightness', -2) || .5) : (A('volume', -1) || .5); }
      if (!mode) return;
      const v = Math.max(0, Math.min(1, base - dy / (innerHeight * .7)));
      if (mode === 'bright') A('brightness', Math.max(.02, v)); else A('volume', v);
      ind.innerHTML = '<span>' + (mode === 'bright' ? '☀️' : (v < .01 ? '🔇' : '🔊')) + '</span><b><i style="height:' + Math.round(v * 100) + '%"></i></b>';
      ind.className = 'tind on ' + (mode === 'bright' ? 'l' : 'r');
    }, { passive: true });
    layer.addEventListener('touchend', (e) => {
      if (mode) { mode = null; setTimeout(() => ind.classList.remove('on'), 600); return; }
      const t = e.changedTouches[0]; if (Math.abs(t.clientX - sx) > 12 || Math.abs(t.clientY - sy) > 12) return;
      lastX = t.clientX; taps++; clearTimeout(tapT);
      if (taps >= 2) {
        const w = innerWidth;
        if (lastX < w * .38) { PLAYER.seekTo(PLAYER.pos - 10000, true); rip.textContent = '« 10 s'; rip.className = 'trip on l'; }
        else if (lastX > w * .62) { PLAYER.seekTo(PLAYER.pos + 10000, true); rip.textContent = '10 s »'; rip.className = 'trip on r'; }
        else PLAYER.toggle();
        clearTimeout(rip._t); rip._t = setTimeout(() => rip.classList.remove('on'), 550);
        tapT = setTimeout(() => { taps = 0; }, 300); return;
      }
      tapT = setTimeout(() => { if (taps === 1) { if (PLAYER.osdOn) PLAYER.hideOsd(); else PLAYER.showOsd(); } taps = 0; }, 260);
    }, { passive: true });
    // Zeitleiste ziehen
    const seek = PLAYER.seekEl; seek._ok = () => {};
    const at = (x) => { const r = seek.getBoundingClientRect(); return Math.max(0, Math.min(1, (x - r.left) / r.width)) * (PLAYER.dur || 0); };
    seek.addEventListener('touchstart', (e) => { e.stopPropagation(); clearTimeout(PLAYER.scrubT); clearTimeout(PLAYER.osdTimer); PLAYER.scrub = at(e.touches[0].clientX); PLAYER.renderTime(); }, { passive: true });
    seek.addEventListener('touchmove', (e) => { e.stopPropagation(); PLAYER.scrub = at(e.touches[0].clientX); PLAYER.renderTime(); }, { passive: true });
    seek.addEventListener('touchend', (e) => { e.stopPropagation(); PLAYER.commitScrub(); }, { passive: true });
    // Browser-Test: Klick auf die Leiste springt dorthin
    seek.addEventListener('mousedown', (e) => { PLAYER.scrub = at(e.clientX); PLAYER.commitScrub(); });
  };

  // ---------- Musik im Hintergrund (Benachrichtigung) ----------
  let bgKey = '';
  setInterval(() => {
    if (!HAS_ANDROID || !window.MUSIC) return;
    const it = MUSIC.active && MUSIC.cur ? MUSIC.cur() : null;
    const k = it ? it.Id + ':' + !!MUSIC.playing : '';
    if (k === bgKey) return; bgKey = k;
    if (it) A('bgAudio', it.Name || 'midnox Media', (it.Artists || [])[0] || it.Album || '', !!MUSIC.playing, true);
    else A('bgAudio', '', '', false, false);
  }, 1500);
});

// ---------- „Auf dem Fernseher abspielen" + Handy als Fernbedienung ----------
const CAST = {
  async tvs() {
    const r = await EMBY.get('/Sessions', { ControllableByUserId: EMBY.user.id });
    return (r || []).filter(s => s.DeviceId !== DEVICE.deviceId && (s.SupportsRemoteControl || (s.Capabilities || {}).SupportsMediaControl) && (s.PlayableMediaTypes || []).indexOf('Video') >= 0)
      .sort((a, b) => (b.Client === 'midnox Media') - (a.Client === 'midnox Media') || new Date(b.LastActivityDate) - new Date(a.LastActivityDate));
  },
  async pick() {
    let list; try { list = await CAST.tvs(); } catch (e) { UI.alert('Fernseher', e.message); return null; }
    if (!list.length) { await UI.alert('Kein Fernseher gefunden', 'Öffne midnox Media auf dem Fernseher und melde dich mit demselben Konto an. Dann taucht er hier auf.'); return null; }
    if (list.length === 1) return list[0];
    const id = await UI.choose('Auf welchem Gerät?', list.map(s => ({ label: '📺 ' + (s.DeviceName || 'Gerät') + ' · ' + (s.Client || '') + (s.NowPlayingItem ? ' – läuft: ' + s.NowPlayingItem.Name : ''), value: s.Id })));
    return id ? list.find(s => s.Id === id) : null;
  },
  async play(item, ticks) {
    const s = await CAST.pick(); if (!s) return;
    try {
      await EMBY.post('/Sessions/' + s.Id + '/Playing', null, { ItemIds: item.Id, PlayCommand: 'PlayNow', StartPositionTicks: Math.round(ticks || 0) });
      UI.toast('Läuft auf ' + (s.DeviceName || 'dem Fernseher'), { icon: '📺' });
      ROUTER.go('remote', { id: s.Id, name: s.DeviceName });
    } catch (e) { UI.alert('Hat nicht geklappt', e.message); }
  },
  async pickTv() { const s = await CAST.pick(); if (s) ROUTER.go('remote', { id: s.Id, name: s.DeviceName }); },
  cmd(id, c, q) { return EMBY.post('/Sessions/' + id + '/Playing/' + c, null, q || {}).catch(e => UI.toast(e.message)); },
};
window.CAST = CAST;

ROUTER.reg('remote', async (scr, arg) => {
  const el = scr.el; el.className = 'page remote'; BG.dim(false);
  el.innerHTML = '<div class="rmhead"><small>Fernbedienung</small><b>📺 ' + esc(arg.name || 'Fernseher') + '</b></div><div class="rmbody"></div>';
  const body = $('.rmbody', el); let t = 0, last = null, scrub = null;
  const fmt = (tk) => fmtTime((tk || 0) / 10000);
  const render = (s) => {
    const it = s && s.NowPlayingItem, ps = (s && s.PlayState) || {};
    if (!it) { body.innerHTML = '<div class="empty">Gerade läuft nichts auf diesem Gerät.<br><br>Wähle einen Film oder eine Folge und tippe „Auf dem TV“.</div>'; BG.set(null); last = null; return; }
    if (!last || last !== it.Id) {
      last = it.Id; BG.set(EMBY.backdrop(it) || EMBY.img(it, 'Primary', 900));
      body.innerHTML = '<div class="rmposter" style="background-image:url(\'' + EMBY.img(it, 'Primary', 500) + '\')"></div>'
        + '<div class="rmtitle"><small>' + (it.Type === 'Episode' ? esc(it.SeriesName || '') + ' · S' + (it.ParentIndexNumber || '?') + ' F' + (it.IndexNumber || '?') : (it.ProductionYear || '')) + '</small><b>' + esc(it.Name) + '</b></div>'
        + '<div class="rmseek"><div class="bar"><i></i></div><div class="tm"><span class="a"></span><span class="b"></span></div></div><div class="rmctl"></div>';
      const ctl = $('.rmctl', body);
      const btn = (html, fn, cls) => { const b = onOk(h('div', 'btn icon ' + (cls || ''), html), fn); ctl.appendChild(b); return b; };
      btn(ic('back10'), () => CAST.cmd(arg.id, 'Seek', { SeekPositionTicks: Math.max(0, (cur().PositionTicks || 0) - 1e8) }));
      btn(ic('pause'), () => CAST.cmd(arg.id, 'PlayPause').then(poll), 'pri pp');
      btn(ic('fwd30'), () => CAST.cmd(arg.id, 'Seek', { SeekPositionTicks: (cur().PositionTicks || 0) + 3e8 }));
      btn(ic('stop'), () => CAST.cmd(arg.id, 'Stop').then(poll));
      const bar = $('.rmseek .bar', body);
      const at = (x) => { const r = bar.getBoundingClientRect(); return Math.max(0, Math.min(1, (x - r.left) / r.width)); };
      bar.addEventListener('touchstart', (e) => { scrub = at(e.touches[0].clientX); paint(); }, { passive: true });
      bar.addEventListener('touchmove', (e) => { scrub = at(e.touches[0].clientX); paint(); }, { passive: true });
      bar.addEventListener('touchend', () => { if (scrub != null) { CAST.cmd(arg.id, 'Seek', { SeekPositionTicks: Math.round(scrub * (it.RunTimeTicks || 0)) }); scrub = null; } });
      bar.addEventListener('mousedown', (e) => { CAST.cmd(arg.id, 'Seek', { SeekPositionTicks: Math.round(at(e.clientX) * (it.RunTimeTicks || 0)) }); });
    }
    scr._s = s; paint();
  };
  const cur = () => ((scr._s || {}).PlayState || {});
  const paint = () => {
    const s = scr._s; if (!s || !s.NowPlayingItem) return; const it = s.NowPlayingItem, ps = s.PlayState || {}, dur = it.RunTimeTicks || 1;
    const pos = scrub != null ? scrub * dur : (ps.PositionTicks || 0);
    const i = $('.rmseek .bar i', body); if (i) i.style.width = Math.min(100, pos / dur * 100) + '%';
    const a = $('.rmseek .a', body), b = $('.rmseek .b', body); if (a) { a.textContent = fmt(pos); b.textContent = '−' + fmt(dur - pos); }
    const pp = $('.rmctl .pp', body); if (pp) pp.innerHTML = ic(ps.IsPaused ? 'play' : 'pause');
  };
  const poll = async () => {
    clearTimeout(t);
    try { const list = await EMBY.get('/Sessions', { ControllableByUserId: EMBY.user.id }); render((list || []).find(x => x.Id === arg.id)); } catch (e) {}
    if (ROUTER.top() === scr) t = setTimeout(poll, 2000);
  };
  scr.leave = scr.destroy = () => clearTimeout(t);
  scr.enter = () => poll();
  await poll();
});
