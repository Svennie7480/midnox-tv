// midnox Emby – Grundlagen: Helfer, Speicher, Netz, Fokus-Steuerung, Dialoge, Texteingabe.
'use strict';
const HAS_ANDROID = typeof window.Android !== 'undefined';
const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
function h(tag, cls, html) { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; }
function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m])); }
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const rem = () => parseFloat(getComputedStyle(document.documentElement).fontSize);
function pad2(n) { return (n < 10 ? '0' : '') + n; }
function fmtTime(ms) { ms = Math.max(0, Math.round((ms || 0) / 1000)); const hh = Math.floor(ms / 3600), mm = Math.floor(ms % 3600 / 60), ss = ms % 60; return (hh ? hh + ':' + pad2(mm) : mm) + ':' + pad2(ss); }
function fmtDur(ticks) { const m = Math.round((ticks || 0) / 600000000); if (!m) return ''; const hh = Math.floor(m / 60), mm = m % 60; return hh ? hh + ' Std. ' + (mm ? mm + ' Min.' : '') : mm + ' Min.'; }
function clock(d) { d = d || new Date(); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()); }
function ago(t) { const s = Math.max(0, Date.now() / 1000 - t); if (s < 60) return 'gerade'; if (s < 3600) return 'vor ' + Math.floor(s / 60) + ' Min.'; if (s < 86400) return 'vor ' + Math.floor(s / 3600) + ' Std.'; const d = Math.floor(s / 86400); return d === 1 ? 'gestern' : 'vor ' + d + ' Tagen'; }
function initials(n) { n = String(n || '?').trim(); return (n[0] || '?').toUpperCase(); }
function colorFor(s) { let x = 0; for (const c of String(s)) x = (x * 31 + c.charCodeAt(0)) >>> 0; const hue = [262, 228, 190, 330, 160, 28, 290, 205][x % 8]; return 'linear-gradient(135deg,hsl(' + hue + ',75%,58%),hsl(' + ((hue + 40) % 360) + ',80%,45%))'; }
function avatar(name, img, cls) { const a = h('div', 'av' + (cls ? ' ' + cls : '')); if (img) { a.style.backgroundImage = 'url("' + img + '")'; } else { a.style.background = colorFor(name); a.textContent = initials(name); } return a; }
function debounce(fn, ms) { let t; return function () { const a = arguments; clearTimeout(t); t = setTimeout(() => fn.apply(this, a), ms); }; }

