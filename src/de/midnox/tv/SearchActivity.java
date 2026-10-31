package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import io.nayuki.qrcodegen.QrCode;
import org.json.*;
import java.io.File;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/** Eine Suche fuer alles: Emby, TV-Programm (30 Std.) und YouTube. Tippen auch per Handy (QR-Code). */
public class SearchActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    EditText input;
    LinearLayout results;
    TextView status;
    MiniServer server;
    int gen;
    final Map<String, Bitmap> imgs = Collections.synchronizedMap(new HashMap<String, Bitmap>());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Style.px(28), 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Style.px(64), 0, Style.px(48), 0);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(new Style.GradientText(this, "Suche", 32));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Film, Serie, Sendung …");
        input.setHintTextColor(0xFF6B6F86);
        input.setTextColor(Color.WHITE);
        input.setTypeface(Style.POP);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final android.graphics.drawable.GradientDrawable ib = new android.graphics.drawable.GradientDrawable();
        ib.setCornerRadius(Style.px(14)); ib.setColor(0xCC15141F); ib.setStroke(Style.px(1), 0x33FFFFFF);
        input.setBackground(ib);
        input.setPadding(Style.px(18), Style.px(12), Style.px(18), Style.px(12));
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) { ib.setStroke(Style.px(has ? 2 : 1), has ? Style.C2 : 0x33FFFFFF); } });
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Style.px(520), -2);
        il.topMargin = Style.px(10);
        left.addView(input, il);
        status = Style.text(this, "", 12, Style.MUTED);
        status.setPadding(Style.px(4), Style.px(8), 0, 0);
        left.addView(status);
        head.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        // QR-Code: auf dem Handy tippen
        String url = null;
        server = new MiniServer(new MiniServer.Handler() { public String[] handle(String m, String path, String body) { return phone(m, path, body); } });
        try { url = "http://" + SettingsActivity.ownIp() + ":" + server.start(8110) + "/"; } catch (Exception ignored) {}
        if (url != null) {
            LinearLayout qr = new LinearLayout(this);
            qr.setOrientation(LinearLayout.VERTICAL);
            qr.setGravity(Gravity.CENTER_HORIZONTAL);
            qr.addView(new SetupActivity.QrView(this, url), new LinearLayout.LayoutParams(Style.px(104), Style.px(104)));
            TextView qt = Style.text(this, "Mit dem Handy tippen", 11, Style.MUTED);
            qt.setPadding(0, Style.px(6), 0, 0);
            qr.addView(qt);
            head.addView(qr);
        }
        col.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, Style.px(10), 0, Style.px(60));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        sv.addView(results);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        root.addView(col);
        setContentView(root);

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { h.removeCallbacks(run); h.postDelayed(run, 700); }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
            h.removeCallbacks(run); h.post(run);
            if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            return false;
        }});
        input.requestFocus();
        String q0 = getIntent().getStringExtra("q");
        if (q0 != null && !q0.isEmpty()) { input.setText(q0); input.setSelection(q0.length()); }
    }

    final Runnable run = new Runnable() { public void run() { search(input.getText().toString().trim()); } };

    // ------------------------------------------------------------ Handy-Seite
    String[] phone(String m, String path, String body) {
        if (path.startsWith("/t")) {
            final String text = body;
            h.post(new Runnable() { public void run() { input.setText(text); input.setSelection(input.getText().length()); } });
            return new String[]{"text/plain", "ok"};
        }
        return new String[]{"text/html", MiniServer.page("midnox TV Suche",
            "<h1>Suche</h1><p class=sub>Was du hier tippst, erscheint direkt am Fernseher.</p><input id=q autofocus placeholder=\"Film, Serie, Sendung …\" autocomplete=off>",
            "var t;document.getElementById('q').addEventListener('input',function(e){clearTimeout(t);var v=e.target.value;t=setTimeout(function(){fetch('/t',{method:'POST',body:v});},250);});")};
    }

    // ------------------------------------------------------------ Suche
    void search(final String q) {
        final int my = ++gen;
        results.removeAllViews();
        if (q.length() < 2) { status.setText(""); return; }
        status.setText("Suche „" + q + "“ …");
        new Thread(new Runnable() { public void run() {
            final List<Emby.Entry> emby = new ArrayList<>(), tv = new ArrayList<>();
            final Emby e = Emby.from(SearchActivity.this);
            if (e != null) try {
                JSONArray a = e.getObj("/emby/Users/" + e.user + "/Items?SearchTerm=" + URLEncoder.encode(q, "UTF-8") + "&IncludeItemTypes=Movie,Series,Episode&Recursive=true&Limit=24" + Emby.FIELDS).optJSONArray("Items");
                emby.addAll(e.entries(a, 24));
            } catch (Exception ignored) {}
            Map<String, List<TvRows.Prog>> d = TvRows.readCache(new File(getFilesDir(), "tvnow.json"));
            long now = System.currentTimeMillis();
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY), day = new SimpleDateFormat("EE", Locale.GERMANY);
            String ql = q.toLowerCase(Locale.GERMANY);
            if (d != null) for (String[] c : TvRows.CHANNELS) {
                List<TvRows.Prog> l = d.get(c[0]);
                if (l == null) continue;
                for (TvRows.Prog p : l) {
                    if (p.stop <= now) continue;
                    if (!(p.title.toLowerCase(Locale.GERMANY).contains(ql) || p.sub.toLowerCase(Locale.GERMANY).contains(ql))) continue;
                    Emby.Entry x = new Emby.Entry();
                    x.id = c[0] + "@" + p.start; x.type = "TV"; x.title = p.title; x.forceText = true;
                    boolean running = p.start <= now;
                    Calendar pc = Calendar.getInstance(); pc.setTimeInMillis(p.start);
                    String when = running ? "läuft jetzt" : (HaPanel.same(pc, Calendar.getInstance()) ? "heute " : day.format(new Date(p.start)) + " ") + hm.format(new Date(p.start));
                    x.sub = c[1] + "  ·  " + when;
                    x.kind = "IM TV  ·  " + c[1].toUpperCase(Locale.GERMANY);
                    x.meta = hm.format(new Date(p.start)) + "–" + hm.format(new Date(p.stop)) + (p.cat.isEmpty() ? "" : "   ·   " + p.cat) + (running ? "   ·   OK schaltet um" : "   ·   OK = Erinnerung");
                    x.overview = p.desc; x.img = p.icon; x.backdrop = p.icon;
                    if (running) x.progress = 100.0 * (now - p.start) / Math.max(1, p.stop - p.start);
                    tv.add(x);
                    if (tv.size() >= 24) break;
                }
            }
            for (Emby.Entry x : emby) if (x.img != null) x.bmp = img(x.img);
            for (Emby.Entry x : tv) if (x.img != null) x.bmp = img(x.img);
            h.post(new Runnable() { public void run() { if (my == gen) show(q, e, emby, tv); } });
        }}).start();
    }

    Bitmap img(String u) {
        Bitmap b = imgs.get(u);
        if (b == null) { b = Emby.fetch(u); if (b != null) imgs.put(u, b); }
        return b;
    }

    void show(final String q, final Emby e, List<Emby.Entry> emby, List<Emby.Entry> tv) {
        results.removeAllViews();
        status.setText(emby.size() + " in Emby  ·  " + tv.size() + " im TV");
        if (!emby.isEmpty()) row("EMBY", emby, new MediaCard.Listener() {
            public void onFocus(MediaCard c) {}
            public void onClick(MediaCard c) {
                Intent i = new Intent(SearchActivity.this, DetailActivity.class);
                i.putExtra("id", c.e.id); i.putExtra("type", c.e.type); i.putExtra("series", c.e.seriesId); i.putExtra("season", c.e.seasonId);
                startActivity(i);
            }
            public void onLongClick(MediaCard c) { Emby.open(SearchActivity.this, "play", e == null ? null : e.serverId(), c.e.linkId); }
        });
        if (!tv.isEmpty()) row("IM TV  ·  NÄCHSTE 30 STUNDEN", tv, new MediaCard.Listener() {
            public void onFocus(MediaCard c) {}
            public void onClick(MediaCard c) {
                String ch = c.e.id.substring(0, c.e.id.indexOf('@'));
                long start = Long.parseLong(c.e.id.substring(c.e.id.indexOf('@') + 1));
                if (start <= System.currentTimeMillis()) { TvRows.openTv(SearchActivity.this, ch); return; }
                boolean on = Daily.toggleReminder(SearchActivity.this, ch, TvRows.name(ch), start, c.e.title);
                Style.toast(SearchActivity.this, on ? "Erinnerung gesetzt (5 Min. vorher)" : "Erinnerung entfernt");
            }
            public void onLongClick(MediaCard c) { onClick(c); }
        });
        // YouTube ueber SmartTube
        TextView cap = Style.text(this, "YOUTUBE", 11, Style.MUTED);
        cap.setLetterSpacing(0.3f); cap.setPadding(Style.px(64), Style.px(10), 0, Style.px(8));
        results.addView(cap);
        LinearLayout yr = new LinearLayout(this);
        yr.setPadding(Style.px(60), 0, 0, 0);
        yr.addView(new Pill(this, "„" + q + "“ auf YouTube suchen  ›", new Runnable() { public void run() { youtube(q); } }));
        results.addView(yr);
    }

    void row(String title, List<Emby.Entry> list, MediaCard.Listener l) {
        TextView cap = Style.text(this, title, 11, Style.MUTED);
        cap.setLetterSpacing(0.3f); cap.setPadding(Style.px(64), Style.px(10), 0, 0);
        results.addView(cap);
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false); sv.setClipChildren(false); sv.setClipToPadding(false);
        sv.setPadding(Style.px(54), Style.px(8), Style.px(54), Style.px(10));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        sv.addView(r);
        for (Emby.Entry e : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(168), Style.px(95));
            lp.setMargins(Style.px(8), 0, Style.px(8), 0);
            r.addView(new MediaCard(this, e, sv, l), lp);
        }
        results.addView(sv);
    }

    void youtube(String q) {
        Intent i;
        try { i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))); }
        catch (Exception ex) { return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (String pkg : new String[]{"com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"}) {
            if (Style.launchIntent(getPackageManager(), pkg) != null) {
                i.setPackage(pkg);
                try { startActivity(i); return; } catch (Exception ignored) {}
            }
        }
        i.setPackage(null);
        try { startActivity(i); } catch (Exception ex) { Style.toast(this, "Keine YouTube-App gefunden"); }
    }

    @Override protected void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }
}
