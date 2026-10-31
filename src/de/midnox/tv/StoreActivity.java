package de.midnox.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** midnox Store: Apps fuer den Fire TV mit einem Klick installieren und aktuell halten. */
public class StoreActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    List<Store.App> apps = new ArrayList<>();
    LinearLayout grid;
    TextView status, dTitle, dDesc, dHint;
    Theme.Overlay overlay;
    final List<Card> cards = new ArrayList<>();
    Store.App focused;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme theme = Theme.current(this);
        theme.apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        overlay = new Theme.Overlay(this, theme);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(Style.px(56), Style.px(36), Style.px(56), Style.px(24));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        main.addView(new Style.GradientText(this, "midnox Store", 30), new LinearLayout.LayoutParams(-1, Style.px(44)));
        status = Style.text(this, "Lade Katalog …", 14, Style.MUTED);
        main.addView(status);

        // Detail des fokussierten Eintrags
        LinearLayout det = new LinearLayout(this);
        det.setOrientation(LinearLayout.VERTICAL);
        det.setPadding(0, Style.px(14), 0, Style.px(10));
        main.addView(det, new LinearLayout.LayoutParams(-1, Style.px(96)));
        dTitle = Style.text(this, "", 18, Color.WHITE);
        dTitle.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
        det.addView(dTitle);
        dDesc = Style.text(this, "", 13, 0xFFC9CCE0);
        dDesc.setMaxLines(2); dDesc.setEllipsize(TextUtils.TruncateAt.END);
        dDesc.setMaxWidth(Style.px(720));
        det.addView(dDesc);
        dHint = Style.text(this, "", 11, Style.MUTED);
        dHint.setPadding(0, Style.px(4), 0, 0);
        det.addView(dHint);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false); sv.setClipChildren(false);
        sv.setPadding(0, Style.px(8), 0, Style.px(30));
        main.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        sv.addView(grid);

        setContentView(root);
        reload();
    }

    void reload() {
        new Thread(new Runnable() { public void run() {
            try {
                final List<Store.App> l = Store.load(StoreActivity.this);
                h.post(new Runnable() { public void run() { apps = l; build(); resolveAll(); } });
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { status.setText("Katalog nicht erreichbar – Internet prüfen"); } });
            }
        }}).start();
    }

    void resolveAll() {
        status.setText("Suche nach neuesten Versionen …");
        new Thread(new Runnable() { public void run() {
            for (final Store.App a : apps) {
                a.resolving = true;
                Store.resolve(a);
                a.resolving = false;
                h.post(new Runnable() { public void run() { refresh(); } });
            }
            h.post(new Runnable() { public void run() { summary(); } });
        }}).start();
    }

    void summary() {
        int upd = 0, inst = 0;
        for (Store.App a : apps) { if (a.hasUpdate()) upd++; if (a.isInstalled()) inst++; }
        status.setText(apps.size() + " Apps  ·  " + inst + " installiert" + (upd > 0 ? "  ·  " + upd + (upd == 1 ? " Update verfügbar" : " Updates verfügbar") : "  ·  alles aktuell"));
    }

    void build() {
        grid.removeAllViews(); cards.clear();
        int cols = 6;
        LinkedHashMap<String, List<Store.App>> groups = new LinkedHashMap<>();
        for (Store.App a : apps) { String k = a.cat.isEmpty() ? "Weitere" : a.cat; if (!groups.containsKey(k)) groups.put(k, new ArrayList<Store.App>()); groups.get(k).add(a); }
        for (Map.Entry<String, List<Store.App>> g : groups.entrySet()) {
        TextView gh = Style.text(this, g.getKey().toUpperCase(Locale.GERMANY), 11, Style.MUTED);
        gh.setLetterSpacing(0.25f);
        gh.setPadding(Style.px(4), Style.px(4), 0, Style.px(10));
        grid.addView(gh);
        LinearLayout row = null;
        List<Store.App> sorted = g.getValue();
        for (int i = 0; i < sorted.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Style.px(140));
                rl.bottomMargin = Style.px(16);
                grid.addView(row, rl);
            }
            Card c = new Card(sorted.get(i));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
            cl.rightMargin = Style.px(16);
            row.addView(c, cl);
            cards.add(c);
        }
        if (sorted.size() % cols != 0) for (int i = sorted.size() % cols; i < cols; i++) row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));
        }
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    void refresh() {
        Store.refreshInstalled(this, apps);
        for (Card c : cards) c.invalidate();
        if (focused != null) detail(focused);
    }

    void detail(Store.App a) {
        focused = a;
        dTitle.setText(a.name + (a.cat.isEmpty() ? "" : "   ·   " + a.cat));
        dDesc.setText(a.desc);
        String v = a.isInstalled() ? "Installiert: " + a.installed + (a.latest != null ? "   ·   Neueste: " + a.latest : "") : a.latest != null ? "Neueste Version: " + a.latest : "";
        if (a.appstore()) {
            dHint.setText(a.isInstalled() ? "Installiert: " + a.installed + "      OK: öffnen   \u00b7   Updates über den Amazon Appstore" : "OK: im Amazon Appstore öffnen und dort installieren");
            return;
        }
        String act = a.busy ? "" : a.hasUpdate() ? "OK: aktualisieren" : a.isInstalled() ? "OK: öffnen   ·   Menü-Taste: deinstallieren" : a.apk != null ? "OK: installieren" : "";
        dHint.setText(v + (v.isEmpty() || act.isEmpty() ? "" : "      ") + act + (a.error != null && !a.isInstalled() ? a.error : ""));
    }

    void act(final Store.App a) {
        if (a.busy) return;
        if (a.appstore() && !a.isInstalled()) {   // Amazon Appstore uebernimmt Installation und Updates
            String u = a.amazon != null ? "amzn://apps/android?p=" + a.amazon : "amzn://apps/android?s=" + Uri.encode(a.search);
            try { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
            catch (Exception e) { Style.toast(this, "Amazon Appstore nicht gefunden"); }
            return;
        }
        if (a.isInstalled() && !a.hasUpdate()) {
            Intent li = Style.launchIntent(getPackageManager(), a.pkg);
            if (li != null) Style.start(this, li); else Style.toast(this, a.name + " hat kein Startsymbol");
            return;
        }
        if (a.apk == null) { Style.toast(this, a.resolving ? "Moment, Version wird gesucht …" : a.error != null ? a.error : "Keine APK verfügbar"); return; }
        a.busy = true; a.status = a.isInstalled() ? "Aktualisiere …" : "Lädt …";
        refresh();
        new Thread(new Runnable() { public void run() {
            String msg; boolean ok;
            try { JSONObject r = new JSONObject(Installer.fromUrl(StoreActivity.this, a.apk)); ok = r.optBoolean("ok"); msg = r.optString("msg"); }
            catch (Exception e) { ok = false; msg = "Fehler: " + e.getMessage(); }
            final boolean fok = ok; final String fmsg = msg;
            h.post(new Runnable() { public void run() {
                a.busy = false;
                a.status = fok ? "" : "Fehlgeschlagen";
                if (!fok) Style.toast(StoreActivity.this, fmsg);
                refresh(); summary();
                SetupActivity.changed = true;   // Startseite zeigt die neue App
            }});
        }}).start();
    }

    void uninstall(Store.App a) {
        if (!a.isInstalled()) return;
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { Style.toast(this, "Deinstallieren nicht möglich"); }
        SetupActivity.changed = true;
    }

    @Override protected void onResume() { super.onResume(); overlay.start(); if (!apps.isEmpty()) { refresh(); summary(); } }
    @Override protected void onPause() { super.onPause(); overlay.stop(); }

    /** Kachel einer App. */
    class Card extends View {
        final Store.App a;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        Card(Store.App a) {
            super(StoreActivity.this);
            this.a = a;
            setFocusable(true); setClickable(true);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { act(Card.this.a); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(130).start();
                if (has) detail(Card.this.a);
                invalidate();
            }});
            setOnKeyListener(new OnKeyListener() { public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { uninstall(Card.this.a); return true; }
                return false;
            }});
            t.setTypeface(Style.POP);
        }

        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), hgt = getHeight();
            RectF r = new RectF(2 * dp, 2 * dp, w - 2 * dp, hgt - 2 * dp);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(isFocused() ? 0xF21C1A2C : 0xCC15141F);
            c.drawRoundRect(r, 16 * dp, 16 * dp, p);
            if (isFocused()) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(Style.brand(r.left, r.right));
                c.drawRoundRect(r, 16 * dp, 16 * dp, p);
                p.setStyle(Paint.Style.FILL); p.setShader(null);
            }
            // Symbol: echtes App-Icon wenn installiert, sonst Monogramm im Verlauf
            float is = 54 * dp, ix = w / 2 - is / 2, iy = 18 * dp;
            Drawable icon = null;
            if (a.isInstalled()) try { icon = getPackageManager().getApplicationIcon(a.pkg); } catch (Exception ignored) {}
            if (icon != null) { icon.setBounds((int) ix, (int) iy, (int) (ix + is), (int) (iy + is)); icon.draw(c); }
            else {
                p.setShader(new LinearGradient(ix, iy, ix + is, iy + is, Style.C1, Style.C3, Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(ix, iy, ix + is, iy + is), 14 * dp, 14 * dp, p);
                p.setShader(null);
                t.setColor(Color.WHITE); t.setTextSize(26 * dp); t.setTextAlign(Paint.Align.CENTER); t.setFakeBoldText(true);
                c.drawText(a.name.substring(0, 1).toUpperCase(Locale.GERMANY), w / 2, iy + is / 2 + 9 * dp, t);
                t.setFakeBoldText(false);
            }
            // Name
            t.setColor(Color.WHITE); t.setTextSize(14 * dp); t.setTextAlign(Paint.Align.CENTER);
            c.drawText(TextUtils.ellipsize(a.name, new android.text.TextPaint(t), w - 20 * dp, TextUtils.TruncateAt.END).toString(), w / 2, iy + is + 24 * dp, t);
            // Status-Pille
            String s; int col;
            if (a.busy) { s = a.status; col = Style.C2; }
            else if (a.appstore() && !a.isInstalled()) { s = "Appstore"; col = 0xFFFBBF24; }
            else if (a.hasUpdate()) { s = "Update " + a.latest; col = 0xFFF59E0B; }
            else if (a.isInstalled()) { s = "Installiert ✓"; col = 0xFF22C55E; }
            else if (a.resolving || (a.github != null || a.page != null) && a.apk == null && a.error == null) { s = "…"; col = Style.MUTED; }
            else if (a.apk == null) { s = "nicht verfügbar"; col = Style.MUTED; }
            else { s = !a.status.isEmpty() ? a.status : "Installieren"; col = Style.C3; }
            t.setTextSize(11 * dp);
            float tw = t.measureText(s) + 20 * dp, py = hgt - 30 * dp;
            RectF pill = new RectF(w / 2 - tw / 2, py, w / 2 + tw / 2, py + 20 * dp);
            p.setColor((col & 0x00FFFFFF) | 0x33000000);
            c.drawRoundRect(pill, 10 * dp, 10 * dp, p);
            t.setColor(col);
            c.drawText(s, w / 2, py + 14 * dp, t);
        }
    }
}
