'use strict';
// midnox Media – midnox TV wie echtes Fernsehen: Senderlogo, „Gleich:“-Einblendung, Trenner zwischen Sendungen,
// Programmvorschau vor 20:15, Jugendschutz-Sperre tagsüber und die Infoseite („Videotext“) zur laufenden Sendung.

const MXSHOW = {
  /** Fläche über dem Video (unter der Senderanzeige) */
  layer() {
    let l = $('#mxlayer');
    if (!l) { l = h('div', ''); l.id = 'mxlayer'; document.body.appendChild(l); }
    return l;
  },
  clear() { const l = $('#mxlayer'); if (l) l.innerHTML = ''; clearInterval(MXSHOW._cd); clearTimeout(MXSHOW._lt); MXSHOW.bug(false); },
  chan() { return LIVE.byId[TVP.id] || {}; },
  hm: (t) => { const d = new Date(t); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); },
  backdrop(id, w) { return id ? EMBY.api + '/Items/' + id + '/Images/Backdrop?maxWidth=' + (w || 1280) + '&quality=80' : ''; },
  poster(id, w) { return id ? EMBY.api + '/Items/' + id + '/Images/Primary?maxWidth=' + (w || 400) + '&quality=85' : ''; },

  /** dezentes Senderlogo oben rechts, solange eine midnox-Sendung läuft */
  bug(on) {
    let b = $('#mxbug');
    if (!on) { if (b) b.remove(); return; }
    const c = MXSHOW.chan();
    if (!b) { b = h('div', ''); b.id = 'mxbug'; document.body.appendChild(b); }
    b.innerHTML = '<span class="e">' + (c.emoji || '📺') + '</span><span class="n">' + esc(c.name || 'midnox TV') + '</span>';
  },

  /** Trenner zwischen zwei Sendungen: „Sie sehen … · Als Nächstes …“ */
  ident(title, sub) {
    const c = MXSHOW.chan(), l = MXSHOW.layer();
    l.innerHTML = '<div class="mxident"><div class="logo">' + (c.emoji || '📺') + '</div><div class="ch">' + esc(c.name || 'midnox TV') + '</div>' +
      (title ? '<div class="kick">' + esc(sub || 'Als Nächstes') + '</div><div class="t">' + esc(title) + '</div>' : '') + '<div class="brand">midnox TV</div></div>';
  },

  /** Programmvorschau in der Lücke vor dem 20:15-Film – mit Countdown und Hintergrundbild */
  preview(r) {
    const c = MXSHOW.chan(), l = MXSHOW.layer(); let nx = r.next || {};
    if (nx.pause) { const a2 = MXTV.at(MXTV.byId[TVP.id], r.stop + nx.d + 1000); if (a2) nx = a2.it; }
    const later = MXTV.progs(TVP.id, r.stop + 1000, r.stop + 6 * 3600e3).slice(0, 3);
    l.innerHTML = '<div class="mxprev">' + (nx.i ? '<div class="bg" style="background-image:url(\'' + MXSHOW.backdrop(nx.i) + '\')"></div>' : '') +
      '<div class="in"><div class="ch">' + (c.emoji || '📺') + ' ' + esc(c.name || '') + '</div><div class="kick">' + (r.next && MXTV.at(MXTV.byId[TVP.id], r.stop + 1000).prime ? 'Heute um 20:15' : 'Gleich') + '</div>' +
      '<div class="t">' + esc(nx.n || '') + '</div><div class="s">' + esc(nx.s || '') + '</div><div class="cd"></div>' +
      '<div class="later">' + later.slice(1).map(p => '<div><b>' + MXSHOW.hm(p[0]) + '</b> ' + esc(p[2]) + '</div>').join('') + '</div></div></div>';
    const cd = $('.cd', l);
    const tick = () => { const s = Math.max(0, Math.round((r.stop - Date.now()) / 1000)); cd.textContent = 'noch ' + Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
    tick(); clearInterval(MXSHOW._cd); MXSHOW._cd = setInterval(tick, 1000);
  },

  /** Jugendschutz: Sendung ist tagsüber gesperrt */
  locked(r) {
    const c = MXSHOW.chan(), l = MXSHOW.layer(), it = r.it || {};
    l.innerHTML = '<div class="mxlock"><div class="age">' + (it.r >= 18 ? '18' : '16') + '</div><div class="t">' + esc(it.n || '') + '</div>' +
      '<p>Diese Sendung ist ab ' + (it.r >= 18 ? '18' : '16') + ' Jahren freigegeben und läuft tagsüber nur mit PIN.<br>Ohne PIN ab ' + (it.r >= 18 ? '23' : '22') + ' Uhr frei.</p>' +
      '<div class="ok">OK drücken für PIN</div><div class="s">' + esc(c.name || '') + ' · bis ' + MXSHOW.hm(r.stop) + ' · danach: ' + esc((r.next && r.next.n) || '') + '</div></div>';
  },
  async unlock() {
    const S = EMBY.settings(), pin = S.fskPin || (window.PROFILES && PROFILES.parentPin && PROFILES.parentPin());
    if (!pin) { UI.toast('Noch keine Jugendschutz-PIN festgelegt – Einstellungen → Live-TV', { icon: '🔞' }); return; }
    if (await PIN.check(pin, 'Jugendschutz-PIN', 'Für 3 Stunden freischalten')) { MXTV.unlockUntil = Date.now() + 3 * 3600e3; TVP.tune(TVP.id); }
  },

  /** „Gleich:“-Einblendung unten links, zwei Minuten vor Ende */
  soon(r) {
    if (!r || !r.next || MXSHOW._soonFor === r.stop) return;
    MXSHOW._soonFor = r.stop;
    let s = $('#mxsoon'); if (!s) { s = h('div', ''); s.id = 'mxsoon'; document.body.appendChild(s); }
    const nx = r.next.pause ? 'Programmvorschau' : r.next.n;
    s.innerHTML = '<span class="k">Gleich</span><span class="t">' + esc(nx) + '</span>';
    s.classList.add('on'); clearTimeout(MXSHOW._st); MXSHOW._st = setTimeout(() => s.classList.remove('on'), 12000);
  },

  // ---------------------------------------------------------------- Infoseite („Videotext“)
  /** Nächste Sendetermine eines Titels auf allen midnox-Kanälen (7 Tage) */
  airings(itemId, max) {
    const out = [], now = Date.now();
    for (const c of MXTV.ch) {
      if (!c.items.some(i => i.i === itemId)) continue;
      for (const p of MXTV.progs(c.id, now - 3 * 3600e3, now + 7 * 864e5)) if (p[4] && p[4].id === itemId && p[1] > now) out.push({ c, start: p[0], stop: p[1], prime: p[4].prime });
    }
    return out.sort((a, b) => a.start - b.start).slice(0, max || 8);
  },
  /** Andere Titel aus derselben Reihe (Filmreihen-Kanal oder Emby-Sammlung) */
  series(itemId) {
    const c = MXTV.ch.find(x => x.group === MXF && x.items.some(i => i.i === itemId));
    return c ? { c, items: c.items.filter(i => i.i !== itemId) } : null;
  },
  async info(itemId, ctx) {
    let it; try { it = await EMBY.get('/Users/' + EMBY.user.id + '/Items/' + itemId); } catch (e) { UI.toast('Infos nicht abrufbar', { icon: '⚠️' }); return; }
    const rows = MXSHOW.airings(itemId), fr = MXSHOW.series(itemId);
    const cast = (it.People || []).filter(p => p.Type === 'Actor').slice(0, 10), dir = (it.People || []).filter(p => p.Type === 'Director').map(p => p.Name);
    const leave = (fn) => { UI.closeAll ? UI.closeAll() : $$('#layer .modal').forEach(m => m._close && m._close()); TVP.stop(true); fn(); };
    UI.modal((box, close) => {
      box.classList.add('mxinfo');
      const top = h('div', 'top');
      top.innerHTML = '<div class="po" style="background-image:url(\'' + MXSHOW.poster(it.Type === 'Episode' ? (it.SeriesId || it.Id) : it.Id, 500) + '\')"></div><div class="meta">' +
        (ctx && ctx.chan ? '<div class="kick">' + esc(ctx.chan) + (ctx.when ? ' · ' + esc(ctx.when) : '') + '</div>' : '') +
        '<h2>' + esc(it.Type === 'Episode' ? (it.SeriesName + ': ' + it.Name) : it.Name) + '</h2>' +
        '<div class="facts">' + [it.ProductionYear, it.RunTimeTicks ? Math.round(it.RunTimeTicks / 6e8) + ' Min.' : '', it.OfficialRating ? 'FSK ' + String(it.OfficialRating).replace(/^(FSK|DE)[- ]?/i, '') : '', it.CommunityRating ? '★ ' + it.CommunityRating.toFixed(1) : '', (it.Genres || []).slice(0, 3).join(', ')].filter(Boolean).map(x => '<span>' + esc(String(x)) + '</span>').join('') + '</div>' +
        (it.Taglines && it.Taglines[0] ? '<div class="tag">„' + esc(it.Taglines[0]) + '“</div>' : '') +
        '<p class="ov">' + esc(it.Overview || 'Keine Beschreibung vorhanden.') + '</p>' +
        (dir.length ? '<div class="small">Regie: ' + esc(dir.join(', ')) + '</div>' : '') + (it.Studios && it.Studios.length ? '<div class="small">Studio: ' + esc(it.Studios.slice(0, 3).map(s => s.Name).join(', ')) + '</div>' : '') + '</div>';
      box.appendChild(top);
      const btns = h('div', 'btns');
      if (it.LocalTrailerCount || (it.RemoteTrailers || []).length) btns.appendChild(onOk(h('div', 'btn pri', ic('trailer') + 'Trailer'), () => leave(() => APP.trailer(it))));
      btns.appendChild(onOk(h('div', 'btn', ic('play') + 'Von vorn ansehen'), () => leave(() => ROUTER.go('detail', { id: it.Id }))));
      btns.appendChild(onOk(h('div', 'btn', '✕ Schließen'), () => close()));
      box.appendChild(btns);
      if (rows.length) {
        const sec = h('div', 'sec', '<h3>Nächste Sendetermine</h3>'); const l = h('div', 'air');
        for (const a of rows) {
          const d = new Date(a.start), day = LIVE.dayName ? LIVE.dayName(a.start) : ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'][d.getDay()];
          const e = h('div', 'ai f', '<b>' + esc(day) + ' ' + MXSHOW.hm(a.start) + (a.prime ? ' ★' : '') + '</b><span>' + (a.c.emoji || '') + ' ' + esc(a.c.name) + '</span><small>' + (REMIND.has(a.c.id, a.start) ? '⏰ Erinnerung an' : 'OK = erinnern') + '</small>');
          onOk(e, () => { REMIND.toggle(a.c.id, a.start, it.Name); $('small', e).textContent = REMIND.has(a.c.id, a.start) ? '⏰ Erinnerung an' : 'OK = erinnern'; });
          l.appendChild(e);
        }
        sec.appendChild(l); box.appendChild(sec);
      }
      if (cast.length) {
        const sec = h('div', 'sec', '<h3>Mit dabei</h3>'); const l = h('div', 'cast');
        for (const p of cast) {
          const e = h('div', 'pp f', '<div class="ph" style="background-image:url(\'' + (p.PrimaryImageTag ? EMBY.api + '/Items/' + p.Id + '/Images/Primary?maxWidth=200&tag=' + p.PrimaryImageTag : '') + '\')">' + (p.PrimaryImageTag ? '' : esc((p.Name || '?')[0])) + '</div><b>' + esc(p.Name) + '</b><small>' + esc(p.Role || '') + '</small>');
          onOk(e, () => leave(() => ROUTER.go('person', { id: p.Id, name: p.Name })));
          l.appendChild(e);
        }
        sec.appendChild(l); box.appendChild(sec);
      }
      if (fr && fr.items.length) {
        const sec = h('div', 'sec', '<h3>' + (fr.c.emoji || '🎞️') + ' Mehr aus der Reihe „' + esc(fr.c.name) + '“</h3>'); const l = h('div', 'cast');
        for (const x of fr.items.slice(0, 20)) {
          const e = h('div', 'pp f film', '<div class="ph" style="background-image:url(\'' + MXSHOW.poster(x.i, 240) + '\')"></div><b>' + esc(x.n) + '</b><small>' + esc(x.s || '') + '</small>');
          onOk(e, () => { close(); setTimeout(() => MXSHOW.info(x.i, { chan: fr.c.name }), 50); });
          l.appendChild(e);
        }
        sec.appendChild(l); box.appendChild(sec);
      }
      setTimeout(() => NAV.focus($('.btn', btns)), 0);
    });
  },
};
window.MXSHOW = MXSHOW;