// ---------- Symbole ----------
const IC = {
  home: '<path d="M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  film: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M7 4v16M17 4v16M3 9h4M17 9h4M3 15h4M17 15h4"/>',
  tv: '<rect x="2.5" y="5" width="19" height="13" rx="2.5"/><path d="m8 21h8M9 2l3 3 3-3"/>',
  music: '<path d="M9 18V5l11-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
  chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1.1-4.6A8 8 0 1 1 21 12z"/><path d="M8.5 11h.01M12 11h.01M15.5 11h.01"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  play: '<path d="M7 4.5v15a1 1 0 0 0 1.5.9l12-7.5a1 1 0 0 0 0-1.8l-12-7.5A1 1 0 0 0 7 4.5z" fill="currentColor" stroke="none"/>',
  pause: '<rect x="6" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/><rect x="14" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/>',
  restart: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',
  back10: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-weight="800">10</text>',
  fwd30: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-weight="800">30</text>',
  next: '<path d="M5 4.5v15l11-7.5z" fill="currentColor" stroke="none"/><path d="M19 5v14"/>',
  prev: '<path d="M19 4.5v15L8 12z" fill="currentColor" stroke="none"/><path d="M5 5v14"/>',
  heart: '<path d="M12 20.5s-8-4.6-8-10.4A4.6 4.6 0 0 1 12 7a4.6 4.6 0 0 1 8 3.1c0 5.8-8 10.4-8 10.4z"/>',
  heartf: '<path d="M12 20.5s-8-4.6-8-10.4A4.6 4.6 0 0 1 12 7a4.6 4.6 0 0 1 8 3.1c0 5.8-8 10.4-8 10.4z" fill="currentColor"/>',
  check: '<path d="m4.5 12.5 5 5 10-11"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>',
  star: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"/>',
  starf: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" fill="currentColor"/>',
  party: '<path d="M4 20 9 5l10 10z"/><path d="M14 4c1-1 3-1 3 1M19 9c1-1 3 0 2 2M17 2v1M22 6h-1"/>',
  share: '<path d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13"/>',
  sub: '<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M6 15h5M13 15h5M6 11h3M11 11h7"/>',
  audio: '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
  shuffle: '<path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/>',
  server: '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
  phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="2.5"/><path d="M11 18h2"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  keyboard: '<rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10"/>',
  bell: '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/>',
  moon: '<path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z"/>',
  trailer: '<rect x="2.5" y="5" width="15" height="14" rx="2"/><path d="m17.5 10 4-2.5v9l-4-2.5"/>',
  skip: '<path d="M5 4.5v15l9-7.5zM15 4.5v15l9-7.5z" fill="currentColor" stroke="none" transform="scale(.85) translate(1 2)"/>',
};
function ic(name, extra) { return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"' + (extra || '') + '>' + (IC[name] || '') + '</svg>'; }

// ---------- Speicher ----------
const STORE = {
  get(k, def) { let v = null; try { v = HAS_ANDROID ? Android.pget(k) : localStorage.getItem('mx_' + k); } catch (e) {} if (v == null) return def; try { return JSON.parse(v); } catch (e) { return def; } },
  set(k, v) { const s = v === undefined || v === null ? null : JSON.stringify(v); try { if (HAS_ANDROID) Android.pset(k, s); else if (s === null) localStorage.removeItem('mx_' + k); else localStorage.setItem('mx_' + k, s); } catch (e) {} },
};
const DEVICE = (() => {
  let i = {}; try { if (HAS_ANDROID) i = JSON.parse(Android.info()); } catch (e) {}
  if (!i.deviceId) { i.deviceId = STORE.get('devId'); if (!i.deviceId) { i.deviceId = 'midnox-web-' + Math.random().toString(36).slice(2, 12); STORE.set('devId', i.deviceId); } }
  i.deviceName = i.deviceName || 'Browser'; i.version = i.version || '1.0';
  return i;
})();

// ---------- Netz ----------
const NET = {
  seq: 0, pend: {},
  request(method, url, headers, body, timeout) {
    if (HAS_ANDROID && Android.http) {
      return new Promise((res, rej) => {
        const id = 'r' + (++NET.seq);
        const t = setTimeout(() => { delete NET.pend[id]; rej(new Error('Zeitüberschreitung')); }, timeout || 35000);
        NET.pend[id] = { res, rej, t };
        Android.http(id, method, url, JSON.stringify(headers || {}), body == null ? null : (typeof body === 'string' ? body : JSON.stringify(body)));
      });
    }
    const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), timeout || 35000);
    const hd = Object.assign({}, headers || {}); if (body != null && !hd['Content-Type']) hd['Content-Type'] = 'application/json';
    return fetch(url, { method, headers: hd, body: body == null ? undefined : (typeof body === 'string' ? body : JSON.stringify(body)), signal: ctl.signal })
      .then(r => r.text().then(text => { clearTimeout(t); return { status: r.status, text }; }))
      .catch(e => { clearTimeout(t); throw new Error(e.name === 'AbortError' ? 'Zeitüberschreitung' : 'Keine Verbindung'); });
  },
  done(id, status, text) {
    const p = NET.pend[id]; if (!p) return; delete NET.pend[id]; clearTimeout(p.t);
    if (status === 0) p.rej(new Error(text && /Unknown host|UnknownHost/i.test(text) ? 'Adresse nicht gefunden' : (text && /timeout|timed out/i.test(text) ? 'Zeitüberschreitung' : 'Keine Verbindung')));
    else p.res({ status, text });
  },
  async json(method, url, headers, body, timeout) {
    const r = await NET.request(method, url, headers, body, timeout);
    let data = null; try { data = r.text ? JSON.parse(r.text) : null; } catch (e) {}
    if (r.status >= 400) { const err = new Error((data && (data.msg || data.Message)) || ('Fehler ' + r.status)); err.status = r.status; err.data = data; throw err; }
    return data;
  },
};

