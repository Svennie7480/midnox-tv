// midnox Media – Senioren-Modus: große Schrift, wenige große Kacheln, Direktstart ins Fernsehen, freundliche Hinweise.
'use strict';

const SENIOR = {
  lvl() { if (window.KIDS && KIDS.on) return 0; const v = EMBY.settings().senior; return window.AUTO && v === undefined ? 1 : (+v || 0); },
  on() { return SENIOR.lvl() > 0; },
  /** Schriftgröße/Kontrast: Klassen auf <html> und <body> */
  apply() {
    const l = SENIOR.lvl();
    for (const el of [document.documentElement, document.body]) { el.classList.toggle('senior', l > 0); el.classList.toggle('senior2', l > 1); }
  },
  /** normaler Text bzw. einfache Sprache im Senioren-Modus */
  t(normal, easy) { return SENIOR.on() ? easy : normal; },
  /** Menü: nur das Wichtigste */
  rail() { return [['seniorhome', 'Start', 'home'], ['live', 'Live-TV', 'live'], ['movies', 'Filme', 'film'], ['shows', 'Serien', 'tv']]; },
  greet() { const h0 = new Date().getHours(); return h0 < 5 ? 'Gute Nacht' : h0 < 11 ? 'Guten Morgen' : h0 < 18 ? 'Guten Tag' : 'Guten Abend'; },
  clock() {
    const d = new Date();
    return { time: d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }), date: d.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' }) };
  },
  /** Fernsehen starten: zuletzt gesehener Sender (sonst erster Favorit, sonst erster Sender) */
  tv() {
    if (!window.LIVE || !LIVE.enabled()) { UI.toast('Fernsehen ist nicht eingerichtet.', { icon: '📺' }); return false; }
    LIVE.open();
    if (!LIVE.ch.length) { ROUTER.go('live'); return false; }
    const favs = LIVE.favs.map(id => LIVE.byId[id]).filter(Boolean);
    const list = favs.length ? favs : LIVE.ch;
    const last = LIVE.last(); const c = (last && LIVE.byId[last]) || list[0];
    TVP.play(c.id, list.map(x => x.id)); return true;
  },
  /** Beim Start gleich fernsehen – wartet kurz, bis die Senderliste da ist */
  autostart() {
    if (window.AUTO) return;
    const s = EMBY.settings(); if (!SENIOR.on() || s.seniorStart === 'home' || !window.LIVE || !LIVE.enabled()) return;
    LIVE.open(); let n = 0;
    const t = setInterval(() => {
      const top = ROUTER.top();
      if (++n > 40 || !top || top.name !== 'seniorhome' || (window.TVP && TVP.active) || $$('#layer .modal').length) { clearInterval(t); return; }
      if (LIVE.ch.length) { clearInterval(t); SENIOR.tv(); }
    }, 300);
  },
  /** Einstellungen nur für Helfer: mit PIN, falls eine festgelegt ist */
  async helper() {
    const s = EMBY.settings(); const pin = s.fskPin || (window.PROFILES && PROFILES.parentPin());
    if (pin && !(await PIN.check(pin, 'PIN für Helfer', 'Die Einstellungen sind für Angehörige gedacht.'))) return;
    ROUTER.go('settings');
  },
};
window.SENIOR = SENIOR;

ROUTER.reg('seniorhome', async (scr) => {
  BG.set(null); BG.dim(false);
  const el = scr.el; el.className = 'page seniorhome'; el.innerHTML = '';
  const head = h('div', 'shead'); el.appendChild(head);
  const draw = () => { const c = SENIOR.clock(); head.innerHTML = '<div><h1>' + SENIOR.greet() + ', ' + esc(EMBY.user.name) + '!</h1><p>' + esc(c.date) + '</p></div><div class="sclock">' + esc(c.time) + '</div>'; };
  draw(); const tick = setInterval(() => { if (ROUTER.top() === scr) draw(); }, 20000); scr.destroy = () => clearInterval(tick);
  const grid = h('div', 'stiles'); grid.setAttribute('data-mem', ''); el.appendChild(grid);
  const tile = (emo, title, sub, fn, cls) => { const t = h('div', 'stile ' + (cls || ''), '<div class="emo">' + emo + '</div><b>' + esc(title) + '</b><small>' + esc(sub || '') + '</small>'); onOk(t, fn); grid.appendChild(t); return t; };
  let lastName = ''; try { const l = window.LIVE && LIVE.last(); const c = l && LIVE.byId[l]; if (c) lastName = c.name; } catch (e) {}
  const tvOn = window.LIVE && LIVE.enabled();
  let first = null;
  if (tvOn) first = tile('📺', 'Fernsehen', lastName ? 'Zuletzt: ' + lastName : 'Live-Fernsehen', () => SENIOR.tv(), 'tv');
  // Weiterschauen: den zuletzt angefangenen Film bzw. die nächste Folge anbieten
  const slot = tile('▶️', 'Weiterschauen', 'Wird gesucht …', () => {}, 'cont hid'); slot.classList.remove('f'); 
  tile('🎬', 'Filme', 'Alle Filme', () => ROUTER.go('movies'), 'film');
  tile('🍿', 'Serien', 'Alle Serien', () => ROUTER.go('shows'), 'show');
  if (EMBY.hasMusic) tile('🎵', 'Musik', 'Lieder und Alben', () => ROUTER.go('music'), 'mus');
  const help = onOk(h('div', 'shelp', '⚙️ Einstellungen (für Helfer)'), () => SENIOR.helper()); el.appendChild(help);
  scr.initial = first || $('.stile.f', grid); NAV.focus(scr.initial);
  scr.enter = () => BG.dim(false);
  try {
    const [r, n] = await Promise.all([EMBY.resume().catch(() => ({ Items: [] })), EMBY.nextUp().catch(() => ({ Items: [] }))]);
    const it = (r.Items || [])[0] || (n.Items || [])[0];
    if (it && ROUTER.top() === scr) {
      const name = it.Type === 'Episode' ? (it.SeriesName || '') + ' – S' + (it.ParentIndexNumber || 0) + ' E' + (it.IndexNumber || 0) : it.Name;
      slot.classList.remove('hid'); slot.classList.add('f'); $('small', slot).textContent = name; slot._ok = () => APP.play(it);
      const bg = EMBY.img(it, 'Backdrop', 640) || EMBY.img(it, 'Primary', 640); if (bg) slot.style.backgroundImage = 'linear-gradient(rgba(10,10,20,.62),rgba(10,10,20,.82)),url("' + bg + '")';
      if (!tvOn || !scr.initial || scr.initial === first) { /* Fokus bleibt */ }
    } else slot.remove();
  } catch (e) { slot.remove(); }
});
