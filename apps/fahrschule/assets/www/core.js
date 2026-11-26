'use strict';
/* midnox Führerschein – Oberfläche für Fernseher (Fernbedienung), Handy (Touch) und Browser. Grundbausteine aus midnox Rezepte. */
// ------------------------------------------------------------------ Grundlagen
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const h = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
const AND = window.Android || null;
const QS = new URLSearchParams(location.search);
const TV = QS.has('tv') || !!(AND && AND.tv && AND.tv());
const INAPP = !!AND;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

const ICON = {
  check2: '',
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  book: '<path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17H6.5A2.5 2.5 0 0 0 4 21.5z"/><path d="M4 21.5A2.5 2.5 0 0 1 6.5 19H20v3H6.5"/><path d="M9 7h7M9 11h5"/>',
  cal: '<rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/><path d="M7.5 13.5h3v3h-3z"/>',
  cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2.5 3h2.6l2.4 12.2a1.6 1.6 0 0 0 1.6 1.3h8.7a1.6 1.6 0 0 0 1.6-1.2L21.5 7H6.2"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>', minus: '<path d="M5 12h14"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
  heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6.5 6.5 0 0 1 3.5 6"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/>',
  chef: '<path d="M6 13.9V20h12v-6.1"/><path d="M6 14a4 4 0 0 1-.6-7.9 5 5 0 0 1 9.3-1.8A4 4 0 1 1 18 14"/><path d="M6 17h12"/>',
  play: '<path d="M7 4v16l13-8z"/>', left: '<path d="m15 18-6-6 6-6"/>', right: '<path d="m9 18 6-6-6-6"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  cloud: '<path d="M17.5 19H7a5 5 0 1 1 1.1-9.9A6 6 0 0 1 19.6 11 4 4 0 0 1 17.5 19z"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2.5"/><path d="M11 18h2"/>',
  dice: '<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.2"/><circle cx="16" cy="16" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="16" cy="8" r="1.2"/><circle cx="8" cy="16" r="1.2"/>',
  share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
  check: '<path d="M20 6 9 17l-5-5"/>', refresh: '<path d="M21 12a9 9 0 1 1-2.6-6.4L21 8"/><path d="M21 3v5h-5"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9.5 2.5h5"/>', move: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  wifi: '<path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0"/><circle cx="12" cy="19.5" r="1"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>', copy: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  down: '<path d="M12 3v13M6 11l6 6 6-6M4 21h16"/>', up: '<path d="M12 21V8M6 13l6-6 6 6M4 3h16"/>', eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
};
const ico = (n, sw = 2) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' + sw + '" stroke-linecap="round" stroke-linejoin="round">' + (ICON[n] || '') + '</svg>';

