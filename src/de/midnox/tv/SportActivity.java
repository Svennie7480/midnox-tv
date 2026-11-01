package de.midnox.tv;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Sport: 1./2. Bundesliga live (mit Sportschau-Radio je Spiel) und Formel 1. */
public class SportActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    final Map<String, Bitmap> icons = Collections.synchronizedMap(new HashMap<String, Bitmap>());
    final Set<String> loading = Collections.synchronizedSet(new HashSet<String>());
    String tab = "bl1";
    int day = 0;
    Sport.League league;
    Sport.F1 f1;
    long lastRadioMap;
    final Map<Integer, String> lastScore = new HashMap<>();
    int gen;

    LinearLayout content, list, tabs;
    TextView status, dayLabel, banner;
    Pill radioBtn;
    TableView table;
    F1View f1v;
    Theme.Overlay overlay;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme theme = Theme.current(this); theme.apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        overlay = new Theme.Overlay(this, theme);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(Style.px(52), Style.px(28), Style.px(52), Style.px(16));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new Style.GradientText(this, "Sport", 30), new LinearLayout.LayoutParams(Style.px(120), Style.px(42)));
        tabs = new LinearLayout(this);
        head.addView(tabs);
        tab(tabs, "1. Bundesliga", "bl1"); tab(tabs, "2. Bundesliga", "bl2"); tab(tabs, "Formel 1", "f1"); tab(tabs, "Handball", "hb2");
        View sp = new View(this); head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        radioBtn = new Pill(this, "Konferenz hören", new Runnable() { public void run() { conference(); } });
        head.addView(radioBtn);
        main.addView(head);
        status = Style.text(this, "Lade …", 12, Style.MUTED);
        status.setPadding(Style.px(4), Style.px(6), 0, Style.px(10));
        main.addView(status);

        content = new LinearLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        banner = Style.text(this, "", 22, Color.WHITE);
        banner.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Style.px(30), Style.px(16), Style.px(30), Style.px(16));
        banner.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF07C3AED, 0xF03B5BF0, 0xF006B6D4}) {{ setCornerRadius(Style.px(20)); }});
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        bl.bottomMargin = Style.px(40);
        root.addView(banner, bl);

        setContentView(root);
        show("bl1");
    }

    void tab(LinearLayout p, String label, final String id) {
        Pill t = new Pill(this, label, new Runnable() { public void run() { day = 0; show(id); } });
        t.setTag(id);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.leftMargin = Style.px(10);
        p.addView(t, lp);
    }

    void show(String id) {
        tab = id;
        for (int i = 0; i < tabs.getChildCount(); i++) { Pill p = (Pill) tabs.getChildAt(i); p.active = id.equals(p.getTag()); p.invalidate(); }
        content.removeAllViews();
        radioBtn.setVisibility(View.VISIBLE);
        if (id.equals("f1")) {
            f1v = new F1View();
            content.addView(f1v, new LinearLayout.LayoutParams(-1, -1));
            status.setText("Lade Formel 1 …");
        } else {
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            content.addView(left, new LinearLayout.LayoutParams(0, -1, 1.45f));
            LinearLayout dh = new LinearLayout(this);
            dh.setGravity(Gravity.CENTER_VERTICAL);
            Pill prev = new Pill(this, "◀", new Runnable() { public void run() { if (league != null && league.day > 1) { day = league.day - 1; reload(); } } });
            Pill next = new Pill(this, "▶", new Runnable() { public void run() { if (league != null && league.day < league.maxDay) { day = league.day + 1; reload(); } } });
            dayLabel = Style.text(this, "", 16, Color.WHITE);
            dayLabel.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
            dayLabel.setPadding(Style.px(14), 0, Style.px(14), 0);
            dh.addView(prev); dh.addView(dayLabel); dh.addView(next);
            left.addView(dh);
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false); sv.setClipToPadding(false);
            list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, Style.px(10), Style.px(10), Style.px(30));
            sv.addView(list);
            left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            table = new TableView();
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -1, 1f); tl.leftMargin = Style.px(24);
            content.addView(table, tl);
            status.setText(id.equals("hb2") ? "Lade 2. Handball-Bundesliga …" : "Lade " + (id.equals("bl1") ? "1." : "2.") + " Bundesliga …");
        }
        reload();
    }

    void reload() {
        final int my = ++gen;
        h.removeCallbacks(refresh);
        final String t = tab; final int d = day;
        new Thread(new Runnable() { public void run() {
            try {
                if (t.equals("f1")) {
                    final Sport.F1 f = Sport.loadF1();
                    h.post(new Runnable() { public void run() { if (my == gen) { f1 = f; fillF1(); } } });
                } else if (t.equals("hb2")) {
                    final Sport.League l = Sport.loadHb(d);
                    h.post(new Runnable() { public void run() { if (my == gen) { league = l; fillLeague(); } } });
                } else {
                    final Sport.League l = Sport.load(t, d);
                    long now = System.currentTimeMillis();
                    boolean near = false;
                    for (Sport.Match m : l.matches) if (now > m.kickoff - 30 * 60000L && now < m.kickoff + 130 * 60000L) near = true;
                    if (near && (now - lastRadioMap > 3 * 60000L || league == null || !league.code.equals(t) || league.day != l.day)) { Sport.mapRadio(l); lastRadioMap = now; }
                    else if (league != null && league.code.equals(t) && league.day == l.day) for (Sport.Match m : l.matches) for (Sport.Match o : league.matches) if (o.id == m.id) m.radio = o.radio;
                    h.post(new Runnable() { public void run() { if (my == gen) { goals(l); league = l; fillLeague(); } } });
                }
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { if (my == gen) { status.setText("Daten gerade nicht erreichbar – neuer Versuch in Kürze"); h.postDelayed(refresh, 30000); } } });
            }
        }}).start();
    }

    final Runnable refresh = new Runnable() { public void run() { reload(); } };

    // ------------------------------------------------------------ Bundesliga
    void fillLeague() {
        Sport.League l = league;
        long now = System.currentTimeMillis();
        boolean live = false;
        for (Sport.Match m : l.matches) if (m.live(now)) live = true;
        dayLabel.setText(l.day + ". Spieltag");
        View f = getCurrentFocus();
        int focusId = f instanceof MatchRow ? ((MatchRow) f).m.id : -1;
        list.removeAllViews();
        MatchRow first = null, again = null, firstLive = null;
        for (Sport.Match m : l.matches) {
            MatchRow r = new MatchRow(m);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Style.px(62)); lp.bottomMargin = Style.px(6);
            list.addView(r, lp);
            if (first == null) first = r;
            if (m.id == focusId) again = r;
            if (firstLive == null && m.live(now)) firstLive = r;
        }
        if (again != null) again.requestFocus();
        else if (f == null || !(f instanceof Pill)) (firstLive != null ? firstLive : first != null ? first : radioBtn).requestFocus();
        table.invalidate();
        String upd = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());
        if (l.code.equals("hb2")) {
            String r = Style.prefs(this).getString("hb_radio", "").trim();
            status.setText((live ? "● LIVE  ·  aktualisiert " + upd + " – alle 30 Sek." : "Stand " + upd) + "   ·   " + (r.isEmpty() ? "Live bei Dyn" : "OK = " + Sport.hbClub(this) + " live im Radio") + "   ·   MENU = Highlights & mehr");
            radioBtn.setText(r.isEmpty() ? "▶  Dyn öffnen" : RadioService.isPlaying(r) ? "❚❚  Radio" : "▶  " + Sport.hbClub(this) + " hören");
        } else {
        status.setText((live ? "● LIVE  ·  aktualisiert " + upd + " – alle 30 Sek." : "Stand " + upd) + "   ·   OK = Radio-Reportage   ·   MENU = Sender, Highlights, Live-Leiste");
        radioBtn.setText(RadioService.isPlaying(Sport.stream(l.code, "konferenz")) ? "❚❚  Konferenz" : "▶  Konferenz hören");
        }
        h.postDelayed(refresh, live ? 30000 : 5 * 60000L);
    }

    void goals(Sport.League l) {
        for (Sport.Match m : l.matches) {
            String sc = m.s1 + ":" + m.s2;
            String old = lastScore.get(m.id);
            lastScore.put(m.id, sc);
            if (old == null || old.equals(sc) || m.s1 < 0 || m.goals.isEmpty()) continue;
            Sport.Goal g = m.goals.get(m.goals.size() - 1);
            flash("TOR!   " + m.t1.shortName + "  " + m.s1 + " : " + m.s2 + "  " + m.t2.shortName + (g.scorer.isEmpty() ? "" : "   ·   " + g.scorer + " " + g.minute + "'"));
        }
    }
    void flash(String t) {
        banner.setText(t); banner.setVisibility(View.VISIBLE); banner.setAlpha(0f);
        banner.animate().alpha(1f).setDuration(300).start();
        h.removeCallbacks(hideBanner); h.postDelayed(hideBanner, 9000);
    }
    final Runnable hideBanner = new Runnable() { public void run() { banner.animate().alpha(0f).setDuration(500).withEndAction(new Runnable() { public void run() { banner.setVisibility(View.GONE); } }).start(); } };

    /** Formel 1 2026: alles bei Sky; RTL zeigt einzelne Rennen frei (u. a. Belgien, Niederlande, Spanien/Madrid, Las Vegas). */
    String f1Tv() {
        String n = f1 != null && f1.next != null ? (f1.next.name + " " + f1.next.place + " " + f1.next.circuit).toLowerCase(Locale.ROOT) : "";
        boolean rtl = n.matches(".*(belgi|dutch|netherlands|zandvoort|spanish|madrid|las vegas).*");
        return rtl ? "Sky und frei bei RTL" : "Sky";
    }

    void conference() {
        if (tab.equals("f1")) { Style.toast(this, Watch.open(this, f1Tv().contains("RTL") ? "RTL" : "Sky")); return; }
        if (league == null) return;
        if (league.code.equals("hb2")) { hbListen(); return; }
        String u = Sport.stream(league.code, "konferenz");
        if (RadioService.isPlaying(u)) RadioService.start(this, "stop");
        else RadioService.playCustom(this, "sport_" + league.code + "_konf", "Sportschau " + league.name + " – Konferenz", 0xFF0067B1, u);
        Style.toast(this, RadioService.isPlaying(u) ? "Konferenz aus" : "Bundesliga-Konferenz der ARD Sportschau");
        h.postDelayed(new Runnable() { public void run() { if (league != null) fillLeague(); } }, 1500);
    }

    /** MENU auf einem Spiel: Radio, Sender oeffnen, Highlights. */
    void options(final Sport.Match m) {
        if (league == null) return;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (!m.finished) {
            if (m.hb) { labels.add("\uD83D\uDCFB  " + (Style.prefs(this).getString("hb_radio", "").trim().isEmpty() ? "Dyn öffnen" : "Radio")); acts.add(new Runnable() { public void run() { hbListen(); } }); }
            else { labels.add("\uD83D\uDCFB  Live-Reportage im Radio"); acts.add(new Runnable() { public void run() { playMatch(m); } }); }
            for (final String w : Watch.who(league.code, m, league.day)) {
                if (m.hb && w.equals("Dyn") && Style.prefs(this).getString("hb_radio", "").trim().isEmpty()) continue;
                labels.add("\uD83D\uDCFA  Ansehen bei " + w);
                acts.add(new Runnable() { public void run() { Style.toast(SportActivity.this, Watch.open(SportActivity.this, w)); } });
            }
        }
        if (m.finished || m.live(now)) {
            labels.add("\uD83C\uDFAC  Highlights auf YouTube");
            acts.add(new Runnable() { public void run() { Style.toast(SportActivity.this, Watch.highlights(SportActivity.this, m.t1.shortName, m.t2.shortName, m.hb)); } });
        }
        labels.add("\u26BD  Live-Leiste ein/aus");
        acts.add(new Runnable() { public void run() { Style.toast(SportActivity.this, LiveBar.toggle(SportActivity.this)); } });
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(m.t1.shortName + " – " + m.t2.shortName)
            .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int i) { acts.get(i).run(); } })
            .show();
    }

    void hbListen() {
        Style.toast(this, Handball.listen(this));
        h.postDelayed(new Runnable() { public void run() { if (league != null) fillLeague(); } }, 1500);
    }

    void playMatch(Sport.Match m) {
        if (m.hb) { hbListen(); return; }
        String u = m.radio != null ? m.radio : Sport.stream(league.code, "konferenz");
        if (RadioService.isPlaying(u)) { RadioService.start(this, "stop"); Style.toast(this, "Radio aus"); }
        else {
            String name = m.radio != null ? m.t1.shortName + " – " + m.t2.shortName + " (Sportschau)" : "Sportschau " + league.name + " – Konferenz";
            RadioService.playCustom(this, "sport_" + m.id, name, 0xFF0067B1, u);
            Style.toast(this, m.radio != null ? "Live-Reportage: " + m.t1.shortName + " – " + m.t2.shortName : "Für dieses Spiel gibt es gerade keine Einzelreportage – Konferenz läuft");
        }
        h.postDelayed(new Runnable() { public void run() { for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate(); } }, 1500);
    }

    Bitmap icon(final String url, final View v) {
        if (url == null) return null;
        Bitmap b = icons.get(url);
        if (b != null || !loading.add(url)) return b;
        new Thread(new Runnable() { public void run() {
            Bitmap x = Emby.fetchAlpha(url);
            if (x != null) { icons.put(url, x); h.post(new Runnable() { public void run() { v.invalidate(); if (table != null) table.invalidate(); } }); }
        }}).start();
        return null;
    }

    final class MatchRow extends View {
        final Sport.Match m;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        MatchRow(Sport.Match m) {
            super(SportActivity.this); this.m = m;
            setFocusable(true); setClickable(true); t.setTypeface(Style.POP);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { playMatch(MatchRow.this.m); } });
            setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { options(MatchRow.this.m); return true; } });
            setOnKeyListener(new OnKeyListener() { public boolean onKey(View v, int k, KeyEvent e) {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(MatchRow.this.m); return true; }
                return k == KeyEvent.KEYCODE_MENU;
            } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { invalidate(); if (f) table.focus = MatchRow.this.m; table.invalidate(); } });
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), hh = getHeight();
            long now = System.currentTimeMillis();
            boolean foc = isFocused(), live = m.live(now);
            RectF r = new RectF(dp, dp, w - dp, hh - dp);
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(foc ? 0xFF1F1D30 : 0xB315141F);
            c.drawRoundRect(r, 14 * dp, 14 * dp, p);
            if (foc) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(Style.brand(r.left, r.right)); c.drawRoundRect(r, 14 * dp, 14 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            if (m.hb && (Sport.isClub(getContext(), m.t1) || Sport.isClub(getContext(), m.t2))) { p.setShader(Style.brand(0, 6 * dp)); c.drawRoundRect(new RectF(dp, 10 * dp, 6 * dp, hh - 10 * dp), 3 * dp, 3 * dp, p); p.setShader(null); }
            // Status
            String st = m.status(now);
            t.setTextSize(11.5f * dp); t.setFakeBoldText(true); t.setTextAlign(Paint.Align.CENTER);
            float sx = 52 * dp, cy = hh / 2;
            if (live) {
                float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(now / 500.0));
                p.setColor(Color.argb((int) (255 * pulse), 239, 68, 68));
                c.drawRoundRect(new RectF(sx - 40 * dp, cy - 12 * dp, sx + 40 * dp, cy + 12 * dp), 12 * dp, 12 * dp, p);
                t.setColor(Color.WHITE); postInvalidateDelayed(120);
            } else t.setColor(m.finished ? 0xFF8A8FA8 : 0xFFC9CCE0);
            c.drawText(st.replace("heute ", ""), sx, cy + 4 * dp, t);
            if (!live && !m.finished && st.startsWith("heute")) { t.setTextSize(9 * dp); t.setColor(Style.MUTED); c.drawText("heute", sx, cy - 10 * dp, t); }
            if (league != null) {   // Wo laeuft das? bzw. Highlights
                String tv = m.finished ? (foc ? "MENU: Highlights" : "") : Watch.label(Watch.who(league.code, m, league.day));
                if (!tv.isEmpty()) { t.setTextSize(8.5f * dp); t.setFakeBoldText(false); t.setColor(m.finished ? 0xFF22D3EE : 0xFF9EA2B8); c.drawText(TextUtils.ellipsize(tv, t, 100 * dp, TextUtils.TruncateAt.END).toString(), sx, hh - 6 * dp, t); }
            }
            // Teams + Ergebnis
            float mid = w * 0.56f, is = 30 * dp;
            t.setFakeBoldText(foc); t.setTextSize(15 * dp); t.setColor(Color.WHITE);
            boolean win1 = m.finished && m.s1 > m.s2, win2 = m.finished && m.s2 > m.s1;
            Bitmap b1 = icon(m.t1.icon, this), b2 = icon(m.t2.icon, this);
            float lineY = m.goals.isEmpty() || !foc ? cy : cy - 7 * dp;
            if (b1 != null) drawIcon(c, b1, mid - 54 * dp - is, lineY - is / 2, is);
            t.setTextAlign(Paint.Align.RIGHT); t.setColor(win2 ? 0xFF9EA2B8 : Color.WHITE);
            c.drawText(TextUtils.ellipsize(m.t1.shortName, t, mid - 64 * dp - is - 104 * dp, TextUtils.TruncateAt.END).toString(), mid - 60 * dp - is, lineY + 5 * dp, t);
            if (b2 != null) drawIcon(c, b2, mid + 54 * dp, lineY - is / 2, is);
            t.setTextAlign(Paint.Align.LEFT); t.setColor(win1 ? 0xFF9EA2B8 : Color.WHITE);
            c.drawText(TextUtils.ellipsize(m.t2.shortName, t, w - (mid + 60 * dp + is) - 46 * dp, TextUtils.TruncateAt.END).toString(), mid + 60 * dp + is, lineY + 5 * dp, t);
            t.setTextAlign(Paint.Align.CENTER); t.setFakeBoldText(true); t.setTextSize(22 * dp);
            t.setColor(live ? 0xFFFCA5A5 : Color.WHITE);
            c.drawText(m.s1 < 0 ? "–:–" : m.s1 + " : " + m.s2, mid, lineY + 8 * dp, t);
            t.setFakeBoldText(false);
            // Torschuetzen
            if (foc && !m.goals.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Sport.Goal g : m.goals) { if (sb.length() > 0) sb.append("   "); sb.append(g.minute).append("' ").append(g.scorer.isEmpty() ? "Tor" : g.scorer).append(g.penalty ? " (E)" : g.own ? " (ET)" : "").append(" ").append(g.s1).append(":").append(g.s2); }
                t.setTextSize(11 * dp); t.setColor(0xFFB4B8CC); t.setTextAlign(Paint.Align.CENTER);
                c.drawText(TextUtils.ellipsize(sb.toString(), t, w - 130 * dp, TextUtils.TruncateAt.END).toString(), (w + 100 * dp) / 2, hh - 10 * dp, t);
            }
            // Radio-Symbol
            boolean playing = RadioService.isPlaying(m.radio != null ? m.radio : "-");
            if (m.radio != null || playing) {
                float rx = w - 26 * dp;
                p.setColor(playing ? 0xFF22C55E : (foc ? 0xFFFFFFFF : 0xFF8A8FA8));
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setStrokeCap(Paint.Cap.ROUND);
                c.drawCircle(rx, cy, 3 * dp, p);
                c.drawArc(new RectF(rx - 9 * dp, cy - 9 * dp, rx + 9 * dp, cy + 9 * dp), -50, 100, false, p);
                c.drawArc(new RectF(rx - 9 * dp, cy - 9 * dp, rx + 9 * dp, cy + 9 * dp), 130, 100, false, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }
    void drawIcon(Canvas c, Bitmap b, float x, float y, float s) {
        float sc = Math.min(s / b.getWidth(), s / b.getHeight());
        float bw = b.getWidth() * sc, bh = b.getHeight() * sc;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(b, null, new RectF(x + (s - bw) / 2, y + (s - bh) / 2, x + (s + bw) / 2, y + (s + bh) / 2), p);
    }

    /** Tabelle rechts; die Teams des ausgewaehlten Spiels sind hervorgehoben. */
    final class TableView extends View {
        Sport.Match focus;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        TableView() { super(SportActivity.this); t.setTypeface(Style.POP); }
        @Override protected void onDraw(Canvas c) {
            Sport.League l = league;
            if (l == null || l.table.isEmpty()) return;
            float dp = Style.dp, w = getWidth(), hh = getHeight();
            p.setColor(0x9915141F); c.drawRoundRect(new RectF(0, 0, w, hh), 16 * dp, 16 * dp, p);
            int n = l.table.size();
            float top = 34 * dp, rowH = Math.min(24 * dp, (hh - top - 8 * dp) / n);
            t.setTextSize(10 * dp); t.setColor(Style.MUTED); t.setLetterSpacing(0.15f); t.setTextAlign(Paint.Align.LEFT);
            c.drawText("TABELLE", 14 * dp, 22 * dp, t);
            t.setTextAlign(Paint.Align.RIGHT);
            c.drawText("SP", w - 92 * dp, 22 * dp, t); c.drawText("DIFF", w - 50 * dp, 22 * dp, t); c.drawText("PKT", w - 14 * dp, 22 * dp, t);
            t.setLetterSpacing(0);
            for (int i = 0; i < n; i++) {
                Sport.Row r = l.table.get(i);
                float y = top + i * rowH, cy = y + rowH / 2;
                boolean hl = focus != null && (r.team.name.equals(focus.t1.name) || r.team.name.equals(focus.t2.name)) || l.code.equals("hb2") && Sport.isClub(getContext(), r.team);
                if (hl) { p.setShader(Style.brand(6 * dp, w - 6 * dp)); p.setAlpha(90); c.drawRoundRect(new RectF(6 * dp, y + dp, w - 6 * dp, y + rowH - dp), 8 * dp, 8 * dp, p); p.setShader(null); p.setAlpha(255); }
                int zone = l.code.equals("hb2") ? (r.pos <= 2 ? 0xFF22C55E : r.pos >= n - 1 ? 0xFFEF4444 : 0) : l.code.equals("bl1") ? (r.pos <= 4 ? 0xFF22C55E : r.pos <= 6 ? 0xFF06B6D4 : r.pos == 16 ? 0xFFF59E0B : r.pos >= 17 ? 0xFFEF4444 : 0)
                                                : (r.pos <= 2 ? 0xFF22C55E : r.pos == 3 ? 0xFF06B6D4 : r.pos == 16 ? 0xFFF59E0B : r.pos >= 17 ? 0xFFEF4444 : 0);
                if (zone != 0) { p.setColor(zone); c.drawRoundRect(new RectF(8 * dp, cy - 7 * dp, 11 * dp, cy + 7 * dp), 1.5f * dp, 1.5f * dp, p); }
                t.setTextSize(12 * dp); t.setColor(hl ? Color.WHITE : 0xFFB4B8CC); t.setTextAlign(Paint.Align.RIGHT); t.setFakeBoldText(hl);
                c.drawText(String.valueOf(r.pos), 34 * dp, cy + 4 * dp, t);
                Bitmap b = icon(r.team.icon, this);
                if (b != null) drawIcon(c, b, 42 * dp, cy - 8 * dp, 16 * dp);
                t.setTextAlign(Paint.Align.LEFT); t.setColor(hl ? Color.WHITE : 0xFFE6E8F2);
                c.drawText(TextUtils.ellipsize(r.team.shortName, t, w - 64 * dp - 110 * dp, TextUtils.TruncateAt.END).toString(), 64 * dp, cy + 4 * dp, t);
                t.setTextAlign(Paint.Align.RIGHT); t.setColor(0xFFB4B8CC);
                c.drawText(String.valueOf(r.played), w - 92 * dp, cy + 4 * dp, t);
                c.drawText((r.diff > 0 ? "+" : "") + r.diff, w - 50 * dp, cy + 4 * dp, t);
                t.setColor(Color.WHITE); t.setFakeBoldText(true);
                c.drawText(String.valueOf(r.points), w - 14 * dp, cy + 4 * dp, t);
                t.setFakeBoldText(false);
            }
        }
    }

    // ------------------------------------------------------------ Formel 1
    void fillF1() {
        f1v.invalidate();
        boolean live = f1 != null && !f1.live.isEmpty();
        String upd = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());
        status.setText(live ? "● LIVE  " + f1.liveSession + "  ·  Positionen von der Zeitmessung (OpenF1), aktualisiert " + upd
                : "Stand " + upd + "   ·   TV: " + f1Tv() + "   ·   Während der Sessions zeigt die Seite die Live-Positionen.");
        radioBtn.setText(f1Tv().contains("RTL") ? "▶  RTL öffnen" : "▶  Sky öffnen");
        h.postDelayed(refresh, live ? 8000 : 10 * 60000L);
        if (tabs.findFocus() == null) tabs.getChildAt(2).requestFocus();
    }

    final class F1View extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        F1View() { super(SportActivity.this); t.setTypeface(Style.POP); }
        @Override protected void onDraw(Canvas c) {
            Sport.F1 f = f1;
            if (f == null) return;
            float dp = Style.dp, w = getWidth(), hh = getHeight(), lw = w * 0.56f;
            long now = System.currentTimeMillis();
            // links oben: naechstes Rennen oder Live-Wertung
            RectF a = new RectF(0, 0, lw - 12 * dp, hh);
            p.setColor(0x9915141F); c.drawRoundRect(a, 16 * dp, 16 * dp, p);
            float x = 18 * dp, y = 30 * dp;
            if (!f.live.isEmpty()) {
                label(c, "LIVE  ·  " + f.liveSession.toUpperCase(Locale.GERMANY), x, y, 0xFFEF4444);
                float rowH = Math.min(22 * dp, (hh - 50 * dp) / Math.max(10, Math.min(20, f.live.size())));
                for (int i = 0; i < f.live.size() && i < 20; i++) posRow(c, f.live.get(i), x, y + 14 * dp + i * rowH, lw - 30 * dp, rowH, true);
            } else if (f.next != null) {
                Sport.Race r = f.next;
                label(c, "NÄCHSTES RENNEN  ·  RUNDE " + r.round, x, y, Style.MUTED);
                t.setTextSize(24 * dp); t.setColor(Color.WHITE); t.setFakeBoldText(true); t.setTextAlign(Paint.Align.LEFT);
                c.drawText(r.name, x, y + 34 * dp, t); t.setFakeBoldText(false);
                t.setTextSize(13 * dp); t.setColor(0xFFB4B8CC);
                c.drawText(r.circuit + "  ·  " + r.place, x, y + 56 * dp, t);
                long left = r.at - now;
                String cd = left <= 0 ? "läuft gerade" : left < 3600000L ? "in " + left / 60000 + " Min." : left < 2 * 86400000L ? "in " + left / 3600000 + " Std. " + (left % 3600000) / 60000 + " Min." : "in " + left / 86400000 + " Tagen";
                t.setTextSize(15 * dp); t.setColor(0xFF22D3EE); t.setFakeBoldText(true);
                c.drawText("Start " + cd, x, y + 82 * dp, t); t.setFakeBoldText(false);
                SimpleDateFormat df = new SimpleDateFormat("EE dd.MM.  HH:mm 'Uhr'", Locale.GERMANY);
                float sy = y + 110 * dp;
                for (Sport.Session s : r.sessions) {
                    boolean past = s.at + 2 * 3600000L < now, race = s.name.equals("Rennen");
                    t.setTextSize(13 * dp); t.setColor(past ? 0xFF5D6074 : race ? Color.WHITE : 0xFFC9CCE0); t.setFakeBoldText(race);
                    c.drawText(s.name, x, sy, t);
                    t.setTextAlign(Paint.Align.RIGHT); c.drawText(df.format(new Date(s.at)), lw - 30 * dp, sy, t); t.setTextAlign(Paint.Align.LEFT);
                    sy += 22 * dp;
                }
                t.setFakeBoldText(false);
                if (!f.lastResult.isEmpty() && f.last != null) {
                    sy += 14 * dp;
                    label(c, "LETZTES RENNEN  ·  " + f.last.name.toUpperCase(Locale.GERMANY), x, sy, Style.MUTED);
                    float rowH = 21 * dp;
                    int n = (int) Math.min(10, (hh - sy - 16 * dp) / rowH);
                    for (int i = 0; i < n && i < f.lastResult.size(); i++) posRow(c, f.lastResult.get(i), x, sy + 10 * dp + i * rowH, lw - 30 * dp, rowH, false);
                }
            }
            // rechts: WM-Stand Fahrer
            RectF b = new RectF(lw + 12 * dp, 0, w, hh);
            p.setColor(0x9915141F); c.drawRoundRect(b, 16 * dp, 16 * dp, p);
            label(c, "FAHRER-WM", b.left + 18 * dp, 30 * dp, Style.MUTED);
            float rowH = Math.min(22 * dp, (hh - 50 * dp) / Math.max(1, Math.min(22, f.drivers.size())));
            for (int i = 0; i < f.drivers.size() && 44 * dp + (i + 1) * rowH < hh; i++) posRow(c, f.drivers.get(i), b.left + 18 * dp, 44 * dp + i * rowH, b.width() - 36 * dp, rowH, false);
            if (!f.live.isEmpty()) postInvalidateDelayed(1000);
        }
        void label(Canvas c, String s, float x, float y, int col) {
            t.setTextSize(10.5f * Style.dp); t.setColor(col); t.setLetterSpacing(0.18f); t.setTextAlign(Paint.Align.LEFT); t.setFakeBoldText(true);
            c.drawText(s, x, y, t); t.setLetterSpacing(0); t.setFakeBoldText(false);
        }
        void posRow(Canvas c, Sport.Pos d, float x, float y, float w, float rowH, boolean live) {
            float dp = Style.dp, cy = y + rowH / 2;
            t.setTextSize(12.5f * dp); t.setTextAlign(Paint.Align.RIGHT); t.setColor(0xFFB4B8CC);
            c.drawText(String.valueOf(d.pos), x + 20 * dp, cy + 4 * dp, t);
            p.setColor(d.color); c.drawRoundRect(new RectF(x + 28 * dp, cy - 7 * dp, x + 31 * dp, cy + 7 * dp), 1.5f * dp, 1.5f * dp, p);
            t.setTextAlign(Paint.Align.LEFT); t.setColor(Color.WHITE); t.setFakeBoldText(true);
            String nm = live || d.name == null ? d.code : d.name;
            c.drawText(TextUtils.ellipsize(nm == null ? "" : nm, t, w * 0.45f, TextUtils.TruncateAt.END).toString(), x + 40 * dp, cy + 4 * dp, t);
            t.setFakeBoldText(false); t.setColor(0xFF8A8FA8); t.setTextSize(11 * dp);
            if (d.team != null && !d.team.equals(d.name)) c.drawText(TextUtils.ellipsize(d.team, t, w * 0.28f, TextUtils.TruncateAt.END).toString(), x + 40 * dp + w * 0.47f, cy + 4 * dp, t);
            t.setTextAlign(Paint.Align.RIGHT); t.setTextSize(12.5f * dp); t.setColor(Color.WHITE);
            String right = live ? (d.gap == null ? "" : d.gap) : d.gap != null && !d.gap.isEmpty() ? d.gap : (d.points == Math.rint(d.points) ? String.valueOf((int) d.points) : String.valueOf(d.points)) + " P.";
            if (!live && d.gap != null && !d.gap.isEmpty() && d.points > 0) right = d.gap;
            c.drawText(right, x + w, cy + 4 * dp, t);
        }
    }

    @Override protected void onResume() { super.onResume(); overlay.start(); if (league != null || f1 != null) reload(); }
    @Override protected void onPause() { super.onPause(); overlay.stop(); h.removeCallbacks(refresh); gen++; }
}
