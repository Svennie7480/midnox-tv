package de.midnox.live;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import org.json.*;
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
    JSONObject pendingUpdate;

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
        vodOsd = new Views.VodOsd(this); vodOsd.setVisibility(View.GONE);
        root.addView(vodOsd, new FrameLayout.LayoutParams(-1, -1));
        library = new Library(this, libCb); library.setVisibility(View.GONE);
        root.addView(library, new FrameLayout.LayoutParams(-1, -1));
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
        U.osdPct = U.prefs(this).getInt("osd", 92);
        group = U.prefs(this).getString("group", Lib.ALL);
        Web.start(this);
        Sync.startResponder(this);
        Web.onChange = new Runnable() { public void run() { refreshAll(); } };
        Web.onPin = new Web.PinCb() { public void show(String p) {
            if (p == null) { if (pinDlg != null) pinDlg.dismiss(); pinDlg = null; toast("PC ist angemeldet \u2713"); return; }
            if (pinDlg != null) pinDlg.dismiss();
            TextView t = U.text(PlayerActivity.this, p.charAt(0) + "  " + p.charAt(1) + "  " + p.charAt(2) + "  " + p.charAt(3), 56, Color.WHITE);
            t.setTypeface(U.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(U.px(40), U.px(26), U.px(40), U.px(26));
            pinDlg = new AlertDialog.Builder(PlayerActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Anmeldecode für den PC").setView(t).setPositiveButton("Schließen", null).show();
            final AlertDialog d = pinDlg;
            h.postDelayed(new Runnable() { public void run() { if (pinDlg == d) { d.dismiss(); pinDlg = null; } } }, 3 * 60000L);
        }};
        Lib.loadCache(this);
        for (Model.Source s : Lib.sources(this)) if (s.type.equals("stalker")) Stalker.active.put(s.id, U.prefs(this).getInt("mac_" + s.id, 0));
        Epg.loadCache(this);
        Logos.load(this);
        initPlayer();
        refreshAll();
        if (Cloud.linked(this)) cloudPull(false);
        if (Lib.channels.isEmpty() || Lib.stale()) reloadSources(!Lib.channels.isEmpty());
        else if (Epg.stale()) reloadEpg();
        handleIntent(getIntent(), true);
        if (Update.due(this)) Update.check(this, new Update.Cb() { public void result(JSONObject u) {
            if (u == null) return; pendingUpdate = u;
            showStatus("⬆  Update " + u.optString("versionName") + " verfügbar – Menü-Taste zum Installieren", 8000);
        } });
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
        if (i != null && i.getStringExtra("vod") != null) {
            VodSearch.Hit hit = VodSearch.load(this, i.getStringExtra("vod"));
            if (hit != null) { if (player != null) player.stop(); banner.setVisibility(View.GONE); library.openHit(hit); return; }
        }
        if (i != null && i.getStringArrayExtra("find") != null) { findAndPlay(i.getStringArrayExtra("find"), i.getStringArrayExtra("teams")); return; }
        if (i != null && i.getStringExtra("url") != null) { playRaw(i.getStringExtra("url"), i.getStringExtra("title")); return; }
        if (i != null && i.getStringExtra("search") != null) { if (player != null) player.stop(); library.open(); library.search(i.getStringExtra("search")); return; }
        String tvg = i == null ? null : i.getStringExtra("tvg");
        if (tvg != null) ch = Lib.byTvg(tvg);
        if (ch == null && name != null) ch = Lib.byName(name);
        if (ch == null && i != null && i.getStringExtra("id") != null) ch = Lib.byId(i.getStringExtra("id"));
        if (ch == null && start) ch = Lib.byId(U.prefs(this).getString("last", ""));
        if (ch == null && start && !Lib.channels.isEmpty()) ch = Lib.channels.get(0);
        if (ch != null) play(ch);
        else if (name != null) toast("„" + name + "“ ist nicht in deinen Sendern");
    }

    /** „Wo läuft das?“: passende Sender suchen, den mit dem Spiel im TV-Guide bevorzugen, Treffer als Gruppe anbieten. */
    void findAndPlay(final String[] keys, final String[] teams) {
        if (Lib.channels.isEmpty()) { showStatus("Sender werden geladen …", 0); h.postDelayed(new Runnable() { public void run() { if (Lib.channels.isEmpty() && Lib.loading) h.postDelayed(this, 1000); else findAndPlay(keys, teams); } }, 1000); return; }
        List<Model.Channel> l = Lib.find(keys);
        if (l.isEmpty()) { showStatus("Keinen passenden Sender gefunden (" + keys[0] + ")", 6000); return; }
        Model.Channel best = null; Model.Prog bestProg = null;
        if (teams != null) for (Model.Channel ch : l) {
            Model.Prog p = Epg.now(ch); if (p == null) p = Epg.next(ch);
            if (p == null) continue;
            String t = U.norm(p.title + " " + (p.desc == null ? "" : p.desc));
            for (String tm : teams) { String n = tm == null ? "" : U.norm(tm); if (n.length() >= 3 && t.contains(n)) { best = ch; bestProg = p; break; } }
            if (best != null) break;
        }
        if (best == null) best = l.get(0);
        Lib.findGroup = "🔎 " + keys[0]; Lib.found = l;
        play(best);
        final String msg = bestProg != null ? "Laut TV-Guide: „" + bestProg.title + "“" : l.size() > 1 ? l.size() + " passende Sender – die Liste zeigt alle (🔎 " + keys[0] + ")" : null;
        if (msg != null) h.postDelayed(new Runnable() { public void run() { showStatus(msg, 7000); } }, 1500);
        if (bestProg == null && l.size() > 1) h.postDelayed(new Runnable() { public void run() { openList(); } }, 2500);
    }
    void cloudPull(final boolean quiet) {
        new Thread(new Runnable() { public void run() {
            try {
                final boolean linked = Cloud.linked(PlayerActivity.this);
                if (Cloud.pull(PlayerActivity.this)) {
                    final boolean still = Cloud.linked(PlayerActivity.this);
                    h.post(new Runnable() { public void run() {
                        if (linked && !still) toast("Dieses Gerät ist nicht mehr freigeschaltet – Paket-Quellen entfernt");
                        else toast("Neue Einstellungen von " + Cloud.packageName(PlayerActivity.this) + " übernommen");
                        reloadSources(true);
                    }});
                }
            } catch (Exception ignored) {}
        }}).start();
    }
    void doUpdate() {
        if (pendingUpdate != null) {
            showStatus("Lade Update " + pendingUpdate.optString("versionName") + " …", 0);
            Update.install(this, pendingUpdate, new Runnable() { public void run() { showStatus("Update fehlgeschlagen – bitte erneut versuchen", 5000); } });
            return;
        }
        showStatus("Suche nach Update …", 0);
        Update.check(this, new Update.Cb() { public void result(JSONObject u) {
            if (u == null) { showStatus("midnox Live ist aktuell", 3500); return; }
            pendingUpdate = u;
            new AlertDialog.Builder(PlayerActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Update " + u.optString("versionName") + " verfügbar")
                .setMessage(u.optString("notes", "Neue Version von midnox Live.") + "\n\nJetzt herunterladen und installieren?")
                .setPositiveButton("Installieren", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    showStatus("Lade Update …", 0);
                    Update.install(PlayerActivity.this, pendingUpdate, new Runnable() { public void run() { showStatus("Update fehlgeschlagen – bitte erneut versuchen", 5000); } });
                } })
                .setNegativeButton("Später", null).show();
        } });
    }
    void askCode() {
        final EditText et = new EditText(this);
        et.setHint("6-stelliger Code");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextSize(28);
        et.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Einrichtungscode").setMessage("Den Code hast du von dem bekommen, der dir midnox Live eingerichtet hat.").setView(et)
            .setPositiveButton("Einrichten", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                final String code = et.getText().toString();
                showStatus("Code wird geprüft …", 0);
                new Thread(new Runnable() { public void run() {
                    try {
                        final String m = Cloud.redeem(PlayerActivity.this, code);
                        h.post(new Runnable() { public void run() { showStatus(m + " – Sender werden geladen …", 5000); reloadSources(false); } });
                    } catch (final Exception e) {
                        h.post(new Runnable() { public void run() { showStatus("\u26A0  " + e.getMessage(), 6000); } });
                    }
                }}).start();
            } })
            .setNegativeButton("Abbrechen", null).show();
        et.requestFocus();
    }

    boolean expiryBusy;
    /** Ablaufdaten pruefen (alle 12 Stunden) und einmal am Tag warnen. */
    void checkExpiry(final boolean force, final boolean showAll) {
        if (expiryBusy) return;
        expiryBusy = true;
        new Thread(new Runnable() { public void run() {
            try { if (force || Expiry.due(PlayerActivity.this)) Expiry.refresh(PlayerActivity.this); } catch (Exception ignored) {}
            expiryBusy = false;
            h.post(new Runnable() { public void run() {
                if (showAll) { hideStatus(); showExpiry(); return; }
                List<String> w = Expiry.warnings(PlayerActivity.this);
                String day = new java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
                if (!w.isEmpty() && !day.equals(U.prefs(PlayerActivity.this).getString("exp_warned", ""))) {
                    U.prefs(PlayerActivity.this).edit().putString("exp_warned", day).apply();
                    showStatus("⏳  " + w.get(0) + (w.size() > 1 ? "  (+" + (w.size() - 1) + " weitere – Menü → Zugänge)" : ""), 12000);
                }
            }});
        }}).start();
    }
    void showExpiry() {
        List<Expiry.Entry> l = Expiry.cached(this);
        StringBuilder sb = new StringBuilder();
        String last = null;
        for (Expiry.Entry e : l) {
            if (!e.name.equals(last)) { sb.append(sb.length() > 0 ? "\n" : "").append(e.name).append("\n"); last = e.name; }
            sb.append(e.warn() ? "  ⚠ " : "  ✓ ").append(e.label.isEmpty() ? "" : e.label + ":  ").append(e.text()).append(e.maxCons > 0 ? "  · " + e.maxCons + (e.maxCons == 1 ? " Verbindung" : " Verbindungen") : "").append("\n");
        }
        if (l.isEmpty()) sb.append("Noch nicht geprüft – „Jetzt prüfen“ wählen.");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Zugänge & Ablaufdatum").setMessage(sb.toString())
            .setPositiveButton("Schließen", null)
            .setNeutralButton("Jetzt prüfen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { showStatus("Prüfe Zugänge …", 0); checkExpiry(true, true); } }).show();
    }
    final Runnable tick = new Runnable() { public void run() {
        if (Cloud.due(PlayerActivity.this)) cloudPull(true);
        if (!Lib.loading && !Lib.channels.isEmpty() && Expiry.due(PlayerActivity.this)) checkExpiry(false, false);
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
        if (!empty && current == null && !vodMode) handleIntent(null, true);
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
                if (vodMode) { vodState(st); return; }
                h.removeCallbacks(stallWatch);
                if (st == Player.STATE_BUFFERING) { if (!tryMsg) showStatus("Lädt …", 0); h.postDelayed(stallWatch, everReady ? 25000 : 15000); }
                else if (st == Player.STATE_READY) { hideStatus(); tryMsg = false; retries = 0; everReady = true; updateBadge(); succeeded(); }
                else if (st == Player.STATE_ENDED && current != null) h.postDelayed(new Runnable() { public void run() { if (current != null) runTry(); } }, 1500);
            }
            @Override public void onPlayerError(PlaybackException e) {
                if (vodMode) { showStatus("Film lässt sich nicht abspielen (" + e.getErrorCodeName().replace("ERROR_CODE_", "").toLowerCase(Locale.ROOT).replace('_', ' ') + ")", 0); return; }
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
        Format a = player == null ? null : player.getAudioFormat();
        String b = "", tech = "";
        if (f != null && f.height > 0) {
            b = f.height >= 2000 ? "4K" : f.height >= 1000 ? "FHD" : f.height >= 700 ? "HD" : "SD";
            tech = f.width + "×" + f.height;
            if (f.frameRate > 0) tech += " · " + Math.round(f.frameRate) + " fps";
            String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
            String codec = m.contains("hevc") ? "HEVC" : m.contains("avc") ? "H.264" : m.contains("mpeg2") ? "MPEG-2" : m.contains("av01") ? "AV1" : m.contains("vp9") ? "VP9" : "";
            if (!codec.isEmpty()) tech += " · " + codec;
            if (f.bitrate > 0) tech += " · " + String.format(Locale.GERMANY, "%.1f Mbit/s", f.bitrate / 1e6);
        }
        if (a != null) {
            String am = a.sampleMimeType == null ? "" : a.sampleMimeType;
            String ac = am.contains("eac3") ? "Dolby Digital+" : am.contains("ac3") ? "Dolby Digital" : am.contains("mp4a") || am.contains("aac") ? "AAC" : am.contains("mpeg") ? "MP2" : "";
            String ch = a.channelCount >= 6 ? "5.1" : a.channelCount == 2 ? "Stereo" : a.channelCount == 1 ? "Mono" : "";
            String lang = a.language == null || a.language.equals("und") ? "" : new Locale(a.language).getDisplayLanguage(Locale.GERMANY);
            String au = (ac + " " + ch).trim() + (lang.isEmpty() ? "" : " " + lang);
            if (!au.isEmpty()) tech += (tech.isEmpty() ? "" : "   ·   ") + "\u266B " + au;
            int tracks = audioTracks().size();
            if (tracks > 1) tech += "  (" + tracks + " Tonspuren)";
        }
        banner.badge = b; banner.tech = tech; banner.invalidate();
    }

    @Override public void play(Model.Channel ch) {
        if (guide.getVisibility() == View.VISIBLE) guide.setVisibility(View.GONE);
        if (list.getVisibility() == View.VISIBLE) {
            closeList();
            if (current != null && current.id.equals(ch.id)) { showBanner(ch); return; }
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
        // eigene Zuordnung (am PC festgelegt), in der dort gewaehlten Reihenfolge
        for (Model.Channel o : Mapping.alternatives(this, ch)) if (plan.size() < 24) addTries(o);
        // derselbe Sender aus anderen Quellen (gleicher Name)
        if (U.prefs(this).getBoolean("fallback", true)) {
            String n = U.norm(ch.name);
            for (Model.Channel o : Lib.channels) if (!o.id.equals(ch.id) && !o.src.equals(ch.src) && U.norm(o.name).equals(n) && plan.size() < 24 && !planned(o)) addTries(o);
        }
    }
    boolean planned(Model.Channel o) { for (Try t : plan) if (t.ch.id.equals(o.id)) return true; return false; }
    void addTries(Model.Channel ch) {
        if (!ch.cmd.isEmpty()) {
            Model.Source s = Lib.source(this, ch.src);
            final java.util.List<String> ms = s == null ? new java.util.ArrayList<String>() : Stalker.macs(s);
            int n = Math.max(1, ms.size()), st = s == null ? 0 : Stalker.activeIndex(s);
            for (int k = 0; k < n; k++) { Try t = new Try(); t.ch = ch; t.mac = (st + k) % n; t.macs = n;
                String addr = t.mac < ms.size() ? ms.get(t.mac) : "";
                t.label = (s == null ? "" : s.name) + (n > 1 ? " · MAC " + (t.mac + 1) + "/" + n : "") + (addr.isEmpty() ? "" : "  " + addr);
                plan.add(t); }
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

    /** Von Hand auf die nächste MAC umschalten und den aktuellen Sender neu starten. */
    void skipMac() {
        if (current == null) return;
        Model.Source s = Lib.source(this, current.src);
        if (s == null || Stalker.macs(s).size() < 2) { toast("Für diese Quelle gibt es nur eine MAC-Adresse"); return; }
        int i = Stalker.nextMac(s);
        U.prefs(this).edit().putInt("mac_" + s.id, i).apply();
        toast("Wechsel auf MAC " + (i + 1) + "/" + Stalker.macs(s).size() + "  ·  " + Stalker.curMac(s));
        buildPlan(current); runTry();
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
    @Override public void library() { closeList(); openLibrary(); }
    @Override public void library(int tab) {
        closeList();
        banner.setVisibility(View.GONE); menu.setVisibility(View.GONE); guide.setVisibility(View.GONE);
        if (player != null) player.stop();
        hideStatus();
        library.openTab(tab);
    }
    @Override public void searchVod(String q) { closeList(); openLibrary(); library.search(q); }
    @Override public void search() {
        final EditText et = new EditText(this);
        et.setHint("Sender, Film oder Serie …");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        final AlertDialog d = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Suche").setMessage("Durchsucht Live-Sender und Filme & Serien.").setView(et)
            .setPositiveButton("Suchen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int w) { String q = et.getText().toString().trim(); if (q.length() >= 1) doSearch(q); } })
            .setNegativeButton("Abbrechen", null).create();
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
            String q = et.getText().toString().trim(); if (q.length() >= 1) { d.dismiss(); doSearch(q); } return true; } });
        d.show(); et.requestFocus();
    }
    void doSearch(String q) {
        if (list.getVisibility() != View.VISIBLE) { banner.setVisibility(View.GONE); list.setVisibility(View.VISIBLE); }
        list.showSearch(q);
        hideLater(list, 30000);
    }
    @Override public void toast(String s) { showStatus(s, 3500); }
    @Override public void manageGroups() {
        List<String[]> all = Lib.allGroups(this);
        final Set<String> hid = Lib.hiddenGroups(this);
        final String[] names = new String[all.size()]; final boolean[] on = new boolean[all.size()];
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) { names[i] = all.get(i)[0]; on[i] = !hid.contains(names[i]); labels[i] = (names[i].isEmpty() ? "(ohne Gruppe)" : names[i]) + "   · " + all.get(i)[1]; }
        pickVisible("Welche Sender-Gruppen anzeigen?", labels, on, new Runnable() { public void run() {
            Set<String> h = new HashSet<>();
            for (int i = 0; i < names.length; i++) if (!on[i]) h.add(names[i]);
            Lib.setHidden(PlayerActivity.this, h);
            list.open(Lib.MANAGE, current == null ? null : current.id);
        } });
    }
    void options(String title, final String[] labels, final Runnable[] acts) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title)
            .setItems(labels, new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { acts[w].run(); } })
            .setNegativeButton("Abbrechen", null).show();
    }
    /** Quelle (Anbieter) einer Gruppe – der erste Sender der Gruppe bestimmt sie. */
    Model.Source srcOfGroup(String g) {
        for (Model.Channel ch : Lib.inGroup(this, g)) { Model.Source s = Lib.source(this, ch.src); if (s != null) return s; }
        return null;
    }
    @Override public void groupOptions(final String g) {
        final Model.Source src = srcOfGroup(g);
        final List<String> l = new ArrayList<>(); final List<Runnable> a = new ArrayList<>();
        if (src != null) { l.add("📡  EPG-Adresse für „" + src.name + "“ …"); a.add(new Runnable() { public void run() { askSourceEpg(src); } }); }
        l.add("🚫  Gruppe „" + g + "“ ausblenden"); a.add(new Runnable() { public void run() { Lib.hideGroup(PlayerActivity.this, g, true); list.afterHide(); toast("„" + g + "“ ausgeblendet"); } });
        l.add("👁  Alle Gruppen verwalten …"); a.add(new Runnable() { public void run() { manageGroups(); } });
        options("Anbieter" + (src != null ? ": " + src.name : "") + "  ·  " + g, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }
    @Override public void channelOptions(final Model.Channel ch) {
        final boolean f = Lib.isFav(this, ch.id);
        final List<String> l = new ArrayList<>(); final List<Runnable> a = new ArrayList<>();
        l.add("▶  Abspielen"); a.add(new Runnable() { public void run() { play(ch); } });
        l.add(f ? "☆  Aus Favoriten entfernen" : "★  Zu Favoriten"); a.add(new Runnable() { public void run() { fav(ch); if (list.group().equals(Lib.FAV)) list.loadGroup(); list.invalidate(); } });
        l.add("📅  Programm (TV-Guide)"); a.add(new Runnable() { public void run() { guide(ch); } });
        l.add("📡  EPG-Sender zuordnen …"); a.add(new Runnable() { public void run() { epgAssign(ch); } });
        if (!vodMode && current != null && current.id.equals(ch.id) && audioTracks().size() > 1) { l.add("🔊  Tonspur wechseln"); a.add(new Runnable() { public void run() { nextAudio(); } }); }
        l.add("🚫  Gruppe „" + ch.group + "“ ausblenden"); a.add(new Runnable() { public void run() { Lib.hideGroup(PlayerActivity.this, ch.group, true); list.afterHide(); toast("„" + ch.group + "“ ausgeblendet"); } });
        options(ch.name, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }
    /** EPG-Adresse (XMLTV) für einen ganzen Anbieter setzen. */
    void askSourceEpg(final Model.Source src) {
        final EditText et = new EditText(this);
        et.setText(src.epg); et.setHint("https://… .xml / .xml.gz");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("EPG für „" + src.name + "“")
            .setMessage("Eigene EPG-Adresse (XMLTV) für alle Sender dieses Anbieters. Leer lassen = Standard (epgshare).")
            .setView(et)
            .setPositiveButton("Speichern", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                List<Model.Source> ss = Lib.sources(PlayerActivity.this);
                for (Model.Source s : ss) if (s.id.equals(src.id)) s.epg = et.getText().toString().trim();
                Lib.saveSources(PlayerActivity.this, ss);
                toast("EPG gespeichert – wird neu geladen …"); reloadEpg();
            } })
            .setNegativeButton("Abbrechen", null).show();
    }
    /** Einem Sender die passende EPG-Kennung zuweisen (Vorschläge + manuell). */
    void epgAssign(final Model.Channel ch) {
        final List<String> ids = Epg.candidates(ch, 12);
        final String cur = Epg.key(ch);
        final List<String> l = new ArrayList<>(); final List<Runnable> a = new ArrayList<>();
        for (final String id : ids) {
            l.add((id.equals(cur) ? "✓  " : "      ") + id);
            a.add(new Runnable() { public void run() { Epg.setOverride(PlayerActivity.this, ch.id, id); toast("EPG „" + id + "“ zugewiesen"); refreshAll(); } });
        }
        l.add("✎  Andere Kennung eingeben …"); a.add(new Runnable() { public void run() { askEpgId(ch, cur); } });
        l.add("↺  Automatisch (Standard)"); a.add(new Runnable() { public void run() { Epg.setOverride(PlayerActivity.this, ch.id, ""); toast("EPG wieder automatisch"); refreshAll(); } });
        options("EPG für „" + ch.name + "“" + (cur != null ? "  (jetzt: " + cur + ")" : "  (keins gefunden)"), l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }
    void askEpgId(final Model.Channel ch, String cur) {
        final EditText et = new EditText(this);
        et.setText(cur == null ? "" : cur); et.setHint("z. B. Das Erste.de");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("EPG-Kennung für „" + ch.name + "“")
            .setMessage("Die tvg-id aus der EPG-Quelle (epgshare). Leer = automatisch zuordnen.")
            .setView(et)
            .setPositiveButton("Speichern", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                Epg.setOverride(PlayerActivity.this, ch.id, et.getText().toString()); toast("Gespeichert"); refreshAll();
            } })
            .setNegativeButton("Abbrechen", null).show();
    }
    /** Auswahlliste mit Haken (Fernbedienung: hoch/runter, OK hakt an/ab). */
    void pickVisible(String title, String[] labels, final boolean[] on, final Runnable save) {
        if (labels.length == 0) { showStatus("Noch nichts geladen", 3000); return; }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title)
            .setMultiChoiceItems(labels, on, new android.content.DialogInterface.OnMultiChoiceClickListener() { public void onClick(android.content.DialogInterface d, int w, boolean v) { on[w] = v; } })
            .setPositiveButton("Speichern", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { save.run(); } })
            .setNeutralButton("Alle an", new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) { java.util.Arrays.fill(on, true); save.run(); } })
            .setNegativeButton("Abbrechen", null).show();
    }

    // ------------------------------------------------------------ Quickübertragen (LAN-Sync ohne Server)
    void quickSync() {
        final String code = U.prefs(this).getString("sync_pin", "");
        if (code.isEmpty()) { askSyncCode(); return; }
        options("⚡ Quickübertragen", new String[]{
            "📤  An andere Geräte senden",
            "📥  Von einem Gerät holen",
            "🔑  Netzwerk-Code ändern (" + code + ")" },
            new Runnable[]{
                new Runnable() { public void run() { syncDiscover(true); } },
                new Runnable() { public void run() { syncDiscover(false); } },
                new Runnable() { public void run() { askSyncCode(); } } });
    }
    void askSyncCode() {
        final EditText et = new EditText(this);
        et.setHint("z. B. 1234"); et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(U.prefs(this).getString("sync_pin", ""));
        et.setTextSize(28); et.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Netzwerk-Code")
            .setMessage("Vergib eine kurze Zahl und trage auf allen deinen Geräten dieselbe ein. Nur Geräte mit gleichem Code finden sich und übertragen zueinander – so landet nichts versehentlich bei fremden Geräten.")
            .setView(et)
            .setPositiveButton("Speichern", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String v = et.getText().toString().trim();
                U.prefs(PlayerActivity.this).edit().putString("sync_pin", v).apply();
                toast(v.isEmpty() ? "Quickübertragen ausgeschaltet" : "Netzwerk-Code gespeichert ✓");
            } })
            .setNegativeButton("Abbrechen", null).show();
    }
    void syncDiscover(final boolean send) {
        showStatus("Suche Geräte im Netzwerk …", 0);
        new Thread(new Runnable() { public void run() {
            final List<Sync.Peer> peers = Sync.discover();
            h.post(new Runnable() { public void run() {
                hideStatus();
                if (peers.isEmpty()) {
                    new AlertDialog.Builder(PlayerActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Kein Gerät gefunden")
                        .setMessage("Es wurde kein weiteres midnox-Live-Gerät gefunden.\n\n• Läuft midnox Live auf dem anderen Fernseher (einmal geöffnet)?\n• Gleicher Netzwerk-Code auf beiden Geräten?\n• Beide im selben WLAN/Netzwerk?")
                        .setPositiveButton("OK", null).show();
                    return;
                }
                if (send) {
                    final String[] labels = new String[peers.size() + 1];
                    final Runnable[] acts = new Runnable[peers.size() + 1];
                    labels[0] = "➤  An alle " + peers.size() + " Geräte senden";
                    acts[0] = new Runnable() { public void run() { syncRun(peers, true, -1); } };
                    for (int i = 0; i < peers.size(); i++) { final int idx = i;
                        labels[i + 1] = peers.get(i).name + "  (" + peers.get(i).count + " Sender)";
                        acts[i + 1] = new Runnable() { public void run() { syncRun(peers, true, idx); } }; }
                    options("📤 An welches Gerät senden?", labels, acts);
                } else {
                    final String[] labels = new String[peers.size()];
                    final Runnable[] acts = new Runnable[peers.size()];
                    for (int i = 0; i < peers.size(); i++) { final int idx = i;
                        labels[i] = peers.get(i).name + "  (" + peers.get(i).count + " Sender)";
                        acts[i] = new Runnable() { public void run() { syncRun(peers, false, idx); } }; }
                    options("📥 Von welchem Gerät holen?", labels, acts);
                }
            } });
        }}).start();
    }
    void syncRun(final List<Sync.Peer> peers, final boolean send, final int idx) {
        showStatus(send ? "Übertrage …" : "Hole Daten …", 0);
        new Thread(new Runnable() { public void run() {
            final List<String> msgs = new ArrayList<>();
            if (send) {
                if (idx < 0) for (Sync.Peer p : peers) msgs.add(Sync.push(PlayerActivity.this, p));
                else msgs.add(Sync.push(PlayerActivity.this, peers.get(idx)));
            } else msgs.add(Sync.pull(PlayerActivity.this, peers.get(idx)));
            h.post(new Runnable() { public void run() {
                hideStatus();
                new AlertDialog.Builder(PlayerActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(send ? "Gesendet" : "Übernommen")
                    .setMessage(android.text.TextUtils.join("\n", msgs))
                    .setPositiveButton("OK", null).show();
            } });
        }}).start();
    }

    // ------------------------------------------------------------ Mediathek (Filme und Serien)
    Library library;
    Views.VodOsd vodOsd;
    boolean vodMode;
    Vod.Item vodItem; Vod.Episode vodEp; List<Vod.Episode> vodNext;
    final Library.Cb libCb = new Library.Cb() {
        public void playVod(Vod.Item it, Vod.Episode ep, List<Vod.Episode> next, long startMs) { startVod(it, ep, next, startMs); }
        public void close() { closeLibrary(); }
        public void toast(String s) { showStatus(s, 3500); }
        public void catOptions(String name, Runnable hide, Runnable manage) {
            options(name, new String[]{"\uD83D\uDEAB  Kategorie ausblenden", "\uD83D\uDC41  Alle Kategorien verwalten …"}, new Runnable[]{hide, manage});
        }
        public void manageCats(List<Vod.Cat> all, Set<String> hidden, final Runnable done) {
            final List<Vod.Cat> l = all;
            final boolean[] on = new boolean[l.size()]; String[] labels = new String[l.size()];
            Set<String> srcs = new HashSet<>(); for (Vod.Cat k : l) srcs.add(k.src);
            for (int i = 0; i < l.size(); i++) {
                Vod.Cat k = l.get(i); on[i] = !hidden.contains(Lib.vodKey(k));
                labels[i] = k.name + (srcs.size() > 1 && Lib.srcNames.containsKey(k.src) ? "   · " + Lib.srcNames.get(k.src) : "");
            }
            pickVisible("Welche Kategorien anzeigen?", labels, on, new Runnable() { public void run() {
                Set<String> h = Lib.hiddenVod(PlayerActivity.this);
                for (int i = 0; i < l.size(); i++) { String key = Lib.vodKey(l.get(i)); if (on[i]) h.remove(key); else h.add(key); }
                Lib.setHiddenVod(PlayerActivity.this, h);
                done.run();
            } });
        }
        public void askSearch(String last) {
            final EditText et = new EditText(PlayerActivity.this);
            et.setHint("Film oder Serie, z. B. Avatar");
            et.setText(last); et.setSelectAllOnFocus(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            final AlertDialog d = new AlertDialog.Builder(PlayerActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Filme & Serien suchen").setView(et)
                .setPositiveButton("Suchen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int w) { String q = et.getText().toString().trim(); if (q.length() >= 2) library.search(q); } })
                .setNegativeButton("Abbrechen", null).create();
            et.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
                String q = et.getText().toString().trim(); if (q.length() >= 2) { d.dismiss(); library.search(q); } return true; } });
            d.show(); et.requestFocus();
        }
    };
    void openLibrary() {
        banner.setVisibility(View.GONE); menu.setVisibility(View.GONE); guide.setVisibility(View.GONE);
        if (player != null) player.stop();   // Live-TV pausieren, spart Bandbreite
        hideStatus();
        library.open();
    }
    void closeLibrary() {
        library.setVisibility(View.GONE);
        if (current != null) { buildPlan(current); runTry(); showBanner(current); }
    }
    void startVod(final Vod.Item it, final Vod.Episode ep, List<Vod.Episode> next, final long startMs) {
        vodMode = true; vodItem = it; vodEp = ep; vodNext = next == null ? new ArrayList<Vod.Episode>() : new ArrayList<>(next);
        library.setVisibility(View.GONE);
        vodOsd.title = it.name; vodOsd.sub = ep == null ? (it.year + (it.duration.isEmpty() ? "" : "  ·  " + it.duration)) : "Staffel " + ep.season + "  ·  Folge " + ep.num + (ep.title.isEmpty() || ep.title.startsWith("Folge ") ? "" : "  ·  " + ep.title);
        vodOsd.pos = startMs; vodOsd.dur = 0; vodOsd.paused = false;
        showVodOsd();
        showStatus("Lädt …", 0);
        new Thread(new Runnable() { public void run() {
            try {
                final String u = Vod.link(PlayerActivity.this, it, ep);
                final Model.Source s = Lib.source(PlayerActivity.this, it.src);
                h.post(new Runnable() { public void run() {
                    if (!vodMode || vodItem != it || vodEp != ep) return;
                    DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory()
                        .setUserAgent(s != null && s.type.equals("stalker") ? Stalker.MAG_UA : U.UA)
                        .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000);
                    MediaItem.Builder mi = new MediaItem.Builder().setUri(u);
                    if (u.toLowerCase(Locale.ROOT).contains(".m3u8")) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
                    player.setMediaSource(new DefaultMediaSourceFactory(ds).createMediaSource(mi.build()));
                    player.prepare();
                    if (startMs > 0) player.seekTo(startMs);
                    player.setPlayWhenReady(true);
                    h.removeCallbacks(vodSaver); h.postDelayed(vodSaver, 10000);
                }});
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { if (vodMode) showStatus("\u26A0  " + e.getMessage(), 0); } });
            }
        }}).start();
    }
    /** Direkter Stream (z. B. Mediathek-Treffer aus der Launcher-Suche): im midnox-Player abspielen. */
    void playRaw(final String url, final String title) {
        if (url == null || url.isEmpty() || player == null) return;
        vodMode = true; vodItem = null; vodEp = null; vodNext = new ArrayList<>();
        library.setVisibility(View.GONE); banner.setVisibility(View.GONE); if (list != null) closeList();
        vodOsd.title = title == null || title.isEmpty() ? "Mediathek" : title; vodOsd.sub = "Mediathek";
        vodOsd.pos = 0; vodOsd.dur = 0; vodOsd.paused = false;
        showVodOsd(); showStatus("Lädt …", 0);
        DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory().setUserAgent(U.UA)
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000);
        MediaItem.Builder mi = new MediaItem.Builder().setUri(url);
        if (url.toLowerCase(Locale.ROOT).contains(".m3u8")) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaSource(new DefaultMediaSourceFactory(ds).createMediaSource(mi.build()));
        player.prepare(); player.setPlayWhenReady(true);
    }
    final Runnable vodSaver = new Runnable() { public void run() {
        if (!vodMode || player == null) return;
        saveVodPos();
        if (vodOsd.getVisibility() == View.VISIBLE) { vodOsd.pos = player.getCurrentPosition(); vodOsd.invalidate(); }
        h.postDelayed(this, 10000);
    }};
    void saveVodPos() {
        if (player == null || vodItem == null) return;
        long d = player.getDuration();
        if (d > 0 && d != C.TIME_UNSET) Vod.savePos(this, vodItem, vodEp, player.getCurrentPosition(), d);
    }
    void vodState(int st) {
        if (st == Player.STATE_BUFFERING) showStatus("Lädt …", 0);
        else if (st == Player.STATE_READY) { hideStatus(); updateBadge(); vodOsd.tech = banner.tech; vodOsd.dur = Math.max(0, player.getDuration()); vodOsd.invalidate(); }
        else if (st == Player.STATE_ENDED) {
            saveVodPos();
            if (vodNext != null && !vodNext.isEmpty()) {
                Vod.Episode n = vodNext.remove(0);
                toast("Nächste Folge: " + n.num + (n.title.isEmpty() || n.title.startsWith("Folge ") ? "" : " · " + n.title));
                startVod(vodItem, n, vodNext, 0);
            } else stopVod();
        }
    }
    void stopVod() {
        saveVodPos();
        vodMode = false;
        h.removeCallbacks(vodSaver);
        if (player != null) player.stop();
        vodOsd.setVisibility(View.GONE); hideStatus();
        library.setVisibility(View.VISIBLE); library.invalidate();
    }
    void showVodOsd() {
        if (player != null && vodMode) { vodOsd.pos = Math.max(0, player.getCurrentPosition()); long d = player.getDuration(); if (d > 0 && d != C.TIME_UNSET) vodOsd.dur = d; vodOsd.paused = !player.getPlayWhenReady(); }
        vodOsd.setVisibility(View.VISIBLE); vodOsd.invalidate();
        h.removeCallbacks(vodOsdTick); h.postDelayed(vodOsdTick, 1000);
        hideLater(vodOsd, vodOsd.paused ? 3600000L : 5000);
    }
    final Runnable vodOsdTick = new Runnable() { public void run() {
        if (vodOsd.getVisibility() != View.VISIBLE || player == null) return;
        vodOsd.pos = Math.max(0, player.getCurrentPosition()); vodOsd.invalidate();
        h.postDelayed(this, 1000);
    }};
    long seekAcc;
    void vodKey(int k, int repeat) {
        if (player == null) return;
        long step = 0;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.setPlayWhenReady(!player.getPlayWhenReady()); saveVodPos(); showVodOsd(); return;
            case KeyEvent.KEYCODE_DPAD_LEFT: step = -10000; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: step = 30000; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: step = -60000; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: step = 60000; break;
            case KeyEvent.KEYCODE_MENU: openMenu(); return;
            default: showVodOsd(); return;
        }
        if (repeat > 6) step *= 6; else if (repeat > 2) step *= 3;   // gedrueckt halten = schneller
        long d = player.getDuration();
        long np = Math.max(0, player.getCurrentPosition() + step);
        if (d > 0 && d != C.TIME_UNSET) np = Math.min(np, d - 2000);
        player.seekTo(np);
        showVodOsd(); vodOsd.pos = np; vodOsd.invalidate();
    }
    @Override public void fav(Model.Channel ch) { toast(Lib.toggleFav(this, ch.id) ? "★ " + ch.name + " ist jetzt Favorit" : ch.name + " aus den Favoriten entfernt"); }

    // ------------------------------------------------------------ Tasten
    // ------------------------------------------------------------ Touch (Tablet/Handy)
    GestureDetector gestures;
    AlertDialog pinDlg;
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures == null) gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            float acc;
            @Override public boolean onDown(MotionEvent e) { acc = 0; return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                float x = e.getX(), y = e.getY();
                if (menu.getVisibility() != View.VISIBLE && vodMode) { if (vodOsd.getVisibility() == View.VISIBLE) vodKey(KeyEvent.KEYCODE_DPAD_CENTER, 0); else showVodOsd(); return true; }
                if (menu.getVisibility() != View.VISIBLE && library.getVisibility() == View.VISIBLE) { library.tap(x, y); return true; }
                if (menu.getVisibility() == View.VISIBLE) { if (!menu.tap(x, y)) menu.setVisibility(View.GONE); return true; }
                if (guide.getVisibility() == View.VISIBLE) { guide.tap(x, y); return true; }
                if (list.getVisibility() == View.VISIBLE) { if (!list.tap(x, y)) closeList(); else hideLater(list, 30000); return true; }
                if (setup.getVisibility() == View.VISIBLE) { askCode(); return true; }
                if (banner.getVisibility() != View.VISIBLE && current != null) showBanner(current); else openList();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (library.getVisibility() == View.VISIBLE) { library.longOk(); return; }
                if (list.getVisibility() == View.VISIBLE) { list.longOk(); return; }
                openMenu();
            }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                float row = 60 * U.dp;
                if (library.getVisibility() == View.VISIBLE) {
                    acc += dy;
                    while (Math.abs(acc) >= row * 2) { int n = acc > 0 ? 1 : -1; acc -= n * row * 2; library.scrollRows(n); }
                    return true;
                }
                if (vodMode) { acc += dx; while (Math.abs(acc) >= row) { int n = acc > 0 ? 1 : -1; acc -= n * row; vodKey(n > 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, 0); } return true; }
                if (list.getVisibility() == View.VISIBLE || guide.getVisibility() == View.VISIBLE) {
                    acc += dy;
                    while (Math.abs(acc) >= row) { int n = acc > 0 ? 1 : -1; acc -= n * row; if (guide.getVisibility() == View.VISIBLE) guide.scrollRows(n); else list.scrollRows(n); }
                    return true;
                }
                return false;
            }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (menu.getVisibility() == View.VISIBLE || list.getVisibility() == View.VISIBLE || guide.getVisibility() == View.VISIBLE || setup.getVisibility() == View.VISIBLE) return false;
                if (Math.abs(vy) > Math.abs(vx) && Math.abs(vy) > 800) { zap(vy < 0 ? 1 : -1); return true; }   // nach oben wischen = naechster Sender
                if (Math.abs(vx) > 800 && vx > 0) { openList(); return true; }                                   // nach rechts wischen = Senderliste
                return false;
            }
        });
        gestures.onTouchEvent(ev);
        return true;
    }

    boolean okDown, okLong;
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // OK lange druecken = Optionen (nur in Senderliste und Mediathek); kurzes OK wird dann beim Loslassen ausgefuehrt
        boolean okKey = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        boolean optView = menu.getVisibility() != View.VISIBLE && !vodMode && (library.getVisibility() == View.VISIBLE || (list.getVisibility() == View.VISIBLE && guide.getVisibility() != View.VISIBLE));
        if (okKey && optView) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { okDown = true; okLong = false; return true; }
                if (!okLong && okDown && (e.isLongPress() || e.getRepeatCount() >= 12)) {
                    okLong = true;
                    if (library.getVisibility() == View.VISIBLE ? library.longOk() : list.longOk()) return true;
                    okLong = false; okDown = false;
                    if (library.getVisibility() == View.VISIBLE) library.key(k); else { list.key(k, false); hideLater(list, 30000); }
                }
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                boolean shortPress = okDown && !okLong; okDown = false; okLong = false;
                if (shortPress) { if (library.getVisibility() == View.VISIBLE) library.key(k); else { list.key(k, false); hideLater(list, 30000); } }
                return true;
            }
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU ? true : super.dispatchKeyEvent(e);
        boolean rep = e.getRepeatCount() > 0;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (menu.getVisibility() != View.VISIBLE && vodMode) { stopVod(); return true; }
            if (menu.getVisibility() != View.VISIBLE && library.getVisibility() == View.VISIBLE) { library.key(k); return true; }
            back(); return true;
        }
        if (setup.getVisibility() == View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { askCode(); return true; }
            if (k == KeyEvent.KEYCODE_MENU) { openMenu(); return true; }
            return super.dispatchKeyEvent(e);
        }
        if (menu.getVisibility() == View.VISIBLE) { menu.key(k); return true; }
        if (vodMode) { vodKey(k, e.getRepeatCount()); return true; }
        if (library.getVisibility() == View.VISIBLE) { library.key(k); return true; }
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
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: if (current != null) showBanner(current); return true;   // OK = nur OSD einblenden
            case KeyEvent.KEYCODE_DPAD_LEFT: openList(); return true;                                                               // links = Senderliste der aktuellen Gruppe
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
        hideLater(banner, osdMs());
    }
    /** Einstellbare OSD-Anzeigedauer (Standard 6 s). */
    static final int[] OSD_SECS = {4, 6, 10, 15, 30};
    int osdMs() { return U.prefs(this).getInt("osd_secs", 6) * 1000; }
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


    // ------------------------------------------------------------ Menue
    void openMenu() {
        List<String> l = new ArrayList<>(); List<Runnable> a = new ArrayList<>();
        if (vodMode) {
            final List<String[]> audio = audioTracks();
            if (audio.size() > 1) { l.add("\uD83D\uDD0A  Tonspur wechseln"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); nextAudio(); } }); }
            if (vodNext != null && !vodNext.isEmpty()) { l.add("\u23ED  Nächste Folge"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); saveVodPos(); Vod.Episode n = vodNext.remove(0); startVod(vodItem, n, vodNext, 0); } }); }
            l.add("\u21BA  Von vorn"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); player.seekTo(0); showVodOsd(); } });
            l.add("\u25A3  Bildformat: " + new String[]{"Anpassen", "Füllen", "Zoom"}[scale]); a.add(new Runnable() { public void run() { scale = (scale + 1) % 3; U.prefs(PlayerActivity.this).edit().putInt("scale", scale).apply(); layoutVideo(); openMenu(); } });
            l.add(osdLabel()); final int oi = l.size() - 1; a.add(new Runnable() { public void run() { setOsd(U.osdPct >= 100 ? 30 : U.osdPct + 10, oi); } });
            l.add("\u23F9  Film beenden"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); stopVod(); } });
            menu.set(vodItem == null ? "Film" : vodItem.name, l, a);
            for (int i = 0; i < l.size(); i++) if (l.get(i).startsWith("\u25D0")) { final int idx = i;
                menu.sliders.put(i, new Runnable[]{ new Runnable() { public void run() { setOsd(U.osdPct - 10, idx); } }, new Runnable() { public void run() { setOsd(U.osdPct + 10, idx); } } }); }
            menu.setVisibility(View.VISIBLE);
            return;
        }
        l.add("\uD83C\uDFAC  Filme & Serien"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); openLibrary(); } });
        if (current != null) {
            l.add("📅  TV-Guide"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); openGuide(); } });
            final boolean fav = Lib.isFav(this, current.id);
            l.add(fav ? "☆  Aus Favoriten entfernen" : "★  Zu Favoriten"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); fav(current); } });
            if (previous != null) { l.add("↺  Letzter Sender: " + previous.name); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); play(previous); } }); }
            final List<String[]> audio = audioTracks();
            if (audio.size() > 1) { l.add("🔊  Tonspur wechseln"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); nextAudio(); } }); }
            final Model.Source cs = Lib.source(this, current.src);
            if (cs != null && Stalker.macs(cs).size() > 1) {
                l.add("➡  Nächste MAC  (jetzt " + (Stalker.activeIndex(cs) + 1) + "/" + Stalker.macs(cs).size() + ")");
                a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); skipMac(); } });
            }
            final int osdIdx = l.size();
            l.add(osdLabel()); a.add(new Runnable() { public void run() { setOsd(U.osdPct >= 100 ? 30 : U.osdPct + 10, osdIdx); } });
            final int cur = U.prefs(this).getInt("osd_secs", 6);
            l.add("🕒  OSD-Anzeigedauer: " + cur + " s"); a.add(new Runnable() { public void run() {
                int i = 0; for (int k = 0; k < OSD_SECS.length; k++) if (OSD_SECS[k] == cur) i = k;
                int next = OSD_SECS[(i + 1) % OSD_SECS.length];
                U.prefs(PlayerActivity.this).edit().putInt("osd_secs", next).apply();
                if (current != null) showBanner(current); openMenu(); } });
            l.add("▣  Bildformat: " + new String[]{"Anpassen", "Füllen", "Zoom"}[scale]); a.add(new Runnable() { public void run() {
                scale = (scale + 1) % 3; U.prefs(PlayerActivity.this).edit().putInt("scale", scale).apply(); layoutVideo(); openMenu(); } });
        }
        l.add("⏳  Zugänge & Ablaufdatum"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); showExpiry(); } });
        l.add(pendingUpdate != null ? "\u2B06  Update auf " + pendingUpdate.optString("versionName") + " installieren" : "\u2B06  Nach Update suchen"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); doUpdate(); } });
        l.add("\uD83D\uDD11  Einrichtungscode eingeben" + (Cloud.linked(this) ? "  (" + Cloud.packageName(this) + ")" : "")); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); askCode(); } });
        l.add("⚙  Quellen einrichten (Handy/PC)"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); showQr(); } });
        l.add("⚡  Quickübertragen (anderes Gerät im Netzwerk)"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); quickSync(); } });
        final boolean merged = Lib.mergeDupes(this);
        l.add("🔗  Doppelte Sender zusammenfassen: " + (merged ? "An" : "Aus")); a.add(new Runnable() { public void run() {
            U.prefs(PlayerActivity.this).edit().putBoolean("merge_dupes", !merged).apply();
            Lib.clearDedup();
            toast(!merged ? "Gleichnamige Sender werden zusammengefasst" : "Alle Sender werden einzeln gezeigt");
            refreshAll(); openMenu(); } });
        l.add("↻  Sender und Programm neu laden"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); reloadSources(false); } });
        l.add("➕  M3U-Adresse hier eingeben"); a.add(new Runnable() { public void run() { menu.setVisibility(View.GONE); askM3u(); } });
        menu.set(current == null ? "midnox Live" : current.num + "  " + current.name, l, a);
        for (int i = 0; i < l.size(); i++) if (l.get(i).startsWith("\u25D0")) { final int idx = i;
            menu.sliders.put(i, new Runnable[]{ new Runnable() { public void run() { setOsd(U.osdPct - 10, idx); } }, new Runnable() { public void run() { setOsd(U.osdPct + 10, idx); } } }); }
        menu.setVisibility(View.VISIBLE);
    }
    String osdLabel() { return "\u25D0  Deckkraft der Anzeigen:   \u25C0  " + U.osdPct + " %  \u25B6"; }
    void setOsd(int pct, int idx) {
        U.osdPct = Math.max(30, Math.min(100, pct));
        U.prefs(this).edit().putInt("osd", U.osdPct).apply();
        if (idx >= 0 && idx < menu.items.size()) menu.items.set(idx, osdLabel());
        menu.invalidate(); list.invalidate(); banner.invalidate();
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
        TextView t3 = U.text(this, "Hast du einen Einrichtungscode?\nDrück OK und gib die sechs Ziffern ein – fertig.\n\nOder selbst einrichten: QR-Code mit dem Handy scannen\nund M3U, Xtream oder Stalker-Portal eintragen.", 18, U.TEXT);
        t3.setLineSpacing(U.px(8), 1f);
        setupUrl = U.text(this, "", 16, 0xFF9EC5FF);
        setupUrl.setPadding(0, U.px(22), 0, U.px(8));
        TextView t4 = U.text(this, "OK: Einrichtungscode   ·   MENU: weitere Möglichkeiten", 15, U.MUTED);
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
        TextView t = U.text(this, "Handy: QR-Code scannen\n\nPC: im Browser  http://" + U.ownIp() + ":" + Web.port + "  öffnen\nund „Code am Fernseher anzeigen“ klicken", 15, U.TEXT);
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
        if (vodMode) { saveVodPos(); vodMode = false; vodOsd.setVisibility(View.GONE); library.setVisibility(View.VISIBLE); }
        if (player != null) { player.release(); player = null; }
    }
    @Override protected void onDestroy() { Web.onChange = null; Web.onPin = null; super.onDestroy(); }
}