// ---------- Hintergrund ----------
const BG = {
  cur: null, flip: false,
  set(url) {
    if (url === BG.cur) return; BG.cur = url;
    const a = $('#bd1'), b = $('#bd2'); const on = BG.flip ? a : b, off = BG.flip ? b : a; BG.flip = !BG.flip;
    if (!url) { a.classList.remove('on'); b.classList.remove('on'); return; }
    const img = new Image();
    img.onload = () => { if (BG.cur !== url) return; on.style.backgroundImage = 'url("' + url + '")'; on.classList.add('on'); off.classList.remove('on'); };
    img.src = url;
  },
  dim(on) { $('#bg').classList.toggle('dim', !!on); },
};

// ---------- Fokus-Steuerung ----------
const NAV = {
  cur: null, lastMove: 0,
  root() {
    const m = $$('#layer .modal'); if (m.length) return m[m.length - 1];
    if (window.PLAYER && PLAYER.active && PLAYER.navRoot) return PLAYER.navRoot();
    return ROUTER.top() ? ROUTER.top().el : document.body;
  },
  scope() {
    const r = NAV.root(); const out = [r];
    if (!$('#layer .modal') && !(window.PLAYER && PLAYER.active) && !$('#rail').classList.contains('hidden') && ROUTER.top() && !ROUTER.top().noRail) out.push($('#rail'));
    return out;
  },
  items() {
    const all = []; for (const s of NAV.scope()) for (const e of s.querySelectorAll('.f')) if (!e.classList.contains('dis') && e.getClientRects().length) all.push(e);
    return all;
  },
  focus(el, opts) {
    if (!el) return;
    if (NAV.cur && NAV.cur !== el) { NAV.cur.classList.remove('focused'); if (NAV.cur._blur) NAV.cur._blur(); }
    NAV.cur = el; el.classList.add('focused');
    let p = el.parentElement; while (p) { if (p.hasAttribute && p.hasAttribute('data-mem')) p._last = el; p = p.parentElement; }
    const rail = $('#rail'); rail.classList.toggle('open', rail.contains(el));
    NAV.scrollTo(el, opts);
    if (el._focus) el._focus();
  },
  scrollTo(el, opts) {
    const tr = el.closest('.track');
    if (tr) {
      const padL = parseFloat(getComputedStyle(tr).paddingLeft) || 0;
      let x = el.offsetLeft - padL; if (tr.dataset.center) x = el.offsetLeft - (tr.parentElement.clientWidth - el.offsetWidth) / 2;
      x = Math.max(0, x); tr.style.transform = 'translateX(' + (-x) + 'px)';
    }
    const vs = el.closest('[data-vs]');
    if (vs) NAV.vscroll(vs, el, opts);
  },
  vscroll(vs, el, opts) {
    const R = rem(); const mode = vs.dataset.vs; const cur = vs._ty || 0;
    const item = el.closest('[data-vsi]') || el;
    const top = item.getBoundingClientRect().top - vs.getBoundingClientRect().top;   // Abstand im Inhalt (ohne aktuelle Verschiebung)
    let ty = cur;
    if (mode === 'anchor') {
      ty = item.hasAttribute('data-vtop') ? 0 : parseFloat(vs.dataset.at || 28) * R - top;
    } else {
      const view = (vs.parentElement.clientHeight || window.innerHeight), hgt = item.getBoundingClientRect().height, m = parseFloat(vs.dataset.margin || 3) * R;
      if (top + cur < m) ty = -(top - m);
      else if (top + hgt + cur > view - m - R * 4) ty = -(top + hgt - view + m + R * 4);
      ty = Math.min(0, ty);
    }
    vs._ty = ty; vs.style.transform = 'translateY(' + ty + 'px)';
    if (vs._onScroll) vs._onScroll(item);
  },
  first(container) {
    const c = container || NAV.root();
    const m = c._last && c._last.isConnected && c._last.getClientRects().length ? c._last : null;
    return m || $$('.f', c).find(e => !e.classList.contains('dis') && e.getClientRects().length);
  },
  move(dir) {
    const items = NAV.items(); const cur = NAV.cur && NAV.cur.isConnected && items.includes(NAV.cur) ? NAV.cur : null;
    if (!cur) { NAV.focus(NAV.first(NAV.root())); return true; }
    const r = cur.getBoundingClientRect(); const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    let best = null, bs = Infinity;
    const rail = $('#rail'), inRail = rail.contains(cur);
    for (const e of items) {
      if (e === cur) continue;
      const q = e.getBoundingClientRect(); const x = q.left + q.width / 2, y = q.top + q.height / 2;
      let pri, ort, overlap;
      if (dir === 'left' || dir === 'right') {
        pri = dir === 'right' ? q.left - r.right : r.left - q.right;
        if ((dir === 'right' ? x - cx : cx - x) <= 2) continue;
        ort = Math.abs(y - cy); overlap = q.bottom > r.top + 2 && q.top < r.bottom - 2;
        // seitwärts nur innerhalb derselben Zeile – sonst geht es ins Menü (links) bzw. nicht weiter
        if (!overlap && !(inRail && dir === 'right')) continue;
        if (!inRail && rail.contains(e)) continue;
        const s = Math.max(0, pri) + ort * 3;
        if (s < bs) { bs = s; best = e; }
      } else {
        pri = dir === 'down' ? q.top - r.bottom : r.top - q.bottom;
        if ((dir === 'down' ? y - cy : cy - y) <= 2) continue;
        ort = Math.abs(x - cx); overlap = q.right > r.left + 2 && q.left < r.right - 2;
        const s = Math.max(-r.height / 2, pri) * 1.2 + ort * (overlap ? .4 : 1) + (overlap ? 0 : 400);
        if (s < bs) { bs = s; best = e; }
      }
    }
    if (!best) return false;
    // Gedächtnis: beim Wechsel in einen anderen Bereich das zuletzt fokussierte Element nehmen
    let p = best.parentElement, mem = null;
    while (p && p !== document.body) { if (p.hasAttribute('data-mem') && !p.contains(cur)) mem = p; p = p.parentElement; }
    if (mem && mem._last && mem._last.isConnected && items.includes(mem._last)) best = mem._last;
    NAV.focus(best); return true;
  },
};

