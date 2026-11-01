package de.midnox.radio;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** Wiedergabe im Hintergrund mit Titelanzeige (ICY bzw. midnox-API), Cover und Einschlaf-Timer. */
public class PlayerService extends Service {
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

    static void play(Context c, Station s) {
        Intent i = new Intent(c, PlayerService.class).setAction("play").putExtra("station", s.json().toString());
        c.startService(i);
    }
    static void toggle(Context c) { c.startService(new Intent(c, PlayerService.class).setAction("toggle")); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
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
            try { switchTo(Station.from(new JSONObject(i.getStringExtra("station")), "", "")); } catch (Exception ignored) {}
        } else if ("toggle".equals(a)) {
            if (playing || preparing) stop();
            else { if (station == null) station = Catalog.last(this); if (station != null) start(); }
        } else if ("stop".equals(a)) stop();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { stop(); h.removeCallbacksAndMessages(null); inst = null; super.onDestroy(); }

    void changed() {
        h.post(new Runnable() { public void run() {
            List<Runnable> l; synchronized (listeners) { l = new ArrayList<>(listeners); }
            for (Runnable r : l) r.run();
        }});
    }

    void foreground(boolean on) {
        if (!on) { stopForeground(true); return; }
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("radio", "midnox Radio", NotificationManager.IMPORTANCE_MIN));
                b = new Notification.Builder(this, "radio");
            } else b = new Notification.Builder(this);
            b.setContentTitle("midnox Radio").setContentText(station == null ? "" : station.name).setSmallIcon(R.drawable.icon);
            startForeground(44, b.build());
        } catch (Exception ignored) {}
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
}
