// midnox Media – midnox TV: Fernsehsender aus der eigenen Emby-/Jellyfin-Sammlung.
// Jeder Kanal hat einen festen Wochenplan (gleicher Zufall für alle Geräte) – man schaltet ein und es läuft schon etwas.
'use strict';
const MXG = 'midnox TV', MXS = 'midnox Serien', MXA = 'midnox Stars';
const RAW = /\.S\d{1,2}E\d{1,3}\.|\b(1080p|720p|2160p|x26[45]|WEB-?DL|BluRay|AC3D?)\b/i;   // Dateiname statt Titel
const MXTV = {
  ch: [], byId: {}, at0: 0, building: false,
  DEFS: [
    { key: 'serien', name: 'Serien-Marathon', emoji: '📺', type: 'Episode', mode: 'series' },
    { key: 'sitcom', name: 'Sitcom-Kanal', emoji: '😂', type: 'Episode', mode: 'series', sgenres: ['Comedy', 'Komödie', 'Sitcom'] },
    { key: 'kino', name: 'Filmabend', emoji: '🎬', type: 'Movie' },
    { key: 'action', name: 'Action Nonstop', emoji: '💥', type: 'Movie', genres: ['Action', 'Abenteuer', 'Adventure'] },
    { key: 'actionklassik', name: 'Action-Klassiker', emoji: '🔫', type: 'Movie', genres: ['Action'], years: [1970, 1999] },
    { key: 'blockbuster', name: 'Blockbuster-Action', emoji: '💣', type: 'Movie', genres: ['Action'], years: [2010, 2100] },
    { key: 'actionthrill', name: 'Action-Thriller', emoji: '🚨', type: 'Movie', allG: [['Action'], ['Thriller', 'Krimi', 'Crime']] },
    { key: 'actionfun', name: 'Action-Komödie', emoji: '😎', type: 'Movie', allG: [['Action'], ['Komödie', 'Comedy']] },
    { key: 'actionscifi', name: 'Sci-Fi-Action', emoji: '🤖', type: 'Movie', allG: [['Action'], ['Science Fiction', 'Sci-Fi']] },
    { key: 'actionserie', name: 'Action-Serien', emoji: '🧨', type: 'Episode', mode: 'series', sgenres: ['Action'] },
    { key: 'lachen', name: 'Lachkanal', emoji: '🤣', type: 'Movie', genres: ['Komödie', 'Comedy'] },
    { key: 'spannung', name: 'Spannung', emoji: '🔪', type: 'Movie', genres: ['Thriller', 'Krimi', 'Crime', 'Mystery'] },
    { key: 'grusel', name: 'Gänsehaut', emoji: '👻', type: 'Movie', genres: ['Horror'] },
    { key: 'horrorklassik', name: 'Horror-Klassiker', emoji: '🧛', type: 'Movie', genres: ['Horror'], years: [1900, 1999] },
    { key: 'horrorneu', name: 'Neuer Horror', emoji: '🩸', type: 'Movie', genres: ['Horror'], years: [2010, 2100] },
    { key: 'psycho', name: 'Psycho-Horror', emoji: '🧠', type: 'Movie', allG: [['Horror'], ['Thriller', 'Mystery', 'Psycho']] },
    { key: 'horrorlach', name: 'Horror zum Lachen', emoji: '🤡', type: 'Movie', allG: [['Horror'], ['Komödie', 'Comedy']] },
    { key: 'gruselserie', name: 'Grusel-Serien', emoji: '🕯️', type: 'Episode', mode: 'series', sgenres: ['Horror'] },
    { key: 'scifi', name: 'Sci-Fi & Fantasy', emoji: '🚀', type: 'Movie', genres: ['Science Fiction', 'Sci-Fi', 'Fantasy'] },
    { key: 'herz', name: 'Herzkino', emoji: '💕', type: 'Movie', genres: ['Liebesfilm', 'Romance', 'Romanze'] },
    { key: 'familie', name: 'Familienkino', emoji: '🧸', type: 'Movie', genres: ['Familie', 'Family', 'Animation', 'Kinder', 'Kids'] },
    { key: 'doku', name: 'Doku', emoji: '🌍', type: 'Movie,Episode', genres: ['Dokumentation', 'Documentary', 'Doku'] },
    { key: 'retro', name: '80er & 90er Kino', emoji: '📼', type: 'Movie', years: [1980, 1999] },
    // --- Jahreszeit (nur in der passenden Zeit auf Sendung) ---
    { key: 'xmas', name: 'Weihnachtskanal', emoji: '🎄', type: 'Movie,Episode', months: [11, 12, 1], title: /weihnacht|christmas|xmas|x-mas|nikolaus|santa|grinch|kevin allein|home alone|polar ?express|bescherung|love actually|tatsächlich liebe|\belf\b|klaus|advent|schneemann|snowman|rentier|holiday/i, genres: ['Weihnacht', 'Christmas', 'Holiday'] },
    { key: 'halloween', name: 'Halloween-Nacht', emoji: '🎃', type: 'Movie,Episode', months: [10], title: /halloween|hexe|witch|vampir|zombie|geist|ghost|spuk|haunt|kürbis/i, genres: ['Horror'] },
    // --- Serien nach Art ---
    { key: 'krimiserie', name: 'Krimi-Serien', emoji: '🕵️', type: 'Episode', mode: 'series', sgenres: ['Krimi', 'Crime', 'Mystery', 'Thriller'] },
    { key: 'dramaserie', name: 'Serien-Drama', emoji: '🎭', type: 'Episode', mode: 'series', sgenres: ['Drama'] },
    { key: 'animeserie', name: 'Anime & Trick', emoji: '🎌', type: 'Episode', mode: 'series', sgenres: ['Anime', 'Animation', 'Zeichentrick'] },
    { key: 'scifiserie', name: 'Serien aus anderen Welten', emoji: '🛸', type: 'Episode', mode: 'series', sgenres: ['Science Fiction', 'Sci-Fi', 'Fantasy'] },
    { key: 'dokuserie', name: 'Doku-Serien', emoji: '🔍', type: 'Episode', mode: 'series', sgenres: ['Dokumentation', 'Documentary', 'Reality'] },
    // --- Filme nach Genre ---
    { key: 'drama', name: 'Großes Drama', emoji: '🎭', type: 'Movie', genres: ['Drama'] },
    { key: 'trick', name: 'Trickfilm-Kanal', emoji: '🖍️', type: 'Movie', genres: ['Animation', 'Zeichentrick', 'Anime'] },
    { key: 'western', name: 'Western', emoji: '🤠', type: 'Movie', genres: ['Western'] },
    { key: 'westernklassik', name: 'Western-Klassiker', emoji: '🐎', type: 'Movie', genres: ['Western'], years: [1900, 1979] },
    { key: 'neowestern', name: 'Neo-Western', emoji: '🌵', type: 'Movie', genres: ['Western'], years: [1990, 2100] },
    { key: 'geschichte', name: 'Krieg & Geschichte', emoji: '⚔️', type: 'Movie', genres: ['Kriegsfilm', 'War', 'Krieg', 'Historie', 'History', 'Geschichte'] },
    { key: 'musik', name: 'Musik & Musical', emoji: '🎵', type: 'Movie', genres: ['Musik', 'Music', 'Musical'] },
    { key: 'helden', name: 'Superhelden', emoji: '🦸', type: 'Movie', studios: /marvel|dc (comics|entertainment|films)|dc studios/i, title: /spider|batman|superman|avengers|x-men|wonder woman|deadpool|iron man|thor\b|captain america|guardians of the galaxy|justice league|aquaman|black panther|doctor strange|ant-man/i },
    { key: 'disney', name: 'Disney & Pixar', emoji: '🏰', type: 'Movie', studios: /disney|pixar/i },
    // --- Filme nach Jahr & Länge ---
    { key: 'klassiker', name: 'Filmklassiker', emoji: '🎞️', type: 'Movie', years: [1900, 1979] },
    { key: 'nuller', name: '2000er-Kino', emoji: '💿', type: 'Movie', years: [2000, 2009] },
    { key: 'neu', name: 'Neues Kino', emoji: '✨', type: 'Movie', recentYears: 3 },
    { key: 'frisch', name: 'Frisch eingetroffen', emoji: '🆕', type: 'Movie,Episode', addedDays: 45 },
    { key: 'kurz', name: 'Kurz & knackig', emoji: '⏱️', type: 'Movie', maxMin: 95 },
    { key: 'epos', name: 'Überlänge', emoji: '🏔️', type: 'Movie', minMin: 150 },
    { key: 'zufall', name: 'Zufallskanal', emoji: '🎲', type: 'Movie,Episode' },
  ],
  MIN_ITEMS: 5, MIN_HOURS: 6, MIN_MOVIE: 20 * 60e3, MIN_EP: 5 * 60e3,

  /** Kanäle aus der Bibliothek zusammenstellen (einmal am Tag, im Speicher des Geräts). */
  MAX_SERIES: 10, MAX_STARS: 10, MIN_SER_EPS: 12, MIN_STAR: 6,
  /** Hauptdarsteller je Film (die ersten 5 der Besetzung) – höchstens einmal pro Woche von Emby geholt */
  async stars(movies) {
    const ck = 'mxstars_' + EMBY.user.id; let cast = null;
    try { const c = JSON.parse(localStorage.getItem(ck) || 'null'); if (c && Date.now() - c.at < 7 * 864e5) cast = c.cast; } catch (e) {}
    if (!cast) {
      cast = {};
      for (let start = 0; start < 6000; start += 500) {   // in Häppchen, damit Emby und das Gerät nicht ins Schwitzen kommen
        const r = await EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'Movie', Recursive: true, Fields: 'People', EnableImages: false, EnableUserData: false, StartIndex: start, Limit: 500 });
        for (const i of r.Items || []) cast[i.Id] = (i.People || []).filter(p => p.Type === 'Actor').slice(0, 5).map(p => p.Id + '|' + p.Name);
        if (!r.Items || r.Items.length < 500) break;
      }
      try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), cast })); } catch (e) {}
    }
    const acc = {}, byId = {}; for (const m of movies) byId[m.Id] = m;
    for (const mid in cast) { const m = byId[mid]; if (!m) continue; for (const pn of cast[mid]) { const k = pn.split('|')[0]; (acc[k] || (acc[k] = { id: k, name: pn.slice(k.length + 1), items: [] })).items.push(m); } }
    return Object.values(acc).filter(a => a.items.length >= MXTV.MIN_STAR && a.items.reduce((s, i) => s + i.RunTimeTicks / 10000, 0) >= MXTV.MIN_HOURS * 3600e3)
      .sort((a, b) => b.items.length - a.items.length).slice(0, MXTV.MAX_STARS);
  },
  /** nacheinander statt gleichzeitig – der Emby-Server bleibt für die Startseite frei */
  async seq(fns) { const out = []; for (const f of fns) out.push(await f()); return out; },
  async init(force) {
    if (!EMBY.user || MXTV.building) return;
    const ck = 'mxtv4_' + EMBY.user.id;   // 4: Serien- und Star-Kanäle
    if (!force) {
      try {
        const c = JSON.parse(localStorage.getItem(ck) || 'null');
        // vorhandenen Plan sofort nutzen; ist er älter als einen Tag, später in Ruhe neu bauen
        if (c && c.ch) { MXTV.set(c.ch); if (Date.now() - c.at > 24 * 3600e3) setTimeout(() => MXTV.init(true), 3 * 60e3); return; }
      } catch (e) {}
    }
    MXTV.building = true;
    try {
      const f = { Recursive: true, Fields: 'Genres,ProductionYear,RunTimeTicks,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,Studios,DateCreated', EnableImageTypes: 'Primary', ImageTypeLimit: 1, EnableUserData: false, EnableImages: false };   // ohne Beschreibungen: viel weniger Last für den Emby-Server
      const [mv, ep, se] = await MXTV.seq([
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Movie', Limit: 4000 }, f)).catch(() => ({})),
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Episode', Limit: 12000, SortBy: 'SeriesSortName,ParentIndexNumber,IndexNumber' }, f)).catch(() => ({})),
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'Series', Recursive: true, Fields: 'Genres,CommunityRating', EnableImages: false, Limit: 4000 }).catch(() => ({})),
      ]);
      const sg = {}; for (const s of (se.Items || [])) sg[s.Id] = s.Genres || [];
      const slim = (i) => ({ i: i.Id, n: MXTV.clean(i), s: i.Type === 'Episode' ? (i.SeriesName || '') + ' · S' + (i.ParentIndexNumber || 0) + 'E' + (i.IndexNumber || 0) : (i.ProductionYear ? String(i.ProductionYear) : ''), o: (i.Overview || '').slice(0, 220), d: Math.round((i.RunTimeTicks || 0) / 10000), g: i.Type === 'Episode' ? i.SeriesId : '' });
      const movies = (mv.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_MOVIE * 1e4), eps = (ep.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_EP * 1e4);
      const hasG = (gs, want) => (gs || []).some(g => want.some(w => g.toLowerCase().indexOf(w.toLowerCase()) >= 0));
      const out = [];
      const now = new Date(), month = now.getMonth() + 1, year = now.getFullYear();
      const studio = (i, re) => (i.Studios || []).some(x => re.test(x.Name || ''));
      const sname = {}; for (const s2 of (se.Items || [])) sname[s2.Id] = s2.Name || '';
      for (const d of MXTV.DEFS) {
        if (d.months && !d.months.includes(month)) continue;                                   // Jahreszeit-Kanäle nur in ihrer Zeit
        const movieOk = (i) => {
          const min = i.RunTimeTicks / 6e8;
          if (d.years && !(i.ProductionYear >= d.years[0] && i.ProductionYear <= d.years[1])) return false;
          if (d.recentYears && !(i.ProductionYear >= year - d.recentYears + 1)) return false;
          if (d.maxMin && min > d.maxMin) return false;
          if (d.minMin && min < d.minMin) return false;
          if (d.addedDays && !(i.DateCreated && now - new Date(i.DateCreated) < d.addedDays * 864e5)) return false;
          if (d.allG && !d.allG.every(g => hasG(i.Genres, g))) return false;                  // z. B. Action UND Komödie
          // Titel ODER Genre ODER Studio – was angegeben ist, reicht einzeln
          const any = [d.title && d.title.test(i.Name || ''), d.genres && hasG(i.Genres, d.genres), d.studios && studio(i, d.studios)].filter(x => x !== undefined);
          return !any.length || any.some(Boolean);
        };
        const epOk = (i) => {
          if (d.addedDays && !(i.DateCreated && now - new Date(i.DateCreated) < d.addedDays * 864e5)) return false;
          // Genre der Serie zählt (einzelne Folgen haben in Emby oft falsche oder fehlende Genres)
          const sgs = sg[i.SeriesId], gs = sgs && sgs.length ? sgs : (RAW.test(i.Name || '') ? [] : i.Genres);
          if (d.sgenres && !hasG(sg[i.SeriesId], d.sgenres)) return false;
          if (d.allG && !d.allG.every(g => hasG(gs, g))) return false;
          if (d.title) return d.title.test(i.Name || '') || d.title.test(sname[i.SeriesId] || '') || (d.key !== 'halloween' && !!d.genres && hasG(gs, d.genres));   // z. B. Weihnachtsfolgen
          return !d.genres || hasG(gs, d.genres);
        };
        if (d.studios && !d.title && d.type === 'Movie' && !movies.some(i => i.Studios)) continue;
        let pool = [];
        if (d.type.indexOf('Movie') >= 0) pool = pool.concat(movies.filter(movieOk));
        if (d.type.indexOf('Episode') >= 0) pool = pool.concat(eps.filter(epOk));
        const minItems = d.months ? 3 : MXTV.MIN_ITEMS, minH = d.months || d.addedDays ? 3 : MXTV.MIN_HOURS;
        const total = pool.reduce((a, i) => a + i.RunTimeTicks / 10000, 0);
        if (pool.length < minItems || total < minH * 3600e3) continue;
        // kein Doppel: Kanal weglassen, wenn er fast dasselbe zeigt wie ein schon vorhandener
        const ids = new Set(pool.map(i => i.Id));
        if (out.some(c => { let same = 0; for (const it of c.items) if (ids.has(it.i)) same++; return same >= ids.size * 0.85 && same >= c.items.length * 0.85; })) continue;
        out.push({ key: d.key, name: d.name, emoji: d.emoji, mode: d.mode || '', items: pool.map(slim) });
      }
      // ---- Beliebteste Serien rund um die Uhr: Favoriten, viel Gesehenes und gut Bewertetes zuerst ----
      const byS = {}; for (const i of eps) (byS[i.SeriesId] || (byS[i.SeriesId] = [])).push(i);
      const sScore = (x) => { const ud = x.UserData || {}, n = (byS[x.Id] || []).length, unpl = ud.UnplayedItemCount == null ? n : ud.UnplayedItemCount, seen = n ? Math.max(0, n - unpl) / n : 0;
        return (ud.IsFavorite ? 4 : 0) + (seen > 0 ? 2 + seen * 2 : 0) + (x.CommunityRating || 6) / 3 + Math.min(1, n / 100); };
      const topS = (se.Items || []).filter(x => (byS[x.Id] || []).length >= MXTV.MIN_SER_EPS && byS[x.Id].reduce((a, i) => a + i.RunTimeTicks / 10000, 0) >= MXTV.MIN_HOURS * 3600e3).sort((a, b) => sScore(b) - sScore(a)).slice(0, MXTV.MAX_SERIES);
      for (const x of topS) out.push({ key: 's-' + x.Id, name: x.Name + ' nonstop', emoji: '🔁', mode: 'series', grp: MXS, items: byS[x.Id].map(slim) });
      // ---- Schauspieler: Kino mit den Stars, die in deiner Sammlung am häufigsten vorkommen ----
      try {
        const stars = await MXTV.stars(movies);
        for (const a of stars) out.push({ key: 'a-' + a.id, name: a.name, emoji: '⭐', grp: MXA, items: a.items.map(slim) });
      } catch (e) {}
      // „Filmabend“ weglassen, wenn es nur einen Filmkanal gäbe, der identisch wäre
      MXTV.set(out);
      try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out })); }   // große Sammlungen: Speicher kann voll sein
      catch (e) { try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out.map(c => Object.assign({}, c, { items: c.items.map(i => Object.assign({}, i, { o: '' })) })) })); } catch (x) {} }
      if (window.LIVE) { LIVE.load(); LIVE.refresh(); }
    } finally { MXTV.building = false; }
  },
  /** Nicht erkannte Dateien („Die.Simpsons.S33E21.German…1080p“) bekommen einen lesbaren Titel */
  clean(i) {
    const n = i.Name || '';
    if (!RAW.test(n)) return n;
    if (i.Type === 'Episode') {
      const m = n.match(/^(.*?)[. _-]+S(\d{1,2})E(\d{1,3})/i), ser = i.SeriesName || (m ? m[1].replace(/[._]/g, ' ').trim() : '');
      return (ser ? ser + ' · ' : '') + 'Folge ' + (i.IndexNumber || (m ? +m[3] : ''));
    }
    return n.replace(/[._]/g, ' ').replace(/\b(19|20)\d\d\b.*$|\b(German|DL|AC3D?|1080p|720p|2160p|WEB|x26[45]|BluRay).*$/i, '').trim() || n;
  },
  ver: 0,
  set(list) {
    MXTV.ver++;
    MXTV.ch = list.map((c, k) => Object.assign({ id: 'mx:' + c.key, num: '', group: c.grp || MXG, logos: [], src: 'mx' }, c));
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
    while (t < to && guard++ < 4000) { const a = MXTV.at(c, t); if (!a) break; out.push([a.start, a.stop, a.it.n, (a.it.s || '') + (a.it.s && a.it.o ? ' – ' : '') + (a.it.o || '')]); t = a.stop + 1; }
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