// ---------- Tasten ----------
window.KEY = function (k, repeat) {
  try {
    if (window.SAVER) { if (SAVER.on) { SAVER.stop(); return; } SAVER.poke(); }
    if (k === 'left' || k === 'right' || k === 'up' || k === 'down') { const t = Date.now(); if (repeat && t - NAV.lastMove < 85) return; NAV.lastMove = t; }
    if (TEXT.active && TEXT.key(k)) return;
    const modals = $$('#layer .modal'); const m = modals[modals.length - 1];
    if (m) { if (m._key && m._key(k)) return; if (k === 'back') { m._close(null); return; } if (k === 'ok') { if (NAV.cur && NAV.cur._ok) NAV.cur._ok(); return; } if (['up', 'down', 'left', 'right'].includes(k)) NAV.move(k); return; }
    if (window.PLAYER && PLAYER.active) { PLAYER.key(k, repeat); return; }
    const s = ROUTER.top();
    if (s && s.key && s.key(k, repeat)) return;
    if (['up', 'down', 'left', 'right'].includes(k)) { if (!NAV.move(k) && k === 'left' && s && !s.noRail) RAIL.enter(); return; }
    if (k === 'ok') { if (NAV.cur && NAV.cur._ok) NAV.cur._ok(); return; }
    if (k === 'menu') { if (window.COMM && COMM.menuAction && COMM.menuAction()) return; if (NAV.cur && NAV.cur._menu) { NAV.cur._menu(); return; } return; }
    if (k === 'back') { if ($('#rail').contains(NAV.cur)) { ROUTER.backFromRail(); return; } ROUTER.back(); return; }
    if (k === 'playpause' || k === 'play' || k === 'pause' || k === 'next' || k === 'prev' || k === 'stop') { if (window.MUSIC && MUSIC.mediaKey(k)) return; }
    if (k === 'search') { ROUTER.go('search'); return; }
  } catch (e) { console.error('KEY', e); }
};
function onOk(el, fn) { el.classList.add('f'); el._ok = fn; return el; }
// Maus/Touch (Handy-Fernbedienungs-Apps, Testbrowser)
document.addEventListener('click', (e) => { const f = e.target.closest('.f'); if (f) { NAV.focus(f); if (f._ok) f._ok(); } });

