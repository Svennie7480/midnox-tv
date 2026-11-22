// midnox Emby – Statistik, Errungenschaften und Wiedergabe-Protokoll (Grundlage für den Jahresrückblick).
'use strict';

/** Protokolliert, wie lange wirklich geschaut/gehört wurde, und meldet es an den Community-Server. */
const WLOG = {
  cur: null,
  begin(it, o) {
    WLOG.flush();
    if (!it || !it.Id) return;
    WLOG.cur = { it, secs: 0, last: Date.now(), playing: false, party: !!(o && o.party), start: new Date(), done: false };
  },
  tick(playing) {
    const c = WLOG.cur; if (!c) return; const now = Date.now();
    if (c.playing) c.secs += Math.min(5, (now - c.last) / 1000);
    c.last = now; c.playing = !!playing;
  },
  flush(done) {
    const c = WLOG.cur; if (!c) return; WLOG.tick(false); WLOG.cur = null;
    if (c.secs < 20 || !window.COMM || !COMM.ok) return;
    const it = c.it, d = c.start;
    COMM.call('log', {
      id: it.Id, type: it.Type, name: it.Name, series: it.SeriesId || '', seriesName: it.SeriesName || '', genres: it.Genres || [], year: it.ProductionYear || 0,
      secs: Math.round(c.secs), done: !!done, party: c.party, hour: d.getHours(), wd: d.getDay(), day: STATS.dayKey(d),
    }).catch(() => {});
    STATS.soon();
  },
};
window.WLOG = WLOG;

const LEVELS = ['Bronze', 'Silber', 'Gold', 'Platin'];
const LEVEL_COL = ['#d08a4e', '#c9d1e4', '#facc15', '#a5f3fc'];
/** Errungenschaften: Schwellen für Bronze/Silber/Gold/Platin. v(s) liefert den aktuellen Wert aus der Gesamtstatistik. */
const ACH = [
  { id: 'filmstunden', icon: '🎬', name: 'Kinogänger', d: '{n} Stunden Filme geschaut', lv: [10, 50, 150, 400], v: s => s.movieH },
  { id: 'serienstunden', icon: '📺', name: 'Serienjunkie', d: '{n} Stunden Serien geschaut', lv: [10, 50, 150, 400], v: s => s.epH },
  { id: 'filme', icon: '🍿', name: 'Cineast', d: '{n} Filme gesehen', lv: [5, 25, 75, 150], v: s => s.movies },
  { id: 'folgen', icon: '🎞️', name: 'Episodenfresser', d: '{n} Folgen gesehen', lv: [25, 100, 300, 750], v: s => s.episodes },
  { id: 'komplett', icon: '✅', name: 'Durchgezogen', d: '{n} Serien komplett gesehen', lv: [1, 5, 15, 30], v: s => s.seriesDone },
  { id: 'binge', icon: '🔥', name: 'Binge-Meister', d: '{n} Folgen an einem Tag', lv: [4, 7, 10, 15], v: s => s.binge },
  { id: 'serie', icon: '📅', name: 'Dranbleiber', d: '{n} Tage am Stück geschaut', lv: [3, 7, 14, 30], v: s => s.streak },
  { id: 'nachteule', icon: '🦉', name: 'Nachteule', d: '{n}× nach Mitternacht geschaut', lv: [1, 10, 30, 75], v: s => s.night },
  { id: 'frueh', icon: '🌅', name: 'Frühaufsteher', d: '{n}× vor 7 Uhr geschaut', lv: [1, 5, 15, 40], v: s => s.early },
  { id: 'genres', icon: '🧭', name: 'Genre-Entdecker', d: '{n} verschiedene Genres', lv: [5, 10, 15, 20], v: s => s.genreCount },
  { id: 'jahrzehnte', icon: '⏳', name: 'Zeitreisender', d: 'Filme aus {n} Jahrzehnten', lv: [3, 5, 7, 9], v: s => s.decades },
  { id: 'klassiker', icon: '🎩', name: 'Klassiker-Fan', d: '{n} Filme von vor 1980', lv: [1, 5, 15, 30], v: s => s.classics },
  { id: 'marathon', icon: '🏃', name: 'Überlänge', d: '{n} Filme mit mehr als 2½ Stunden', lv: [1, 5, 15, 30], v: s => s.longMovies },
  { id: 'kritiker', icon: '⭐', name: 'Kritiker', d: '{n} Bewertungen abgegeben', lv: [5, 25, 100, 250], v: s => s.ratings },
  { id: 'tipps', icon: '💬', name: 'Tippgeber', d: '{n} Nachrichten & Empfehlungen im Chat', lv: [5, 25, 100, 300], v: s => s.chat },
  { id: 'gastgeber', icon: '👑', name: 'Gastgeber', d: '{n} Watch-Partys gestartet', lv: [1, 5, 20, 50], v: s => s.partyHost },
  { id: 'partygast', icon: '🎉', name: 'Partygast', d: '{n} Watch-Partys beigetreten', lv: [1, 5, 20, 50], v: s => s.partyJoin },
  { id: 'matchmaker', icon: '💜', name: 'Matchmaker', d: '{n} Runden „Was gucken wir heute?“', lv: [1, 5, 15, 40], v: s => s.matches },
  { id: 'musik', icon: '🎧', name: 'Ohrwurm', d: '{n} Stunden Musik gehört', lv: [2, 10, 50, 150], v: s => s.musicH },
];
window.ACH = ACH;

