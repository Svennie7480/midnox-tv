// midnox Emby – Bildschirmschoner: langsam überblendende Hintergründe aus der eigenen Bibliothek, Uhr, „Läuft gerade".
'use strict';
const SAVER = {
  on: false, last: Date.now(), items: [], itemsAt: 0, idx: 0, t: 0, startedAt: 0, awake: null,
  minutes() { const m = EMBY.settings().saver; return m === undefined ? 5 : m; },
  poke() { SAVER.last = Date.now(); if (SAVER.awake === false) SAVER.keepAwake(true); },
  keepAwake(on) { SAVER.awake = on; try { if (HAS_ANDROID && Android.keepAwake) Android.keepAwake(on); } catch (e) {} },
  check() {
    const m = SAVER.minutes();
    if (SAVER.on) { if (Date.now() - SAVER.startedAt > 60 * 60000 && SAVER.awake) SAVER.keepAwake(false); return; }
    if (!m || !EMBY.user || PLAYER.active || (window.TVP && TVP.active) || (window.ADAPTER && ADAPTER.playing) || $('#layer .modal')) return;   // nie während Live-TV / midnox TV / Wiedergabe
    if (Date.now() - SAVER.last > m * 60000) SAVER.start();
  },
  async start() {
    if (SAVER.on) return; SAVER.on = true; SAVER.startedAt = Date.now();
    let el = $('#saver'); if (!el) { el = h('div', '', '<div class="sv a"></div><div class="sv b"></div><div class="svshade"></div><div class="svclock"></div><div class="svinfo"></div><div class="svnow"></div><div class="svlogo">midnox <b>Emby</b></div>'); el.id = 'saver'; document.body.appendChild(el); }
    el.className = 'show';
    try {
      if (!SAVER.items.length || Date.now() - SAVER.itemsAt > 30 * 60000) {
        const r = await EMBY.items({ Recursive: true, IncludeItemTypes: 'Movie,Series', ImageTypes: 'Backdrop', SortBy: 'Random', Limit: 40 });
        SAVER.items = (r.Items || []).filter(i => EMBY.backdrop(i)); SAVER.itemsAt = Date.now();
      }
    } catch (e) {}
    SAVER.tick(); SAVER.next();
  },
  next() {
    if (!SAVER.on) return;
    const el = $('#saver'); const it = SAVER.items.length ? SAVER.items[SAVER.idx++ % SAVER.items.length] : null;
    if (it) {
      const url = EMBY.backdrop(it); const img = new Image();
      img.onload = () => {
        if (!SAVER.on) return;
        const a = $('.sv.a', el), b = $('.sv.b', el); const show = a.classList.contains('on') ? b : a, hide = show === a ? b : a;
        show.style.backgroundImage = 'url("' + url + '")'; show.classList.remove('on'); void show.offsetWidth; show.classList.add('on'); hide.classList.remove('on');
        const info = $('.svinfo', el); info.classList.remove('in'); void info.offsetWidth;
        info.innerHTML = '<small>' + (it.Type === 'Series' ? 'Serie' : 'Film') + (it.ProductionYear ? ' · ' + it.ProductionYear : '') + '</small>' + esc(it.Name); info.classList.add('in');
      };
      img.src = url;
    }
    SAVER.t = setTimeout(SAVER.next, 12000);
  },
  tick() {
    if (!SAVER.on) return;
    const el = $('#saver'); const d = new Date();
    $('.svclock', el).innerHTML = clock(d) + '<small>' + d.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) + '</small>';
    const n = $('.svnow', el);
    if (MUSIC.active && MUSIC.cur()) { const it = MUSIC.cur(); n.innerHTML = '<div class="c" style="background-image:url(\'' + EMBY.img(it, 'Primary', 160) + '\')"></div><div><small>' + (MUSIC.playing ? 'Läuft gerade' : 'Pausiert') + '</small>' + esc(it.Name) + '<span>' + esc((it.Artists || [])[0] || '') + '</span></div>'; n.style.display = ''; }
    else n.style.display = 'none';
    setTimeout(SAVER.tick, 5000);
  },
  stop() {
    if (!SAVER.on) return; SAVER.on = false; clearTimeout(SAVER.t);
    const el = $('#saver'); if (el) el.className = '';
    SAVER.last = Date.now(); if (!SAVER.awake) SAVER.keepAwake(true);
  },
};
window.SAVER = SAVER;
setInterval(() => { try { SAVER.check(); } catch (e) {} }, 15000);
