package de.midnox.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** midnox Tools: Speedtest, Speicher, Apps, Netzwerk und Geraete-Infos fuer Fire TV und Android TV. */
public class ToolsActivity extends Activity {
    static final int OVERVIEW = 0, SPEED = 1, STORAGE = 2, APPS = 3, NET = 4, SYSTEM = 5;
    static final String[] PAGES = {"Übersicht", "Speedtest", "Speicher", "Apps", "Netzwerk", "System"};
    static final String[] ICONS = {"◉", "⏱", "▤", "▦", "⌁", "ⓘ"};
    final Handler h = new Handler(Looper.getMainLooper());
    Stage stage;

    // Daten
    volatile Sys.Net net; volatile JSONObject pub; volatile String ssid = "", fireOs = "", adbState = "";
    volatile long pingGw = -2, pingCf = -2, pingDns = -2;
    volatile List<Sys.App> apps; volatile boolean appsLoading, adbOk;
    volatile float temp = -1;
    volatile String busy = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stage = new Stage(this);
        setContentView(stage);
        net = Sys.net(this);
        bg(new Runnable() { public void run() { pub = Sys.publicInfo(); } });
        connectAdb(false);
        h.post(tick);
    }
    @Override protected void onDestroy() { Speed.stop = true; h.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onResume() { super.onResume(); net = Sys.net(this); stage.invalidate(); }

    final Runnable tick = new Runnable() { public void run() {
        int ph = Speed.phase;
        boolean anim = ph == Speed.PING || ph == Speed.DOWN || ph == Speed.UP || !busy.isEmpty() || appsLoading;
        if (anim || stage.page == SPEED || stage.page == OVERVIEW) stage.invalidate();
        h.postDelayed(this, anim ? 50 : 1000);
    }};
    void bg(final Runnable r) { new Thread(new Runnable() { public void run() { try { r.run(); } catch (Exception ignored) {} h.post(new Runnable() { public void run() { stage.invalidate(); } }); } }).start(); }

    /** ADB zum eigenen Geraet: einmalig am Fernseher „Zulassen“ bestaetigen. */
    void connectAdb(final boolean loud) {
        adbState = "verbinde …";
        bg(new Runnable() { public void run() {
            try {
                Adb.shell(ToolsActivity.this, "echo ok");
                adbOk = true; adbState = "verbunden";
                fireOs = Sys.fireOs(ToolsActivity.this);
                temp = Sys.temperature(ToolsActivity.this);
                if (net.ssid.isEmpty()) ssid = Sys.ssidAdb(ToolsActivity.this);
                loadApps();
            } catch (Exception e) {
                adbOk = false;
                if (Adb.status.contains("bestätigen")) adbState = Adb.status;
                else if (Adb.lastError != null && Adb.lastError.contains("ConnectException")) adbState = "ADB an? Dann einmal aus- und wieder einschalten";
                else adbState = "nicht verfügbar – ADB-Debugging einschalten";
                if (loud) toast("ADB: " + adbState);
            }
        }});
    }
    void loadApps() {
        if (appsLoading) return;
        appsLoading = true;
        bg(new Runnable() { public void run() { try { apps = Sys.apps(ToolsActivity.this); } finally { appsLoading = false; } } });
    }
    void runPings() {
        pingGw = pingCf = pingDns = -3;
        bg(new Runnable() { public void run() {
            Sys.Net n = Sys.net(ToolsActivity.this); net = n;
            pingGw = n.gateway.equals("–") ? -1 : Sys.tcpPing(n.gateway, 80);
            if (pingGw < 0 && !n.gateway.equals("–")) pingGw = Sys.tcpPing(n.gateway, 53);
            pingCf = Sys.tcpPing("1.1.1.1", 443);
            pingDns = Sys.tcpPing("8.8.8.8", 443);
        }});
    }
    void cleanCaches() {
        if (!adbOk) { toast("Dafür wird ADB gebraucht – unter System „ADB verbinden“"); return; }
        busy = "Leere Caches …";
        bg(new Runnable() { public void run() {
            try { final long f = Sys.trimCaches(ToolsActivity.this); busy = ""; toast(f > 0 ? U.bytes(f) + " freigegeben" : "Caches geleert – es war nichts Nennenswertes zu holen"); apps = null; loadApps(); }
            catch (Exception e) { busy = ""; toast("Fehler: " + e.getMessage()); }
        }});
    }
    void freeRam() {
        if (!adbOk) { toast("Dafür wird ADB gebraucht – unter System „ADB verbinden“"); return; }
        busy = "Beende Hintergrund-Apps …";
        bg(new Runnable() { public void run() {
            try { long f = Sys.freeRam(ToolsActivity.this); busy = ""; toast(f > 0 ? U.bytes(f) + " Arbeitsspeicher frei geworden" : "Hintergrund-Apps beendet"); }
            catch (Exception e) { busy = ""; toast("Fehler: " + e.getMessage()); }
        }});
    }
    void reboot() {
        if (!adbOk) { toast("Dafür wird ADB gebraucht"); return; }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Jetzt neu starten?").setMessage("Der Fernseher startet in wenigen Sekunden neu.")
            .setPositiveButton("Neu starten", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { bg(new Runnable() { public void run() { try { Adb.shell(ToolsActivity.this, "reboot"); } catch (Exception ignored) {} } }); } })
            .setNegativeButton("Abbrechen", null).show();
    }
    void appMenu(final Sys.App a) {
        List<String> l = new ArrayList<>(); final List<Runnable> r = new ArrayList<>();
        final Intent li = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg) != null ? getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg) : getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (li != null) { l.add("▶  Öffnen"); r.add(new Runnable() { public void run() { try { startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {} } }); }
        if (adbOk) { l.add("⏹  Beenden erzwingen"); r.add(new Runnable() { public void run() { bg(new Runnable() { public void run() { try { Adb.shell(ToolsActivity.this, "am force-stop " + a.pkg); toast(a.name + " wurde beendet"); } catch (Exception e) { toast("Fehler: " + e.getMessage()); } } }); } }); }
        l.add("ⓘ  App-Info (Android-Einstellungen)"); r.add(new Runnable() { public void run() { try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("App-Info lässt sich hier nicht öffnen"); } } });
        if (adbOk) { l.add("🧹  Daten löschen (App zurücksetzen)"); r.add(new Runnable() { public void run() { confirmClear(a); } }); }
        if (!a.system) { l.add("🗑  Deinstallieren"); r.add(new Runnable() { public void run() { try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("Deinstallieren nicht möglich"); } } }); }
        String sz = a.total() > 0 ? "  ·  " + U.bytes(a.total()) : "";
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(a.name + sz)
            .setItems(l.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { r.get(w).run(); } }).show();
    }
    void confirmClear(final Sys.App a) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Daten von " + a.name + " löschen?")
            .setMessage("Die App wird auf den Zustand nach der Installation zurückgesetzt: Anmeldungen, Einstellungen und Downloads gehen verloren.")
            .setPositiveButton("Löschen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { bg(new Runnable() { public void run() {
                try { String r = Adb.shell(ToolsActivity.this, "pm clear " + a.pkg); toast(r.contains("Success") ? a.name + " wurde zurückgesetzt" : r.trim()); apps = null; loadApps(); } catch (Exception e) { toast("Fehler: " + e.getMessage()); }
            } }); } })
            .setNegativeButton("Abbrechen", null).show();
    }
    String toastText; long toastAt;
    void toast(final String s) { h.post(new Runnable() { public void run() { toastText = s; toastAt = System.currentTimeMillis(); stage.invalidate(); h.postDelayed(new Runnable() { public void run() { stage.invalidate(); } }, 4200); } }); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return e.getKeyCode() == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(e);
        if (stage.key(e.getKeyCode())) return true;
        return super.dispatchKeyEvent(e);
    }

    // ================================================================ Darstellung
    static final int HEAD = 0, KV = 1, BAR = 2, BTN = 3, APP = 4, TEXT = 5;
    static final class Row {
        int type; String a = "", b = ""; float frac; int color; Runnable act; Sys.App app;
        Row(int t, String a, String b) { type = t; this.a = a; this.b = b == null ? "" : b; }
        boolean focusable() { return type == BTN || type == APP; }
    }
    Row head(String s) { return new Row(HEAD, s, null); }
    Row kv(String k, String v) { return new Row(KV, k, v); }
    Row bar(String k, String v, float f, int col) { Row r = new Row(BAR, k, v); r.frac = f; r.color = col; return r; }
    Row btn(String s, Runnable a) { Row r = new Row(BTN, s, null); r.act = a; return r; }
    Row txt(String s) { return new Row(TEXT, s, null); }

    final class Stage extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<RectF> hitR = new ArrayList<>(); final List<Runnable> hitA = new ArrayList<>();
        int page = OVERVIEW, cf = 0; boolean side = true; float scrollY, dp, W, H;
        List<Row> rows = new ArrayList<>();
        Stage(Context c) { super(c); setFocusable(true); }

        void hit(RectF r, Runnable a) { hitR.add(new RectF(r)); hitA.add(a); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            for (int i = hitR.size() - 1; i >= 0; i--) if (hitR.get(i).contains(e.getX(), e.getY())) { hitA.get(i).run(); invalidate(); return true; }
            return true;
        }
        void text(Canvas c, String s, float x, float y, float size, int col, boolean bold) { t.setShader(null); t.setTypeface(bold ? U.BOLD : U.POP); t.setTextSize(size); t.setColor(col); c.drawText(s, x, y, t); }
        void rtext(Canvas c, String s, float x, float y, float size, int col, boolean bold) { t.setShader(null); t.setTypeface(bold ? U.BOLD : U.POP); t.setTextSize(size); t.setColor(col); c.drawText(s, x - t.measureText(s), y, t); }
        void ctext(Canvas c, String s, float x, float y, float size, int col, boolean bold) { t.setShader(null); t.setTypeface(bold ? U.BOLD : U.POP); t.setTextSize(size); t.setColor(col); c.drawText(s, x - t.measureText(s) / 2, y, t); }
        void card(Canvas c, RectF r, int col, float rad) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawRoundRect(r, rad, rad, p); }
        void focus(Canvas c, RectF r, float rad) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, rad, rad, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }

        @Override protected void onDraw(Canvas c) {
            dp = U.dp; W = getWidth(); H = getHeight(); hitR.clear(); hitA.clear();
            c.drawColor(U.BG);
            p.setShader(new RadialGradient(W * 0.1f, 0, W * 0.6f, 0x337C3AED, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, p); p.setShader(null);
            drawSide(c);
            rows = build(page);
            float x = 290 * dp, top = 40 * dp;
            if (page == SPEED) { drawGauge(c, x, top); top += 300 * dp; }
            if (page == OVERVIEW) { top = drawTiles(c, x, top); }
            drawRows(c, x, top);
            drawToast(c);
        }
        // ------------------------------------------------------------ Seitenleiste
        void drawSide(Canvas c) {
            RectF sr = new RectF(0, 0, 260 * dp, H); card(c, sr, 0xCC100F1A, 0);
            t.setTypeface(U.BOLD); t.setTextSize(26 * dp); t.setShader(U.brand(28 * dp, 140 * dp)); c.drawText("midnox", 28 * dp, 62 * dp, t); t.setShader(null);
            text(c, "Tools", 28 * dp + t.measureText("midnox "), 62 * dp, 26 * dp, Color.WHITE, true);
            for (int i = 0; i < PAGES.length; i++) {
                RectF r = new RectF(18 * dp, 100 * dp + i * 58 * dp, 242 * dp, 100 * dp + i * 58 * dp + 48 * dp);
                boolean sel = page == i;
                if (sel) { if (side) { p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 24 * dp, 24 * dp, p); p.setShader(null); } else card(c, r, U.CARD2, 24 * dp); }
                text(c, ICONS[i], r.left + 20 * dp, r.centerY() + 7 * dp, 20 * dp, sel ? Color.WHITE : U.MUTED, true);
                text(c, PAGES[i], r.left + 56 * dp, r.centerY() + 7 * dp, 18 * dp, sel ? Color.WHITE : 0xFFC9CCE0, sel);
                final int ii = i; hit(r, new Runnable() { public void run() { setPage(ii); side = true; } });
            }
            text(c, "ADB: " + adbState, 28 * dp, H - 28 * dp, 13 * dp, adbOk ? U.OK : U.MUTED, false);
        }
        void setPage(int i) {
            page = i; cf = 0; scrollY = 0;
            if (i == NET) { net = Sys.net(ToolsActivity.this); if (pingCf == -2) runPings(); }
            if ((i == APPS || i == STORAGE) && apps == null && adbOk) loadApps();
            if (i == SYSTEM && adbOk) bg(new Runnable() { public void run() { temp = Sys.temperature(ToolsActivity.this); } });
            invalidate();
        }
        // ------------------------------------------------------------ Inhalte
        List<Row> build(int pg) {
            List<Row> l = new ArrayList<>();
            Context cx = ToolsActivity.this;
            switch (pg) {
                case OVERVIEW: {
                    l.add(btn("⏱  Speedtest starten", new Runnable() { public void run() { setPage(SPEED); Speed.run(ToolsActivity.this); } }));
                    l.add(btn("🧹  Caches leeren", new Runnable() { public void run() { cleanCaches(); } }));
                    l.add(btn("⚡  Arbeitsspeicher freigeben", new Runnable() { public void run() { freeRam(); } }));
                    if (!adbOk) { l.add(txt("Für Aufräumen, App-Größen und Temperatur braucht midnox Tools Zugriff über ADB-Debugging. Unter „System“ → „ADB verbinden“ einmal am Fernseher „Zulassen“ bestätigen.")); }
                    break;
                }
                case SPEED: {
                    int ph = Speed.phase;
                    boolean running = ph == Speed.PING || ph == Speed.DOWN || ph == Speed.UP;
                    l.add(btn(running ? "■  Abbrechen" : ph == Speed.IDLE ? "▶  Speedtest starten" : "↻  Nochmal messen", new Runnable() { public void run() { if (Speed.phase == Speed.PING || Speed.phase == Speed.DOWN || Speed.phase == Speed.UP) Speed.stop = true; else Speed.run(ToolsActivity.this); } }));
                    JSONArray hs = Speed.history(cx);
                    if (hs.length() > 0) {
                        l.add(head("Verlauf"));
                        SimpleDateFormat f = new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY);
                        for (int i = 0; i < hs.length(); i++) { JSONObject o = hs.optJSONObject(i);
                            l.add(kv(f.format(new Date(o.optLong("at"))) + "  ·  " + o.optString("net"), "↓ " + U.num(o.optDouble("down"), 0) + "   ↑ " + U.num(o.optDouble("up"), 0) + " Mbit/s   ·   " + U.num(o.optDouble("ping"), 0) + " ms")); }
                    }
                    break;
                }
                case STORAGE: {
                    long[] st = Sys.storage();
                    l.add(head("Interner Speicher"));
                    l.add(bar("Belegt", U.bytes(st[0] - st[1]) + " von " + U.bytes(st[0]) + "  ·  frei: " + U.bytes(st[1]), (st[0] - st[1]) / (float) Math.max(1, st[0]), st[1] < 1e9 ? U.BAD : st[1] < 2.5e9 ? U.WARN : U.C2));
                    if (adbOk && Sys.totals[0] + Sys.totals[1] + Sys.totals[2] > 0) {
                        l.add(kv("Apps (Programme)", U.bytes(Sys.totals[0])));
                        l.add(kv("App-Daten", U.bytes(Sys.totals[1])));
                        l.add(kv("Zwischenspeicher (Cache)", U.bytes(Sys.totals[2])));
                    }
                    l.add(btn("🧹  Alle Caches leeren", new Runnable() { public void run() { cleanCaches(); } }));
                    if (!adbOk) l.add(txt("Details zu Apps und Cache gibt es mit ADB-Zugriff (System → ADB verbinden)."));
                    else if (apps == null) l.add(txt(appsLoading ? "Ermittle App-Größen …" : ""));
                    else {
                        l.add(head("Größte Apps"));
                        int n = 0; for (Sys.App a : apps) { if (n++ >= 8) break; Row r = new Row(APP, a.name, U.bytes(a.total())); r.app = a; l.add(r); }
                    }
                    l.add(txt("Tipp: Den Cache leeren schadet nicht – Apps bauen ihn bei Bedarf neu auf. Bei wenig freiem Speicher werden Fire TVs spürbar langsamer."));
                    break;
                }
                case APPS: {
                    if (apps == null) { l.add(txt(appsLoading ? "Lade Apps …" : "Lade Apps …")); if (!appsLoading) loadApps(); break; }
                    l.add(txt(apps.size() + " Apps  ·  sortiert nach Größe" + (adbOk ? "" : "  ·  Größen von Daten/Cache nur mit ADB") + "  ·  OK = Aktionen"));
                    for (Sys.App a : apps) {
                        Row r = new Row(APP, a.name, a.total() > 0 ? U.bytes(a.total()) + (a.cache > 1000000 ? "  ·  Cache " + U.bytes(a.cache) : "") : a.system ? "System" : "");
                        r.app = a; l.add(r);
                    }
                    break;
                }
                case NET: {
                    Sys.Net n = net == null ? Sys.net(cx) : net;
                    l.add(head("Verbindung"));
                    l.add(kv("Art", n.type));
                    String s = !n.ssid.isEmpty() ? n.ssid : ssid;
                    if (n.type.equals("WLAN")) {
                        if (!s.isEmpty()) l.add(kv("WLAN", s));
                        l.add(bar("Signal", n.signal + " %  ·  " + n.rssi + " dBm  ·  " + quality(n.rssi), n.signal / 100f, n.rssi > -60 ? U.OK : n.rssi > -72 ? U.WARN : U.BAD));
                        l.add(kv("Frequenz / Verbindung", (n.freq > 4900 ? "5 GHz" : n.freq > 0 ? "2,4 GHz" : "–") + (n.freq > 0 ? " (" + n.freq + " MHz)" : "") + "  ·  " + n.linkMbps + " Mbit/s"));
                    }
                    l.add(kv("IP-Adresse", n.ip));
                    l.add(kv("Router (Gateway)", n.gateway));
                    l.add(kv("DNS-Server", n.dns));
                    l.add(head("Internet"));
                    JSONObject pi = pub;
                    l.add(kv("Öffentliche IP", pi == null ? "wird ermittelt …" : pi.optString("ip", "–")));
                    if (pi != null && pi.has("org")) l.add(kv("Anbieter", pi.optString("org").replaceAll("^AS\\d+\\s*", "")));
                    if (pi != null && pi.has("city")) l.add(kv("Standort laut Anbieter", pi.optString("city") + ", " + pi.optString("country")));
                    l.add(kv("Antwortzeit Router", ms(pingGw)));
                    l.add(kv("Antwortzeit Cloudflare (1.1.1.1)", ms(pingCf)));
                    l.add(kv("Antwortzeit Google (8.8.8.8)", ms(pingDns)));
                    l.add(btn("↻  Erneut messen", new Runnable() { public void run() { pub = null; bg(new Runnable() { public void run() { pub = Sys.publicInfo(); } }); runPings(); } }));
                    if (n.type.equals("WLAN") && n.rssi <= -72 && n.rssi != 0) l.add(txt("Schwaches WLAN: Router näher aufstellen, 5 GHz nutzen oder – am besten – ein LAN-Adapter am Fire TV."));
                    break;
                }
                case SYSTEM: {
                    long[] ram = Sys.ram(cx);
                    l.add(head("Gerät"));
                    l.add(kv("Modell", Sys.model()));
                    if (!fireOs.isEmpty()) l.add(kv("Betriebssystem", fireOs));
                    l.add(kv("Android", Sys.android()));
                    l.add(kv("Prozessor", Sys.cpu()));
                    l.add(kv("Bild", Sys.display(cx)));
                    l.add(bar("Arbeitsspeicher", U.bytes(ram[0] - ram[1]) + " von " + U.bytes(ram[0]) + " belegt", (ram[0] - ram[1]) / (float) Math.max(1, ram[0]), (ram[0] - ram[1]) / (float) ram[0] > 0.85f ? U.WARN : U.C2));
                    if (temp > 0) l.add(kv("Temperatur (Chip)", U.num(temp, 0) + " °C" + (temp > 75 ? "  ·  heiß – für Luft sorgen" : "")));
                    l.add(kv("Läuft seit", Sys.uptime()));
                    l.add(head("Werkzeuge"));
                    l.add(btn("⚡  Arbeitsspeicher freigeben", new Runnable() { public void run() { freeRam(); } }));
                    l.add(btn("⟳  Neu starten", new Runnable() { public void run() { reboot(); } }));
                    l.add(btn(adbOk ? "✓  ADB verbunden" : "🔌  ADB verbinden", new Runnable() { public void run() { connectAdb(true); } }));
                    if (!adbOk) l.add(txt("So geht’s: Einstellungen → Mein Fire TV → Entwickleroptionen → ADB-Debugging „An“. Dann hier „ADB verbinden“ und am Fernseher „Immer von diesem Computer zulassen“ bestätigen. Hast du den midnox-Launcher installiert und dort ADB schon einmal erlaubt, nutzt midnox Tools dieselbe Freigabe automatisch. Bleibt es trotz „An“ grau, ADB-Debugging einmal aus- und wieder einschalten."));
                    l.add(txt("Version " + version() + "  ·  midnox Tools"));
                    break;
                }
            }
            return l;
        }
        String quality(int rssi) { return rssi > -50 ? "ausgezeichnet" : rssi > -60 ? "gut" : rssi > -70 ? "mittel" : "schwach"; }
        String ms(long v) { return v == -2 ? "–" : v == -3 ? "misst …" : v < 0 ? "nicht erreichbar" : v + " ms"; }
        String version() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; } }

        void drawRows(Canvas c, float x, float top) {
            float w = W - x - 40 * dp;
            // Hoehen und Fokus
            List<Integer> foc = new ArrayList<>(); for (int i = 0; i < rows.size(); i++) if (rows.get(i).focusable()) foc.add(i);
            if (cf >= foc.size()) cf = Math.max(0, foc.size() - 1);
            int fi = !side && !foc.isEmpty() ? foc.get(cf) : -1;
            float[] ys = new float[rows.size() + 1]; float y = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                boolean inlineBtn = r.type == BTN && i > 0 && rows.get(i - 1).type == BTN;
                ys[i] = inlineBtn ? ys[i - 1] : y;
                if (!inlineBtn) y += rowH(r, w);
            }
            ys[rows.size()] = y;
            // Mitscrollen
            float avail = H - top - 20 * dp;
            if (fi >= 0) { float fy = ys[fi], fh = rowH(rows.get(fi), w); if (fy - scrollY < 0) scrollY = fy; if (fy + fh - scrollY > avail) scrollY = fy + fh - avail; }
            scrollY = Math.max(0, Math.min(scrollY, Math.max(0, y - avail)));
            c.save(); c.clipRect(x - 10 * dp, top - 6 * dp, W, H);
            float bx = x; int btnRowStart = -1;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i); float ry = top + ys[i] - scrollY, rh = rowH(r, w);
                if (r.type == BTN) { if (i == 0 || rows.get(i - 1).type != BTN) bx = x; }
                if (ry > H || ry + rh < top - 40 * dp) { if (r.type == BTN) { t.setTextSize(17 * dp); t.setTypeface(U.BOLD); bx += t.measureText(r.a) + 56 * dp + 14 * dp; } continue; }
                boolean f = i == fi;
                switch (r.type) {
                    case HEAD: text(c, r.a.toUpperCase(Locale.GERMAN), x, ry + 34 * dp, 14 * dp, 0xFF9EC5FF, true); break;
                    case KV: {
                        RectF rr = new RectF(x, ry + 3 * dp, x + w, ry + rh - 3 * dp); card(c, rr, U.CARD, 12 * dp);
                        text(c, U.ell(t, r.a, w * 0.45f), x + 18 * dp, rr.centerY() + 6 * dp, 16 * dp, 0xFFB4B8CC, false);
                        t.setTypeface(U.BOLD); t.setTextSize(16 * dp); String v = U.ell(t, r.b, w * 0.52f);
                        rtext(c, v, x + w - 18 * dp, rr.centerY() + 6 * dp, 16 * dp, Color.WHITE, true);
                        break;
                    }
                    case BAR: {
                        RectF rr = new RectF(x, ry + 3 * dp, x + w, ry + rh - 3 * dp); card(c, rr, U.CARD, 12 * dp);
                        text(c, r.a, x + 18 * dp, rr.top + 26 * dp, 16 * dp, 0xFFB4B8CC, false);
                        rtext(c, r.b, x + w - 18 * dp, rr.top + 26 * dp, 15 * dp, Color.WHITE, true);
                        RectF bb = new RectF(x + 18 * dp, rr.bottom - 20 * dp, x + w - 18 * dp, rr.bottom - 10 * dp); card(c, bb, 0x22FFFFFF, 5 * dp);
                        RectF fb = new RectF(bb.left, bb.top, bb.left + bb.width() * Math.max(0.01f, Math.min(1, r.frac)), bb.bottom);
                        if (r.color == U.C2) { p.setShader(U.brand(fb.left, bb.right)); c.drawRoundRect(fb, 5 * dp, 5 * dp, p); p.setShader(null); } else card(c, fb, r.color, 5 * dp);
                        break;
                    }
                    case BTN: {
                        t.setTypeface(U.BOLD); t.setTextSize(17 * dp); float bw = t.measureText(r.a) + 56 * dp;
                        RectF rr = new RectF(bx, ry + 6 * dp, bx + bw, ry + rh - 6 * dp);
                        if (f) { p.setShader(U.brand(rr.left, rr.right)); c.drawRoundRect(rr, rr.height() / 2, rr.height() / 2, p); p.setShader(null); } else card(c, rr, 0x26FFFFFF, rr.height() / 2);
                        ctext(c, r.a, rr.centerX(), rr.centerY() + 6 * dp, 17 * dp, Color.WHITE, true);
                        final Row fr = r; final int idx = foc.indexOf(i); hit(rr, new Runnable() { public void run() { side = false; cf = idx; fr.act.run(); } });
                        bx += bw + 14 * dp;
                        break;
                    }
                    case APP: {
                        RectF rr = new RectF(x, ry + 3 * dp, x + w, ry + rh - 3 * dp); card(c, rr, f ? U.CARD2 : U.CARD, 12 * dp); if (f) focus(c, rr, 12 * dp);
                        text(c, U.ell(t, r.a, w * 0.55f), x + 18 * dp, rr.centerY() + 6 * dp, 16 * dp, Color.WHITE, f);
                        rtext(c, r.b, x + w - 18 * dp, rr.centerY() + 6 * dp, 15 * dp, 0xFFC9CCE0, false);
                        final Row fr = r; final int idx = foc.indexOf(i); hit(rr, new Runnable() { public void run() { side = false; cf = idx; appMenu(fr.app); } });
                        break;
                    }
                    case TEXT: {
                        t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(U.MUTED);
                        List<String> ls = U.wrap(t, r.a, w); for (int k = 0; k < ls.size(); k++) c.drawText(ls.get(k), x, ry + 24 * dp + k * 20 * dp, t);
                        break;
                    }
                }
            }
            c.restore();
            if (!busy.isEmpty()) {
                RectF r = new RectF(x, H - 70 * dp, x + 420 * dp, H - 24 * dp); card(c, r, U.CARD2, 23 * dp);
                float a = (System.currentTimeMillis() % 1000) / 1000f * 360;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); p.setShader(U.brand(r.left, r.left + 40 * dp)); c.drawArc(new RectF(r.left + 16 * dp, r.top + 12 * dp, r.left + 38 * dp, r.top + 34 * dp), a, 270, false, p); p.setShader(null); p.setStyle(Paint.Style.FILL);
                text(c, busy, r.left + 52 * dp, r.centerY() + 6 * dp, 16 * dp, Color.WHITE, true);
            }
        }
        float rowH(Row r, float w) {
            switch (r.type) {
                case HEAD: return 46 * dp;
                case KV: case APP: return 50 * dp;
                case BAR: return 70 * dp;
                case BTN: return 58 * dp;
                default: { t.setTextSize(15 * dp); t.setTypeface(U.POP); return U.wrap(t, r.a, w).size() * 20 * dp + 14 * dp; }
            }
        }
        // ------------------------------------------------------------ Uebersicht: Kacheln
        float drawTiles(Canvas c, float x, float top) {
            float w = W - x - 40 * dp, gap = 16 * dp, tw = (w - 2 * gap) / 3, th = 120 * dp;
            long[] st = Sys.storage(), ram = Sys.ram(ToolsActivity.this);
            Sys.Net n = net;
            JSONArray hs = Speed.history(ToolsActivity.this); JSONObject last = hs.length() > 0 ? hs.optJSONObject(0) : null;
            String[][] tiles = {
                {"Gerät", Sys.MODELS.containsKey(Build.MODEL) ? Sys.MODELS.get(Build.MODEL) : Build.MODEL, fireOs.isEmpty() ? Sys.android() : fireOs},
                {"Speicher frei", U.bytes(st[1]), "von " + U.bytes(st[0])},
                {"Arbeitsspeicher frei", U.bytes(ram[1]), "von " + U.bytes(ram[0])},
                {"Netzwerk", n == null ? "–" : n.type + (n.signal >= 0 ? "  ·  " + n.signal + " %" : ""), n == null ? "" : n.ip},
                {"Letzter Speedtest", last == null ? "noch keiner" : "↓ " + U.num(last.optDouble("down"), 0) + " Mbit/s", last == null ? "OK auf „Speedtest starten“" : "↑ " + U.num(last.optDouble("up"), 0) + " Mbit/s  ·  " + U.num(last.optDouble("ping"), 0) + " ms"},
                {"Temperatur · Laufzeit", temp > 0 ? U.num(temp, 0) + " °C" : "–", "läuft seit " + Sys.uptime()}};
            float[] frac = {-1, (st[0] - st[1]) / (float) Math.max(1, st[0]), (ram[0] - ram[1]) / (float) Math.max(1, ram[0]), n != null && n.signal >= 0 ? n.signal / 100f : -1, -1, temp > 0 ? Math.min(1, temp / 90f) : -1};
            for (int i = 0; i < 6; i++) {
                RectF r = new RectF(x + (i % 3) * (tw + gap), top + (i / 3) * (th + gap), x + (i % 3) * (tw + gap) + tw, top + (i / 3) * (th + gap) + th);
                card(c, r, U.CARD, 18 * dp);
                text(c, tiles[i][0], r.left + 20 * dp, r.top + 30 * dp, 14 * dp, U.MUTED, false);
                t.setTypeface(U.BOLD); t.setTextSize(22 * dp); t.setColor(Color.WHITE); c.drawText(U.ell(t, tiles[i][1], tw - 40 * dp), r.left + 20 * dp, r.top + 64 * dp, t);
                t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(0xFFB4B8CC); c.drawText(U.ell(t, tiles[i][2], tw - 40 * dp), r.left + 20 * dp, r.top + 88 * dp, t);
                if (frac[i] >= 0) {
                    RectF bb = new RectF(r.left + 20 * dp, r.bottom - 18 * dp, r.right - 20 * dp, r.bottom - 12 * dp); card(c, bb, 0x22FFFFFF, 3 * dp);
                    int col = i == 1 && st[1] < 1e9 ? U.BAD : i == 5 && temp > 75 ? U.WARN : 0;
                    RectF fb = new RectF(bb.left, bb.top, bb.left + bb.width() * Math.max(0.02f, frac[i]), bb.bottom);
                    if (col != 0) card(c, fb, col, 3 * dp); else { p.setShader(U.brand(fb.left, bb.right)); c.drawRoundRect(fb, 3 * dp, 3 * dp, p); p.setShader(null); }
                }
            }
            return top + 2 * th + gap + 16 * dp;
        }
        // ------------------------------------------------------------ Speedtest: Tacho
        final float[] SCALE = {0, 10, 25, 50, 100, 250, 500, 1000};
        float pos(double v) {   // Mbit/s -> 0..1 entlang der Skala
            if (v <= 0) return 0;
            for (int i = 1; i < SCALE.length; i++) if (v <= SCALE[i]) return (float) ((i - 1 + (v - SCALE[i - 1]) / (SCALE[i] - SCALE[i - 1])) / (SCALE.length - 1));
            return 1;
        }
        float needle;
        void drawGauge(Canvas c, float x, float top) {
            float w = W - x - 40 * dp, cx = x + 210 * dp, cy = top + 160 * dp, rad = 140 * dp;
            int ph = Speed.phase;
            double val = ph == Speed.DOWN || ph == Speed.UP ? Speed.live : ph == Speed.DONE ? Speed.down : 0;
            needle += (pos(val) - needle) * 0.15f;
            RectF ar = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(18 * dp);
            p.setColor(0x22FFFFFF); c.drawArc(ar, 150, 240, false, p);
            if (needle > 0.002f) { p.setShader(new SweepGradient(cx, cy, new int[]{U.C3, U.C1, U.C2, U.C3}, new float[]{0, 0.42f, 0.7f, 1f})); c.drawArc(ar, 150, 240 * needle, false, p); p.setShader(null); }
            p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT);
            for (int i = 0; i < SCALE.length; i++) {   // Skalenbeschriftung
                double a = Math.toRadians(150 + 240.0 * i / (SCALE.length - 1));
                ctext(c, String.valueOf((int) SCALE[i]), cx + (float) Math.cos(a) * (rad - 38 * dp), cy + (float) Math.sin(a) * (rad - 38 * dp) + 5 * dp, 12 * dp, U.MUTED, false);
            }
            String big = ph == Speed.PING ? "…" : ph == Speed.IDLE ? "–" : ph == Speed.FAIL ? "!" : U.num(val, val < 10 ? 1 : 0);
            ctext(c, big, cx, cy + 14 * dp, 52 * dp, Color.WHITE, true);
            ctext(c, ph == Speed.FAIL ? "Fehler" : "Mbit/s", cx, cy + 42 * dp, 15 * dp, U.MUTED, false);
            String st = ph == Speed.IDLE ? "Bereit – OK zum Starten" : ph == Speed.PING ? "Messe Antwortzeit …" : ph == Speed.DOWN ? "↓ Download wird gemessen …" : ph == Speed.UP ? "↑ Upload wird gemessen …" : ph == Speed.DONE ? "Fertig" : Speed.error;
            ctext(c, st, cx, cy + 96 * dp, 16 * dp, ph == Speed.FAIL ? U.BAD : 0xFFC9CCE0, true);
            // Ergebnisse rechts
            float rx = cx + rad + 50 * dp, rw = x + w - rx;
            String[][] res = {{"Antwortzeit", Speed.ping < 0 ? "–" : U.num(Speed.ping, 0) + " ms", Speed.jitter < 0 ? "" : "Schwankung " + U.num(Speed.jitter, 0) + " ms"},
                {"↓ Download", Speed.down < 0 ? (ph == Speed.DOWN ? U.num(Speed.live, 0) + " …" : "–") : U.num(Speed.down, Speed.down < 10 ? 1 : 0) + " Mbit/s", Speed.down > 0 ? verdict(Speed.down) : ""},
                {"↑ Upload", Speed.up < 0 ? (ph == Speed.UP ? U.num(Speed.live, 0) + " …" : "–") : U.num(Speed.up, Speed.up < 10 ? 1 : 0) + " Mbit/s", ""}};
            for (int i = 0; i < 3; i++) {
                RectF r = new RectF(rx, top + 20 * dp + i * 84 * dp, rx + rw, top + 20 * dp + i * 84 * dp + 72 * dp);
                boolean act = (i == 0 && ph == Speed.PING) || (i == 1 && ph == Speed.DOWN) || (i == 2 && ph == Speed.UP);
                card(c, r, act ? U.CARD2 : U.CARD, 16 * dp); if (act) focus(c, r, 16 * dp);
                text(c, res[i][0], r.left + 20 * dp, r.top + 26 * dp, 14 * dp, U.MUTED, false);
                text(c, res[i][1], r.left + 20 * dp, r.top + 56 * dp, 24 * dp, Color.WHITE, true);
                if (!res[i][2].isEmpty()) rtext(c, res[i][2], r.right - 18 * dp, r.top + 54 * dp, 13 * dp, 0xFFB4B8CC, false);
            }
            if (!Speed.server.isEmpty()) text(c, "Messserver: " + Speed.server, rx, top + 280 * dp, 13 * dp, U.MUTED, false);
        }
        String verdict(double mbps) { return mbps >= 100 ? "reicht für mehrere 4K-Streams" : mbps >= 25 ? "reicht für 4K" : mbps >= 10 ? "reicht für Full HD" : mbps >= 5 ? "HD möglich, 4K nicht" : "knapp – Ruckler möglich"; }

        void drawToast(Canvas c) {
            if (toastText == null || System.currentTimeMillis() - toastAt > 4000) return;
            t.setTypeface(U.BOLD); t.setTextSize(18 * dp);
            float w = Math.min(W - 340 * dp, t.measureText(toastText) + 60 * dp), x0 = 290 * dp + (W - 330 * dp - w) / 2;
            RectF r = new RectF(x0, 14 * dp, x0 + w, 62 * dp);
            p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 24 * dp, 24 * dp, p); p.setShader(null);
            String s = U.ell(t, toastText, w - 40 * dp); t.setColor(Color.WHITE); c.drawText(s, r.centerX() - t.measureText(s) / 2, r.centerY() + 6 * dp, t);
        }

        // ------------------------------------------------------------ Tasten
        boolean key(int k) {
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
            List<Integer> foc = new ArrayList<>(); for (int i = 0; i < rows.size(); i++) if (rows.get(i).focusable()) foc.add(i);
            if (k == KeyEvent.KEYCODE_BACK) { if (!side) { side = true; invalidate(); return true; } finish(); return true; }
            if (side) {
                if (k == KeyEvent.KEYCODE_DPAD_UP && page > 0) setPage(page - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN && page < PAGES.length - 1) setPage(page + 1);
                else if ((k == KeyEvent.KEYCODE_DPAD_RIGHT || ok) && !foc.isEmpty()) { side = false; cf = 0; }
                else if (ok && page == SPEED) Speed.run(ToolsActivity.this);
                invalidate(); return true;
            }
            if (foc.isEmpty()) { side = true; invalidate(); return true; }
            if (cf >= foc.size()) cf = foc.size() - 1;
            int line = lineOf(foc.get(cf));
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { if (cf > 0 && lineOf(foc.get(cf - 1)) == line) cf--; else side = true; }
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { if (cf + 1 < foc.size() && lineOf(foc.get(cf + 1)) == line) cf++; }
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { for (int j = cf + 1; j < foc.size(); j++) if (lineOf(foc.get(j)) != line) { cf = j; break; } }
            else if (k == KeyEvent.KEYCODE_DPAD_UP) {
                int j = cf - 1; while (j >= 0 && lineOf(foc.get(j)) == line) j--;
                if (j >= 0) { int l2 = lineOf(foc.get(j)); while (j > 0 && lineOf(foc.get(j - 1)) == l2) j--; cf = j; }
            } else if (ok) {
                Row r = rows.get(foc.get(cf));
                if (r.type == BTN) r.act.run(); else if (r.type == APP) appMenu(r.app);
            }
            invalidate(); return true;
        }
        /** Zeile einer Zeile: Knoepfe direkt hintereinander stehen nebeneinander. */
        int lineOf(int i) { while (i > 0 && rows.get(i).type == BTN && rows.get(i - 1).type == BTN) i--; return i; }
    }
}