const STATS = {
  data: null, at: 0, busy: null, t: 0, got: {}, fresh: [],
  dayKey(d) { return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); },
  soon() { clearTimeout(STATS.t); STATS.t = setTimeout(() => STATS.check(true), 6000); },

  /** Emby-Historie (alles Gesehene) + Protokoll + Community-Zähler holen. */
  async load(force) {
    if (STATS.data && !force && Date.now() - STATS.at < 10 * 60000) return STATS.data;
    if (STATS.busy) return STATS.busy;
    STATS.busy = (async () => {
      const F = 'Genres,ProductionYear,RunTimeTicks,UserData,UserDataLastPlayedDate,UserDataPlayCount,SeriesName,ParentId';
      const grab = async (type) => {
        const out = []; let start = 0;
        for (let i = 0; i < 12; i++) {
          const r = await EMBY.items({ Recursive: true, IncludeItemTypes: type, Filters: 'IsPlayed', Fields: F, EnableImageTypes: 'Primary,Backdrop', SortBy: 'DatePlayed', SortOrder: 'Descending', StartIndex: start, Limit: 500 });
          out.push(...(r.Items || [])); start += 500; if (start >= (r.TotalRecordCount || 0)) break;
        }
        return out;
      };
      const year = new Date().getFullYear();
      const [movies, eps, sDone, my, myPrev] = await Promise.all([
        grab('Movie').catch(() => []), grab('Episode').catch(() => []),
        EMBY.items({ Recursive: true, IncludeItemTypes: 'Series', Filters: 'IsPlayed', Limit: 0, Fields: '' }).then(r => r.TotalRecordCount || 0).catch(() => 0),
        COMM.ok ? COMM.call('mystats', null, '&year=' + year).catch(() => null) : null,
        COMM.ok ? COMM.call('mystats', null, '&year=' + (year - 1)).catch(() => null) : null,
      ]);
      // Genres der Serien nachladen (Folgen haben meist keine eigenen)
      const sids = [...new Set(eps.map(e => e.SeriesId).filter(Boolean))];
      const series = {};
      for (let i = 0; i < sids.length; i += 100) {
        try { const r = await EMBY.items({ Ids: sids.slice(i, i + 100).join(','), Fields: 'Genres,ProductionYear', EnableImageTypes: 'Primary,Backdrop,Logo' }); for (const s of r.Items || []) series[s.Id] = s; } catch (e) {}
      }
      STATS.data = { movies, eps, seriesDone: sDone, my, myPrev, series, year };
      if (my && my.got) STATS.got = Object.assign({}, my.got, STATS.got);
      STATS.at = Date.now(); STATS.busy = null;
      return STATS.data;
    })();
    return STATS.busy;
  },

  /** Kennzahlen berechnen – für ein Jahr (Rückblick) oder insgesamt (Errungenschaften). */
  calc(year) {
    const D = STATS.data; if (!D) return null;
    const inY = (iso) => { if (!year) return true; if (!iso) return false; return new Date(iso).getFullYear() === year; };
    const lp = (it) => (it.UserData || {}).LastPlayedDate;
    const rt = (it) => (it.RunTimeTicks || 0) / 1e7;
    const movies = D.movies.filter(m => inY(lp(m))), eps = D.eps.filter(e => inY(lp(e)));
    const log = (year ? ((year === D.year ? (D.my || {}).log : (D.myPrev || {}).log) || []) : [].concat(((D.myPrev || {}).log) || [], ((D.my || {}).log) || []));
    const played = new Set(movies.map(m => m.Id).concat(eps.map(e => e.Id)));
    const s = { year, movies: movies.length, episodes: eps.length };
    s.movieSecs = movies.reduce((a, m) => a + rt(m), 0) + log.filter(l => l.k === 'Movie' && !played.has(l.id)).reduce((a, l) => a + l.s, 0);
    s.epSecs = eps.reduce((a, e) => a + rt(e), 0) + log.filter(l => l.k === 'Episode' && !played.has(l.id)).reduce((a, l) => a + l.s, 0);
    s.musicSecs = log.filter(l => l.k === 'Audio').reduce((a, l) => a + l.s, 0);
    s.movieH = Math.floor(s.movieSecs / 3600); s.epH = Math.floor(s.epSecs / 3600); s.musicH = Math.floor(s.musicSecs / 3600);
    s.hours = Math.round((s.movieSecs + s.epSecs) / 3600);
    // Zeitpunkte: Emby (letztes Ansehen) + Protokoll
    const times = movies.concat(eps).map(i => new Date(lp(i))).concat(log.filter(l => l.k !== 'Audio').map(l => new Date(l.t * 1000)));
    s.hoursOf = new Array(24).fill(0); s.wdOf = new Array(7).fill(0);
    s.night = 0; s.early = 0;
    const days = {};
    for (const d of times) { if (isNaN(d)) continue; const h = d.getHours(); s.hoursOf[h]++; s.wdOf[d.getDay()]++; if (h < 4) s.night++; if (h >= 4 && h < 7) s.early++; }
    // Folgen pro Tag (Binge) und Serie am Stück
    const epDays = {};
    for (const e of eps) { const d = new Date(lp(e)); if (!isNaN(d)) { const k = STATS.dayKey(d); epDays[k] = (epDays[k] || 0) + 1; days[k] = 1; } }
    for (const m of movies) { const d = new Date(lp(m)); if (!isNaN(d)) days[STATS.dayKey(d)] = 1; }
    const logEpDay = {}; for (const l of log) { if (l.day) days[l.day] = 1; if (l.k === 'Episode' && l.day) { logEpDay[l.day] = logEpDay[l.day] || new Set(); logEpDay[l.day].add(l.id); } }
    for (const k in logEpDay) epDays[k] = Math.max(epDays[k] || 0, logEpDay[k].size);
    let bingeDay = '', binge = 0; for (const k in epDays) if (epDays[k] > binge) { binge = epDays[k]; bingeDay = k; }
    s.binge = binge; s.bingeDay = bingeDay;
    const dk = Object.keys(days).sort(); let streak = 0, cur = 0, prev = null, streakEnd = '';
    for (const k of dk) { const d = new Date(k + 'T12:00:00'); cur = prev && (d - prev) / 864e5 < 1.5 ? cur + 1 : 1; if (cur > streak) { streak = cur; streakEnd = k; } prev = d; }
    s.streak = streak; s.streakEnd = streakEnd; s.activeDays = dk.length;
    // Serien & Genres
    const bySeries = {};
    for (const e of eps) { const id = e.SeriesId || e.ParentId; if (!id) continue; const b = bySeries[id] || (bySeries[id] = { id, name: e.SeriesName || '', n: 0, secs: 0 }); b.n++; b.secs += rt(e); }
    s.topSeries = Object.values(bySeries).sort((a, b) => b.n - a.n).slice(0, 5).map(b => Object.assign(b, { item: D.series[b.id] || null }));
    s.seriesCount = Object.keys(bySeries).length;
    const gen = {};
    const addG = (gs, w) => { for (const g of gs || []) gen[g] = (gen[g] || 0) + w; };
    for (const m of movies) addG(m.Genres, 2);
    for (const e of eps) addG((D.series[e.SeriesId] || {}).Genres || e.Genres, 0.5);
    s.genres = Object.keys(gen).map(g => ({ g, n: gen[g] })).sort((a, b) => b.n - a.n);
    s.genreCount = s.genres.length;
    s.topMovies = movies.slice().sort((a, b) => ((b.UserData || {}).PlayCount || 0) - ((a.UserData || {}).PlayCount || 0) || new Date(lp(b)) - new Date(lp(a))).slice(0, 6);
    s.decades = new Set(movies.filter(m => m.ProductionYear).map(m => Math.floor(m.ProductionYear / 10))).size;
    s.classics = movies.filter(m => m.ProductionYear && m.ProductionYear < 1980).length;
    s.longMovies = movies.filter(m => rt(m) > 9000).length;
    s.longest = movies.slice().sort((a, b) => rt(b) - rt(a))[0] || null;
    s.oldest = movies.filter(m => m.ProductionYear).sort((a, b) => a.ProductionYear - b.ProductionYear)[0] || null;
    s.first = movies.concat(eps).filter(i => lp(i)).sort((a, b) => new Date(lp(a)) - new Date(lp(b)))[0] || null;
    s.seriesDone = year ? 0 : D.seriesDone;
    // Community
    const my = year ? (year === D.year ? D.my : D.myPrev) : D.my;
    const scope = year ? 'year' : 'all';
    const cnt = (my && my.cnt && my.cnt[scope]) || {};
    s.ratings = my ? my.ratings[scope] : 0; s.bestRating = my && year ? my.ratings.best : null;
    s.chat = my ? my.chat[scope] : 0; s.recs = my ? my.chat.recs : 0;
    s.partyHost = cnt.party_host || 0; s.partyJoin = cnt.party_join || 0; s.matches = cnt.match_host || 0;
    s.partyMins = Math.round(log.filter(l => l.p).reduce((a, l) => a + l.s, 0) / 60);
    // Typ
    s.persona = STATS.persona(s);
    return s;
  },

  persona(s) {
    const total = s.movieSecs + s.epSecs || 1;
    const nightShare = (s.hoursOf.slice(22).reduce((a, b) => a + b, 0) + s.hoursOf.slice(0, 4).reduce((a, b) => a + b, 0)) / Math.max(1, s.hoursOf.reduce((a, b) => a + b, 0));
    if (s.partyHost + s.partyJoin >= 5) return { t: 'Stimmungskanone', d: 'Allein gucken? Nicht mit dir. Du holst alle aufs Sofa – und sei es per Watch-Party.', e: '🎉' };
    if (s.binge >= 7) return { t: 'Binge-Profi', d: '„Nur noch eine Folge“ – und dann ist es hell draußen. Staffeln sind für dich Tagesrationen.', e: '🔥' };
    if (nightShare > .45) return { t: 'Nachteule', d: 'Wenn alle schlafen, läuft bei dir der Abspann. Deine beste Zeit beginnt nach 22 Uhr.', e: '🦉' };
    if (s.epSecs > s.movieSecs * 2.5) return { t: 'Serien-Marathoni', d: 'Du liebst lange Geschichten und bleibst dran, Folge für Folge.', e: '📺' };
    if (s.movieSecs > s.epSecs * 1.5) return { t: 'Kinoseele', d: 'Großes Kino statt Kleinkram: Du nimmst dir Zeit für richtige Filme.', e: '🎬' };
    if (s.genreCount >= 10) return { t: 'Genre-Hopper', d: 'Ob Krimi, Komödie oder Doku – du probierst einfach alles aus.', e: '🧭' };
    if (s.ratings >= 10) return { t: 'Kritikerherz', d: 'Du schaust nicht nur, du ordnest ein. Deine Sterne helfen allen in der Runde.', e: '⭐' };
    return total > 36000 ? { t: 'Sofa-Genießer', d: 'Gute Mischung aus Filmen und Serien – du weißt, was dir gefällt.', e: '🛋️' } : { t: 'Frischling', d: 'Dein Jahr bei midnox hat gerade erst angefangen. Da geht noch was!', e: '🌱' };
  },

  level(a, v) { let l = 0; a.lv.forEach((x, i) => { if (v >= x) l = i + 1; }); return l; },

  /** Errungenschaften prüfen, neue feiern und mit dem Server abgleichen. */
  async check(force) {
    if (!EMBY.user) return;
    try { await STATS.load(force); } catch (e) { return; }
    const all = STATS.calc(0); if (!all) return;
    const stored = Object.assign({}, STATS.got, STORE.get('ach_' + EMBY.user.id, {}));
    const first = !Object.keys(stored).length && !STORE.get('ach_init_' + EMBY.user.id, false);
    const got = Object.assign({}, stored), fresh = [];
    for (const a of ACH) {
      const l = STATS.level(a, a.v(all) || 0);
      if (l > ((got[a.id] || {}).lv || 0)) { got[a.id] = { lv: l, t: Math.floor(Date.now() / 1000) }; fresh.push({ a, l }); }
    }
    STATS.got = got; STORE.set('ach_' + EMBY.user.id, got); STORE.set('ach_init_' + EMBY.user.id, true);
    const y = STATS.calc(STATS.data.year);
    if (COMM.ok) COMM.call('ach', { got, year: STATS.data.year, sum: { hours: y.hours, movies: y.movies, episodes: y.episodes, binge: y.binge, streak: y.streak, topGenre: (y.genres[0] || {}).g || '', persona: y.persona.t, topSeries: (y.topSeries[0] || {}).name || '', liveH: window.LVSTAT ? LVSTAT.calc(STATS.data.year).hours : 0, topChannel: window.LVSTAT ? ((LVSTAT.calc(STATS.data.year).top[0] || {}).n || '') : '' } }).catch(() => {});
    if (!fresh.length) return;
    if (first) { UI.toast(fresh.length + ' Errungenschaften aus deiner Emby-Historie freigeschaltet!', { icon: '🏆' }); return; }
    for (const f of fresh) STATS.fresh.push(f);
    STATS.celebrate();
  },
  celebrate() {
    if (STATS._showing || !STATS.fresh.length) return;
    if (PLAYER.active || $('#layer .modal')) { setTimeout(STATS.celebrate, 5000); return; }
    const { a, l } = STATS.fresh.shift(); STATS._showing = true;
    const el = h('div', 'achpop lv' + l, '<div class="medal lv' + l + '"><span>' + a.icon + '</span></div><div><small>Errungenschaft freigeschaltet · ' + LEVELS[l - 1] + '</small><b>' + esc(a.name) + '</b><div>' + esc(a.d.replace('{n}', a.lv[l - 1])) + '</div></div>');
    document.body.appendChild(el);
    if (window.SOUND && SOUND.play) try { SOUND.play('ach'); } catch (e) {}
    setTimeout(() => el.classList.add('out'), 4200);
    setTimeout(() => { el.remove(); STATS._showing = false; STATS.celebrate(); }, 4700);
  },
};
window.STATS = STATS;