// ---------- Seiten ----------
const ROUTER = {
  stack: [], routes: {},
  top() { return ROUTER.stack[ROUTER.stack.length - 1]; },
  reg(name, fn) { ROUTER.routes[name] = fn; },
  async go(name, arg, opts) {
    opts = opts || {};
    const make = ROUTER.routes[name]; if (!make) return;
    const prev = ROUTER.top();
    if (prev) { prev._focus = NAV.cur; if (opts.replace || opts.root) { ROUTER.drop(opts.root ? ROUTER.stack.length : 1); } else { prev.el.classList.add('inactive'); if (prev.leave) prev.leave(); } }
    const scr = { name, arg, el: h('div', 'page'), main: !!opts.root }; scr.el.setAttribute('data-mem', '');
    ROUTER.stack.push(scr); $('#pages').appendChild(scr.el);
    RAIL.mark(name);
    scr.el.appendChild(h('div', 'loading skel', '<div class="bar"></div><div class="line">' + '<div class="blk"></div>'.repeat(7) + '</div><div class="bar"></div><div class="line">' + '<div class="blk"></div>'.repeat(7) + '</div>'));
    try { await make(scr, arg); }
    catch (e) { console.error(e); scr.el.innerHTML = ''; scr.el.appendChild(h('div', 'empty', 'Das hat nicht geklappt: ' + esc(e.message || e))); }
    if (ROUTER.top() !== scr) return scr;
    const ld = $('.loading', scr.el); if (ld && scr.el.firstChild !== ld) ld.remove(); else if (ld && scr.el.children.length > 1) ld.remove();
    if (!NAV.cur || !scr.el.contains(NAV.cur)) NAV.focus(scr.initial || NAV.first(scr.el));
    return scr;
  },
  drop(n) { for (let i = 0; i < n; i++) { const s = ROUTER.stack.pop(); if (!s) break; if (s.leave) s.leave(); if (s.destroy) s.destroy(); s.el.remove(); } },
  back() {
    const cur = ROUTER.top();
    if (cur && cur.onBack && cur.onBack()) return;
    if (ROUTER.stack.length <= 1) {
      if (cur && cur.name !== 'home' && ROUTER.routes.home && EMBY.user) { ROUTER.go('home', null, { root: true }); return; }
      if (!$('#rail').contains(NAV.cur) && cur && !cur.noRail && !$('#rail').classList.contains('hidden')) { RAIL.enter(); return; }
      UI.confirm('midnox Emby beenden?', '', 'Beenden').then(ok => { if (ok) { if (HAS_ANDROID) Android.exit(); } });
      return;
    }
    ROUTER.drop(1);
    const t = ROUTER.top(); t.el.classList.remove('inactive'); RAIL.mark(t.name);
    if (t.enter) t.enter();
    NAV.focus(t._focus && t._focus.isConnected ? t._focus : NAV.first(t.el));
  },
  backFromRail() { const t = ROUTER.top(); if (t) NAV.focus(t.el._last && t.el._last.isConnected ? t.el._last : NAV.first(t.el)); },
  refreshTop() { const t = ROUTER.top(); if (t && t.refresh) t.refresh(); },
};

