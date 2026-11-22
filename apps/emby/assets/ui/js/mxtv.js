// midnox Media – midnox TV: Fernsehsender aus der eigenen Emby-/Jellyfin-Sammlung.
// Jeder Kanal hat einen festen Wochenplan (gleicher Zufall für alle Geräte) – man schaltet ein und es läuft schon etwas.
'use strict';
const MXG = 'midnox TV';
const MXTV = {
  ch: [], byId: {}, at0: 0, building: false,
  DEFS: [
    { key: 'serien', name: 'Serien-Marathon', emoji: '📺', type: 'Episode', mode: 'series' },
    { key: 'sitcom', name: 'Sitcom-Kanal', emoji: '😂', type: 'Episode', mode: 'series', sgenres: ['Comedy', 'Komödie', 'Sitcom'] },
    { key: 'kino', name: 'Filmabend', emoji: '🎬', type: 'Movie' },
    { key: 'action', name: 'Action Nonstop', emoji: '💥', type: 'Movie', genres: ['Action', 'Abenteuer', 'Adventure'] },
    { key: 'lachen', name: 'Lachkanal', emoji: '🤣', type: 'Movie', genres: ['Komödie', 'Comedy'] },
    { key: 'spannung', name: 'Spannung', emoji: '🔪', type: 'Movie', genres: ['Thriller', 'Krimi', 'Crime', 'Mystery'] },
    { key: 'grusel', name: 'Gänsehaut', emoji: '👻', type: 'Movie', genres: ['Horror'] },
    { key: 'scifi', name: 'Sci-Fi & Fantasy', emoji: '🚀', type: 'Movie', genres: ['Science Fiction', 'Sci-Fi', 'Fantasy'] },
    { key: 'herz', name: 'Herzkino', emoji: '💕', type: 'Movie', genres: ['Liebesfilm', 'Romance', 'Romanze'] },
    { key: 'familie', name: 'Familienkino', emoji: '🧸', type: 'Movie', genres: ['Familie', 'Family', 'Animation', 'Kinder', 'Kids'] },
    { key: 'doku', name: 'Doku', emoji: '🌍', type: 'Movie,Episode', genres: ['Dokumentation', 'Documentary', 'Doku'] },
    { key: 'retro', name: '80er & 90er Kino', emoji: '📼', type: 'Movie', years: [1980, 1999] },
  ],
  MIN_ITEMS: 5, MIN_HOURS: 6, MIN_MOVIE: 20 * 60e3, MIN_EP: 5 * 60e3,

  /** Kanäle aus der Bibliothek zusammenstellen (einmal am Tag, im Speicher des Geräts). */
  async init(force) {
    if (!EMBY.user || MXTV.building) return;
    const ck = 'mxtv_' + EMBY.user.id;
    if (!force) { try { const c = JSON.parse(localStorage.getItem(ck) || 'null'); if (c && Date.now() - c.at < 20 * 3600e3) { MXTV.set(c.ch); return; } } catch (e) {} }
    MXTV.building = true;
    try {
      const f = { Recursive: true, Fields: 'Genres,ProductionYear,RunTimeTicks,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,Overview', EnableImageTypes: 'Primary', ImageTypeLimit: 1, EnableUserData: false };
      const [mv, ep, se] = await Promise.all([
        EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Movie', Limit: 4000 }, f)).catch(() => ({})),
        EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Episode', Limit: 12000, SortBy: 'SeriesSortName,ParentIndexNumber,IndexNumber' }, f)).catch(() => ({})),
        EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'Series', Recursive: true, Fields: 'Genres', Limit: 4000 }).catch(() => ({})),
      ]);
      const sg = {}; for (const s of (se.Items || [])) sg[s.Id] = s.Genres || [];
      const slim = (i) => ({ i: i.Id, n: i.Name, s: i.Type === 'Episode' ? (i.SeriesName || '') + ' · S' + (i.ParentIndexNumber || 0) + 'E' + (i.IndexNumber || 0) : (i.ProductionYear ? String(i.ProductionYear) : ''), o: (i.Overview || '').slice(0, 220), d: Math.round((i.RunTimeTicks || 0) / 10000), g: i.Type === 'Episode' ? i.SeriesId : '' });
      const movies = (mv.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_MOVIE * 1e4), eps = (ep.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_EP * 1e4);
      const hasG = (gs, want) => (gs || []).some(g => want.some(w => g.toLowerCase().indexOf(w.toLowerCase()) >= 0));
      const out = [];
      for (const d of MXTV.DEFS) {
        let pool = [];
        if (d.type.indexOf('Movie') >= 0) pool = pool.concat(movies.filter(i => (!d.genres || hasG(i.Genres, d.genres)) && (!d.years || (i.ProductionYear >= d.years[0] && i.ProductionYear <= d.years[1]))));
        if (d.type.indexOf('Episode') >= 0) pool = pool.concat(eps.filter(i => (!d.genres || hasG(i.Genres && i.Genres.length ? i.Genres : sg[i.SeriesId], d.genres)) && (!d.sgenres || hasG(sg[i.SeriesId], d.sgenres))));
        const total = pool.reduce((a, i) => a + i.RunTimeTicks / 10000, 0);
        if (pool.length < MXTV.MIN_ITEMS || total < MXTV.MIN_HOURS * 3600e3) continue;
        if (d.key === 'familie' && out.some(c => c.key === 'kino') && pool.length > movies.length * 0.8) continue;   // kein Doppel
        out.push({ key: d.key, name: d.name, emoji: d.emoji, mode: d.mode || '', items: pool.map(slim) });
      }
      // „Filmabend“ weglassen, wenn es nur einen Filmkanal gäbe, der identisch wäre
      MXTV.set(out);
      try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out })); }   // große Sammlungen: Speicher kann voll sein
      catch (e) { try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out.map(c => Object.assign({}, c, { items: c.items.map(i => Object.assign({}, i, { o: '' })) })) })); } catch (x) {} }
      if (window.LIVE) { LIVE.load(); LIVE.refresh(); }
    } finally { MXTV.building = false; }
  },
  ver: 0,
  set(list) {
    MXTV.ver++;
    MXTV.ch = list.map((c, k) => Object.assign({ id: 'mx:' + c.key, num: '', group: MXG, logos: [], src: 'mx' }, c));
    MXTV.byId = {}; for (const c of MXTV.ch) { MXTV.byId[c.id] = c; c._plan = null; }
  },
  // ---- Fester Wochenplan ----
  week(t) { const d = new Date(t); d.setHours(0, 0, 0, 0); const wd = (d.getDay() + 6) % 7; d.setDate(d.getDate() - wd); return d.getTime(); },
  rng(seed) { let s = seed >>> 0 || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; },
  hash(str) { let x = 2166136261; for (let i = 0; i < str.length; i++) { x ^= str.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; },
  plan(c, wk) {
    if (c._plan && c._plan.wk === wk) return c._plan;
    const r = MXTV.rng(MXTV.hash(c.key + ':' + wk));
    let seq;
    if (c.mode === 'series') {   // Serienblöcke: Serien gemischt, Folgen in Reihenfolge, je Block 3 Folgen
      const by = {}; const order = [];
      for (const it of c.items) { if (!by[it.g]) { by[it.g] = []; order.push(it.g); } by[it.g].push(it); }
      for (let i = order.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
      const pos = {}; for (const g of order) pos[g] = Math.floor(r() * by[g].length);
      seq = []; let guard = 0;
      while (seq.length < c.items.length && guard++ < 100000) for (const g of order) { for (let k = 0; k < 3; k++) { seq.push(by[g][pos[g] % by[g].length]); pos[g]++; } if (seq.length >= c.items.length) break; }
    } else {
      seq = c.items.slice();
      for (let i = seq.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [seq[i], seq[j]] = [seq[j], seq[i]]; }
    }
    const starts = []; let t = 0; for (const it of seq) { starts.push(t); t += it.d; }
    c._plan = { wk, seq, starts, total: t };
    return c._plan;
  },
  /** Was läuft auf Kanal c zur Zeit t? → { it, start, stop } */
  at(c, t) {
    const wk = MXTV.week(t), p = MXTV.plan(c, wk); if (!p.total) return null;
    const off = (t - wk) % p.total;
    let lo = 0, hi = p.starts.length - 1;
    while (lo < hi) { const m = (lo + hi + 1) >> 1; if (p.starts[m] <= off) lo = m; else hi = m - 1; }
    const base = t - off; const it = p.seq[lo];
    return { it, start: base + p.starts[lo], stop: base + p.starts[lo] + it.d };
  },
  progs(id, from, to) {
    const c = MXTV.byId[id]; if (!c) return [];
    const out = []; let t = from, guard = 0;
    while (t < to && guard++ < 4000) { const a = MXTV.at(c, t); if (!a) break; out.push([a.start, a.stop, a.it.n, (a.it.s ? a.it.s + ' – ' : '') + a.it.o]); t = a.stop + 1; }
    return out;
  },
  // ---- Abspielen (über den Live-Player) ----
  async tune(id, t) {
    const c = MXTV.byId[id]; if (!c) throw new Error('Kanal nicht gefunden');
    const now = Math.max(Date.now(), t || 0), a = MXTV.at(c, now); if (!a) throw new Error('Kanal ist leer');
    const off = Math.max(0, now - a.start);
    const item = { Id: a.it.i, MediaType: 'Video', RunTimeTicks: a.it.d * 10000 };
    const info = await EMBY.playbackInfo(item, { startMs: off });
    return { info, off, stop: a.stop, it: a.it };
  },
};
window.MXTV = MXTV;
