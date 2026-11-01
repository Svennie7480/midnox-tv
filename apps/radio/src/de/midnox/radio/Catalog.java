package de.midnox.radio;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Senderkatalog (radio.json im GitHub-Repo, lokal zwischengespeichert) + Favoriten/Verlauf. */
final class Catalog {
    static final String BASE = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/radio/";

    static final class Group {
        String id, name, type, logo; int color;
        final List<Station> stations = new ArrayList<>();
        boolean dynamic;   // wird live aus radio-browser geladen
    }

    static List<Group> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray gs = root.getJSONArray("groups");
        List<Group> out = new ArrayList<>();
        for (int i = 0; i < gs.length(); i++) {
            JSONObject g = gs.getJSONObject(i);
            Group x = new Group();
            x.id = g.optString("id"); x.name = g.optString("name"); x.type = g.optString("type");
            String l = g.optString("logo", "");
            x.logo = l.isEmpty() || l.equals("null") ? null : BASE + l;
            x.color = U.color(g.optString("color", ""), U.C1);
            JSONArray st = g.getJSONArray("stations");
            for (int k = 0; k < st.length(); k++) x.stations.add(Station.from(st.getJSONObject(k), BASE, x.name));
            if (!x.stations.isEmpty()) out.add(x);
        }
        return out;
    }

    static File cache(Context c) { return new File(c.getFilesDir(), "radio.json"); }

    static List<Group> cached(Context c) {
        try { return parse(U.read(new FileInputStream(cache(c)))); } catch (Exception e) { return null; }
    }

    static List<Group> fetch(Context c) throws Exception {
        String j = U.get(BASE + "radio.json?t=" + System.currentTimeMillis() / 600000);
        List<Group> g = parse(j);
        try (FileOutputStream o = new FileOutputStream(cache(c))) { o.write(j.getBytes("UTF-8")); }
        return g;
    }

    // ------------------------------------------------------------ Favoriten & zuletzt gehoert
    static List<Station> list(Context c, String key) {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Station.from(a.getJSONObject(i), "", ""));
        } catch (Exception ignored) {}
        return out;
    }
    static void save(Context c, String key, List<Station> l) {
        JSONArray a = new JSONArray();
        for (Station s : l) a.put(s.json());
        U.prefs(c).edit().putString(key, a.toString()).apply();
    }
    static Set<String> favIds;
    static boolean isFav(Context c, Station s) {
        if (favIds == null) { favIds = new HashSet<>(); for (Station f : list(c, "favs")) favIds.add(f.id); }
        return favIds.contains(s.id);
    }
    static boolean toggleFav(Context c, Station s) {
        favIds = null;
        List<Station> l = list(c, "favs");
        for (Iterator<Station> it = l.iterator(); it.hasNext(); ) if (it.next().id.equals(s.id)) { it.remove(); save(c, "favs", l); return false; }
        l.add(0, s); save(c, "favs", l); return true;
    }
    static void played(Context c, Station s) {
        List<Station> l = list(c, "recent");
        for (Iterator<Station> it = l.iterator(); it.hasNext(); ) if (it.next().id.equals(s.id)) it.remove();
        l.add(0, s);
        while (l.size() > 30) l.remove(l.size() - 1);
        save(c, "recent", l);
        U.prefs(c).edit().putString("last", s.json().toString()).apply();
    }
    static Station last(Context c) {
        try { return Station.from(new JSONObject(U.prefs(c).getString("last", "")), "", ""); } catch (Exception e) { return null; }
    }
}
