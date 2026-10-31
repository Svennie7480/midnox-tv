package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeActivity extends Activity {
    static final String[] DEFAULT = {"com.mb.android", "de.cbc.tvnow.firetv", "com.teamsmart.videomanager.tv", "ar.tvplayer.tv", "studio.scillarium.ottnavigator"};
    static final Map<String, String> NAMES = new HashMap<>();
    static {
        NAMES.put("com.mb.android", "Emby");
        NAMES.put("de.cbc.tvnow.firetv", "RTL+");
        NAMES.put("com.teamsmart.videomanager.tv", "SmartTube");
        NAMES.put("ar.tvplayer.tv", "TiviMate");
        NAMES.put("studio.scillarium.ottnavigator", "OTT Navigator");
    }

    // ---------- Favoriten (Apps auf der Startseite) ----------
    static List<String> favs(Context c) {
        String s = c.getSharedPreferences("midnox", MODE_PRIVATE).getString("favs", null);
        if (s == null) return new ArrayList<>(Arrays.asList(DEFAULT));
        List<String> l = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) l.add(p);
        return l;
    }
    static void saveFavs(Context c, List<String> l) {
        c.getSharedPreferences("midnox", MODE_PRIVATE).edit().putString("favs", TextUtils.join(",", l)).apply();
    }
    /** @return true wenn hinzugefuegt, false wenn entfernt */
    static boolean toggleFav(Context c, String pkg) {
        List<String> l = favs(c);
        boolean add = !l.remove(pkg);
        if (add) l.add(pkg);
        saveFavs(c, l);
        return add;
    }
    static String label(PackageManager pm, String pkg) {
        if (NAMES.containsKey(pkg)) return NAMES.get(pkg);
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception e) { return pkg; }
    }

    TextView clock, date;
    LinearLayout row, alertBox;
    HorizontalScrollView scroller;
    HaPanel ha;
    EmbyRows emby;
    VScroll vscroll;
    Hero hero;
    LinearLayout home;
    Tile updateTile;
    org.json.JSONObject update;
    long lastUpdateCheck;
    static final int HERO_H = 196;
    LinearLayout content;
    String tileSig = "";
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() { public void run() { updateClock(); weatherTick(); idleTick(); radioTick(); sleepTick(); h.postDelayed(this, 15000); } };

    void sleepTick() {
        int m = Sleep.remainingMin(this);
        sleepInfo.setVisibility(m > 0 ? View.VISIBLE : View.GONE);
        sleepInfo.setText("\u263E  Einschlaf-Timer  \u00b7  " + m + " Min.");
    }

    /** Laeuft das Radio im Hintergrund: Mini-Anzeige im Kopf + Live-Kachel mit dem aktuellen Sender. */
    void showRadio() {
        RadioService s = RadioService.inst;
        boolean on = s != null && (s.playing || s.preparing);
        if (!on) { nowPlaying.setVisibility(View.GONE); lastRadio = 0; return; }
        Radio.Song c = s.current;
        nowPlaying.setText("\u266A  " + s.station.name + (c == null || c.title.isEmpty() ? "" : "  \u00b7  " + (c.artist.isEmpty() ? "" : c.artist + " \u2013 ") + c.title));
        nowPlaying.setVisibility(View.VISIBLE);
        radioBanner.title = c == null ? s.station.name : c.title;
        radioBanner.artist = c == null ? "" : (c.artist.isEmpty() ? s.station.name : c.artist);
        Bitmap bm = c == null || c.art == null ? null : s.covers.get(c.art);
        if (bm == null) bm = Stations.logo(s.station);   // kein Cover: Senderlogo
        if (bm != null) radioBanner.cover = bm;
        if (radioTile != null) radioTile.invalidate();
    }

    /** Live-Kachel des Radios: aktuelles Cover und Titel. */
    void radioTick() {
        RadioService rs = RadioService.inst;
        if (rs != null && (rs.playing || rs.preparing)) return;   // Kachel folgt dem laufenden Sender
        if (radioTile == null || System.currentTimeMillis() - lastRadio < 30000) return;
        lastRadio = System.currentTimeMillis();
        new Thread(new Runnable() { public void run() {
            try {
                final Radio.State st = Radio.fetch(HomeActivity.this);
                final Bitmap bm = RadioNet.bitmap(st.now.art);
                h.post(new Runnable() { public void run() {
                    radioBanner.title = st.now.title; radioBanner.artist = st.now.artist;
                    if (bm != null) radioBanner.cover = bm;
                    if (radioTile != null) radioTile.invalidate();
                }});
            } catch (Exception ignored) {}
        }}).start();
    }

    void weatherTick() {
        boolean on = Style.prefs(this).getBoolean("weather_on", true) && Style.loadConfig(this) != null;
        if (!on) { weatherRow.setVisibility(View.GONE); return; }
        if (System.currentTimeMillis() - lastWeather < 10 * 60000) return;
        lastWeather = System.currentTimeMillis();
        new Thread(new Runnable() { public void run() {
            final Weather.Now n = Weather.fetch(HomeActivity.this);
            h.post(new Runnable() { public void run() {
                if (n == null) { weatherRow.setVisibility(View.GONE); lastWeather = 0; return; }
                weatherIcon.set(n.cond);
                weatherText.setText(Weather.line(n));
                weatherRow.setVisibility(View.VISIBLE);
            }});
        }}).start();
    }

    /** Bildschirmschoner nach x Minuten ohne Tastendruck auf der Startseite. */
    void idleTick() {
        if (Style.prefs(this).getBoolean("sleep_mode", false)) return;
        int min = Style.prefs(this).getInt("ss_idle", 4);
        if (min > 0 && System.currentTimeMillis() - lastInput > min * 60000L) {
            lastInput = System.currentTimeMillis();
            startActivity(new Intent(this, ScreensaverActivity.class));
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = System.currentTimeMillis();
        if (Style.prefs(this).getBoolean("sleep_mode", false)) Style.prefs(this).edit().putBoolean("sleep_mode", false).apply();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) { startActivity(new Intent(this, SmartHomeActivity.class)); return true; }
            if (e.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && RadioService.inst != null) { RadioService.start(this, "toggle"); return true; }
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) return true;
        return super.dispatchKeyEvent(e);
    }

    final Radio.Banner radioBanner = new Radio.Banner();
    Tile radioTile;
    long lastRadio;
    TextView nowPlaying, sleepInfo;
    TvRows tvRows;
    final Runnable radioChanged = new Runnable() { public void run() { showRadio(); } };
    LinearLayout weatherRow; Weather.Icon weatherIcon; TextView weatherText;
    long lastWeather, lastInput = System.currentTimeMillis();
    volatile String message;   // Live-Nachricht aus Home Assistant
    Theme theme;
    Theme.Overlay overlay;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Stations.init(this);
        theme = Theme.current(this);
        theme.apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        root.setClipChildren(false);
        overlay = new Theme.Overlay(this, theme);   // Deko hinter allem anderen
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // Kopfzeile: Logo links, Uhr rechts
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(Style.px(64), Style.px(40), Style.px(64), 0);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(new Style.GradientText(this, "midnox.de", 50));
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sub = Style.text(this, "TV", 13, 0xCCFFFFFF);
        sub.setLetterSpacing(0.45f);
        sub.setPadding(Style.px(5), Style.px(2), 0, 0);
        subRow.addView(sub);
        if (!theme.greeting.isEmpty()) {
            TextView dot = Style.text(this, "\u00b7", 15, Style.MUTED);
            dot.setPadding(Style.px(10), Style.px(1), Style.px(10), 0);
            subRow.addView(dot);
            Style.GradientText greet = new Style.GradientText(this, theme.greeting, 14);
            greet.setPadding(0, Style.px(2), 0, 0);
            subRow.addView(greet);
        }
        brand.addView(subRow);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 0));
        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        clock = Style.text(this, "", 46, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        date = Style.text(this, "", 14, Style.MUTED);
        date.setLetterSpacing(0.15f);
        clock.setShadowLayer(Style.px(10), 0, 0, 0xCC000000);
        date.setShadowLayer(Style.px(6), 0, 0, 0xCC000000);
        time.addView(clock); time.addView(date);
        weatherRow = new LinearLayout(this);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        weatherRow.setPadding(0, Style.px(6), 0, 0);
        weatherIcon = new Weather.Icon(this);
        weatherRow.addView(weatherIcon, new LinearLayout.LayoutParams(Style.px(20), Style.px(20)));
        weatherText = Style.text(this, "", 13, 0xCCFFFFFF);
        weatherText.setShadowLayer(Style.px(6), 0, 0, 0xCC000000);
        weatherText.setPadding(Style.px(8), 0, 0, 0);
        weatherRow.addView(weatherText);
        weatherRow.setVisibility(View.GONE);
        time.addView(weatherRow);
        nowPlaying = Style.text(this, "", 12, 0xE6FFFFFF);
        nowPlaying.setShadowLayer(Style.px(6), 0, 0, 0xCC000000);
        nowPlaying.setGravity(Gravity.END);
        nowPlaying.setSingleLine(true); nowPlaying.setMaxWidth(Style.px(330)); nowPlaying.setEllipsize(TextUtils.TruncateAt.END);
        nowPlaying.setPadding(0, Style.px(5), 0, 0);
        nowPlaying.setVisibility(View.GONE);
        time.addView(nowPlaying);
        sleepInfo = Style.text(this, "", 12, 0xCCFFFFFF);
        sleepInfo.setShadowLayer(Style.px(6), 0, 0, 0xCC000000);
        sleepInfo.setPadding(0, Style.px(4), 0, 0);
        sleepInfo.setVisibility(View.GONE);
        time.addView(sleepInfo);
        time.setPadding(0, Style.px(40), Style.px(64), 0);
        FrameLayout headFrame = new FrameLayout(this);
        headFrame.setClipChildren(false);
        FrameLayout normal = new FrameLayout(this);   // Logo + Hinweise (weicht dem Titelbild)
        normal.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        headFrame.addView(normal, new FrameLayout.LayoutParams(-1, -1));
        hero = new Hero(this, normal);
        headFrame.addView(hero.info, new FrameLayout.LayoutParams(-1, -1));
        headFrame.addView(time, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));

        // Hinweise oben in der Mitte
        alertBox = new LinearLayout(this);
        alertBox.setOrientation(LinearLayout.VERTICAL);
        alertBox.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(Style.px(330), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        alp.topMargin = Style.px(46);
        normal.addView(alertBox, alp);

        // Scrollende Seite: Kopf, Apps, Weiterschauen, Zuhause, Neu in ...
        vscroll = new VScroll(this);
        vscroll.setVerticalScrollBarEnabled(false);
        vscroll.setClipChildren(true);    // Reihen nicht in den Kopfbereich zeichnen
        vscroll.setVerticalFadingEdgeEnabled(true);
        vscroll.setFadingEdgeLength(Style.px(28));
        vscroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(0, 0, 0, Style.px(200));
        vscroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        vscroll.setClipToPadding(false);
        vscroll.setPadding(0, Style.px(10), 0, 0);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setClipChildren(false);
        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(Style.px(24), Style.px(10), Style.px(24), Style.px(4));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        scroller.addView(row, new FrameLayout.LayoutParams(-2, -2));
        center.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
        content.addView(center, new LinearLayout.LayoutParams(-1, -2));
        emby = new EmbyRows(this);
        content.addView(emby.top, new LinearLayout.LayoutParams(-1, -2));

        // Zuhause: Home-Assistant-Werte
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(0, Style.px(6), 0, Style.px(14));
        TextView hcap = Style.text(this, "ZUHAUSE", 11, Style.MUTED);
        hcap.setLetterSpacing(0.3f);
        hcap.setPadding(Style.px(64), 0, 0, Style.px(8));
        home.addView(hcap);
        LinearLayout hrow = new LinearLayout(this);
        hrow.setGravity(Gravity.CENTER_HORIZONTAL);
        hrow.setPadding(Style.px(32), 0, Style.px(32), 0);
        ha = new HaPanel(this);
        hrow.addView(ha, new LinearLayout.LayoutParams(-2, -2));
        home.addView(hrow, new LinearLayout.LayoutParams(-1, -2));
        content.addView(home, new LinearLayout.LayoutParams(-1, -2));

        tvRows = new TvRows(this);
        content.addView(tvRows.box, new LinearLayout.LayoutParams(-1, -2));
        content.addView(emby.bottom, new LinearLayout.LayoutParams(-1, -2));
        root.addView(hero.backdrop, new FrameLayout.LayoutParams(-1, -1));
        headFrame.setClipChildren(true);
        root.addView(headFrame, new FrameLayout.LayoutParams(-1, Style.px(HERO_H)));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(-1, -1);
        vlp.topMargin = Style.px(HERO_H);   // Liste beginnt erst unter dem Kopfbereich
        // Eigener Rahmen, der die Liste hart an seiner Kante abschneidet.
        // (Ob ein View an seinen Grenzen beschnitten wird, entscheidet sein Eltern-View;
        //  root zeichnet bewusst ueber Grenzen hinaus, darum dieser Zwischenrahmen.)
        FrameLayout listBox = new FrameLayout(this);
        listBox.setClipChildren(true);
        listBox.addView(vscroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(listBox, vlp);

        // dezente Versionsanzeige unten rechts
        String vn = "?";
        try { vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView ver = Style.text(this, "midnox TV  \u00b7  v" + vn, 10, 0x806B6F86);
        ver.setLetterSpacing(0.12f);
        FrameLayout.LayoutParams verLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        verLp.setMargins(0, 0, Style.px(18), Style.px(10));
        root.addView(ver, verLp);

        if (b == null) splash(root);
        startService(new Intent(this, KinoService.class));

        setContentView(root);
        buildTiles(true);
    }

    void buildTiles(boolean force) {
        final PackageManager pm = getPackageManager();
        List<String> shown = new ArrayList<>();
        for (String p : favs(this)) if (Style.launchIntent(pm, p) != null) shown.add(p);
        String sig = (Style.prefs(this).getBoolean("hide_radio", false) ? "" : "R,") + TextUtils.join(",", shown) + (update != null ? "#u" + update.optInt("versionCode") : "");
        if (!force && sig.equals(tileSig)) return;
        tileSig = sig;
        String focusedTag = null;
        View cur = row.findFocus();
        if (cur instanceof Tile) focusedTag = ((Tile) cur).tag;
        row.removeAllViews();
        radioTile = null;

        float screenDp = getResources().getDisplayMetrics().widthPixels / Style.dp;
        int n = shown.size();
        float tileW = Math.max(140, Math.min(268, (screenDp - 48 - 80 - 20 * (n + 2)) / (n + 1.1f)));
        float cardH = (tileW - 20) * 9f / 16f;
        int tileH = Style.px(cardH + 10 + 34);
        Tile first = null, toFocus = null;
        for (final String p : shown) {
            Drawable banner = null, icon = null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                banner = pm.getApplicationBanner(ai);
                if (banner == null) icon = pm.getApplicationIcon(ai);
            } catch (Exception ignored) {}
            final Intent li = Style.launchIntent(pm, p);
            Tile t = new Tile(this, label(pm, p), banner, icon, Tile.GLYPH_NONE,
                    new Runnable() { public void run() { Style.start(HomeActivity.this, li); } },
                    new Runnable() { public void run() {
                        toggleFav(HomeActivity.this, p);
                        Toast.makeText(HomeActivity.this, label(pm, p) + " von der Startseite entfernt", Toast.LENGTH_SHORT).show();
                        buildTiles(false);
                    } });
            t.tag = p;
            addTile(t, Style.px(tileW), tileH);
            if (first == null) first = t;
            if (p.equals(focusedTag)) toFocus = t;
        }
        int small = Style.px(Math.max(100, tileW * 0.6f));
        if (!Style.prefs(this).getBoolean("hide_radio", false)) {
            Tile radio = new Tile(this, "midnox Radio", radioBanner, null, Tile.GLYPH_NONE,
                    new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, RadioActivity.class)); } }, null);
            radio.tag = "#radio";
            radioTile = radio;
            addTile(radio, Style.px(tileW), tileH);
            if ("#radio".equals(focusedTag)) toFocus = radio;
        }
        Tile search = new Tile(this, "Suche", null, null, Tile.GLYPH_SEARCH,
                new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, SearchActivity.class)); } }, null);
        search.tag = "#search";
        addTile(search, small, tileH);
        if ("#search".equals(focusedTag)) toFocus = search;
        if (Style.loadConfig(this) != null) {
            Tile home = new Tile(this, "Zuhause", null, null, Tile.GLYPH_HOME,
                    new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, SmartHomeActivity.class)); } }, null);
            home.tag = "#home";
            addTile(home, small, tileH);
            if ("#home".equals(focusedTag)) toFocus = home;
        }
        Tile store = new Tile(this, "Store", null, null, Tile.GLYPH_STORE,
                new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, StoreActivity.class)); } }, null);
        store.tag = "#store";
        addTile(store, small, tileH);
        if ("#store".equals(focusedTag)) toFocus = store;
        Tile all = new Tile(this, "Alle Apps", null, null, Tile.GLYPH_GRID,
                new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, AppsActivity.class)); } }, null);
        all.tag = "#all";
        addTile(all, small, tileH);
        Tile set = new Tile(this, "Einstellungen", null, null, Tile.GLYPH_GEAR,
                new Runnable() { public void run() { startActivity(new Intent(HomeActivity.this, SettingsActivity.class)); } }, null);
        set.tag = "#settings";
        addTile(set, small, tileH);
        if (update != null) {
            final org.json.JSONObject u = update;
            updateTile = new Tile(this, "Update " + u.optString("versionName"), null, null, Tile.GLYPH_UPDATE,
                    new Runnable() { public void run() { Updater.install(HomeActivity.this, u); } }, null);
            updateTile.tag = "#update";
            addTile(updateTile, small, tileH);
        }
        if (first == null) first = all;
        if ("#all".equals(focusedTag)) toFocus = all;
        if ("#settings".equals(focusedTag)) toFocus = set;
        (toFocus != null ? toFocus : first).requestFocus();
    }

    /** Senkrechte Seite ohne automatisches Mitscrollen - das uebernimmt reveal(). */
    static class VScroll extends ScrollView {
        VScroll(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) { return 0; }
    }

    /** Kopfbereich: null = Logo, sonst Titelbild + Infos zum fokussierten Titel. */
    void showHero(Emby.Entry e) { if (hero != null) hero.show(e); }

    /** Die Reihe des fokussierten Elements nach oben holen (wie bei Netflix). */
    void reveal(View focused) {
        if (vscroll == null || content == null) return;
        View v = focused;
        while (v != null && v.getParent() != content && v.getParent() instanceof View) v = (View) v.getParent();
        if (v == null || v.getParent() != content) return;
        int idx = content.indexOfChild(v);
        if (idx <= 0) { vscroll.smoothScrollTo(0, 0); return; }        // Apps: ganz oben
        // innerhalb eines Containers (Emby oben/unten) die konkrete Reihe suchen
        int y = v.getTop();
        View inner = focused;
        while (inner != null && inner.getParent() != v && inner.getParent() instanceof View) inner = (View) inner.getParent();
        if (inner != null && inner.getParent() == v) y += inner.getTop();
        vscroll.smoothScrollTo(0, Math.max(0, y - Style.px(4)));
    }

    void addTile(Tile t, int w, int h) {
        t.scroller = scroller;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(Style.px(10), 0, Style.px(10), 0);
        row.addView(t, lp);
    }

    void openSettings() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.settings.v2");
        if (i == null) i = new Intent(android.provider.Settings.ACTION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Style.start(this, i);
    }

    /** Hinweise aus Home Assistant oben in der Mitte anzeigen. */
    void setAlerts(List<String> alerts) {
        alertBox.removeAllViews();
        if (message != null) {
            AlertPill mp = new AlertPill(this, message);
            mp.setSingleLine(false);
            mp.setMaxLines(3);
            mp.setMaxWidth(Style.px(330));
            mp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            mp.setLineSpacing(Style.px(2), 1f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
            mlp.bottomMargin = Style.px(6);
            alertBox.addView(mp, mlp);
        }
        for (int i = 0; i < Math.min(message != null ? 2 : 3, alerts.size()); i++) {
            Style.Fit fit = new Style.Fit(this);
            fit.addView(new AlertPill(this, alerts.get(i)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = Style.px(6);
            alertBox.addView(fit, lp);
        }
    }

    static class AlertPill extends TextView {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float phase;
        AlertPill(Context c, String s) {
            super(c);
            setText(s);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            setTextColor(Color.WHITE);
            setTypeface(Style.POP);
            setSingleLine(true);
            setPadding(Style.px(30), Style.px(7), Style.px(16), Style.px(7));
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp;
            RectF r = new RectF(1 * dp, 1 * dp, getWidth() - 1 * dp, getHeight() - 1 * dp);
            float rad = Math.min(r.height() / 2, 16 * dp);
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xCC15141F);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(Style.brand(r.left, r.right)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * dp);
            c.drawRoundRect(r, rad, rad, p);
            // pulsierender Punkt
            phase += 0.08f;
            float a = 0.55f + 0.45f * (float) Math.sin(phase);
            p.setStyle(Paint.Style.FILL);
            p.setShader(Style.brand(10 * dp, 22 * dp));
            p.setAlpha((int) (255 * a));
            c.drawCircle(16 * dp, getHeight() / 2f, 5 * dp, p);
            p.setAlpha(255);
            super.onDraw(c);
            postInvalidateDelayed(50);
        }
    }

    /** Startbild: midnox.de im Verlauf, blendet weich in die Startseite. */
    void splash(final FrameLayout root) {
        // gleiches Bild wie das Startfenster des Systems -> nahtloser Uebergang
        final FrameLayout sp = new FrameLayout(this);
        sp.setBackgroundColor(0xFF0B0A14);
        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("splash_logo", "drawable", getPackageName()));
        sp.addView(logo, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(sp, new FrameLayout.LayoutParams(-1, -1));
        logo.animate().scaleX(1.03f).scaleY(1.03f).setDuration(1700).start();
        h.postDelayed(new Runnable() { public void run() {
            sp.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() { public void run() {
                root.removeView(sp);
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            } }).start();
        }}, 1500);
    }

    void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(now));
        date.setText(new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now).toUpperCase(Locale.GERMANY));
    }

    @Override protected void onResume() {
        super.onResume();
        if (SetupActivity.changed) { SetupActivity.changed = false; recreate(); return; }
        if (Style.loadConfig(this) == null) { message = "Willkommen bei midnox TV!\nEinstellungen \u2192 Mit Handy einrichten"; setAlerts(new ArrayList<String>()); }
        Theme now = Theme.current(this);
        if (!now.id.equals(theme.id) || !now.greeting.equals(theme.greeting)) { recreate(); return; } // neue Saison / neuer Tag
        overlay.start();
        // ohne Home Assistant keine leere "Zuhause"-Reihe
        home.setVisibility(Style.prefs(this).getBoolean("hide_home", false) || Style.loadConfig(this) == null || !Style.loadConfig(this).has("url") ? View.GONE : View.VISIBLE);
        buildTiles(false);
        if (System.currentTimeMillis() - lastUpdateCheck > 6 * 3600 * 1000L) {
            lastUpdateCheck = System.currentTimeMillis();
            Updater.check(this, new Updater.Callback() { public void result(org.json.JSONObject u) {
                if (u != null) { update = u; buildTiles(false); }
            }});
        }
        lastInput = System.currentTimeMillis();
        lastWeather = 0; lastRadio = 0;
        RadioService.listen(radioChanged); showRadio(); sleepTick();
        h.postDelayed(new Runnable() { public void run() { if (!isFinishing()) Morning.maybe(HomeActivity.this); } }, 1200);
        h.post(tick); ha.start(); emby.start(); tvRows.start();
    }
    @Override protected void onPause() { super.onPause(); RadioService.unlisten(radioChanged); h.removeCallbacks(tick); ha.stop(); emby.stop(); tvRows.stop(); overlay.stop(); }
    @Override public void onBackPressed() {
        // Startbildschirm bleibt; Zurueck springt nach oben zu den Apps
        if (vscroll.getScrollY() > 0) {
            vscroll.smoothScrollTo(0, 0);
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        }
    }
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (row.getChildCount() > 0 && !row.hasFocus()) row.getChildAt(0).requestFocus();
        scroller.smoothScrollTo(0, 0);
        vscroll.smoothScrollTo(0, 0);
    }
}
