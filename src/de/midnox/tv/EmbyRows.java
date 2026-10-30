package de.midnox.tv;

import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Emby-Reihen: "Weiterschauen" (oben) und je Bibliothek "Neu in ..." (unten). */
public class EmbyRows implements MediaCard.Listener {
    final HomeActivity act;
    final LinearLayout top, bottom;
    final Sub resume;
    final LinkedHashMap<String, Sub> subs = new LinkedHashMap<>();
    final Handler ui = new Handler(Looper.getMainLooper());
    Map<String, Bitmap> cache = new HashMap<>();
    volatile int gen;
    volatile Emby emby;

    EmbyRows(HomeActivity a) {
        act = a;
        top = column(a); bottom = column(a);
        resume = new Sub(a, "WEITERSCHAUEN");
        top.addView(resume.box, new LinearLayout.LayoutParams(-1, -2));
    }
    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        return l;
    }

    // ---------------- MediaCard.Listener
    @Override public void onFocus(MediaCard card) { act.reveal(card); act.showHero(card.e); }
    @Override public void onClick(MediaCard card) {
        Intent i = new Intent(act, DetailActivity.class);
        i.putExtra("id", card.e.id);
        i.putExtra("type", card.e.type);
        i.putExtra("series", card.e.seriesId);
        i.putExtra("season", card.e.seasonId);
        act.startActivity(i);
    }
    @Override public void onLongClick(MediaCard card) {
        Emby e = emby;
        Emby.open(act, "play", e == null ? null : e.serverId(), card.e.linkId);
    }

    /** Eine Reihe mit Ueberschrift und waagerecht scrollenden Vorschaubildern. */
    class Sub {
        final LinearLayout box, row;
        final TextView cap;
        final HorizontalScrollView sv;
        String title, sig = "";
        Sub(Context c, String title) {
            this.title = title;
            box = column(c);
            box.setPadding(0, Style.px(6), 0, 0);
            cap = Style.text(c, title, 11, Style.MUTED);
            cap.setLetterSpacing(0.3f);
            cap.setPadding(Style.px(64), 0, 0, 0);
            box.addView(cap);
            sv = new HorizontalScrollView(c);
            sv.setHorizontalScrollBarEnabled(false);
            sv.setClipChildren(false);
            sv.setClipToPadding(false);
            sv.setPadding(Style.px(54), Style.px(8), Style.px(54), Style.px(10));
            row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            sv.addView(row);
            box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
            box.setVisibility(View.GONE);
        }
        void set(List<Emby.Entry> list) {
            cap.setText(title);
            if (list == null || list.isEmpty()) { box.setVisibility(View.GONE); row.removeAllViews(); sig = ""; return; }
            StringBuilder sb = new StringBuilder();
            for (Emby.Entry e : list) sb.append(e.id).append(':').append((int) e.progress).append(e.bmp != null ? '+' : '-').append(',');
            box.setVisibility(View.VISIBLE);
            if (sb.toString().equals(sig)) return;
            sig = sb.toString();
            View f = row.findFocus();
            int focusIdx = f == null ? -1 : row.indexOfChild(f);
            row.removeAllViews();
            for (Emby.Entry e : list) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(168), Style.px(95));
                lp.setMargins(Style.px(8), 0, Style.px(8), 0);
                row.addView(new MediaCard(act, e, sv, EmbyRows.this), lp);
            }
            if (focusIdx >= 0 && focusIdx < row.getChildCount()) row.getChildAt(focusIdx).requestFocus();
        }
        void error(String msg) {
            cap.setText(msg.toUpperCase(Locale.GERMANY));
            row.removeAllViews(); sig = "";
            box.setVisibility(View.VISIBLE);
        }
    }

    void start() {
        final int g = ++gen;
        new Thread(new Runnable() { public void run() {
            while (g == gen) {
                load();
                if (g != gen) return;
                try { Thread.sleep(180000); } catch (InterruptedException e) { return; }
            }
        }}, "emby-refresh").start();
    }
    void stop() { gen++; }

    void load() {
        Emby e = Emby.from(act);
        emby = e;
        if (e == null) { status("keine Emby-Konfiguration"); post(null, null, null); return; }
        SharedPreferences pr = Style.prefs(act);
        try {
            e.serverId();
            List<Emby.Entry> rl = pr.getBoolean("hide_resume", false) ? new ArrayList<Emby.Entry>()
                    : e.entries(e.getObj("/emby/Users/" + e.user + "/Items/Resume?Limit=16&MediaTypes=Video&Recursive=true" + Emby.FIELDS).optJSONArray("Items"), 16);

            LinkedHashMap<String, String> titles = new LinkedHashMap<>();
            LinkedHashMap<String, List<Emby.Entry>> lists = new LinkedHashMap<>();
            JSONArray views = e.getObj("/emby/Users/" + e.user + "/Views?x=1").optJSONArray("Items");
            JSONArray known = new JSONArray();
            for (int i = 0; views != null && i < views.length(); i++) {
                JSONObject v = views.getJSONObject(i);
                String type = v.optString("CollectionType", ""), name = v.optString("Name"), id = v.getString("Id");
                if (!(type.isEmpty() || type.equals("movies") || type.equals("tvshows") || type.equals("mixed") || type.equals("homevideos"))) continue;
                known.put(new JSONObject().put("id", id).put("name", name));
                if (pr.getBoolean("hide_" + id, false)) continue;
                List<Emby.Entry> l = e.entries(e.getArr("/emby/Users/" + e.user + "/Items/Latest?Limit=16&ParentId=" + id + Emby.FIELDS), 16);
                if (!l.isEmpty()) { titles.put(id, "NEU IN " + name.toUpperCase(Locale.GERMANY)); lists.put(id, l); }
            }
            pr.edit().putString("emby_libs", known.toString()).apply(); // fuer das Einstellungsmenue

            Map<String, Bitmap> next = new HashMap<>();
            List<Emby.Entry> all = new ArrayList<>(rl);
            for (List<Emby.Entry> l : lists.values()) all.addAll(l);
            for (Emby.Entry en : all) {
                if (en.img == null) continue;
                Bitmap b = next.get(en.img);
                if (b == null) b = cache.get(en.img);
                if (b == null) b = Emby.fetch(en.img);
                if (b != null) next.put(en.img, b);
                en.bmp = b;
            }
            cache = next;
            status("ok: " + rl.size() + " weiterschauen, " + lists.size() + " Bibliotheks-Reihen");
            post(rl, titles, lists);
        } catch (Exception ex) {
            status("Fehler: " + ex);
            final String msg = "Emby nicht erreichbar (" + ex.getClass().getSimpleName() + ")";
            ui.post(new Runnable() { public void run() { resume.error(msg); } });
        }
    }

    void post(final List<Emby.Entry> rl, final LinkedHashMap<String, String> titles, final LinkedHashMap<String, List<Emby.Entry>> lists) {
        ui.post(new Runnable() { public void run() {
            resume.set(rl);
            if (titles == null) { bottom.removeAllViews(); subs.clear(); return; }
            for (Iterator<String> it = subs.keySet().iterator(); it.hasNext(); ) {
                String k = it.next();
                if (!titles.containsKey(k)) { bottom.removeView(subs.get(k).box); it.remove(); }
            }
            int idx = 0;
            for (String k : titles.keySet()) {
                Sub s = subs.get(k);
                if (s == null) {
                    s = new Sub(act, titles.get(k));
                    subs.put(k, s);
                    bottom.addView(s.box, Math.min(idx, bottom.getChildCount()), new LinearLayout.LayoutParams(-1, -2));
                }
                s.title = titles.get(k);
                s.set(lists.get(k));
                idx++;
            }
        }});
    }

    void status(String msg) {
        try {
            java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(act.getExternalFilesDir(null), "emby_status.txt"));
            o.write((new Date() + "  " + msg).getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {}
    }
}
