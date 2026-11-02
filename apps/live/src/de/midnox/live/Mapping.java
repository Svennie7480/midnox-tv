package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.util.*;

/**
 * Senderzuordnung: gleiche Sender, die bei Anbietern verschieden heissen ("ARD" / "Das Erste HD"), zu einer Gruppe
 * zusammenfassen. Geht ein Sender nicht (alle MACs probiert), kommen die anderen Mitglieder der Gruppe der Reihe nach dran.
 */
final class Mapping {
    static final class Group { String name; final List<String> ids = new ArrayList<>(); }

    static List<Group> load(Context c) {
        List<Group> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("mapping", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Group g = new Group(); g.name = o.optString("n");
                JSONArray ids = o.optJSONArray("ids");
                for (int k = 0; ids != null && k < ids.length(); k++) g.ids.add(ids.getString(k));
                if (g.ids.size() > 0) l.add(g);
            }
        } catch (Exception ignored) {}
        return l;
    }
    static void save(Context c, List<Group> l) {
        JSONArray a = new JSONArray();
        try { for (Group g : l) if (g.ids.size() > 1) a.put(new JSONObject().put("n", g.name).put("ids", new JSONArray(g.ids))); } catch (Exception ignored) {}
        U.prefs(c).edit().putString("mapping", a.toString()).apply();
    }

    /** Andere Mitglieder der Gruppe(n) dieses Senders, in der festgelegten Reihenfolge. */
    static List<Model.Channel> alternatives(Context c, Model.Channel ch) {
        List<Model.Channel> out = new ArrayList<>();
        Set<String> seen = new HashSet<>(); seen.add(ch.id);
        for (Group g : load(c)) {
            if (!g.ids.contains(ch.id)) continue;
            for (String id : g.ids) {
                if (!seen.add(id)) continue;
                Model.Channel o = Lib.byId(id);
                if (o != null) out.add(o);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Vorschlaege
    static final String[][] ALIASES = {
        {"ard", "daserste", "erste", "ardhd"}, {"prosieben", "pro7"}, {"rtlzwei", "rtl2"}, {"kabeleins", "kabel1"}, {"sat1", "sat"},
        {"superrtl", "toggoplus"}, {"zdfneo", "neo"}, {"orf1", "orfeins"}, {"ntv", "n24"}, {"welt", "n24doku"}, {"skysportnews", "skysportnewshd"},
        {"ndr", "ndrfernsehen", "ndrsh", "ndrfernsehensh"}, {"wdr", "wdrfernsehen"}, {"br", "brfernsehen"}, {"mdr", "mdrfernsehen"}, {"hr", "hrfernsehen"}, {"swr", "swrfernsehen"},
    };
    static String key(Model.Channel ch) {
        String n = U.norm(ch.name);
        for (String[] a : ALIASES) for (String x : a) if (n.equals(x)) return "alias:" + a[0];
        String e = Epg.key(ch);
        if (e != null) return "epg:" + e.toLowerCase(Locale.ROOT);
        return "name:" + n;
    }
    /** Sender, die in mindestens zwei Quellen vorkommen und noch nicht zugeordnet sind. */
    static List<Group> suggest(Context c) {
        Set<String> mapped = new HashSet<>();
        for (Group g : load(c)) mapped.addAll(g.ids);
        Map<String, List<Model.Channel>> by = new LinkedHashMap<>();
        for (Model.Channel ch : Lib.channels) {
            if (mapped.contains(ch.id)) continue;
            String k = key(ch);
            if (k.equals("name:")) continue;
            List<Model.Channel> l = by.get(k);
            if (l == null) { l = new ArrayList<>(); by.put(k, l); }
            l.add(ch);
        }
        List<Group> out = new ArrayList<>();
        for (List<Model.Channel> l : by.values()) {
            Set<String> srcs = new HashSet<>();
            for (Model.Channel ch : l) srcs.add(ch.src);
            if (srcs.size() < 2) continue;
            // je Quelle hoechstens drei Kandidaten (HD zuerst)
            Collections.sort(l, new Comparator<Model.Channel>() { public int compare(Model.Channel a, Model.Channel b) {
                int ha = a.name.toUpperCase(Locale.ROOT).contains("HD") ? 0 : 1, hb = b.name.toUpperCase(Locale.ROOT).contains("HD") ? 0 : 1;
                return ha != hb ? ha - hb : a.name.length() - b.name.length();
            }});
            Group g = new Group();
            Map<String, Integer> per = new HashMap<>();
            for (Model.Channel ch : l) { int n = per.containsKey(ch.src) ? per.get(ch.src) : 0; if (n < 3) { g.ids.add(ch.id); per.put(ch.src, n + 1); } }
            g.name = clean(l.get(0).name);
            out.add(g);
            if (out.size() >= 200) break;
        }
        return out;
    }
    static String clean(String n) { return n.replaceAll("^\\s*\\|?[A-Z]{2,3}\\|?\\s*[:|•-]?\\s*", "").replaceAll("(?i)\\s*\\b(fhd|uhd|hd|sd|4k|hevc|raw)\\b\\s*", " ").trim(); }

    static JSONObject channelJson(Model.Channel ch) throws JSONException {
        return new JSONObject().put("id", ch.id).put("name", ch.name).put("src", Lib.srcNames.containsKey(ch.src) ? Lib.srcNames.get(ch.src) : "").put("group", ch.group).put("num", ch.num);
    }
}
