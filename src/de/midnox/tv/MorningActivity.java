package de.midnox.tv;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Guten-Morgen-Bildschirm: Uhr, Wetter, "Dein Tag" und Nachrichten. */
public class MorningActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    TextView clock, date, sun, wTemp, wLine, newsTitle, newsDesc, newsSrc, status;
    Weather.Icon wIcon;
    LinearLayout lines, newsList, weatherBox;
    ImageView newsImg;
    Theme.Overlay overlay;
    Morning.Day day;
    final Map<String, Bitmap> imgs = new HashMap<>();
    long lastKey = System.currentTimeMillis();
    String nameShown = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme theme = Theme.current(this);
        theme.apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        overlay = new Theme.Overlay(this, theme);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(Style.px(52), Style.px(40), Style.px(52), Style.px(34));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- links: Uhr, Begruessung, Wetter, Knoepfe
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        main.addView(left, new LinearLayout.LayoutParams(Style.px(290), -1));
        clock = Style.text(this, "", 64, Color.WHITE);
        clock.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
        clock.setIncludeFontPadding(false);
        left.addView(clock);
        final FrameLayout greetBox = new FrameLayout(this);
        left.addView(greetBox, new LinearLayout.LayoutParams(-1, Style.px(42)));
        greetBox.setTag("greet");
        date = Style.text(this, "", 15, 0xFFC9CCE0);
        date.setLineSpacing(Style.px(2), 1f);
        left.addView(date);

        weatherBox = new LinearLayout(this);
        weatherBox.setOrientation(LinearLayout.VERTICAL);
        weatherBox.setPadding(0, Style.px(26), 0, 0);
        weatherBox.setVisibility(View.GONE);
        left.addView(weatherBox);
        LinearLayout wRow = new LinearLayout(this);
        wRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherBox.addView(wRow);
        wIcon = new Weather.Icon(this);
        wRow.addView(wIcon, new LinearLayout.LayoutParams(Style.px(64), Style.px(64)));
        wTemp = Style.text(this, "", 38, Color.WHITE);
        wTemp.setPadding(Style.px(14), 0, 0, 0);
        wRow.addView(wTemp);
        wLine = Style.text(this, "", 14, 0xFFC9CCE0);
        wLine.setPadding(0, Style.px(6), 0, 0);
        weatherBox.addView(wLine);
        sun = Style.text(this, "", 12, Style.MUTED);
        sun.setPadding(0, Style.px(4), 0, 0);
        weatherBox.addView(sun);

        View spacer = new View(this);
        left.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        left.addView(btns);
        Pill go = new Pill(this, "Los geht’s", new Runnable() { public void run() { finish(); } });
        btns.addView(go, wrap(0));
        Pill radio = new Pill(this, "Radio dazu", new Runnable() { public void run() { RadioService.start(MorningActivity.this, "play"); finish(); } });
        btns.addView(radio, wrap(Style.px(10)));
        go.requestFocus();

        // ---------------- Mitte: Dein Tag
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Style.px(30), Style.px(8), Style.px(30), 0);
        main.addView(mid, new LinearLayout.LayoutParams(0, -1, 1f));
        mid.addView(head("DEIN TAG"));
        status = Style.text(this, "Lade die Infos für heute …", 14, Style.MUTED);
        mid.addView(status);
        lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        mid.addView(lines);

        // ---------------- rechts: Nachrichten
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(0, Style.px(8), 0, 0);
        right.setTag("news");
        main.addView(right, new LinearLayout.LayoutParams(Style.px(330), -1));
        right.addView(head("NACHRICHTEN"));
        newsImg = new ImageView(this);
        newsImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        newsImg.setBackgroundColor(0x3315141F);
        newsImg.setClipToOutline(true);
        newsImg.setOutlineProvider(new ViewOutlineProvider() { public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * Style.dp); } });
        right.addView(newsImg, new LinearLayout.LayoutParams(-1, Style.px(150)));
        newsSrc = Style.text(this, "", 10, Style.MUTED);
        newsSrc.setLetterSpacing(0.15f);
        newsSrc.setPadding(0, Style.px(8), 0, 0);
        right.addView(newsSrc);
        newsTitle = Style.text(this, "", 15, Color.WHITE);
        newsTitle.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
        newsTitle.setMaxLines(2); newsTitle.setEllipsize(TextUtils.TruncateAt.END);
        right.addView(newsTitle);
        newsDesc = Style.text(this, "", 12, 0xFFC9CCE0);
        newsDesc.setMaxLines(3); newsDesc.setEllipsize(TextUtils.TruncateAt.END);
        newsDesc.setLineSpacing(Style.px(2), 1f);
        newsDesc.setPadding(0, Style.px(3), 0, Style.px(8));
        right.addView(newsDesc);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        right.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        newsList = new LinearLayout(this);
        newsList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(newsList);
        right.setVisibility(Style.prefs(this).getInt("morning_news", 2) == 0 ? View.GONE : View.VISIBLE);

        setContentView(root);
        tick.run();
        new Thread(new Runnable() { public void run() {
            final Morning.Day d = Morning.load(MorningActivity.this);
            h.post(new Runnable() { public void run() { day = d; fill(); } });
        }}).start();
    }

    LinearLayout.LayoutParams wrap(int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.topMargin = top; return lp; }

    TextView head(String s) {
        TextView t = Style.text(this, s, 11, Style.MUTED);
        t.setLetterSpacing(0.25f);
        t.setPadding(0, 0, 0, Style.px(12));
        return t;
    }

    final Runnable tick = new Runnable() { public void run() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(now));
        String g = Morning.greeting() + (day == null || day.name.isEmpty() ? "" : ", " + day.name);
        if (!g.equals(nameShown)) {
            nameShown = g;
            FrameLayout box = (FrameLayout) clock.getRootView().findViewWithTag("greet");
            if (box != null) { box.removeAllViews(); box.addView(new Style.GradientText(MorningActivity.this, g, 28), new FrameLayout.LayoutParams(-1, -1)); }
        }
        String dt = new SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY).format(now);
        if (day != null && !day.holiday.isEmpty()) dt += "\n" + day.holiday;
        date.setText(dt);
        if (System.currentTimeMillis() - lastKey > 15 * 60000L) { finish(); return; }
        h.postDelayed(this, 15000);
    }};

    void fill() {
        tick.run();
        // Wetter
        Weather.Now w = day.weather;
        if (w != null) {
            weatherBox.setVisibility(View.VISIBLE);
            wIcon.set(w.cond);
            wTemp.setText(Double.isNaN(w.temp) ? "" : Weather.deg(w.temp));
            StringBuilder sb = new StringBuilder(Weather.name(w.cond));
            if (!Double.isNaN(w.hi) && !Double.isNaN(w.lo)) sb.append("  ·  ").append(Weather.deg(w.lo)).append(" bis ").append(Weather.deg(w.hi));
            if (w.rain >= 0) sb.append("\nRegenrisiko ").append(w.rain).append(" %").append(w.rain >= 50 ? " – Schirm mitnehmen" : "");
            wLine.setText(sb.toString());
        }
        sun.setText(day.sun);
        // Tag
        lines.removeAllViews();
        status.setVisibility(day.lines.isEmpty() ? View.VISIBLE : View.GONE);
        status.setText(Style.loadConfig(this) == null ? "Mit Home Assistant verbunden siehst du hier Dienst, Termine, Müll und mehr." : "Heute steht nichts Besonderes an. Genieß den Tag!");
        int max = 9;
        for (Morning.Line l : day.lines) { if (max-- <= 0) break; lines.addView(line(l)); }
        // Nachrichten
        newsList.removeAllViews();
        for (int i = 0; i < day.news.size(); i++) newsList.addView(newsRow(day.news.get(i)));
        if (!day.news.isEmpty()) showNews(day.news.get(0));
        else if (Style.prefs(this).getInt("morning_news", 2) > 0) { newsTitle.setText("Nachrichten gerade nicht erreichbar"); newsImg.setVisibility(View.GONE); }
    }

    static int color(int kind) {
        switch (kind) {
            case Morning.WARN: return 0xFFEF4444;
            case Morning.SHIFT: return Style.C1;
            case Morning.CAL: return Style.C3;
            case Morning.BDAY: return 0xFFEC4899;
            case Morning.TRASH: return 0xFF22C55E;
            case Morning.CAR: return Style.C2;
            default: return Style.MUTED;
        }
    }

    View line(final Morning.Line l) {
        LinearLayout row = new LinearLayout(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            { setWillNotDraw(false); }
            @Override protected void onDraw(Canvas c) {
                float dp = Style.dp;
                p.setColor(l.kind == Morning.WARN ? 0x33EF4444 : 0x6615141F);
                c.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 12 * dp, 12 * dp, p);
                p.setColor(color(l.kind));
                c.drawRoundRect(new RectF(0, 8 * dp, 4 * dp, getHeight() - 8 * dp), 2 * dp, 2 * dp, p);
            }
        };
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Style.px(18), Style.px(9), Style.px(14), Style.px(9));
        TextView t = Style.text(this, l.title, 16, Color.WHITE);
        t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t);
        if (!l.sub.isEmpty()) {
            TextView s = Style.text(this, l.sub, 12, l.kind == Morning.WARN ? 0xFFFCA5A5 : 0xFF9EA2B8);
            s.setMaxLines(1); s.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Style.px(8);
        row.setLayoutParams(lp);
        return row;
    }

    View newsRow(final Morning.News n) {
        final TextView t = new TextView(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float dp = Style.dp;
                if (isFocused()) { p.setShader(Style.brand(0, getWidth())); p.setAlpha(255); c.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 10 * dp, 10 * dp, p); }
                super.onDraw(c);
            }
        };
        t.setText(n.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(0xFFE6E8F2);
        t.setTypeface(Style.POP);
        t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Style.px(12), Style.px(7), Style.px(12), Style.px(7));
        t.setFocusable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
            t.setTextColor(has ? Color.WHITE : 0xFFE6E8F2);
            if (has) showNews(n);
        }});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Style.px(2);
        t.setLayoutParams(lp);
        return t;
    }

    void showNews(final Morning.News n) {
        newsSrc.setText(n.src.toUpperCase(Locale.GERMANY));
        newsTitle.setText(n.title);
        newsDesc.setText(n.desc);
        newsImg.setVisibility(View.VISIBLE);
        if (n.img == null) { newsImg.setImageDrawable(null); return; }
        Bitmap cached = imgs.get(n.img);
        newsImg.setImageBitmap(cached);
        if (cached != null) return;
        new Thread(new Runnable() { public void run() {
            Bitmap b = Emby.fetch(n.img);
            if (b == null) b = RadioNet.bitmap(n.img);
            final Bitmap fb = b;
            if (fb != null) h.post(new Runnable() { public void run() {
                imgs.put(n.img, fb);
                if (n.title.contentEquals(newsTitle.getText())) newsImg.setImageBitmap(fb);
            }});
        }}).start();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastKey = System.currentTimeMillis();
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); overlay.start(); }
    @Override protected void onPause() { super.onPause(); overlay.stop(); }
    @Override protected void onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null); }
}