// ---------- Navigationsleiste ----------
const RAIL = {
  items: [['home', 'Start', 'home'], ['movies', 'Filme', 'film'], ['shows', 'Serien', 'tv'], ['music', 'Musik', 'music'], ['community', 'Community', 'chat'], ['match', 'Was gucken?', 'heart'], ['search', 'Suche', 'search'], ['settings', 'Einstellungen', 'gear']],
  build() {
    const r = $('#rail'); r.innerHTML = '<div class="logo">m</div><div class="lt">midnox <b>Emby</b></div>';
    for (const [id, label, icon] of RAIL.items) {
      if (id === 'music' && !EMBY.hasMusic) continue;
      const el = h('div', 'ri', ic(icon) + '<span>' + label + '</span>'); el.dataset.id = id;
      onOk(el, () => { if (ROUTER.top() && ROUTER.top().name === id && ROUTER.stack.length === 1) { ROUTER.backFromRail(); return; } ROUTER.go(id, null, { root: true }); });
      r.appendChild(el);
    }
    const me = h('div', 'me'); me.dataset.id = 'me'; me.appendChild(avatar(EMBY.user.name, EMBY.userImg())); me.appendChild(h('span', '', esc(EMBY.user.name))); onOk(me, () => ROUTER.go('me', null, { root: true })); r.appendChild(me);
    r.classList.remove('hidden');
  },
  mark(name) { $$('#rail .ri').forEach(e => e.classList.toggle('cur', e.dataset.id === name || (ROUTER.stack[0] && ROUTER.stack[0].name === e.dataset.id))); },
  enter() { const cur = $('#rail .ri.cur') || $('#rail .ri'); if (cur) NAV.focus(cur); },
  badge(id, n) { const el = $('#rail .ri[data-id="' + id + '"]'); if (!el) return; let b = $('.badge', el); if (!n) { if (b) b.remove(); return; } if (!b) { b = h('i', 'badge'); el.appendChild(b); } b.textContent = n > 9 ? '9+' : n; },
};

// ---------- Dialoge & Hinweise ----------
const UI = {
  modal(build, opts) {
    opts = opts || {};
    return new Promise((resolve) => {
      const prevFocus = NAV.cur;
      const m = h('div', 'modal'); const box = h('div', 'box'); m.appendChild(box);
      m._close = (v) => { if (!m.isConnected) return; m.remove(); if (opts.onClose) opts.onClose(v); if (prevFocus && prevFocus.isConnected) NAV.focus(prevFocus); else if (!$('#layer .modal')) { const t = ROUTER.top(); if (t && !(window.PLAYER && PLAYER.active)) NAV.focus(NAV.first(t.el)); } resolve(v); };
      build(box, m._close, m);
      $('#layer').appendChild(m);
      NAV.focus(opts.initial ? opts.initial(box) : NAV.first(box));
    });
  },
  confirm(title, text, yes, no) {
    return UI.modal((box, close) => {
      box.appendChild(h('h2', '', esc(title))); if (text) box.appendChild(h('p', '', esc(text)));
      const b = h('div', 'btns');
      b.appendChild(onOk(h('div', 'btn pri', esc(yes || 'OK')), () => close(true)));
      b.appendChild(onOk(h('div', 'btn', esc(no || 'Abbrechen')), () => close(false)));
      box.appendChild(b);
    });
  },
  alert(title, text) { return UI.modal((box, close) => { box.appendChild(h('h2', '', esc(title))); if (text) box.appendChild(h('p', '', esc(text))); const b = h('div', 'btns'); b.appendChild(onOk(h('div', 'btn pri', 'OK'), () => close(true))); box.appendChild(b); }); },
  /** Auswahl-Liste. options: [{label, value, sub, sel}] */
  choose(title, options, sub) {
    return UI.modal((box, close, m) => {
      box.appendChild(h('h2', '', esc(title))); if (sub) box.appendChild(h('p', '', esc(sub)));
      const list = h('div', 'opts'); list.setAttribute('data-vs', 'keep'); list.dataset.margin = '0.5';
      const wrap = h('div', ''); wrap.style.cssText = 'max-height:34rem;overflow:hidden;margin-top:1.6rem'; wrap.appendChild(list); list.style.marginTop = '0';
      let initial = null;
      for (const o of options) {
        const el = h('div', 'opt', '<span class="ck">' + (o.sel ? '✓' : '') + '</span><span>' + esc(o.label) + '</span>' + (o.sub ? '<small>' + esc(o.sub) + '</small>' : ''));
        onOk(el, () => close(o.value)); list.appendChild(el); if (o.sel && !initial) initial = el;
      }
      box.appendChild(wrap); m._init = initial;
    }, { initial: (box) => box.parentElement._init || $('.opt', box) });
  },
  toast(text, opts) {
    opts = opts || {};
    const t = h('div', 'toast' + (opts.cls ? ' ' + opts.cls : ''), (opts.icon ? '<div class="ic">' + opts.icon + '</div>' : '') + '<div>' + (opts.title ? '<b>' + esc(opts.title) + '</b>' : '') + esc(text) + (opts.hint ? '<span class="hint">' + esc(opts.hint) + '</span>' : '') + '</div>');
    $('#toasts').appendChild(t);
    while ($('#toasts').children.length > 4) $('#toasts').firstChild.remove();
    const kill = () => { t.classList.add('out'); setTimeout(() => t.remove(), 350); };
    setTimeout(kill, opts.ms || 4200);
    return { el: t, close: kill };
  },
  banner(text, ms) {
    const b = $('#banner'); b.innerHTML = ic('bell', ' style="width:1.8rem;height:1.8rem;flex:none"') + '<span>' + esc(text) + '</span>';
    b.classList.remove('off'); clearTimeout(UI._bt); UI._bt = setTimeout(() => b.classList.add('off'), ms || 12000);
  },
  qr(url) { try { const q = qrcode(0, 'M'); q.addData(url); q.make(); return q.createSvgTag({ cellSize: 4, margin: 1, scalable: true }); } catch (e) { return ''; } },
};

