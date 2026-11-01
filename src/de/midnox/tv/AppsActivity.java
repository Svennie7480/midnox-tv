package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Uebersicht aller installierten Apps. Lange druecken / Menue-Taste: auf die Startseite legen oder entfernen. */
public class AppsActivity extends Activity {
    static final int COLS = 5;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Style.px(52), Style.px(32), Style.px(52), 0);
        col.addView(new Style.GradientText(this, "Alle Apps", 34));
        TextView hint = Style.text(this, "OK: öffnen   ·   OK lange drücken oder \u2630-Taste: Haken setzen = App erscheint auf der Startseite   ·   bequemer am PC: Einstellungen \u203a PC-Verwaltung", 12, Style.MUTED);
        hint.setPadding(Style.px(2), Style.px(6), 0, Style.px(8));
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, Style.px(10), 0, Style.px(30));
        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        sv.addView(grid);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        root.addView(col);
        setContentView(root);

        final PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> apps = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                if (!apps.containsKey(p)) apps.put(p, ri);
            }
        }
        List<String> pkgs = new ArrayList<>(apps.keySet());
        final Map<String, String> labels = new HashMap<>();
        for (String p : pkgs) labels.put(p, HomeActivity.label(pm, p));
        Collections.sort(pkgs, new Comparator<String>() { public int compare(String a, String c) { return labels.get(a).compareToIgnoreCase(labels.get(c)); } });

        float screenDp = getResources().getDisplayMetrics().widthPixels / Style.dp;
        float tileW = (screenDp - 104 - COLS * 20) / COLS;
        int tw = Style.px(tileW), th = Style.px((tileW - 20) * 9f / 16f + 10 + 42);
        List<String> favs = HomeActivity.favs(this);
        LinearLayout line = null;
        Tile first = null;
        for (int i = 0; i < pkgs.size(); i++) {
            if (i % COLS == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                grid.addView(line);
            }
            final String p = pkgs.get(i);
            ResolveInfo ri = apps.get(p);
            Drawable banner = null, icon = null;
            try { banner = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) {}
            if (banner == null) try { banner = pm.getApplicationBanner(p); } catch (Exception ignored) {}
            if (banner == null) icon = ri.loadIcon(pm);
            final Tile[] self = new Tile[1];
            final Intent li = Style.launchIntent(pm, p);
            Tile t = new Tile(this, labels.get(p), banner, icon, Tile.GLYPH_NONE,
                    new Runnable() { public void run() { if (li != null) Style.start(AppsActivity.this, li); } },
                    new Runnable() { public void run() {
                        boolean added = HomeActivity.toggleFav(AppsActivity.this, p);
                        self[0].pinned = added;
                        self[0].invalidate();
                        Toast.makeText(AppsActivity.this, labels.get(p) + (added ? " liegt jetzt auf der Startseite" : " von der Startseite entfernt"), Toast.LENGTH_SHORT).show();
                    } });
            self[0] = t;
            t.pinned = favs.contains(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tw, th);
            lp.setMargins(Style.px(10), Style.px(4), Style.px(10), Style.px(4));
            line.addView(t, lp);
            if (first == null) first = t;
        }
        if (first != null) first.requestFocus();
    }
}
