package de.midnox.tv;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/**
 * Senderliste: Reihenfolge fuer "Jetzt im TV", Quelle je Sender (MagentaTV / RTL+ / ausblenden)
 * und MagentaTV-Senderplatz.
 */
public class ChannelListActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    LinearLayout list;
    ScrollView sv;
    final List<String[]> order = new ArrayList<>();
    Map<String, List<TvRows.Prog>> epg;
    ChRow active;   // Zeile im Verschiebe- oder Platz-Modus
    static final int MOVE = 1, NUMBER = 2;
    int mode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new Style.Background());
        root.setPadding(Style.px(64), Style.px(32), Style.px(64), Style.px(18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new Style.GradientText(this, "Senderliste", 28), new LinearLayout.LayoutParams(Style.px(240), Style.px(40)));
        TextView sub = Style.text(this, "für „Jetzt im TV“ auf der Startseite", 13, Style.MUTED);
        head.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));
        Pill reset = new Pill(this, "Zurücksetzen", new Runnable() { public void run() { reset(); } });
        head.addView(reset);
        root.addView(head);

        TextView keys = Style.text(this, "◀ ▶  Quelle wählen     OK  verschieben     Menü-Taste  MagentaTV-Platz     ▶❚❚  Sender testen", 12, 0xFF9EA2B8);
        keys.setPadding(0, Style.px(10), 0, Style.px(12));
        root.addView(keys);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Style.px(4), Style.px(4), Style.px(4), Style.px(40));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        order.addAll(TvRows.ordered(this));
        build(0);
        new Thread(new Runnable() { public void run() {
            final Map<String, List<TvRows.Prog>> d = TvRows.readCache(new File(getFilesDir(), "tvnow.json"));
            h.post(new Runnable() { public void run() { epg = d; for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate(); } });
        }}).start();
    }

    void build(int focus) {
        list.removeAllViews();
        for (String[] ch : order) {
            ChRow r = new ChRow(ch);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Style.px(58));
            lp.bottomMargin = Style.px(6);
            list.addView(r, lp);
        }
        if (list.getChildCount() > 0) list.getChildAt(Math.min(focus, list.getChildCount() - 1)).requestFocus();
    }

    void reset() {
        android.content.SharedPreferences.Editor e = Style.prefs(this).edit();
        e.remove("tv_order");
        for (String[] ch : TvRows.CHANNELS) e.remove("tvsrc_" + ch[0]).remove("mtv_" + ch[0]);
        e.apply();
        order.clear(); order.addAll(TvRows.ordered(this));
        build(0);
        SetupActivity.changed = true;
        Style.toast(this, "Senderliste zurückgesetzt");
    }

    List<String> sources(String ch) {
        List<String> l = new ArrayList<>();
        l.add("magenta");
        if (TvRows.rtlSlug(ch) != null && TvRows.hasRtl(this)) l.add("rtl");
        l.add("hide");
        return l;
    }

    void move(ChRow r, int d) {
        int i = list.indexOfChild(r), j = i + d;
        if (j < 0 || j >= list.getChildCount()) return;
        Collections.swap(order, i, j);
        list.removeView(r);
        list.addView(r, j);
        r.requestFocus();
        final ChRow fr = r;
        h.post(new Runnable() { public void run() { fr.requestRectangleOnScreen(new Rect(0, -Style.px(70), fr.getWidth(), fr.getHeight() + Style.px(70))); } });
        for (int k = 0; k < list.getChildCount(); k++) list.getChildAt(k).invalidate();
    }

    void endMode() {
        if (mode == MOVE) { TvRows.saveOrder(this, order); Style.toast(this, "Reihenfolge gespeichert"); }
        mode = 0;
        if (active != null) active.invalidate();
        active = null;
        SetupActivity.changed = true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (active != null && e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) endMode();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Eine Zeile: Platz in der Reihe, Sender, laufende Sendung, Quelle. */
    final class ChRow extends View {
        final String[] ch;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        ChRow(String[] ch) {
            super(ChannelListActivity.this);
            this.ch = ch;
            setFocusable(true); setClickable(true);
            t.setTypeface(Style.POP);
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { if (!f && active == ChRow.this) endMode(); invalidate(); } });
        }

        @Override public boolean onKeyDown(int k, KeyEvent e) {
            String id = ch[0];
            if (mode == MOVE && active == this) {
                if (k == KeyEvent.KEYCODE_DPAD_UP) { move(this, -1); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) { move(this, 1); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { endMode(); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            }
            if (mode == NUMBER && active == this) {
                int n = TvRows.number(ChannelListActivity.this, id), d = 0;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) d = -1; else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) d = 1;
                else if (k == KeyEvent.KEYCODE_DPAD_UP) d = 10; else if (k == KeyEvent.KEYCODE_DPAD_DOWN) d = -10;
                else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MENU) { endMode(); return true; }
                if (d != 0) {
                    Style.prefs(ChannelListActivity.this).edit().putInt("mtv_" + id, Math.max(0, Math.min(999, n + d))).apply();
                    invalidate(); return true;
                }
            }
            if (mode == 0) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    List<String> src = sources(id);
                    int i = src.indexOf(TvRows.source(ChannelListActivity.this, id));
                    i = (i + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1) + src.size()) % src.size();
                    Style.prefs(ChannelListActivity.this).edit().putString("tvsrc_" + id, src.get(i)).apply();
                    SetupActivity.changed = true;
                    invalidate(); return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { mode = MOVE; active = this; invalidate(); return true; }
                if (k == KeyEvent.KEYCODE_MENU) { mode = NUMBER; active = this; invalidate(); return true; }
                if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY) { TvRows.openTv(ChannelListActivity.this, id); return true; }
            }
            return super.onKeyDown(k, e);
        }

        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), hh = getHeight();
            boolean foc = isFocused(), act = active == this;
            String src = TvRows.source(ChannelListActivity.this, ch[0]);
            boolean hidden = src.equals("hide");
            RectF r = new RectF(dp, dp, w - dp, hh - dp);
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            p.setColor(act ? 0xFF241F3D : foc ? 0xFF1C1B29 : 0x8015141F);
            c.drawRoundRect(r, 14 * dp, 14 * dp, p);
            if (foc) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth((act ? 3 : 2) * dp); p.setShader(Style.brand(r.left, r.right)); c.drawRoundRect(r, 14 * dp, 14 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            // Position
            int pos = list.indexOfChild(this) + 1;
            t.setTextAlign(Paint.Align.CENTER); t.setTextSize(15 * dp); t.setFakeBoldText(true);
            t.setColor(hidden ? 0xFF4A4D60 : 0xFF9EA2B8);
            c.drawText(String.valueOf(pos), 30 * dp, hh / 2 + 5 * dp, t);
            if (act && mode == MOVE) { t.setColor(Color.WHITE); t.setTextSize(13 * dp); c.drawText("▲", 30 * dp, 15 * dp, t); c.drawText("▼", 30 * dp, hh - 6 * dp, t); }
            // Name + laufende Sendung
            t.setTextAlign(Paint.Align.LEFT); t.setFakeBoldText(foc);
            t.setColor(hidden ? 0xFF5D6074 : Color.WHITE); t.setTextSize(16 * dp);
            c.drawText(ch[1], 60 * dp, hh / 2 - 2 * dp, t);
            t.setFakeBoldText(false);
            String now = current();
            t.setColor(hidden ? 0xFF4A4D60 : 0xFF8A8FA8); t.setTextSize(12 * dp);
            float right = w - 300 * dp;
            if (!now.isEmpty()) c.drawText(TextUtils.ellipsize("läuft: " + now, t, right - 70 * dp, TextUtils.TruncateAt.END).toString(), 60 * dp, hh / 2 + 15 * dp, t);
            // Quelle
            String label; int col;
            int nr = TvRows.number(ChannelListActivity.this, ch[0]);
            if (src.equals("rtl")) { label = "RTL+ Livestream"; col = 0xFFE4002B; }
            else if (src.equals("magenta")) { label = "MagentaTV" + (nr > 0 ? "  ·  Platz " + nr : "  ·  nur App"); col = 0xFFE20074; }
            else { label = "ausgeblendet"; col = 0xFF6B6F86; }
            if (act && mode == NUMBER) { label = "MagentaTV-Platz  ◀ " + nr + " ▶   (▲▼ ±10)"; col = 0xFFE20074; }
            t.setTextSize(13 * dp); t.setFakeBoldText(true);
            float tw = t.measureText(label) + 28 * dp;
            RectF pill = new RectF(w - 18 * dp - tw, hh / 2 - 14 * dp, w - 18 * dp, hh / 2 + 14 * dp);
            p.setColor((col & 0x00FFFFFF) | (foc ? 0x55000000 : 0x2E000000));
            c.drawRoundRect(pill, 14 * dp, 14 * dp, p);
            t.setColor(hidden ? 0xFF8A8FA8 : Color.WHITE);
            c.drawText(label, pill.left + 14 * dp, hh / 2 + 5 * dp, t);
            t.setFakeBoldText(false);
            if (foc && mode == 0) { t.setColor(0xFF9EA2B8); t.setTextSize(15 * dp); c.drawText("◀", pill.left - 22 * dp, hh / 2 + 5 * dp, t); }
        }

        String current() {
            Map<String, List<TvRows.Prog>> d = epg;
            if (d == null || d.get(ch[0]) == null) return "";
            long now = System.currentTimeMillis();
            for (TvRows.Prog p : d.get(ch[0])) if (p.start <= now && p.stop > now) return p.title;
            return "";
        }
    }
}