// ---------- Texteingabe (Fire-TV-Tastatur oder Handy) ----------
const TEXT = {
  active: null,
  /** Zeigt ein Eingabefeld; liefert den Text oder null. */
  ask(title, opts) {
    opts = opts || {};
    return UI.modal((box, close, m) => {
      box.style.minWidth = '60rem';
      box.appendChild(h('h2', '', esc(title))); if (opts.sub) box.appendChild(h('p', '', esc(opts.sub)));
      const field = h('div', 'field f'); field.style.marginTop = '1.8rem';
      let val = opts.value || '';
      const show = () => { field.textContent = val ? (opts.password ? '•'.repeat(val.length) : val) : (opts.placeholder || 'Hier tippen …'); field.classList.toggle('ph', !val); };
      show();
      const inp = $('#tvinput'); inp.type = opts.password ? 'password' : 'text'; inp.value = val;
      inp.oninput = () => { val = inp.value; show(); };
      inp.onkeydown = (e) => { if (e.key === 'Enter') { e.preventDefault(); val = inp.value; inp.blur(); if (HAS_ANDROID) Android.keyboard(false); show(); NAV.focus(okb); } };
      const openKb = () => { inp.value = val; inp.focus(); if (HAS_ANDROID) Android.keyboard(true); };
      field._ok = openKb;
      box.appendChild(field);
      const b = h('div', 'btns');
      const okb = onOk(h('div', 'btn pri', esc(opts.okLabel || 'Fertig')), () => close(val.trim() || (opts.allowEmpty ? '' : null)));
      b.appendChild(okb);
      b.appendChild(onOk(h('div', 'btn', ic('phone') + 'Per Handy tippen'), async () => {
        const got = await PHONE.ask({ mode: 'text', title, password: !!opts.password, value: opts.password ? '' : val });
        if (got && typeof got.text === 'string') { val = got.text; inp.value = val; show(); NAV.focus(okb); }
      }));
      b.appendChild(onOk(h('div', 'btn', 'Abbrechen'), () => close(null)));
      box.appendChild(b);
      m._key = (k) => { if (k === 'back' && document.activeElement === inp) { inp.blur(); if (HAS_ANDROID) Android.keyboard(false); return true; } return false; };
      setTimeout(() => { if (opts.autoKeyboard !== false && HAS_ANDROID) openKb(); }, 250);
    }, { onClose: () => { const inp = $('#tvinput'); inp.blur(); inp.oninput = inp.onkeydown = null; if (HAS_ANDROID) Android.keyboard(false); } });
  },
  key() { return false; },
};

