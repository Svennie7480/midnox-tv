package de.midnox.live;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import java.util.*;

/**
 * midnox Live: Vollbild-Player (Media3/ExoPlayer) mit Senderliste (OK), Infoleiste, TV-Guide (Rechts in der Liste
 * oder MENU) und Favoriten. Hoch/Runter = Sender wechseln.
 */
public class PlayerActivity extends Activity implements Views.ListCb {
    final Handler h = new Handler(Looper.getMainLooper());
    ExoPlayer player;
    FrameLayout videoBox;
    SurfaceView surface;
    Views.ListPanel list;
    Views.Banner banner;
    Views.Guide guide;
    Views.Menu menu;
    TextView status, numEntry;
    LinearLayout setup;
    Model.Channel current, previous;
    String group;
    int retries, scale;   // scale: 0 anpassen, 1 fuellen, 2 zoom
    long lastBack;
    float videoRatio = 16f / 9f;
    final StringBuilder digits = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this); Img.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videoBox = new FrameLayout(this);
        surface = new SurfaceView(this);
        videoBox.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(videoBox, new FrameLayout.LayoutParams(-1, -1));
        banner = new Views.Banner(this); banner.setVisibility(View.GONE);
        root.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        list = new Views.ListPanel(this, this); list.setVisibility(View.GONE);
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));
        guide = new Views.Guide(this, this); guide.setVisibility(View.GONE);
        root.addView(guide, new FrameLayout.LayoutParams(-1, -1));
        menu = new Views.Menu(this); menu.setVisibility(View.GONE);
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        status = U.text(this, "", 16, Color.WHITE);
        status.setPadding(U.px(18), U.px(10), U.px(18), U.px(10));
        status.setBackground(round(0xCC15141F));
        status.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT); sl.topMargin = U.px(30); sl.rightMargin = U.px(40);
        root.addView(status, sl);
        numEntry = U.text(this, "", 44, Color.WHITE);
        numEntry.setTypeface(U.BOLD);
        numEntry.setPadding(U.px(22), U.px(10), U.px(22), U.px(10));
        numEntry.setBackground(round(0xCC15141F));
        numEntry.setVisibility(View.GONE);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT); nl.topMargin = U.px(80); nl.rightMargin = U.px(40);
        root.addView(numEntry, nl);
        setup = buildSetup();
        root.addView(setup, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        scale = U.prefs(this).getInt("scale", 0);
        group = U.prefs(this).getString("group", Lib.ALL);
        Web.start(this);
        Web.onChange = new Runnable() { public void run() { refreshAll(); } };
        Lib.loadCache(this);
        for (Model.Source s : Lib.sources(this)) if (s.type.equals("stalker")) Stalker.active.put(s.id, U.prefs(this).getInt("mac_" + s.id, 0));
        Epg.loadCache(this);
        Logos.load(this);
        initPlayer();
        refreshAll();
        if (Lib.channels.isEmpty() || Lib.stale()) reloadSources(!Lib.channels.isEmpty());
        else if (Epg.stale()) reloadEpg();
        handleIntent(getIntent(), true);
        h.postDelayed(tick, 30000);
    }

    static android.graphics.drawable.GradientDrawable round(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(U.px(14)); return d;
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i, false); }
    /** Aufruf vom midnox-Launcher: Extra "channel" = Sendername (oder "id"). */
    void handleIntent(Intent i, boolean start) {
        String name = i == null ? null : i.getStringExtra("channel");
        Model.Channel ch = null;
        if (name != null) ch = Lib.byName(name);
        if (ch == null && i != null && i.getStringExtra("id") != null) ch = Lib.byId(i.getStringExtra("id"));
        if (ch == null && start) ch = Lib.byId(U.prefs(this).getString("last", ""));
        if (ch == null && start && !Lib.channels.isEmpty()) ch = Lib.channels.get(0);
        if (ch != null) play(ch);
        else if (name != null) toast("„" + name + "“ ist nicht in deinen Sendern");
    }

    final Runnable tick = new Runnable() { public void run() {
        if (list.getVisibility() == View.VISIBLE) list.invalidate();
        if (banner.getVisibility() == View.VISIBLE) banner.invalidate();
        if (guide.getVisibility() == View.VISIBLE) guide.invalidate();
        if (Lib.stale() && !Lib.loading) reloadSources(true);
        else if (Epg.stale() && !Epg.loading && !Lib.loading) reloadEpg();
        h.postDelayed(this, 30000);
    }};

    void refreshAll() {
        boolean empty = Lib.channels.isEmpty();
        setup.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) updateSetup();
        if (!empty && current == null) handleIntent(null, true);
        list.invalidate(); banner.invalidate(); guide.invalidate();
    }

    // ------------------------------------------------------------ Laden
    void reloadSources(final boolean quiet) {
        if (!quiet) showStatus("Sender werden geladen …", 0);
        new Thread(new Runnable() { public void run() {
            Lib.reload(PlayerActivity.this);
            h.post(new Runnable() { public void run() {
                refreshAll();
                if (!quiet) showStatus(Lib.channels.size() + " Sender geladen" + (Lib.errors.isEmpty() ? "" : " – " + Lib.errors.size() + " Quelle(n) mit Fehler"), 4000);
            }});
            Epg.reload(PlayerActivity.this);
            h.post(new Runnable() { public void run() { refreshAll(); } });
        }}).start();
    }
    void reloadEpg() {
        new Thread(new Runnable() { public void run() { Epg.reload(PlayerActivity.this); h.post(new Runnable() { public void run() { refreshAll(); } }); } }).start();
    }

    // ------------------------------------------------------------ Player
    void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int st) {
                h.removeCallbacks(stallWatch);
                if (st == Player.STATE_BUFFERING) { if (!tryMsg) showStatus("Lädt …", 0); h.postDelayed(stallWatch, everReady ? 25000 : 15000); }
                else if (st == Player.STATE_READY) { hideStatus(); tryMsg = false; retries = 0; everReady = true; updateBadge(); succeeded(); }
                else if (st == Player.STATE_ENDED && current != null) h.postDelayed(new Runnable() { public void run() { if (current != null) runTry(); } }, 1500);
            }
            @Override public void onPlayerError(PlaybackException e) {
                if (current == null) return;
                lastError = e.getErrorCodeName().replace("ERROR_CODE_", "").toLowerCase(Locale.ROOT).replace('_', ' ');
                // lief schon: einmal neu verbinden; sonst gleich naechste MAC / Quelle
                if (everReady && retries++ < 2) { showStatus("Verbindung unterbrochen – neuer Versuch …", 0); h.postDelayed(new Runnable() { public void run() { if (current != null) runTry(); } }, 1500 * retries); }
                else failNext();
            }
            @Override public void onVideoSizeChanged(VideoSize v) {
                if (v.width > 0 && v.height > 0) { videoRatio = v.width * v.pixelWidthHeightRatio / v.height; layoutVideo(); updateBadge(); }
            }
        });
    }
    void layoutVideo() {
        int w = videoBox.getWidth(), hh = videoBox.getHeight();
        if (w == 0 || hh == 0) { videoBox.post(new Runnable() { public void run() { layoutVideo(); } }); return; }
        int vw, vh;
        float screen = w / (float) hh;
        if (scale == 1) { vw = w; vh = hh; }                                 // fuellen (verzerrt)
        else if (scale == 2) { if (videoRatio > screen) { vh = hh; vw = Math.round(hh * videoRatio); } else { vw = w; vh = Math.round(w / videoRatio); } }   // zoom
        else { if (videoRatio > screen) { vw = w; vh = Math.round(w / videoRatio); } else { vh = hh; vw = Math.round(hh * videoRatio); } }
        surface.setLayoutParams(new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER));
    }
    void updateBadge() {
        Format f = player == null ? null : player.getVideoFormat();
        String b = "";
        if (f != null && f.height > 0) b = f.height >= 2000 ? "4K" : f.height >= 1000 ? "FHD" : f.height >= 700 ? "HD" : "SD";
        banner.badge = b; banner.invalidate();
    }

    @Override public void play(Model.Channel ch) {
        if (guide.getVisibility() == View.VISIBLE) guide.setVisibility(View.GONE);
        if (list.getVisibility() == View.VISIBLE) {
            if (current != null && current.id.equals(ch.id)) { closeList(); return; }   // zweites OK schliesst die Liste
            list.playingId = ch.id; list.invalidate();
        }
        if (current != null && !current.id.equals(ch.id)) previous = current;
        current = ch;
        retries = 0;
        U.prefs(this).edit().putString("last", ch.id).apply();
        showBanner(ch);
        buildPlan(ch);
        runTry();
        Epg.ensureShort(this, ch, new Runnable() { public void run() { banner.invalidate(); } });
    }
    void start(Model.Channel ch, boolean retry) { runTry(); }

    // ------------------------------------------------------------ Ausweichen: naechste MAC, dann andere Quelle
    static final class Try { Model.Channel ch; int mac = -1, macs; String label = ""; }
    final List<Try> plan = new ArrayList<>();
    int planPos; boolean everReady, tryMsg; String lastError = "";

    void buildPlan(Model.Channel ch) {
        plan.clear(); planPos = 0; everReady = false; tryMsg = false;
        addTries(ch);
        // derselbe Sender aus anderen Quellen (gleicher Name)
        if (U.prefs(this).getBoolean("fallback", true)) {
            String n = U.norm(ch.name);
            for (Model.Channel o : Lib.channels) if (!o.id.equals(ch.id) && !o.src.equals(ch.src) && U.norm(o.name).equals(n) && plan.size() < 16) addTries(o);
        }
    }
    void addTries(Model.Channel ch) {
        if (!ch.cmd.isEmpty()) {
            Model.Source s = Lib.source(this, ch.src);
            int n = s == null ? 1 : Math.max(1, Stalker.macs(s).size()), st = s == null ? 0 : Stalker.activeIndex(s);
            for (int k = 0; k < n; k++) { Try t = new Try(); t.ch = ch; t.mac = (st + k) % n; t.macs = n; t.label = (s == null ? "" : s.name) + (n > 1 ? " · MAC " + (t.mac + 1) + " von " + n : ""); plan.add(t); }
        } else {
            Model.Source s = Lib.source(this, ch.src);
            Try t = new Try(); t.ch = ch; t.label = s == null ? "" : s.name; plan.add(t);
        }
    }
    void failNext() {
        h.removeCallbacks(stallWatch);
        planPos++; retries = 0; everReady = false;
        if (planPos >= plan.size()) {
            tryMsg = false;
            String what = plan.size() > 1 ? " – alle " + plan.size() + " Wege probiert" : "";
            showStatus("„" + current.name + "“ lässt sich gerade nicht abspielen" + what + (lastError.isEmpty() ? "" : " (" + lastError + ")"), 0);
            return;
        }
        runTry();
    }
    final Runnable stallWatch = new Runnable() { public void run() {
        if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING && current != null) { lastError = "kein Bild"; if (everReady && retries++ < 1) runTry(); else failNext(); }
    }};
    void runTry() {
        if (current == null || plan.isEmpty()) return;
        if (planPos >= plan.size()) planPos = 0;
        final Try t = plan.get(planPos);
        final Model.Channel ch = current;
        if (planPos > 0) { tryMsg = true; showStatus(ch.name + ": versuche " + (t.ch.src.equals(ch.src) ? "" : "Ausweich-Quelle ") + t.label + " …", 0); }
        else showStatus("Lädt …", 0);
        if (!t.ch.cmd.isEmpty()) {   // Stalker: Adresse erst holen
            new Thread(new Runnable() { public void run() {
                try {
                    Model.Source s = Lib.source(PlayerActivity.this, t.ch.src);
                    if (s == null) throw new Exception("Quelle fehlt");
                    final String u = Stalker.link(s, t.ch, Math.max(0, t.mac));
                    h.post(new Runnable() { public void run() { if (current == ch && plan.indexOf(t) == planPos) prepare(t.ch, u, Stalker.MAG_UA); } });
                } catch (final Exception e) {
                    h.post(new Runnable() { public void run() { if (current == ch && plan.indexOf(t) == planPos) { lastError = e.getMessage(); failNext(); } } });
                }
            }}).start();
        } else prepare(t.ch, t.ch.url, t.ch.ua.isEmpty() ? null : t.ch.ua);
    }
    /** Es laeuft: funktionierende MAC merken, damit der naechste Sender gleich darueber startet. */
    void succeeded() {
        if (planPos >= plan.size()) return;
        Try t = plan.get(planPos);
        if (t.mac >= 0) { Stalker.active.put(t.ch.src, t.mac); U.prefs(this).edit().putInt("mac_" + t.ch.src, t.mac).apply(); }
        if (planPos > 0) toast(current.name + " läuft über " + t.label);
        banner.via = t.label; banner.invalidate();
    }

    void prepare(Model.Channel ch, String url, String ua) {
        DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory()
            .setUserAgent(ua != null ? ua : U.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000).setReadTimeoutMs(15000);
        if (!ch.referer.isEmpty()) { Map<String, String> hd = new HashMap<>(); hd.put("Referer", ch.referer); ds.setDefaultRequestProperties(hd); }
        MediaItem.Builder mi = new MediaItem.Builder().setUri(url);
        String lu = url.toLowerCase(Locale.ROOT);
        if (lu.contains(".m3u8") || lu.contains("m3u8")) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lu.contains(".mpd")) mi.setMimeType(MimeTypes.APPLICATION_MPD);
        MediaSource ms = new DefaultMediaSourceFactory(ds).createMediaSource(mi.build());
        player.setMediaSource(ms);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ------------------------------------------------------------ ListCb
    @Override public void guide(Model.Channel ch) {
        List<Model.Channel> l = list.chans.isEmpty() ? Lib.channels : list.chans;
        guide.open(l, ch, list.group());
        list.setVisibility(View.GONE); guide.setVisibility(View.VISIBLE);
    }
    @Override public void settings() { closeList(); openMenu(); }
    @Override public void fav(Model.Channel ch) { toast(Lib.toggleFav(this, ch.id) ? "★ " + ch.name + " ist jetzt Favorit" : ch.name + " aus den Favoriten entfernt"); }

    // ------------------------------------------------------------ Tasten
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU ? true : super.dispatchKeyEvent(e);
        boolean rep = e.getRepeatCount() > 0;
        if (k == KeyEvent.KEYCODE_BACK) { back(); return true; }
        if (setup.getVisibility() == View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { askM3u(); return true; }
            if (k == KeyEvent.KEYCODE_MENU) { openMenu(); return true; }
            return super.dispatchKeyEvent(e);
        }
        if (menu.getVisibility() == View.VISIBLE) { menu.key(k); return true; }
        if (guide.getVisibility() == View.VISIBLE) { guide.key(k); return true; }
        if (list.getVisibility() == View.VISIBLE) {
            if (!list.key(k, rep) && k == KeyEvent.KEYCODE_DPAD_LEFT) closeList();
            hideLater(list, 30000);
            return true;
        }
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) { digit(k - KeyEvent.KEYCODE_0); return true; }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP: zap(1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: zap(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_LEFT: openList(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_INFO: if (current != null) showBanner(current); return true;
            case KeyEvent.KEYCODE_MENU: openMenu(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player.isPlaying()) { player.pause(); showStatus("⏸  Pause", 0); } else if (current != null) { start(current, false); }   // live: wieder live einsteigen
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND: if (previous != null) play(previous); return true;   // letzter Sender
            case KeyEvent.KEYCODE_GUIDE: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: openGuide(); return true;
        }
        return super.dispatchKeyEvent(e);
    }
    void back() {
        if (menu.getVisibility() == View.VISIBLE) { menu.setVisibility(View.GONE); return; }
        if (guide.getVisibility() == View.VISIBLE) { guide.setVisibility(View.GONE); openList(); return; }
        if (list.getVisibility() == View.VISIBLE) { closeList(); return; }
        if (banner.getVisibility() == View.VISIBLE) { banner.setVisibility(View.GONE); return; }
        if (System.currentTimeMillis() - lastBack < 2500) { finish(); return; }
        lastBack = System.currentTimeMillis();
        toast("Nochmal ZURÜCK zum Beenden");
    }

    void zap(int d) {
        List<Model.Channel> l = Lib.inGroup(this, group);
        if (l.isEmpty()) l = Lib.channels;
        if (l.isEmpty()) return;
        int i = current == null ? -1 : indexOf(l, current.id);
        if (i < 0) { l = Lib.channels; i = current == null ? -1 : indexOf(l, current.id); }
        int n = ((i + d) % l.size() + l.size()) % l.size();
        play(l.get(n));
    }
    static int indexOf(List<Model.Channel> l, String id) { for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return i; return -1; }

    void digit(int d) {
        if (digits.length() >= 4) digits.setLength(0);
        digits.append(d);
        numEntry.setText(digits); numEntry.setVisibility(View.VISIBLE);
        h.removeCallbacks(numGo); h.postDelayed(numGo, 1600);
    }
    final Runnable numGo = new Runnable() { public void run() {
        numEntry.setVisibility(View.GONE);
        try { Model.Channel ch = Lib.byNum(Integer.parseInt(digits.toString())); if (ch != null) play(ch); else toast("Kein Sender auf Platz " + digits); } catch (Exception ignored) {}
        digits.setLength(0);
    }};

    // ------------------------------------------------------------ Overlays
    void openList() {
        if (Lib.channels.isEmpty()) return;
        banner.setVisibility(View.GONE);
        list.open(group, current == null ? null : current.id);
        list.setVisibility(View.VISIBLE);
        hideLater(list, 30000);
    }
    void closeList() {
        group = list.group();
        U.prefs(this).edit().putString("group", group).apply();
        list.setVisibility(View.GONE);
    }
    void openGuide() {
        if (Lib.channels.isEmpty()) return;
        List<Model.Channel> l = Lib.inGroup(this, group);
        if (l.isEmpty()) l = Lib.channels;
        guide.open(l, current, group);
        banner.setVisibility(View.GONE); list.setVisibility(View.GONE);
        guide.setVisibility(View.VISIBLE);
    }
    void showBanner(Model.Channel ch) {
        if (banner.ch == null || !banner.ch.id.equals(ch.id)) banner.via = Lib.srcNames.containsKey(ch.src) ? Lib.srcNames.get(ch.src) : "";
        banner.ch = ch; banner.msg = "";
        banner.setVisibility(View.VISIBLE); banner.invalidate();
        hideLater(banner, 6000);
    }
    final Map<View, Runnable> hiders = new HashMap<>();
    void hideLater(final View v, long ms) {
        Runnable old = hiders.get(v);
        if (old != null) h.removeCallbacks(old);
        Runnable r = new Runnable() { public void run() { if (v == list) closeList(); else v.setVisibility(View.GONE); } };
        hiders.put(v, r);
        h.postDelayed(r, ms);
    }
    void showStatus(String s, long ms) {
        status.setText(s); status.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideStatusR);
        if (ms > 0) h.postDelayed(hideStatusR, ms);
    }
    final Runnable hideStatusR = new Runnable() { public void run() { status.setVisibility(View.GONE); } };
    void hideStatus() { status.setVisibility(View.GONE); }
    void toast(String s) { showStatus(s, 3000); }

    // ------------------------------------------------------------ Menue
    void openMenu() {
        List<String> l = new ArrayList<>(); List<Runnable> a = new ArrayList<>();
        if (current != null) {
            l.add("📅  TV-Guide"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); openGuide(); } });
            final boolean fav = Lib.isFav(this, current.id);
            l.add(fav ? "☆  Aus Favoriten entfernen" : "★  Zu Favoriten"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); fav(current); } });
            if (previous != null) { l.add("↺  Letzter Sender: " + previous.name); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); play(previous); } }); }
            final List<String[]> audio = audioTracks();
            if (audio.size() > 1) { l.add("🔊  Tonspur wechseln"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); nextAudio(); } }); }
            l.add("▣  Bildformat: " + new String[]{"Anpassen", "Füllen", "Zoom"}[scale]); a.add(new Runnable() { public void run() {
                scale = (scale + 1) % 3; U.prefs(PlayerActivity.this).edit().putInt("scale", scale).apply(); layoutVideo(); openMenu(); } });
        }
        l.add("⚙  Quellen einrichten (Handy/PC)"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); showQr(); } });
        l.add("↻  Sender und Programm neu laden"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); reloadSources(false); } });
        l.add("➕  M3U-Adresse hier eingeben"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); askM3u(); } });
        menu.set(current == null ? "midnox Live" : current.num + "  " + current.name, l, a);
        menu.setVisibility(View.VISIBLE);
    }
    List<String[]> audioTracks() {
        List<String[]> out = new ArrayList<>();
        if (player == null) return out;
        Tracks t = player.getCurrentTracks();
        for (Tracks.Group g : t.getGroups()) if (g.getType() == C.TRACK_TYPE_AUDIO) for (int i = 0; i < g.length; i++) {
            Format f = g.getTrackFormat(i);
            String n = f.label != null ? f.label : f.language != null ? new Locale(f.language).getDisplayLanguage(Locale.GERMANY) : "Ton " + (out.size() + 1);
            out.add(new String[]{n, g.isTrackSelected(i) ? "1" : "0"});
        }
        return out;
    }
    void nextAudio() {
        Tracks t = player.getCurrentTracks();
        List<Object[]> all = new ArrayList<>(); int cur = -1;
        for (Tracks.Group g : t.getGroups()) if (g.getType() == C.TRACK_TYPE_AUDIO) for (int i = 0; i < g.length; i++) { if (g.isTrackSelected(i)) cur = all.size(); all.add(new Object[]{g, i}); }
        if (all.size() < 2) return;
        Object[] n = all.get((cur + 1) % all.size());
        Tracks.Group g = (Tracks.Group) n[0]; int idx = (Integer) n[1];
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), idx)).build());
        Format f = g.getTrackFormat(idx);
        toast("Ton: " + (f.label != null ? f.label : f.language != null ? new Locale(f.language).getDisplayLanguage(Locale.GERMANY) : "Spur " + ((cur + 1) % all.size() + 1)));
    }

    // ------------------------------------------------------------ Einrichtung
    TextView setupUrl; FrameLayout qrBox;
    LinearLayout buildSetup() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(U.BG);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = U.text(this, "midnox Live", 44, Color.WHITE);
        TextView t2 = U.text(this, "Dein Fernsehen – mit M3U-Liste, Xtream-Zugang oder Stalker-Portal.", 18, U.MUTED);
        t2.setPadding(0, U.px(10), 0, U.px(26));
        TextView t3 = U.text(this, "1.  QR-Code mit dem Handy scannen\n2.  Quelle eintragen (oder M3U-Datei hochladen)\n3.  Fertig – die Sender erscheinen hier von selbst", 18, U.TEXT);
        t3.setLineSpacing(U.px(8), 1f);
        setupUrl = U.text(this, "", 16, 0xFF9EC5FF);
        setupUrl.setPadding(0, U.px(22), 0, U.px(8));
        TextView t4 = U.text(this, "OK: M3U-Adresse direkt am Fernseher eingeben", 15, U.MUTED);
        txt.addView(t1); txt.addView(t2); txt.addView(t3); txt.addView(setupUrl); txt.addView(t4);
        v.addView(txt, new LinearLayout.LayoutParams(U.px(560), -2));
        qrBox = new FrameLayout(this);
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(U.px(260), U.px(260)); ql.leftMargin = U.px(60);
        v.addView(qrBox, ql);
        v.setVisibility(View.GONE);
        return v;
    }
    void updateSetup() {
        String u = Web.url(this);
        setupUrl.setText(Lib.loading ? "Sender werden geladen …" : u);
        qrBox.removeAllViews();
        qrBox.addView(new QrView(this, u), new FrameLayout.LayoutParams(-1, -1));
    }
    void showQr() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL); v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(U.px(30), U.px(20), U.px(30), U.px(10));
        v.addView(new QrView(this, Web.url(this)), new LinearLayout.LayoutParams(U.px(240), U.px(240)));
        TextView t = U.text(this, Web.url(this) + "\n\nMit dem Handy scannen oder am PC öffnen:\nQuellen hinzufügen, EPG-Adressen, Gruppen ausblenden.", 15, U.TEXT);
        t.setPadding(0, U.px(16), 0, 0); t.setGravity(Gravity.CENTER);
        v.addView(t);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Quellen einrichten").setView(v).setPositiveButton("Fertig", null).show();
    }
    void askM3u() {
        final EditText et = new EditText(this);
        et.setHint("http://…/playlist.m3u");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("M3U-Adresse").setView(et)
            .setPositiveButton("Hinzufügen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String u = et.getText().toString().trim();
                if (u.isEmpty()) return;
                Model.Source s = new Model.Source();
                s.id = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFFFL); s.type = "m3u"; s.url = u; s.name = "M3U-Liste";
                List<Model.Source> l = Lib.sources(PlayerActivity.this); l.add(s); Lib.saveSources(PlayerActivity.this, l);
                reloadSources(false);
            } })
            .setNegativeButton("Abbrechen", null).show();
    }

    static final class QrView extends View {
        final io.nayuki.qrcodegen.QrCode qr; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        QrView(Context c, String text) { super(c); qr = io.nayuki.qrcodegen.QrCode.encodeText(text, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM); }
        @Override protected void onDraw(Canvas c) {
            float w = Math.min(getWidth(), getHeight()), pad = w * 0.07f, cell = (w - 2 * pad) / qr.size;
            p.setColor(Color.WHITE); c.drawRoundRect(new RectF(0, 0, w, w), w * 0.06f, w * 0.06f, p);
            p.setColor(0xFF0B0A14);
            for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++)
                if (qr.getModule(x, y)) c.drawRect(pad + x * cell, pad + y * cell, pad + (x + 1) * cell + 0.5f, pad + (y + 1) * cell + 0.5f, p);
        }
    }

    // ------------------------------------------------------------ Lebenszyklus
    @Override protected void onStart() { super.onStart(); if (player == null) { initPlayer(); if (current != null) start(current, false); } }
    @Override protected void onStop() {
        super.onStop();
        if (player != null) { player.release(); player = null; }
    }
    @Override protected void onDestroy() { Web.onChange = null; super.onDestroy(); }
}