function toast(msg, ms = 2600) { const t = $('#toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove('on'), ms); }

// ------------------------------------------------------------------ Fokus-Steuerung (Fernbedienung / Tastatur)
const NAV = {
  cur: null,
  scope() { const ov = $$('#layer .ov'); return ov.length ? ov[ov.length - 1] : document.body; },
  items() { const sc = NAV.scope(); return $$('.f', sc).filter(e => (sc !== document.body || !e.closest('#layer')) && e.offsetParent !== null && !e.disabled && !e.classList.contains('off')); },
  set(e, noScroll) {
    if (NAV.cur && NAV.cur !== e) NAV.cur.classList.remove('foc');
    NAV.cur = e; if (!e) return;
    e.classList.add('foc');
    if (!/^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName)) { if (document.activeElement && document.activeElement !== e && /^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) document.activeElement.blur(); }
    if (!noScroll) NAV.reveal(e);
  },
  reveal(e) {
    const r = e.getBoundingClientRect(), row = e.closest('.row');
    if (row) { const rr = row.getBoundingClientRect(); if (r.left < rr.left + 20 || r.right > rr.right - 20) row.scrollLeft += r.left - rr.left - rr.width / 2 + r.width / 2; }
    const box = e.closest('.dlg, .cook .side, .cook .txt') || null;
    if (box) { const b = box.getBoundingClientRect(); if (r.top < b.top + 30 || r.bottom > b.bottom - 30) box.scrollTop += r.top - b.top - b.height / 3; return; }
    const top = TV ? 140 : 90, bot = innerHeight - (document.body.classList.contains('narrow') ? 110 : 60);
    if (r.top < top) scrollBy({ top: r.top - top - (TV ? 60 : 20), behavior: 'smooth' });
    else if (r.bottom > bot) scrollBy({ top: r.bottom - bot + (TV ? 60 : 20), behavior: 'smooth' });
  },
  first(root) { const sc = root || NAV.scope(); const e = $$('.f', sc).find(x => x.offsetParent !== null && !x.disabled && !(sc === document.body && x.closest('#layer')) && !x.closest('#rail')); if (e) NAV.set(e, true); else NAV.set(null); },
  move(dir) {
    const items = NAV.items(); if (!items.length) return;
    if (!NAV.cur || !items.includes(NAV.cur)) { NAV.first(); return; }
    const a = NAV.cur.getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    let best = null, bs = 1e9;
    for (const e of items) {
      if (e === NAV.cur) continue;
      const b = e.getBoundingClientRect(), bx = b.left + b.width / 2, by = b.top + b.height / 2;
      let pri, sec, ov;
      if (dir === 'left' || dir === 'right') {
        pri = dir === 'left' ? a.left - b.right : b.left - a.right; if ((dir === 'left' ? bx >= ax : bx <= ax)) continue;
        ov = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top); sec = ov > 0 ? 0 : Math.abs(by - ay);
      } else {
        pri = dir === 'up' ? a.top - b.bottom : b.top - a.bottom; if ((dir === 'up' ? by >= ay : by <= ay)) continue;
        ov = Math.min(a.right, b.right) - Math.max(a.left, b.left); sec = ov > 0 ? Math.abs(bx - ax) * .15 : Math.abs(bx - ax);
      }
      const s = Math.max(pri, -5) + sec * 2.2 + (e.closest('#rail') && !NAV.cur.closest('#rail') && dir !== 'left' ? 1e5 : 0);
      if (s < bs) { bs = s; best = e; }
    }
    if (best) NAV.set(best);
    else if (dir === 'left' && !NAV.cur.closest('#rail') && NAV.scope() === document.body) { const on = $('#rail .it.on') || $('#rail .it'); if (on) NAV.set(on); }
  },
  ok() {
    const e = NAV.cur; if (!e) return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName)) { e.focus(); if (e.select && e.type !== 'file' && e.tagName === 'INPUT') try { e.setSelectionRange(e.value.length, e.value.length); } catch (x) {} return; }
    e.click();
  },
};
document.addEventListener('keydown', (ev) => {
  const k = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', Escape: 'back', GoBack: 'back', BrowserBack: 'back' }[ev.key];
  if (!k) return;
  const ae = document.activeElement, typing = ae && /^(INPUT|TEXTAREA|SELECT)$/.test(ae.tagName) && ae.type !== 'checkbox';
  if (typing) {
    if (k === 'back') { ev.preventDefault(); ae.blur(); NAV.set(ae, true); return; }
    if (ae.tagName === 'TEXTAREA' && k !== 'ok') { const atTop = ae.selectionStart === 0, atEnd = ae.selectionEnd === ae.value.length; if (!((k === 'up' && atTop) || (k === 'down' && atEnd))) return; }
    else if (k === 'left' || k === 'right' || (k === 'ok' && ae.tagName === 'TEXTAREA')) return;
    if (k === 'ok') { if (ae._enter) { ev.preventDefault(); ae._enter(); } return; }
    ae.blur(); NAV.set(ae, true);
  }
  if (!TV && !document.body.classList.contains('kb') && k !== 'back') { document.body.classList.add('kb'); if (!NAV.cur || !NAV.cur.isConnected) { NAV.first(); ev.preventDefault(); return; } }
  ev.preventDefault();
  if (VIEW.key && VIEW.key(k)) return;
  if (k === 'back') { APP.back(); return; }
  if (k === 'ok') NAV.ok(); else NAV.move(k);
});
document.addEventListener('mousedown', () => { if (!TV) document.body.classList.remove('kb'); }, true);
document.addEventListener('focusin', (e) => { const f = e.target.closest && e.target.closest('.f'); if (f && f !== NAV.cur && /^(INPUT|TEXTAREA|SELECT)$/.test(f.tagName)) { if (NAV.cur) NAV.cur.classList.remove('foc'); NAV.cur = f; f.classList.add('foc'); } });