// ---------- Handy als Tastatur / Einrichtung ----------
const PHONE = {
  waiting: null,
  /** Zeigt einen QR-Code; das Handy schickt die Eingabe zurück. */
  ask(state) {
    if (!HAS_ANDROID) return Promise.resolve(null);
    const url = Android.phone(JSON.stringify(state));
    if (!url) { UI.toast('Handy-Helfer konnte nicht starten'); return Promise.resolve(null); }
    return new Promise((resolve) => {
      UI.modal((box, close) => {
        PHONE.waiting = (data) => close(data);
        box.appendChild(h('h2', '', state.mode === 'login' ? 'Mit dem Handy einrichten' : 'Mit dem Handy tippen'));
        const q = h('div', 'qr'); q.innerHTML = '<div class="code">' + UI.qr(url) + '</div>';
        const t = h('div', '', '<p>QR-Code mit dem Handy scannen (gleiches WLAN). Die Eingabe landet direkt hier auf dem Fernseher.</p><p class="url" style="margin-top:1.4rem">' + esc(url.replace(/\?k=.*/, '')) + '</p>');
        q.appendChild(t); box.appendChild(q);
        const b = h('div', 'btns'); b.appendChild(onOk(h('div', 'btn', 'Abbrechen'), () => close(null))); box.appendChild(b);
      }, { onClose: () => { PHONE.waiting = null; try { Android.phoneOff(); } catch (e) {} } }).then(resolve);
    });
  },
  got(data) { if (PHONE.waiting) { const w = PHONE.waiting; PHONE.waiting = null; w(data); } },
};
window.NET = NET; window.UI = UI; window.NAV = NAV; window.ROUTER = ROUTER;

// ---------- Farbthema ----------
const THEME = {
  names: { midnox: 'midnox Violett', ozean: 'Ozean', glut: 'Glut', wald: 'Wald', advent: 'Advent', halloween: 'Halloween', fruehling: 'Frühling', sommer: 'Sommer', einheit: 'Tag der Deutschen Einheit' },
  list: [['auto', 'Saisonal automatisch'], ['midnox', 'midnox Violett'], ['ozean', 'Ozean'], ['glut', 'Glut'], ['wald', 'Wald']],
  seasonal(d) {
    d = d || new Date(); const m = d.getMonth() + 1, day = d.getDate();
    if (m === 10 && day === 3) return 'einheit';
    if (m === 10 && day >= 20 || m === 11 && day === 1) return 'halloween';
    if (m === 12 || m === 11 && day >= 28) return 'advent';
    if (m >= 3 && m <= 5) return 'fruehling';
    if (m >= 6 && m <= 8) return 'sommer';
    return 'midnox';
  },
  apply(name) {
    const t = name === 'auto' || !name ? THEME.seasonal() : name;
    document.body.className = document.body.className.replace(/\bth-\S+/g, '').trim();
    if (t !== 'midnox') document.body.classList.add('th-' + t);
    return t;
  },
};
window.THEME = THEME;
/** Glass-Optik: full (mit Unschärfe), lite (ohne Unschärfe), off (klassisch). */
const GLASS = { list: [['full', 'An – Glas mit Unschärfe'], ['lite', 'Sparsam – ohne Unschärfe (ältere Sticks)'], ['off', 'Aus – klassisch dunkel']],
  apply(v) { v = v || 'full'; document.body.classList.toggle('glass', v !== 'off'); document.body.classList.toggle('glass-lite', v === 'lite'); } };
window.GLASS = GLASS;
