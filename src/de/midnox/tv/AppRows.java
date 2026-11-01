package de.midnox.tv;

import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Startseiten-Reihe "Zuletzt in deinen Apps": Weiterschauen ueber Netflix, Prime, Disney+, YouTube … */
public class AppRows implements MediaCard.Listener {
    final HomeActivity act;
    final LinearLayout box, row;
    final HorizontalScrollView sv;
    final Handler ui = new Handler(Looper.getMainLooper());
    final Map<String, Bitmap> banners = new HashMap<>();
    final Map<String, JSONObject> byId = new HashMap<>();
    String sig = "";

    AppRows(HomeActivity a) {
        act = a;
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setPadding(0, Style.px(6), 0, 0);
        TextView cap = Style.text(a, "ZULETZT IN DEINEN APPS", 11, Style.MUTED);
        cap.setLetterSpacing(0.3f);
        cap.setPadding(Style.px(64), 0, 0, 0);
        box.addView(cap);
        sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false); sv.setClipToPadding(false);
        sv.setPadding(Style.px(54), Style.px(8), Style.px(54), Style.px(10));
        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        sv.addView(row);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        box.setVisibility(View.GONE);
    }

    void refresh() {
        if (!Style.prefs(act).getBoolean("mw_row", true) || !Style.prefs(act).getBoolean("mw_on", true)) { box.setVisibility(View.GONE); return; }
        List<JSONObject> l = MediaWatch.recent(act, 14);
        StringBuilder sb = new StringBuilder();
        for (JSONObject x : l) sb.append(x.optString("key")).append(x.optString("sub")).append(',');
        if (l.isEmpty()) { box.setVisibility(View.GONE); row.removeAllViews(); sig = ""; return; }
        box.setVisibility(View.VISIBLE);
        if (sb.toString().equals(sig)) return;
        sig = sb.toString();
        row.removeAllViews(); byId.clear();
        for (JSONObject x : l) {
            Emby.Entry e = new Emby.Entry();
            e.id = x.optString("key"); e.type = "app";
            e.title = x.optString("title");
            String app = MediaWatch.appName(act, x.optString("pkg"));
            e.sub = x.optString("sub").isEmpty() ? app : x.optString("sub") + "  ·  " + app;
            e.forceText = true;
            e.bmp = banner(x.optString("pkg"));
            byId.put(e.id, x);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(168), Style.px(95));
            lp.setMargins(Style.px(8), 0, Style.px(8), 0);
            row.addView(new MediaCard(act, e, sv, this), lp);
        }
    }

    /** Banner der App (Fire-TV-Apps haben eins), sonst Symbol auf Markenhintergrund. */
    Bitmap banner(String pkg) {
        Bitmap b = banners.get(pkg);
        if (b != null) return b;
        try {
            PackageManager pm = act.getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            boolean isBanner = d != null;
            if (d == null) d = pm.getApplicationIcon(pkg);
            int w = 320, h = 180;
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (isBanner) { d.setBounds(0, 0, w, h); d.draw(c); }
            else {
                Paint p = new Paint(); p.setShader(new LinearGradient(0, 0, w, h, 0xFF2A1F4F, 0xFF0E3A4A, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                d.setBounds(w / 2 - 44, 30, w / 2 + 44, 118); d.draw(c);
            }
            banners.put(pkg, b);
        } catch (Exception ignored) {}
        return b;
    }

    @Override public void onFocus(MediaCard card) { act.reveal(card); }
    @Override public void onClick(MediaCard card) { JSONObject x = byId.get(card.e.id); if (x != null) MediaWatch.open(act, x); }
    @Override public void onLongClick(MediaCard card) { onClick(card); }
}
