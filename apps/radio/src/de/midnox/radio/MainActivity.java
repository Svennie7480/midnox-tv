package de.midnox.radio;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

/** Senderauswahl: links die Kategorien, rechts die Sender als Kacheln, unten was gerade laeuft. */
public class MainActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    List<Catalog.Group> groups = new ArrayList<>();
    final List<Nav> nav = new ArrayList<>();
    final List<Station> shown = new ArrayList<>();
    ListView side;
    GridView grid;
    TextView title, count, hint;
    EditText search;
    NowBar bar;
    Ui.Bg bg;
    Nav current;
    int loadToken;
    static List<Station> playList = new ArrayList<>();   // fuer Sender-Wechsel mit links/rechts im Player

    static final class Nav {
        String id, label, logo; int color; boolean header; Catalog.Group group;
        Nav(String id, String label) { this.id = id; this.label = label; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this); Img.init(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        bg = new Ui.Bg();
        root.setBackground(bg);

        LinearLayout main = new LinearLayout(this);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- links: Kategorien
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(U.px(26), U.px(26), U.px(10), U.px(16));
        left.setBackgroundColor(0x660B0A14);
        main.addView(left, new LinearLayout.LayoutParams(U.px(250), -1));
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(U.px(6), 0, 0, U.px(16));
        TextView m = U.text(this, "midnox", 24, 0xFFFFFFFF);
        m.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        brand.addView(m);
        TextView r = Ui.gradient(this, " Radio", 24);
        brand.addView(r);
        left.addView(brand);
        side = new ListView(this);
        side.setDivider(null);
        side.setSelector(new ColorDrawableNone());
        side.setVerticalScrollBarEnabled(false);
        side.setItemsCanFocus(false);
        left.addView(side, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------------- rechts: Sender
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(U.px(30), U.px(26), U.px(30), 0);
        main.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        right.addView(head, new LinearLayout.LayoutParams(-1, U.px(44)));
        title = U.text(this, "", 26, 0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        head.addView(title);
        count = U.text(this, "", 13, U.MUTED);
        count.setPadding(U.px(14), 0, 0, U.px(4));
        head.addView(count);
        search = new EditText(this);
        search.setHint("Sender, Stadt oder Musikrichtung …");
        search.setHintTextColor(U.MUTED); search.setTextColor(0xFFFFFFFF);
        search.setSingleLine(true); search.setTextSize(18);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(new RoundBg());
        search.setPadding(U.px(18), U.px(10), U.px(18), U.px(10));
        search.setVisibility(View.GONE);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
            runSearch(v.getText().toString()); hideKeyboard(); grid.requestFocus(); return true; } });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2); sl.topMargin = U.px(12);
        right.addView(search, sl);
        hint = U.text(this, "", 14, U.MUTED);
        hint.setPadding(0, U.px(16), 0, 0);
        right.addView(hint);
        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(U.px(14)); grid.setHorizontalSpacing(U.px(14));
        grid.setSelector(new ColorDrawableNone());
        grid.setClipToPadding(false);
        grid.setPadding(U.px(4), U.px(14), U.px(4), U.px(14));
        grid.setVerticalScrollBarEnabled(false);
        grid.setDrawSelectorOnTop(false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, 0, 1f);
        right.addView(grid, gl);
        bar = new NowBar(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, U.px(84)); bl.bottomMargin = U.px(18);
        right.addView(bar, bl);

        setContentView(root);

        side.setAdapter(sideAdapter);
        grid.setAdapter(gridAdapter);
        side.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                for (int i = 0; i < side.getChildCount(); i++) side.getChildAt(i).invalidate();
                final Nav n = nav.get(pos);
                h.removeCallbacks(showSel);
                showSel.n = n;
                h.postDelayed(showSel, 220);
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        side.setOnItemClickListener(new AdapterView.OnItemClickListener() { public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            Nav n = nav.get(pos);
            if (n != current) show(n);
            if ("search".equals(n.id)) { search.requestFocus(); showKeyboard(); }
            else if (!shown.isEmpty()) grid.requestFocus();
        }});
        side.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { for (int i = 0; i < side.getChildCount(); i++) side.getChildAt(i).invalidate(); } });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).invalidate(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).invalidate(); } });
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() { public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            Station s = shown.get(pos);
            PlayerService ps = PlayerService.inst;
            playList = new ArrayList<>(shown);
            if (ps != null && ps.station != null && ps.station.id.equals(s.id) && (ps.playing || ps.preparing)) openPlayer();
            else PlayerService.play(MainActivity.this, s);
        }});
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { fav(shown.get(pos)); return true; } });

        // Katalog: sofort aus dem Zwischenspeicher, dann frisch aus dem Netz
        List<Catalog.Group> c = Catalog.cached(this);
        if (c != null) setGroups(c);
        else { buildNav(); title.setText("Lade Sender …"); }
        new Thread(new Runnable() { public void run() {
            try {
                final List<Catalog.Group> g = Catalog.fetch(MainActivity.this);
                h.post(new Runnable() { public void run() { boolean first = groups.isEmpty(); setGroups(g); if (first) side.requestFocus(); } });
            } catch (Exception e) {
                h.post(new Runnable() { public void run() { if (groups.isEmpty()) { title.setText("Keine Verbindung"); hint.setText("Die Senderliste konnte nicht geladen werden. Internet prüfen und die App neu öffnen."); } } });
            }
        }}).start();
    }

    final ShowSel showSel = new ShowSel();
    final class ShowSel implements Runnable { Nav n; public void run() { if (n != null && n != current && !n.header) show(n); } }

    void setGroups(List<Catalog.Group> g) {
        String keep = current == null ? null : current.id;
        groups = g;
        buildNav();
        int pos = 0;
        for (int i = 0; i < nav.size(); i++) if (nav.get(i).id.equals(keep == null ? (Catalog.list(this, "favs").isEmpty() ? "g:midnox" : "favs") : keep)) pos = i;
        if (keep == null) for (int i = 0; i < nav.size(); i++) if (nav.get(i).id.equals("g:midnox")) { pos = i; break; }
        current = null;
        side.setSelection(pos);
        show(nav.get(pos));
    }

    void buildNav() {
        nav.clear();
        Catalog.Group midnox = null;
        for (Catalog.Group g : groups) if (g.id.equals("midnox")) midnox = g;
        if (midnox != null) add(midnox, "midnox Radio");
        Nav f = new Nav("favs", "Favoriten"); f.color = 0xFFEC4899; nav.add(f);
        Nav r = new Nav("recent", "Zuletzt gehört"); r.color = U.C3; nav.add(r);
        Nav s = new Nav("search", "Suche"); s.color = U.C2; nav.add(s);
        header("SENDERFAMILIEN");
        for (Catalog.Group g : groups) if ("network".equals(g.type)) add(g, g.name);
        header("MUSIKRICHTUNGEN");
        for (Catalog.Group g : groups) if ("genre".equals(g.type)) add(g, g.name);
        header("BUNDESLÄNDER");
        for (Catalog.Group g : groups) if ("region".equals(g.type)) add(g, g.name);
        header("ENTDECKEN");
        Nav de = new Nav("top:DE", "Beliebt in Deutschland"); de.color = 0xFFF59E0B; nav.add(de);
        Nav at = new Nav("top:AT", "Österreich"); at.color = 0xFFEF4444; nav.add(at);
        Nav ch = new Nav("top:CH", "Schweiz"); ch.color = 0xFFEF4444; nav.add(ch);
        Nav w = new Nav("top:", "Weltweit beliebt"); w.color = U.C3; nav.add(w);
        sideAdapter.notifyDataSetChanged();
    }
    void add(Catalog.Group g, String label) {
        Nav n = new Nav("g:" + g.id, label); n.group = g; n.color = g.color; n.logo = g.logo != null ? g.logo : g.stations.isEmpty() ? null : g.stations.get(0).logo;
        if (!"network".equals(g.type) && !"featured".equals(g.type)) n.logo = null;
        nav.add(n);
    }
    void header(String t) { Nav n = new Nav("h:" + t, t); n.header = true; nav.add(n); }

    void show(final Nav n) {
        current = n;
        final int my = ++loadToken;
        title.setText(n.label);
        search.setVisibility("search".equals(n.id) ? View.VISIBLE : View.GONE);
        hint.setVisibility(View.GONE);
        bg.tint = n.color != 0 ? n.color : U.C1; bg.invalidateSelf();
        if (n.group != null) { setShown(n.group.stations, ""); return; }
        if ("favs".equals(n.id)) { setShown(Catalog.list(this, "favs"), "Noch keine Favoriten.\nAuf einem Sender OK gedrückt halten (oder Menü-Taste) – dann landet er hier."); return; }
        if ("recent".equals(n.id)) { setShown(Catalog.list(this, "recent"), "Hier erscheinen die Sender, die du zuletzt gehört hast."); return; }
        if ("search".equals(n.id)) {
            setShown(new ArrayList<Station>(), "Über 50.000 Sender weltweit: Name, Stadt oder Musikrichtung eingeben.");
            if (search.getText().length() > 0) runSearch(search.getText().toString());
            return;
        }
        if (n.id.startsWith("top:")) {
            setShown(new ArrayList<Station>(), "Lade …");
            final String cc = n.id.substring(4);
            new Thread(new Runnable() { public void run() {
                try {
                    final List<Station> l = Browser.top(cc.isEmpty() ? null : cc);
                    h.post(new Runnable() { public void run() { if (my == loadToken) setShown(l, "Keine Sender gefunden."); } });
                } catch (Exception e) { h.post(new Runnable() { public void run() { if (my == loadToken) setShown(new ArrayList<Station>(), "Senderverzeichnis gerade nicht erreichbar."); } }); }
            }}).start();
        }
    }

    void runSearch(final String q) {
        if (q.trim().length() < 2) return;
        final int my = ++loadToken;
        hint.setText("Suche …"); hint.setVisibility(View.VISIBLE);
        // zuerst im eigenen Katalog (mit Logos), dann weltweit
        final List<Station> own = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String ql = q.toLowerCase(Locale.GERMANY).trim();
        for (Catalog.Group g : groups) for (Station s : g.stations)
            if ((s.name.toLowerCase(Locale.GERMANY).contains(ql) || g.name.toLowerCase(Locale.GERMANY).contains(ql) || s.tags.contains(ql)) && ids.add(s.id)) own.add(s);
        setShown(own, "");
        new Thread(new Runnable() { public void run() {
            try {
                final List<Station> l = Browser.search(q.trim());
                h.post(new Runnable() { public void run() {
                    if (my != loadToken) return;
                    Set<String> have = new HashSet<>();
                    for (Station s : own) { have.add(s.id); have.add(s.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")); }
                    List<Station> all = new ArrayList<>(own);
                    for (Station s : l) if (!have.contains(s.id) && !have.contains(s.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))) all.add(s);
                    setShown(all, "Nichts gefunden – anders schreiben?");
                }});
            } catch (Exception e) { h.post(new Runnable() { public void run() { if (my == loadToken) setShown(own, "Weltweite Suche gerade nicht erreichbar."); } }); }
        }}).start();
    }

    void setShown(List<Station> l, String empty) {
        shown.clear(); shown.addAll(l);
        gridAdapter.notifyDataSetChanged();
        grid.setSelection(0);
        count.setText(l.isEmpty() ? "" : l.size() == 1 ? "1 Sender" : l.size() + " Sender");
        hint.setText(empty);
        hint.setVisibility(l.isEmpty() && !empty.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void fav(Station s) {
        boolean on = Catalog.toggleFav(this, s);
        Toast.makeText(this, on ? "♥  " + s.name + " ist jetzt ein Favorit" : s.name + " aus den Favoriten entfernt", Toast.LENGTH_SHORT).show();
        if (current != null && "favs".equals(current.id)) show(current);
        else gridAdapter.notifyDataSetChanged();
        bar.invalidate();
    }

    void openPlayer() { startActivity(new Intent(this, PlayerActivity.class)); }

    void showKeyboard() { InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (im != null) im.showSoftInput(search, 0); }
    void hideKeyboard() { InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0); }

    final Runnable changed = new Runnable() { public void run() {
        bar.invalidate();
        for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).invalidate();
        PlayerService ps = PlayerService.inst;
        if (ps != null && ps.error != null && ps.error != lastErr) { lastErr = ps.error; Toast.makeText(MainActivity.this, ps.station.name + ": " + ps.error, Toast.LENGTH_LONG).show(); }
    }};
    String lastErr;

    @Override protected void onResume() {
        super.onResume();
        PlayerService.listen(changed);
        bar.invalidate();
        if (current != null && ("favs".equals(current.id) || "recent".equals(current.id))) show(current);
        h.post(eq);
    }
    @Override protected void onPause() { super.onPause(); PlayerService.unlisten(changed); h.removeCallbacks(eq); }

    // Equalizer-Animation am laufenden Sender
    final Runnable eq = new Runnable() { public void run() {
        PlayerService ps = PlayerService.inst;
        if (ps != null && ps.playing) { bar.invalidate(); for (int i = 0; i < grid.getChildCount(); i++) { View v = grid.getChildAt(i); if (v instanceof Card && ((Card) v).isPlaying()) v.invalidate(); } }
        h.postDelayed(this, 120);
    }};

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE) { PlayerService.toggle(this); return true; }
            if (k == KeyEvent.KEYCODE_MENU && grid.hasFocus() && grid.getSelectedItemPosition() >= 0 && grid.getSelectedItemPosition() < shown.size()) { fav(shown.get(grid.getSelectedItemPosition())); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && grid.hasFocus() && grid.getSelectedItemPosition() % 5 == 0) { side.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && side.hasFocus() && !shown.isEmpty()) { grid.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && grid.hasFocus()) {
                int p = grid.getSelectedItemPosition();
                if (p + 5 >= shown.size() && hasNow()) { bar.requestFocus(); return true; }
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP && bar.hasFocus() && !shown.isEmpty()) { grid.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && search.hasFocus() && !shown.isEmpty()) { grid.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP && grid.hasFocus() && grid.getSelectedItemPosition() < 5 && search.getVisibility() == View.VISIBLE) { search.requestFocus(); return true; }
        }
        if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && (grid.hasFocus() || bar.hasFocus() || search.hasFocus())) { side.requestFocus(); return true; }
        if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_DOWN && (grid.hasFocus() || bar.hasFocus() || search.hasFocus())) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed() {
        PlayerService ps = PlayerService.inst;
        if (ps != null && (ps.playing || ps.preparing)) Toast.makeText(this, "Das Radio läuft im Hintergrund weiter – Pause-Taste hält es an.", Toast.LENGTH_LONG).show();
        super.onBackPressed();
    }

    boolean hasNow() { PlayerService ps = PlayerService.inst; return ps != null && ps.station != null; }

    // ------------------------------------------------------------ Adapter
    final BaseAdapter sideAdapter = new BaseAdapter() {
        public int getCount() { return nav.size(); }
        public Object getItem(int i) { return nav.get(i); }
        public long getItemId(int i) { return i; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return !nav.get(i).header; }
        @Override public int getViewTypeCount() { return 1; }
        public View getView(int i, View v, ViewGroup p) {
            SideRow r = v instanceof SideRow ? (SideRow) v : new SideRow(MainActivity.this);
            r.set(nav.get(i), i);
            return r;
        }
    };
    final BaseAdapter gridAdapter = new BaseAdapter() {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup p) {
            Card c = v instanceof Card ? (Card) v : new Card(MainActivity.this);
            c.set(shown.get(i), i);
            return c;
        }
    };

    final class SideRow extends View {
        Nav n; int pos;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        SideRow(Context c) { super(c); t.setTypeface(U.POP); }
        void set(Nav n, int pos) { this.n = n; this.pos = pos; setLayoutParams(new AbsListView.LayoutParams(-1, n.header ? U.px(40) : U.px(40))); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), dp = U.dp;
            if (n.header) {
                t.setColor(U.MUTED); t.setTextSize(10 * dp); t.setLetterSpacing(0.22f); t.setFakeBoldText(false);
                c.drawText(n.label, 8 * dp, hh - 10 * dp, t); t.setLetterSpacing(0);
                return;
            }
            boolean sel = side.getSelectedItemPosition() == pos, foc = sel && side.hasFocus(), act = current == n;
            RectF r = new RectF(0, 3 * dp, w - 4 * dp, hh - 3 * dp);
            if (foc) { p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, r.height() / 2, r.height() / 2, p); p.setShader(null); }
            else if (act) { p.setColor(0x26FFFFFF); c.drawRoundRect(r, r.height() / 2, r.height() / 2, p); }
            float ix = 10 * dp, is = 22 * dp, cy = hh / 2;
            Bitmap b = n.logo == null ? null : Img.now(n.logo);
            if (n.logo != null && b == null) Img.load(n.logo, 96, new Img.Cb() { public void done(Bitmap x) { if (x != null) invalidate(); } });
            if (b != null) {
                Station dummy = new Station(); dummy.name = n.label; dummy.color = n.color;
                Ui.logo(c, new RectF(ix, cy - is / 2, ix + is, cy + is / 2), dummy, b, 5 * dp);
            } else {
                p.setColor(foc ? 0xFFFFFFFF : n.color); c.drawCircle(ix + is / 2, cy, 5 * dp, p);
            }
            t.setColor(foc ? 0xFFFFFFFF : act ? 0xFFFFFFFF : U.TEXT); t.setTextSize(14 * dp); t.setFakeBoldText(foc || act);
            c.drawText(Ui.ell(n.label, t, w - ix - is - 22 * dp), ix + is + 12 * dp, cy + 5 * dp, t);
            t.setFakeBoldText(false);
        }
    }

    final class Card extends View {
        Station s; int pos;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Card(Context c) { super(c); t.setTypeface(U.POP); setLayoutParams(new AbsListView.LayoutParams(-1, U.px(178))); }
        void set(Station s, int pos) { this.s = s; this.pos = pos; invalidate(); }
        boolean isPlaying() { PlayerService ps = PlayerService.inst; return ps != null && ps.station != null && s != null && ps.station.id.equals(s.id) && (ps.playing || ps.preparing); }
        @Override protected void onDraw(Canvas c) {
            if (s == null) return;
            float w = getWidth(), hh = getHeight(), dp = U.dp;
            boolean foc = grid.hasFocus() && grid.getSelectedItemPosition() == pos;
            RectF card = new RectF(2 * dp, 2 * dp, w - 2 * dp, hh - 2 * dp);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(foc ? 0xFF201E33 : 0xB315141F);
            c.drawRoundRect(card, 16 * dp, 16 * dp, p);
            if (foc) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); p.setShader(U.brand(card.left, card.right)); c.drawRoundRect(card, 16 * dp, 16 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            float ls = Math.min(w - 40 * dp, hh - 66 * dp);
            RectF lr = new RectF(w / 2 - ls / 2, 14 * dp, w / 2 + ls / 2, 14 * dp + ls);
            Bitmap b = Img.now(s.logo);
            if (s.logo != null && b == null) { final Station mine = s; Img.load(s.logo, 256, new Img.Cb() { public void done(Bitmap x) { if (x != null && s == mine) invalidate(); } }); }
            Ui.logo(c, lr, s, b, 14 * dp);
            if (isPlaying()) {   // Equalizer
                p.setColor(0xCC0B0A14); c.drawRoundRect(new RectF(lr.right - 34 * dp, lr.bottom - 28 * dp, lr.right - 6 * dp, lr.bottom - 6 * dp), 6 * dp, 6 * dp, p);
                p.setShader(U.brand(lr.right - 30 * dp, lr.right - 10 * dp));
                long tm = System.currentTimeMillis();
                for (int i = 0; i < 4; i++) {
                    float hgt = (float) (5 + 11 * Math.abs(Math.sin(tm / (170.0 + i * 37) + i))) * dp;
                    float x = lr.right - 30 * dp + i * 5.5f * dp;
                    c.drawRoundRect(new RectF(x, lr.bottom - 10 * dp - hgt, x + 3.5f * dp, lr.bottom - 10 * dp), 1.5f * dp, 1.5f * dp, p);
                }
                p.setShader(null);
            }
            if (Catalog.isFav(MainActivity.this, s)) { t.setColor(0xFFEC4899); t.setTextSize(15 * dp); t.setTextAlign(Paint.Align.LEFT); c.drawText("♥", lr.left + 6 * dp, lr.top + 18 * dp, t); }
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(0xFFFFFFFF); t.setTextSize(13.5f * dp); t.setFakeBoldText(foc);
            boolean chan = !s.sub.isEmpty() && current != null && current.group != null && "network".equals(current.group.type);
            String nm = chan ? s.sub : s.name;
            c.drawText(Ui.ell(nm, t, w - 16 * dp), w / 2, lr.bottom + 22 * dp, t);
            t.setFakeBoldText(false);
            t.setColor(U.MUTED); t.setTextSize(11 * dp);
            String cap = chan ? current.group.name : s.caption();
            c.drawText(Ui.ell(cap, t, w - 16 * dp), w / 2, lr.bottom + 39 * dp, t);
        }
    }

    /** Leiste unten: was gerade laeuft. OK oeffnet den Player. */
    final class NowBar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        NowBar(Context c) {
            super(c); setFocusable(true); setClickable(true); t.setTypeface(U.POP);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { if (hasNow()) openPlayer(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { invalidate(); } });
        }
        @Override protected void onDraw(Canvas c) {
            PlayerService ps = PlayerService.inst;
            Station s = ps == null ? null : ps.station;
            float w = getWidth(), hh = getHeight(), dp = U.dp;
            RectF r = new RectF(0, 6 * dp, w, hh);
            p.setShader(null); p.setColor(isFocused() ? 0xFF201E33 : 0xD915141F);
            c.drawRoundRect(r, 18 * dp, 18 * dp, p);
            if (isFocused()) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(U.brand(0, w)); c.drawRoundRect(r, 18 * dp, 18 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            if (s == null) {
                t.setColor(U.MUTED); t.setTextSize(13 * dp); t.setTextAlign(Paint.Align.LEFT);
                c.drawText("Sender wählen und OK drücken  ·  OK lange drücken = Favorit  ·  Pause-Taste = Radio an/aus", 22 * dp, r.centerY() + 5 * dp, t);
                return;
            }
            float ls = r.height() - 16 * dp;
            RectF lr = new RectF(10 * dp, r.top + 8 * dp, 10 * dp + ls, r.top + 8 * dp + ls);
            Bitmap cov = ps.cover != null ? ps.cover : Img.now(s.logo);
            if (cov == null && s.logo != null) Img.load(s.logo, 256, new Img.Cb() { public void done(Bitmap x) { invalidate(); } });
            Ui.logo(c, lr, s, cov, 10 * dp);
            float x = lr.right + 16 * dp;
            t.setTextAlign(Paint.Align.LEFT);
            t.setColor(U.MUTED); t.setTextSize(11 * dp); t.setLetterSpacing(0.12f);
            String state = ps.preparing ? "VERBINDE …" : ps.playing ? "LÄUFT GERADE" : ps.error != null ? "GESTOPPT · " + ps.error.toUpperCase(Locale.GERMANY) : "PAUSIERT";
            if (ps.sleepLeft() > 0) state += "   ·   AUS IN " + ps.sleepLeft() + " MIN.";
            c.drawText(Ui.ell(state, t, w - x - 120 * dp), x, r.top + 24 * dp, t);
            t.setLetterSpacing(0);
            t.setColor(0xFFFFFFFF); t.setTextSize(16 * dp); t.setFakeBoldText(true);
            String song = ps.current != null && !ps.current.title.isEmpty() ? (ps.current.artist.isEmpty() ? ps.current.title : ps.current.artist + " – " + ps.current.title) : "";
            c.drawText(Ui.ell(s.name, t, w - x - 120 * dp), x, r.top + 46 * dp, t);
            t.setFakeBoldText(false);
            t.setColor(0xFFC9CCE0); t.setTextSize(13 * dp);
            if (!song.isEmpty()) c.drawText(Ui.ell(song, t, w - x - 120 * dp), x, r.top + 66 * dp, t);
            // Play/Pause-Symbol rechts
            float cx = w - 46 * dp, cy = r.centerY();
            p.setShader(U.brand(cx - 22 * dp, cx + 22 * dp)); c.drawCircle(cx, cy, 22 * dp, p); p.setShader(null); p.setColor(0xFFFFFFFF);
            if (ps.playing || ps.preparing) { c.drawRoundRect(new RectF(cx - 8 * dp, cy - 9 * dp, cx - 3 * dp, cy + 9 * dp), 1.5f * dp, 1.5f * dp, p); c.drawRoundRect(new RectF(cx + 3 * dp, cy - 9 * dp, cx + 8 * dp, cy + 9 * dp), 1.5f * dp, 1.5f * dp, p); }
            else { Path tri = new Path(); tri.moveTo(cx - 6 * dp, cy - 10 * dp); tri.lineTo(cx + 10 * dp, cy); tri.lineTo(cx - 6 * dp, cy + 10 * dp); tri.close(); c.drawPath(tri, p); }
            if (ps.playing) {
                p.setShader(U.brand(cx - 110 * dp, cx - 70 * dp));
                long tm = System.currentTimeMillis();
                for (int i = 0; i < 5; i++) {
                    float hgt = (float) (4 + 14 * Math.abs(Math.sin(tm / (160.0 + i * 41) + i * 1.3))) * dp;
                    float bx = cx - 110 * dp + i * 7 * dp;
                    c.drawRoundRect(new RectF(bx, cy + 9 * dp - hgt, bx + 4 * dp, cy + 9 * dp), 2 * dp, 2 * dp, p);
                }
                p.setShader(null);
            }
        }
    }

    static final class ColorDrawableNone extends android.graphics.drawable.ColorDrawable { ColorDrawableNone() { super(0); } }
    static final class RoundBg extends android.graphics.drawable.Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            RectF r = new RectF(getBounds()); p.setStyle(Paint.Style.FILL); p.setColor(0xFF15141F);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * U.dp); p.setShader(U.brand(r.left, r.right));
            c.drawRoundRect(new RectF(r.left + U.dp, r.top + U.dp, r.right - U.dp, r.bottom - U.dp), r.height() / 2, r.height() / 2, p); p.setShader(null);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
