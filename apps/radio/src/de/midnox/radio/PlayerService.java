package de.midnox.radio;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.media.browse.MediaBrowser;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import android.service.media.MediaBrowserService;
import org.json.*;
import java.util.*;

/** Wiedergabe im Hintergrund mit Titelanzeige (ICY bzw. midnox-API), Cover und Einschlaf-Timer.
 *  Zugleich MediaBrowserService: Senderlisten fuer Android Auto, Steuerung per MediaSession
 *  (Sperrbildschirm, Bluetooth, Lenkrad, Sprachbefehle). */
public class PlayerService extends MediaBrowserService {
    static volatile PlayerService inst;
    static final List<Runnable> listeners = new ArrayList<>();
    static void listen(Runnable r) { synchronized (listeners) { if (!listeners.contains(r)) listeners.add(r); } }
    static void unlisten(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    static final class Song { String artist = "", title = "", art; long at; }

    final Handler h = new Handler(Looper.getMainLooper());
    Station station;
    Song current;
    final List<Song> history = new ArrayList<>();
    Bitmap cover;
    MediaPlayer player;
    Net.Proxy proxy;
    boolean playing, preparing;
    String error;
    int session;
    long sleepAt;
    AudioManager am;
    AudioManager.OnAudioFocusChangeListener focus;
    MediaSession ms;
    List<Station> queue = new ArrayList<>();   // fuer Weiter/Zurueck (Lenkrad, Benachrichtigung)
    final Map<String, Station> known = new HashMap<>();
    boolean fg;

    static void play(Context c, Station s) {
        Intent i = new Intent(c, PlayerService.class).setAction("play").putExtra("station", s.json().toString());
        c.startService(i);
    }
    static void toggle(Context c) { c.startService(new Intent(c, PlayerService.class).setAction("toggle")); }

    @Override public IBinder onBind(Intent i) { return i != null && SERVICE_INTERFACE.equals(i.getAction()) ? super.onBind(i) : null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        U.init(this); Img.init(this);
        ms = new MediaSession(this, "midnoxRadio");
        ms.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        ms.setCallback(cb);
        try { ms.setSessionActivity(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), pif())); } catch (Exception ignored) {}
        setSessionToken(ms.getSessionToken());
        ms.setActive(true);
        session();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioManager.OnAudioFocusChangeListener() { public void onAudioFocusChange(int f) {
            if (f == AudioManager.AUDIOFOCUS_LOSS) stop();
            else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (player != null) player.setVolume(0.15f, 0.15f); }
            else if (f == AudioManager.AUDIOFOCUS_GAIN) { if (player != null) player.setVolume(1f, 1f); }
        }};
        h.post(tick);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if ("play".equals(a)) {
            if (!MainActivity.playList.isEmpty()) queue = new ArrayList<>(MainActivity.playList);
            try { switchTo(Station.from(new JSONObject(i.getStringExtra("station")), "", "")); } catch (Exception ignored) {}
        } else if ("toggle".equals(a)) {
            if (playing || preparing) stop();
            else { if (station == null) station = Catalog.last(this); if (station != null) start(); }
        } else if ("stop".equals(a)) { stop(); fgOff(true); }
        else if ("next".equals(a)) step(1);
        else if ("prev".equals(a)) step(-1);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { stop(); fgOff(true); h.removeCallbacksAndMessages(null); try { ms.release(); } catch (Exception ignored) {} inst = null; super.onDestroy(); }
    @Override public void onTaskRemoved(Intent i) { if (!playing && !preparing) stopSelf(); super.onTaskRemoved(i); }

    void changed() {
        h.post(new Runnable() { public void run() {
            session(); notif();
            List<Runnable> l; synchronized (listeners) { l = new ArrayList<>(listeners); }
            for (Runnable r : l) r.run();
        }});
    }

    static int pif() { return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0); }
    PendingIntent act(String a) { return PendingIntent.getService(this, a.hashCode(), new Intent(this, PlayerService.class).setAction(a), pif()); }

    /** Benachrichtigung mit Mediensteuerung (Sperrbildschirm, Handy). */
    Notification build() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("radio2", "midnox Radio", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, "radio2");
        } else b = new Notification.Builder(this);
        String song = current != null && !current.title.isEmpty() ? (current.artist.isEmpty() ? current.title : current.artist + " – " + current.title) : (preparing ? "Verbinde …" : "");
        b.setSmallIcon(R.drawable.icon).setContentTitle(station == null ? "midnox Radio" : station.name).setContentText(song)
         .setVisibility(Notification.VISIBILITY_PUBLIC).setShowWhen(false).setOngoing(playing || preparing)
         .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), pif()))
         .setDeleteIntent(act("stop"));
        Bitmap big = cover != null ? cover : station == null ? null : Img.now(station.logo);
        if (big != null) b.setLargeIcon(big);
        b.addAction(new Notification.Action(android.R.drawable.ic_media_previous, "Vorheriger Sender", act("prev")));
        b.addAction(playing || preparing ? new Notification.Action(android.R.drawable.ic_media_pause, "Pause", act("toggle"))
                                         : new Notification.Action(android.R.drawable.ic_media_play, "Abspielen", act("toggle")));
        b.addAction(new Notification.Action(android.R.drawable.ic_media_next, "Nächster Sender", act("next")));
        b.setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }
    void foreground(boolean on) {
        if (!on) { fgOff(false); return; }
        try {
            Notification n = build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(44, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(44, n);
            fg = true;
        } catch (Exception e) { notif(); }   // z. B. Start aus dem Hintergrund nicht erlaubt: dann nur Benachrichtigung
    }
    /** Pausiert: Benachrichtigung bleibt (wegwischbar), damit man weiterhoeren kann. */
    void fgOff(boolean remove) {
        try { stopForeground(remove); } catch (Exception ignored) {}
        fg = false;
        if (remove) { try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(44); } catch (Exception ignored) {} }
        else notif();
    }
    void notif() {
        if (U.tv || station == null) return;
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(44, build()); } catch (Exception ignored) {}
    }

    /** MediaSession: Titel, Cover und Status fuer Auto, Sperrbildschirm und Bluetooth. */
    void session() {
        if (ms == null) return;
        long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                  | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY_FROM_SEARCH;
        int st = preparing ? PlaybackState.STATE_BUFFERING : playing ? PlaybackState.STATE_PLAYING : error != null ? PlaybackState.STATE_ERROR : station != null ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_NONE;
        PlaybackState.Builder pb = new PlaybackState.Builder().setActions(acts).setState(st, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f);
        if (error != null && !playing) pb.setErrorMessage(error);
        try { ms.setPlaybackState(pb.build()); } catch (Exception ignored) {}
        if (station == null) return;
        MediaMetadata.Builder mb = new MediaMetadata.Builder();
        boolean song = current != null && !current.title.isEmpty();
        mb.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "s:" + station.id);
        mb.putString(MediaMetadata.METADATA_KEY_TITLE, song ? current.title : station.name);
        mb.putString(MediaMetadata.METADATA_KEY_ARTIST, song ? current.artist : station.caption());
        mb.putString(MediaMetadata.METADATA_KEY_ALBUM, station.name);
        mb.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, song ? current.title : station.name);
        mb.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, song ? (current.artist.isEmpty() ? station.name : current.artist + " · " + station.name) : station.caption());
        mb.putLong(MediaMetadata.METADATA_KEY_DURATION, -1);
        Bitmap art = cover != null ? cover : Img.now(station.logo);
        if (art != null) { mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art); mb.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art); }
        else if (station.logo != null) { mb.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, LogoProvider.uri(station.logo).toString());
            final Station want = station; Img.load(station.logo, 512, new Img.Cb() { public void done(Bitmap x) { if (x != null && want == station) { session(); notif(); } } }); }
        try { ms.setMetadata(mb.build()); } catch (Exception ignored) {}
    }

    void step(int d) {
        List<Station> l = !queue.isEmpty() ? queue : MainActivity.playList;
        if (l.isEmpty()) { List<Station> f = Catalog.list(this, "favs"); l = f.isEmpty() ? Catalog.list(this, "recent") : f; }
        if (l.isEmpty()) return;
        int i = -1;
        for (int k = 0; k < l.size(); k++) if (station != null && l.get(k).id.equals(station.id)) i = k;
        i = (i + d + l.size()) % l.size();
        switchTo(l.get(i));
    }

    // ------------------------------------------------------------ Steuerung
    void switchTo(Station s) {
        if (station != null && s.id.equals(station.id) && (playing || preparing)) return;
        stop();
        station = s;
        Catalog.played(this, s);
        session++;
        current = null; history.clear(); cover = null; error = null;
        start();
    }

    void start() {
        if (preparing || playing || station == null) return;
        if (am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Ton ist gerade belegt"; changed(); return; }
        preparing = true; error = null; changed();
        foreground(true);
        final Station st = station;
        final int my = session;
        new Thread(new Runnable() { public void run() {
            String err = null, local = null;
            Net.Proxy px = null;
            try {
                Net.Resp r = Net.open(st.url);
                int code = r.code; r.close();
                if (code != 200) err = code == 401 || code == 403 ? "Der Sender lässt gerade nicht zu (HTTP " + code + ")" : code == 404 ? "Sender nicht mehr erreichbar (404)" : "HTTP " + code;
                else {
                    px = new Net.Proxy(st.url);
                    if (!st.midnox()) px.listener = new Net.Proxy.TitleListener() { public void title(String t) { onStreamTitle(t, my); } };
                    local = px.start();
                }
            } catch (Exception e) { err = "Keine Verbindung zum Sender"; }
            final String url = local, msg = err;
            final Net.Proxy fpx = px;
            h.post(new Runnable() { public void run() {
                if (my != session || !preparing) { if (fpx != null) fpx.stop(); return; }
                if (url == null) { preparing = false; error = msg; foreground(false); changed(); return; }
                proxy = fpx;
                startPlayer(url);
            }});
        }}).start();
        if (st.midnox()) pollMidnox();
    }

    void startPlayer(String url) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(url);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer mp) {
                preparing = false; playing = true; mp.start(); changed();
            }});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer mp, int what, int extra) {
                stop(); error = "Dieser Stream lässt sich nicht abspielen"; changed(); return true;
            }});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer mp) {
                stop(); error = "Verbindung zum Sender verloren"; changed();
            }});
            player.prepareAsync();
        } catch (Exception ex) { stop(); error = "Wiedergabe nicht möglich"; changed(); }
    }

    void stop() {
        preparing = false; playing = false;
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
        if (proxy != null) { proxy.stop(); proxy = null; }
        try { am.abandonAudioFocus(focus); } catch (Exception ignored) {}
        foreground(false);
        changed();
    }

    /** Einschlaf-Timer: Minuten (0 = aus). */
    void sleep(int min) { sleepAt = min <= 0 ? 0 : System.currentTimeMillis() + min * 60000L; changed(); }
    int sleepLeft() { return sleepAt == 0 ? 0 : (int) Math.max(1, (sleepAt - System.currentTimeMillis() + 59999) / 60000); }

    final Runnable tick = new Runnable() { public void run() {
        if (sleepAt > 0) {
            long left = sleepAt - System.currentTimeMillis();
            if (left <= 0) { sleepAt = 0; stop(); if (player == null) { /* aus */ } }
            else if (left < 60000 && player != null) { float v = left / 60000f; player.setVolume(v, v); }
        }
        if (station != null && station.midnox() && playing && System.currentTimeMillis() - lastPoll > 15000) pollMidnox();
        h.postDelayed(this, 1000);
    }};

    // ------------------------------------------------------------ Titel & Cover
    long lastPoll;
    void pollMidnox() {
        lastPoll = System.currentTimeMillis();
        final Station st = station; final int my = session;
        if (st == null || st.nowplaying == null) return;
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject o = new JSONObject(U.get(st.nowplaying));
                JSONObject np = o.getJSONObject("now_playing").getJSONObject("song");
                final Song s = new Song(); s.artist = np.optString("artist"); s.title = np.optString("title"); s.art = np.optString("art", null);
                final List<Song> hist = new ArrayList<>();
                JSONArray hs = o.optJSONArray("song_history");
                for (int i = 0; hs != null && i < hs.length() && i < 10; i++) {
                    JSONObject x = hs.getJSONObject(i).getJSONObject("song");
                    Song y = new Song(); y.artist = x.optString("artist"); y.title = x.optString("title"); y.art = x.optString("art", null); y.at = hs.getJSONObject(i).optLong("played_at");
                    hist.add(y);
                }
                final Bitmap b = s.art == null ? null : Img.fetch(s.art, 600);
                h.post(new Runnable() { public void run() {
                    if (my != session) return;
                    boolean nw = current == null || !current.title.equals(s.title);
                    current = s; history.clear(); history.addAll(hist);
                    if (b != null) cover = b;
                    if (nw || b != null) changed();
                }});
            } catch (Exception ignored) {}
        }}).start();
    }

    void onStreamTitle(final String raw, final int my) {
        h.post(new Runnable() { public void run() {
            if (my != session || raw.isEmpty() || station == null || station.midnox()) return;
            String a = "", t = raw;
            int i = raw.indexOf(" - ");
            if (i > 0) { a = raw.substring(0, i).trim(); t = raw.substring(i + 3).trim(); }
            if (current != null && current.title.equals(t) && current.artist.equals(a)) return;
            if (current != null && !current.title.isEmpty()) { history.add(0, current); while (history.size() > 12) history.remove(history.size() - 1); }
            final Song s = new Song(); s.title = t; s.artist = a; s.at = System.currentTimeMillis() / 1000;
            current = s; cover = null;
            changed();
            if (a.isEmpty() || looksLikeAd(raw)) return;
            new Thread(new Runnable() { public void run() {
                String url = Net.coverUrl(s.artist, s.title);
                if (url == null) return;
                final Bitmap b = Img.fetch(url, 600);
                if (b == null) return;
                h.post(new Runnable() { public void run() { if (current == s) { s.art = url; cover = b; changed(); } }});
            }}).start();
        }});
    }

    static boolean looksLikeAd(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.contains("werbung") || l.contains("nachrichten") || l.contains("www.") || l.contains(".de") && !l.contains(" - ");
    }

    // ------------------------------------------------------------ Steuerung von aussen (Auto, Bluetooth, Sperrbildschirm)
    final MediaSession.Callback cb = new MediaSession.Callback() {
        @Override public void onPlay() { if (station == null) station = Catalog.last(PlayerService.this); if (station == null) { List<Station> f = favsOrMidnox(); if (!f.isEmpty()) { switchTo(f.get(0)); return; } } if (station != null) start(); }
        @Override public void onPause() { stop(); }
        @Override public void onStop() { stop(); }
        @Override public void onSkipToNext() { step(1); }
        @Override public void onSkipToPrevious() { step(-1); }
        @Override public void onPlayFromMediaId(String id, Bundle extras) {
            Station st = known.get(id);
            if (st == null && id.startsWith("s:")) st = findById(id.substring(2));
            if (st != null) switchTo(st);
        }
        @Override public void onPlayFromSearch(final String query, Bundle extras) {
            if (query == null || query.trim().isEmpty()) { onPlay(); return; }
            new Thread(new Runnable() { public void run() {
                final List<Station> r = search(query);
                h.post(new Runnable() { public void run() { if (!r.isEmpty()) { queue = r; switchTo(r.get(0)); } else { error = "Kein Sender zu „" + query + "“ gefunden"; changed(); } } });
            }}).start();
        }
    };

    List<Catalog.Group> groups() {
        List<Catalog.Group> g = Catalog.cached(this);
        if (g == null) try { g = Catalog.fetch(this); } catch (Exception e) { g = new ArrayList<>(); }
        return g;
    }
    List<Station> favsOrMidnox() {
        List<Station> f = Catalog.list(this, "favs");
        if (f.isEmpty()) for (Catalog.Group g : groups()) if (g.id.equals("midnox")) f = new ArrayList<>(g.stations);
        return f;
    }
    Station findById(String id) {
        for (String k : new String[]{"favs", "recent"}) for (Station s : Catalog.list(this, k)) if (s.id.equals(id)) return s;
        for (Catalog.Group g : groups()) for (Station s : g.stations) if (s.id.equals(id)) return s;
        return null;
    }
    /** Suche: eigener Katalog zuerst, dann weltweit. */
    List<Station> search(String q) {
        String ql = q.toLowerCase(Locale.GERMANY).replaceAll("^(spiele|spiel|höre|hör|starte)\\s+", "").replaceAll("\\s*(auf|in|mit|bei)?\\s*midnox( radio)?$", "").trim();
        if (ql.isEmpty() || ql.equals("radio")) return favsOrMidnox();
        List<Station> out = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (Catalog.Group g : groups()) for (Station s : g.stations)
            if ((s.name.toLowerCase(Locale.GERMANY).contains(ql) || g.name.toLowerCase(Locale.GERMANY).contains(ql) || s.tags.contains(ql)) && ids.add(s.id)) out.add(s);
        if (out.size() < 5) try { for (Station s : Browser.search(ql)) if (ids.add(s.id)) out.add(s); } catch (Exception ignored) {}
        return out;
    }

    // ------------------------------------------------------------ Android Auto: Senderbibliothek
    static final String ROOT = "root";
    @Override public BrowserRoot onGetRoot(String pkg, int uid, Bundle hints) {
        Bundle x = new Bundle();
        x.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
        x.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1);   // Ordner als Liste
        x.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2);    // Sender als Kacheln
        return new BrowserRoot(ROOT, x);
    }

    @Override public void onLoadChildren(final String parent, final Result<List<MediaBrowser.MediaItem>> result) {
        result.detach();
        new Thread(new Runnable() { public void run() {
            List<MediaBrowser.MediaItem> out = new ArrayList<>();
            try { out = children(parent); } catch (Exception ignored) {}
            result.sendResult(out);
        }}).start();
    }

    List<MediaBrowser.MediaItem> children(String parent) {
        List<MediaBrowser.MediaItem> out = new ArrayList<>();
        List<Catalog.Group> gs = groups();
        if (ROOT.equals(parent)) {
            for (Catalog.Group g : gs) if (g.id.equals("midnox")) out.add(folder("g:midnox", "midnox Radio", "Unser Sender", g.logo != null ? g.logo : g.stations.get(0).logo));
            out.add(folder("favs", "Favoriten", "Deine Lieblingssender", null));
            out.add(folder("recent", "Zuletzt gehört", "", null));
            out.add(folder("t:network", "Senderfamilien", "ffn, RTL, bigFM …", null));
            out.add(folder("t:genre", "Musikrichtungen", "Rock, Pop, Schlager …", null));
            out.add(folder("t:region", "Bundesländer", "Regionale Sender", null));
            return out;
        }
        List<Station> list = null;
        if (parent.equals("favs") || parent.equals("recent")) list = Catalog.list(this, parent);
        else if (parent.startsWith("t:")) {
            String t = parent.substring(2);
            for (Catalog.Group g : gs) if (t.equals(g.type)) out.add(folder("g:" + g.id, g.name, g.stations.size() + " Sender", "network".equals(t) ? (g.logo != null ? g.logo : g.stations.get(0).logo) : null));
            return out;
        } else if (parent.startsWith("g:")) {
            for (Catalog.Group g : gs) if (("g:" + g.id).equals(parent)) list = g.stations;
        }
        if (list != null) { for (Station s : list) out.add(item(s)); final List<Station> q = new ArrayList<>(list); h.post(new Runnable() { public void run() { queue = q; } }); }
        return out;
    }

    MediaBrowser.MediaItem folder(String id, String title, String sub, String logo) {
        MediaDescription.Builder d = new MediaDescription.Builder().setMediaId(id).setTitle(title);
        if (sub != null && !sub.isEmpty()) d.setSubtitle(sub);
        if (logo != null) d.setIconUri(LogoProvider.uri(logo));
        return new MediaBrowser.MediaItem(d.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE);
    }
    MediaBrowser.MediaItem item(Station s) {
        known.put("s:" + s.id, s);
        MediaDescription.Builder d = new MediaDescription.Builder().setMediaId("s:" + s.id).setTitle(s.name);
        String cap = s.caption(); if (!cap.isEmpty()) d.setSubtitle(cap);
        if (s.logo != null) d.setIconUri(LogoProvider.uri(s.logo));
        return new MediaBrowser.MediaItem(d.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }
}