// ------------------------------------------------------------------ Dialoge
const UI = {
  open(html, cls, onClose) {
    const prev = NAV.cur;
    const ov = h('div', 'ov'), d = h('div', 'dlg ' + (cls || ''), html); ov.appendChild(d); $('#layer').appendChild(ov);
    ov.addEventListener('click', (e) => { if (e.target === ov) UI.close(ov); });
    ov._close = () => { ov.remove(); if (onClose) onClose(); if (prev && prev.isConnected) NAV.set(prev, true); else NAV.first(); };
    setTimeout(() => NAV.first(d), 0);
    return d;
  },
  close(ov) { ov = ov || $$('#layer .ov').pop(); if (ov) ov._close(); },
  top() { return $$('#layer .ov').length > 0; },
  /** Auswahl: items = [{t, i(icon), v, danger, sub}] → Promise<v|null> */
  choose(title, items, sub) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2>' + (sub ? '<p>' + sub + '</p>' : '') + '<div class="opts"></div>', '', () => { if (!done) res(null); });
      const box = $('.opts', d);
      for (const it of items) {
        const b = h('button', 'opt f' + (it.danger ? ' danger' : ''), (it.i ? ico(it.i) : '') + '<span>' + esc(it.t) + (it.sub ? '<br><small class="mut">' + esc(it.sub) + '</small>' : '') + '</span>');
        b.onclick = () => { done = true; UI.close(); res(it.v); }; box.appendChild(b);
      }
    });
  },
  confirm(title, text, yes = 'Ja', danger) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2><p>' + (text || '') + '</p><div class="btns"><button class="btn f no">Abbrechen</button><button class="btn pri f yes' + (danger ? ' danger' : '') + '">' + esc(yes) + '</button></div>', '', () => { if (!done) res(false); });
      $('.no', d).onclick = () => UI.close();
      $('.yes', d).onclick = () => { done = true; UI.close(); res(true); };
      setTimeout(() => NAV.set($('.yes', d), true), 0);
    });
  },
  prompt(title, val, opt = {}) {
    return new Promise((res) => {
      let done = false;
      const d = UI.open('<h2>' + esc(title) + '</h2>' + (opt.text ? '<p>' + opt.text + '</p>' : '') + (opt.area ? '<textarea class="in f" rows="5"></textarea>' : '<input class="in f" type="' + (opt.type || 'text') + '">') + '<div class="btns"><button class="btn f no">Abbrechen</button><button class="btn pri f yes">' + esc(opt.ok || 'OK') + '</button></div>', '', () => { if (!done) res(null); });
      const inp = $('.in', d); inp.value = val || ''; if (opt.ph) inp.placeholder = opt.ph;
      const ok = () => { done = true; const v = inp.value; UI.close(); res(v); };
      inp._enter = ok; if (!opt.area) inp.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !TV) ok(); });
      $('.no', d).onclick = () => UI.close(); $('.yes', d).onclick = ok;
      setTimeout(() => { NAV.set(inp, true); if (!TV) inp.focus(); }, 30);
    });
  },
};

