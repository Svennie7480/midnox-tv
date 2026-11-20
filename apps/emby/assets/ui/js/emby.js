// midnox Media – Anbindung an Emby- und Jellyfin-Server (gleiche Grund-Schnittstelle, Unterschiede hier gekapselt).
'use strict';
const FIELDS = 'PrimaryImageAspectRatio,Overview,Genres,CommunityRating,OfficialRating,RunTimeTicks,ProductionYear,PremiereDate,EndDate,UserData,ChildCount,RecursiveItemCount,Status,SeriesStatus,Taglines,ParentId,AlbumArtist,Artists';
const IMGQ = '&EnableImageTypes=Primary,Backdrop,Thumb,Logo&ImageTypeLimit=1&EnableUserData=true';

const EMBY = {
  root: '', api: '', token: '', jf: false, user: null, server: null, hasMusic: true, views: [], codecs: null, ws: null,
  settings() {
    return Object.assign({ bitrate: 0, audioLang: 'ger', subMode: 'smart', subLang: 'ger', autoNext: true, autoSkip: false, showWatching: true, community: 'https://midnox.de/midnoxtv/emby/index.php', theme: 'auto' }, STORE.get('settings', {}));
  },
  saveSettings(s) { STORE.set('settings', s); },
  authHeader() {
    if (EMBY.jf) return 'MediaBrowser Client="midnox Media", Device="' + DEVICE.deviceName.replace(/"/g, '') + '", DeviceId="' + DEVICE.deviceId + '", Version="' + DEVICE.version + '"' + (EMBY.token ? ', Token="' + EMBY.token + '"' : '');
    return 'Emby UserId="' + (EMBY.user ? EMBY.user.id : '') + '", Client="midnox Media", Device="' + DEVICE.deviceName.replace(/"/g, '') + '", DeviceId="' + DEVICE.deviceId + '", Version="' + DEVICE.version + '"' + (EMBY.token ? ', Token="' + EMBY.token + '"' : '');
  },
  headers() {
    if (EMBY.jf) return { 'Authorization': EMBY.authHeader(), 'Accept': 'application/json' };
    const hd = { 'X-Emby-Authorization': EMBY.authHeader(), 'Accept': 'application/json' }; if (EMBY.token) hd['X-Emby-Token'] = EMBY.token; return hd;
  },
  /** Schlüssel für Adressen (Streams, Bilder mit Login): Emby api_key, Jellyfin ApiKey. */
  tq() { return (EMBY.jf ? 'ApiKey=' : 'api_key=') + EMBY.token; },
  /** Vom Server gelieferte Pfade (Stream, Untertitel) zu einer vollständigen Adresse machen. */
  abs(u) {
    if (/^https?:/i.test(u)) return u;
    const p = EMBY.jf ? u : (u.startsWith('/emby') ? u : '/emby' + u);
    return EMBY.root + p + (/[?&](api_key|ApiKey)=/i.test(u) ? '' : (u.indexOf('?') < 0 ? '?' : '&') + EMBY.tq());
  },
  kindName() { return EMBY.jf ? 'Jellyfin' : 'Emby'; },
  qs(q) { if (!q) return ''; const p = []; for (const k in q) if (q[k] !== undefined && q[k] !== null && q[k] !== '') p.push(encodeURIComponent(k) + '=' + encodeURIComponent(q[k])); return p.length ? '?' + p.join('&') : ''; },
  async req(method, path, body, q, timeout) {
    try { const r = await NET.json(method, EMBY.api + path + EMBY.qs(q), EMBY.headers(), body, timeout); return method === 'GET' && window.KIDS && KIDS.on ? KIDS.filter(r) : r; }
    catch (e) { if (e.status === 401 && EMBY.token) { EMBY.onAuthLost(); } throw e; }
  },
  get(path, q, t) { return EMBY.req('GET', path, null, q, t); },
  post(path, body, q) { return EMBY.req('POST', path, body == null ? {} : body, q); },
  del(path, q) { return EMBY.req('DELETE', path, null, q); },
  onAuthLost() { if (EMBY._lost) return; EMBY._lost = true; UI.alert('Abgemeldet', 'Deine Anmeldung bei ' + EMBY.kindName() + ' ist abgelaufen. Bitte melde dich neu an.').then(() => { EMBY._lost = false; APP.logout(true); }); },

  // ---------- Server finden ----------
  candidates(addr) {
    addr = String(addr || '').trim().replace(/\/+$/, '').replace(/\/(web|emby)(\/.*)?$/i, '');
    if (!addr) return [];
    const out = [];
    const hasProto = /^https?:\/\//i.test(addr);
    const hostPart = addr.replace(/^https?:\/\//i, '');
    const hasPort = /^[^/]+:\d+/.test(hostPart);
    if (hasProto) { out.push(addr); if (!hasPort && /^http:/i.test(addr)) out.push(addr.replace(/^(http:\/\/[^/]+)/i, '$1:8096')); }
    else { if (!hasPort) { out.push('http://' + hostPart + ':8096'); out.push('https://' + hostPart); out.push('https://' + hostPart + ':8920'); } else out.push('http://' + hostPart, 'https://' + hostPart); out.push('http://' + hostPart); }
    return Array.from(new Set(out));
  },
  async probe(addr) {
    let lastErr = null;
    for (const c of EMBY.candidates(addr)) {
      // Jellyfin antwortet ohne Präfix (mit ProductName), Emby auf jeden Fall unter /emby
      try {
        const info = await NET.json('GET', c + '/System/Info/Public', { 'Accept': 'application/json' }, null, 7000);
        if (info && info.Id && /jellyfin/i.test(info.ProductName || '')) return { url: c, id: info.Id, name: info.ServerName, version: info.Version, kind: 'jellyfin' };
      } catch (e) { lastErr = e; }
      try {
        const info = await NET.json('GET', c + '/emby/System/Info/Public', { 'Accept': 'application/json' }, null, 7000);
        if (info && info.Id) return { url: c, id: info.Id, name: info.ServerName, version: info.Version, kind: /jellyfin/i.test(info.ProductName || '') ? 'jellyfin' : 'emby' };
      } catch (e) { lastErr = e; }
    }
    throw new Error(lastErr ? 'Kein Emby- oder Jellyfin-Server unter dieser Adresse (' + lastErr.message + ')' : 'Ungültige Adresse');
  },
  use(server) { EMBY.server = server; EMBY.jf = server.kind === 'jellyfin'; EMBY.root = server.url.replace(/\/+$/, ''); EMBY.api = EMBY.root + (EMBY.jf ? '' : '/emby'); EMBY.codecs = null; },
  servers() { return STORE.get('servers', []); },
  saveServer(srv, user) {
    const list = EMBY.servers().filter(s => s.id !== srv.id);
    const old = EMBY.servers().find(s => s.id === srv.id) || { users: [] };
    const users = (old.users || []).filter(u => !user || u.id !== user.id);
    if (user) users.unshift(user);
    list.unshift({ id: srv.id, name: srv.name, url: srv.url, kind: srv.kind || 'emby', users });
    STORE.set('servers', list);
  },
  async publicUsers() { return (await EMBY.get('/Users/Public')) || []; },
  async login(name, pw) {
    const ah = EMBY.authHeader().replace(/, Token="[^"]*"/, '');
    const r = await NET.json('POST', EMBY.api + '/Users/AuthenticateByName', Object.assign({ 'Content-Type': 'application/json' }, EMBY.jf ? { 'Authorization': ah } : { 'X-Emby-Authorization': ah }), { Username: name, Pw: pw || '' });
    EMBY.token = r.AccessToken; EMBY.setUser(r.User);
    EMBY.saveServer(EMBY.server, { id: EMBY.user.id, name: EMBY.user.name, token: EMBY.token });
    STORE.set('active', { server: EMBY.server.id, user: EMBY.user.id });
    return EMBY.user;
  },
  setUser(u) { EMBY.user = { id: u.Id, name: u.Name, admin: !!(u.Policy && u.Policy.IsAdministrator), imgTag: u.PrimaryImageTag || '', policy: u.Policy || {} }; },
  async restore() {
    const act = STORE.get('active'); if (!act) return false;
    const srv = EMBY.servers().find(s => s.id === act.server); if (!srv) return false;
    const u = (srv.users || []).find(x => x.id === act.user); if (!u || !u.token) return false;
    EMBY.use(srv); EMBY.token = u.token; EMBY.user = { id: u.id, name: u.name, admin: false };
    const me = await EMBY.get('/Users/' + u.id, null, 12000);
    EMBY.setUser(me); return true;
  },
  async logoutUser() {
    try { await EMBY.post('/Sessions/Logout'); } catch (e) {}
    const list = EMBY.servers(); const s = list.find(x => EMBY.server && x.id === EMBY.server.id);
    if (s) { s.users = (s.users || []).filter(u => u.id !== (EMBY.user && EMBY.user.id)); STORE.set('servers', list); }
    STORE.set('active', null); EMBY.token = ''; EMBY.user = null; EMBY.closeSocket();
  },
  userImg(u) { u = u || EMBY.user; return u && (u.imgTag || u.PrimaryImageTag) ? EMBY.api + '/Users/' + (u.id || u.Id) + '/Images/Primary?maxWidth=200&tag=' + (u.imgTag || u.PrimaryImageTag) : ''; },

  // ---------- Bilder ----------
  img(item, type, w) {
    if (!item) return '';
    w = w || 400; const R = EMBY.api + '/Items/';
    const T = item.ImageTags || {};
    const q = (id, t, tag, idx) => R + id + '/Images/' + t + (idx != null ? '/' + idx : '') + '?maxWidth=' + w + '&quality=88' + (tag ? '&tag=' + tag : '');
    switch (type) {
      case 'Primary':
        if (T.Primary) return q(item.Id, 'Primary', T.Primary);
        if (item.AlbumId && item.AlbumPrimaryImageTag) return q(item.AlbumId, 'Primary', item.AlbumPrimaryImageTag);
        if (item.SeriesId && item.SeriesPrimaryImageTag) return q(item.SeriesId, 'Primary', item.SeriesPrimaryImageTag);
        if (item.PrimaryImageTag) return q(item.Id, 'Primary', item.PrimaryImageTag);
        return '';
      case 'Backdrop':
        if (item.BackdropImageTags && item.BackdropImageTags.length) return q(item.Id, 'Backdrop', item.BackdropImageTags[0], 0);
        if (item.ParentBackdropItemId && item.ParentBackdropImageTags && item.ParentBackdropImageTags.length) return q(item.ParentBackdropItemId, 'Backdrop', item.ParentBackdropImageTags[0], 0);
        return '';
      case 'Thumb':
        if (T.Thumb) return q(item.Id, 'Thumb', T.Thumb);
        if (item.ParentThumbItemId && item.ParentThumbImageTag) return q(item.ParentThumbItemId, 'Thumb', item.ParentThumbImageTag);
        if (item.SeriesThumbImageTag && item.SeriesId) return q(item.SeriesId, 'Thumb', item.SeriesThumbImageTag);
        return '';
      case 'Logo':
        if (T.Logo) return q(item.Id, 'Logo', T.Logo);
        if (item.ParentLogoItemId && item.ParentLogoImageTag) return q(item.ParentLogoItemId, 'Logo', item.ParentLogoImageTag);
        return '';
    }
    return '';
  },
  /** Querformat-Bild: Folge → Standbild, sonst Thumb, sonst Hintergrund. */
  land(item, w) {
    if (item.Type === 'Episode' && item.ImageTags && item.ImageTags.Primary) return EMBY.img(item, 'Primary', w || 600);
    return EMBY.img(item, 'Thumb', w || 600) || EMBY.img(item, 'Backdrop', w || 600) || EMBY.img(item, 'Primary', w || 600);
  },
  backdrop(item) { return EMBY.img(item, 'Backdrop', 1920) || ''; },

  // ---------- Inhalte ----------
  async loadViews() {
    const r = await EMBY.get('/Users/' + EMBY.user.id + '/Views');
    EMBY.views = (r && r.Items) || [];
    EMBY.hasMusic = EMBY.views.some(v => v.CollectionType === 'music');
    return EMBY.views;
  },
  viewsOf(type) { return EMBY.views.filter(v => v.CollectionType === type); },
  items(q) { return EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ Fields: FIELDS, EnableImageTypes: 'Primary,Backdrop,Thumb,Logo', ImageTypeLimit: 1 }, q)); },
  item(id) { return EMBY.get('/Users/' + EMBY.user.id + '/Items/' + id, { Fields: FIELDS + ',People,Studios,MediaSources,MediaStreams,Chapters,RemoteTrailers,LocalTrailerCount,ProviderIds,ExternalUrls' + (EMBY.jf ? ',Trickplay' : '') }); },
  resume(media) { return EMBY.get('/Users/' + EMBY.user.id + '/Items/Resume', { Limit: 24, Recursive: true, Fields: FIELDS, MediaTypes: media || 'Video', EnableImageTypes: 'Primary,Backdrop,Thumb,Logo', ImageTypeLimit: 1 }); },
  nextUp(seriesId) { return EMBY.get('/Shows/NextUp', { UserId: EMBY.user.id, Limit: seriesId ? 1 : 24, SeriesId: seriesId, Fields: FIELDS, EnableImageTypes: 'Primary,Backdrop,Thumb,Logo', ImageTypeLimit: 1 }); },
  latest(parentId, limit) { return EMBY.get('/Users/' + EMBY.user.id + '/Items/Latest', { ParentId: parentId, Limit: limit || 24, Fields: FIELDS, EnableImageTypes: 'Primary,Backdrop,Thumb,Logo', ImageTypeLimit: 1, GroupItems: true }); },
  seasons(seriesId) { return EMBY.get('/Shows/' + seriesId + '/Seasons', { UserId: EMBY.user.id, Fields: FIELDS }); },
  episodes(seriesId, seasonId) { return EMBY.get('/Shows/' + seriesId + '/Episodes', { UserId: EMBY.user.id, SeasonId: seasonId, Fields: FIELDS + ',MediaStreams', EnableImageTypes: 'Primary,Backdrop,Thumb', ImageTypeLimit: 1 }); },
  similar(id) { return EMBY.get('/Items/' + id + '/Similar', { UserId: EMBY.user.id, Limit: 16, Fields: FIELDS }); },
  genres(parentId) { return EMBY.get('/Genres', { UserId: EMBY.user.id, ParentId: parentId, SortBy: 'SortName' }); },
  setPlayed(id, on) { return on ? EMBY.post('/Users/' + EMBY.user.id + '/PlayedItems/' + id) : EMBY.del('/Users/' + EMBY.user.id + '/PlayedItems/' + id); },
  setFav(id, on) { return on ? EMBY.post('/Users/' + EMBY.user.id + '/FavoriteItems/' + id) : EMBY.del('/Users/' + EMBY.user.id + '/FavoriteItems/' + id); },
  search(term) {
    return EMBY.items({ SearchTerm: term, Recursive: true, IncludeItemTypes: 'Movie,Series,Episode,MusicAlbum,Audio,MusicArtist,BoxSet', Limit: 60 });
  },
  persons(term) { return EMBY.get('/Persons', { SearchTerm: term, UserId: EMBY.user.id, Limit: 20, Fields: 'PrimaryImageAspectRatio' }); },

  // ---------- Wiedergabe ----------
  detectCodecs() {
    if (EMBY.codecs) return EMBY.codecs;
    let list = [];
    try { if (HAS_ANDROID && Android.codecs) list = JSON.parse(Android.codecs()); } catch (e) {}
    const has = (m) => list.length ? list.indexOf(m) >= 0 : null;
    const v = ['h264'];
    if (has('video/hevc') !== false) v.push('hevc');
    if (has('video/x-vnd.on2.vp9') !== false) v.push('vp9');
    if (has('video/av01')) v.push('av1');
    if (has('video/mpeg2')) v.push('mpeg2video');
    if (has('video/mp4v-es')) v.push('mpeg4');
    const a = ['aac', 'mp3', 'mp2', 'flac', 'opus', 'vorbis', 'pcm_s16le', 'pcm_s24le', 'alac'];
    if (has('audio/ac3') !== false) a.push('ac3');
    if (has('audio/eac3') !== false) a.push('eac3');
    if (has('audio/vnd.dts')) a.push('dts', 'dca');
    if (has('audio/true-hd')) a.push('truehd');
    EMBY.codecs = { v, a }; return EMBY.codecs;
  },
  profile(maxBitrate) {
    const C = EMBY.detectCodecs();
    const textSubs = ['srt', 'subrip', 'ass', 'ssa', 'vtt', 'webvtt', 'mov_text', 'text', 'smi', 'microdvd'].map(f => ({ Format: f, Method: 'External' }));
    const imgSubs = ['pgssub', 'pgs', 'dvdsub', 'dvbsub', 'sub', 'vobsub'].map(f => ({ Format: f, Method: 'Encode' }));
    return {
      Name: 'midnox Media', MaxStreamingBitrate: maxBitrate, MaxStaticBitrate: 200000000, MusicStreamingTranscodingBitrate: 320000,
      DirectPlayProfiles: [
        { Container: 'mp4,m4v,mkv,webm,mov,ts,mpegts,m2ts,avi,flv,3gp', Type: 'Video', VideoCodec: C.v.join(','), AudioCodec: C.a.join(',') },
        { Container: 'mp3,aac,m4a,m4b,flac,ogg,oga,opus,wav,webma,mka,alac', Type: 'Audio' },
      ],
      TranscodingProfiles: [
        { Container: 'ts', Type: 'Video', VideoCodec: C.v.indexOf('hevc') >= 0 ? 'h264,hevc' : 'h264', AudioCodec: 'aac' + (C.a.indexOf('ac3') >= 0 ? ',ac3' : ''), Context: 'Streaming', Protocol: 'hls', MaxAudioChannels: '6', MinSegments: '1', BreakOnNonKeyFrames: true },
        { Container: 'mp3', Type: 'Audio', AudioCodec: 'mp3', Context: 'Streaming', Protocol: 'http', MaxAudioChannels: '2' },
      ],
      ContainerProfiles: [],
      CodecProfiles: [{ Type: 'Video', Codec: 'h264', Conditions: [{ Condition: 'LessThanEqual', Property: 'VideoLevel', Value: '52', IsRequired: false }] }],
      SubtitleProfiles: textSubs.concat(imgSubs),
      ResponseProfiles: [],
    };
  },
  async playbackInfo(item, opts) {
    opts = opts || {};
    const s = EMBY.settings();
    let max = s.bitrate || 120000000;
    // Handy unterwegs (mobile Daten): Qualität begrenzen
    try { if (window.TOUCH && HAS_ANDROID && Android.metered && Android.metered() && s.mobileBitrate !== 0) max = Math.min(max, s.mobileBitrate || 4000000); } catch (e) {}
    const q = { UserId: EMBY.user.id, StartTimeTicks: Math.round((opts.startMs || 0) * 10000), IsPlayback: true, AutoOpenLiveStream: true, MaxStreamingBitrate: max,
      MediaSourceId: opts.mediaSourceId, AudioStreamIndex: opts.audioIndex, SubtitleStreamIndex: opts.subIndex };
    const r = await EMBY.post('/Items/' + item.Id + '/PlaybackInfo', { DeviceProfile: EMBY.profile(max) }, q);
    if (r && r.ErrorCode) throw new Error(r.ErrorCode === 'NotAllowed' ? 'Wiedergabe für dieses Konto nicht erlaubt' : 'Wiedergabe nicht möglich (' + r.ErrorCode + ')');
    const src = (r.MediaSources || []).find(m => !opts.mediaSourceId || m.Id === opts.mediaSourceId) || (r.MediaSources || [])[0];
    if (!src) throw new Error('Keine abspielbare Quelle gefunden');
    let url, method;
    const key = EMBY.tq() + '&DeviceId=' + encodeURIComponent(DEVICE.deviceId) + '&PlaySessionId=' + r.PlaySessionId;
    if (src.SupportsDirectPlay && !opts.forceTranscode) {
      method = 'DirectPlay';
      url = item.MediaType === 'Audio'
        ? EMBY.api + '/Audio/' + item.Id + '/stream.' + (src.Container || 'mp3') + '?Static=true&MediaSourceId=' + src.Id + '&' + key
        : EMBY.api + '/Videos/' + item.Id + '/stream.' + (src.Container || 'mkv').split(',')[0] + '?Static=true&MediaSourceId=' + src.Id + '&' + key;
    } else if (src.SupportsDirectStream && src.DirectStreamUrl && !opts.forceTranscode) {
      method = 'DirectStream'; url = EMBY.abs(src.DirectStreamUrl);
    } else if (src.TranscodingUrl) {
      method = 'Transcode'; url = EMBY.abs(src.TranscodingUrl);
    } else throw new Error('Diese Datei kann auf diesem Gerät nicht abgespielt werden');
    return { src, url, method, playSessionId: r.PlaySessionId, hls: method === 'Transcode' && /m3u8/i.test(url) };
  },
  subUrl(item, src, stream) {
    if (stream.DeliveryUrl) return EMBY.abs(stream.DeliveryUrl);
    return EMBY.api + '/Videos/' + item.Id + '/' + src.Id + '/Subtitles/' + stream.Index + '/0/Stream.vtt?' + EMBY.tq();
  },
  report(kind, body) { return EMBY.post('/Sessions/Playing' + (kind === 'start' ? '' : kind === 'progress' ? '/Progress' : '/Stopped'), body).catch(() => {}); },
  /** Jellyfin: Intro/Abspann/Rückblick als Medien-Segmente → wie Embys Kapitelmarken behandeln. */
  async segments(id) {
    if (!EMBY.jf) return [];
    try { const r = await EMBY.get('/MediaSegments/' + id); return (r.Items || []).map(x => ({ type: x.Type, start: x.StartTicks / 10000, end: x.EndTicks / 10000 })); } catch (e) { return []; }
  },
  stopEncoding(playSessionId) { return EMBY.del('/Videos/ActiveEncodings', { DeviceId: DEVICE.deviceId, PlaySessionId: playSessionId }).catch(() => {}); },

  // ---------- Live-Verbindung (Server-Nachrichten, Fernsteuerung) ----------
  async capabilities() {
    try {
      await EMBY.post('/Sessions/Capabilities/Full', { PlayableMediaTypes: ['Audio', 'Video'], SupportsMediaControl: true, SupportsPersistentIdentifier: true,
        SupportedCommands: ['DisplayMessage', 'SetAudioStreamIndex', 'SetSubtitleStreamIndex', 'Mute', 'Unmute', 'SetVolume', 'DisplayContent', 'Play', 'Playstate', 'PlayNext', 'PlayMediaSource', 'SendString', 'GoHome', 'Back'] });
    } catch (e) {}
  },
  openSocket() {
    EMBY.closeSocket();
    const url = EMBY.root.replace(/^http/i, 'ws') + (EMBY.jf ? '/socket?' : '/embywebsocket?') + EMBY.tq() + '&deviceId=' + encodeURIComponent(DEVICE.deviceId);
    let ws; try { ws = new WebSocket(url); } catch (e) { return; }
    EMBY.ws = ws;
    ws.onmessage = (ev) => { let m; try { m = JSON.parse(ev.data); } catch (e) { return; } EMBY.onSocket(m); };
    ws.onopen = () => { EMBY._wsTry = 0; clearInterval(EMBY._ka); EMBY._ka = setInterval(() => { try { ws.send(JSON.stringify({ MessageType: 'KeepAlive' })); } catch (e) {} }, 30000); };
    ws.onclose = () => { clearInterval(EMBY._ka); if (EMBY.ws === ws && EMBY.token) { const d = Math.min(60000, 3000 * Math.pow(2, EMBY._wsTry = (EMBY._wsTry || 0) + 1)); setTimeout(() => { if (EMBY.ws === ws) EMBY.openSocket(); }, d); } };
  },
  closeSocket() { const w = EMBY.ws; EMBY.ws = null; clearInterval(EMBY._ka); if (w) try { w.close(); } catch (e) {} },
  onSocket(m) {
    const d = m.Data || {};
    switch (m.MessageType) {
      case 'GeneralCommand': {
        const a = d.Arguments || {};
        if (d.Name === 'DisplayMessage') UI.toast(a.Text || '', { title: a.Header || 'Nachricht vom Server', icon: '📣', cls: 'admin', ms: Math.max(5000, +a.TimeoutMs || 9000) });
        else if (d.Name === 'SetVolume' && window.PLAYER) PLAYER.volume(+a.Volume / 100);
        else if (d.Name === 'GoHome') ROUTER.go('home', null, { root: true });
        break;
      }
      case 'Playstate': if (window.PLAYER && PLAYER.remote) PLAYER.remote(d); else if (window.MUSIC) MUSIC.remote(d); break;
      case 'Play':
        if (d.ItemIds && d.ItemIds.length) APP.remotePlay(d.ItemIds, d.PlayCommand, d.StartPositionTicks);
        break;
      case 'UserDataChanged': case 'LibraryChanged': APP.dirty = true; break;
      case 'ForceKeepAlive': try { EMBY.ws.send(JSON.stringify({ MessageType: 'KeepAlive' })); } catch (e) {} break;
    }
  },
};
window.EMBY = EMBY;