/** Erinnerungen an Sendungen auf midnox TV: kurz vor Beginn meldet sich die App */
const REMIND = {
  list() { try { return JSON.parse(localStorage.getItem('mx_remind') || '[]').filter(r => r.t > Date.now() - 600e3); } catch (e) { return []; } },
  save(l) { localStorage.setItem('mx_remind', JSON.stringify(l)); },
  has(cid, t) { return REMIND.list().some(r => r.c === cid && r.t === t); },
  toggle(cid, t, title) {
    const l = REMIND.list(), i = l.findIndex(r => r.c === cid && r.t === t);
    if (i >= 0) { l.splice(i, 1); UI.toast('Erinnerung gelöscht', { icon: '⏰' }); }
    else { l.push({ c: cid, t, n: title }); UI.toast('Ich erinnere dich um ' + MXSHOW.hm(t - 120e3), { icon: '⏰' }); }
    REMIND.save(l);
  },
  check() {
    const l = REMIND.list(), now = Date.now(); let ch = false;
    for (const r of l) {
      if (r.done || now < r.t - 120e3) continue;
      r.done = true; ch = true;
      const c = LIVE.byId[r.c];
      UI.modal((box, close) => {
        box.appendChild(h('h2', '', '⏰ Gleich: ' + esc(r.n)));
        box.appendChild(h('p', '', 'Um ' + MXSHOW.hm(r.t) + ' auf ' + esc(c ? c.name : 'midnox TV')));
        const b = h('div', 'btns');
        b.appendChild(onOk(h('div', 'btn pri', 'Jetzt einschalten'), () => { close(); if (window.PLAYER && PLAYER.active) PLAYER.stop('user'); TVP.play(r.c, LIVE.ch.filter(x => x.group === (LIVE.byId[r.c] || {}).group).map(x => x.id)); }));
        b.appendChild(onOk(h('div', 'btn', 'Später'), () => close()));
        box.appendChild(b); setTimeout(() => NAV.focus($('.btn', b)), 0);
      });
    }
    if (ch) REMIND.save(l);
  },
};
window.REMIND = REMIND;
setInterval(() => { if (window.EMBY && EMBY.user && window.MXTV && MXTV.ch.length) REMIND.check(); }, 20000);
