package de.midnox.quiz;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Freundesgruppen (Server) und eigene Fragenpakete per Code. */
final class Groups {
    static JSONArray list(Context c) { try { return new JSONArray(U.prefs(c).getString("groups", "[]")); } catch (Exception e) { return new JSONArray(); } }
    static void store(Context c, JSONArray a) { U.prefs(c).edit().putString("groups", a.toString()).apply(); }
    static void add(Context c, String code, String name) {
        JSONArray a = list(c), b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optString("code").equals(code)) b.put(a.optJSONObject(i));
        try { b.put(new JSONObject().put("code", code).put("name", name)); } catch (Exception ignored) {}
        store(c, b);
    }
    static void remove(Context c, String code) {
        JSONArray a = list(c), b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optString("code").equals(code)) b.put(a.optJSONObject(i));
        store(c, b);
    }
    static JSONObject post(String action, JSONObject body) throws Exception { return new JSONObject(U.http(Scores.SERVER + "?a=" + action, body.toString())); }
    static JSONObject create(Context c, String name, String me) throws Exception {
        JSONObject r = post("group_new", new JSONObject().put("dev", Scores.device(c)).put("n", me).put("name", name));
        if (r.optBoolean("ok")) add(c, r.optString("code"), r.optString("name"));
        return r;
    }
    static JSONObject join(Context c, String code, String me) throws Exception {
        JSONObject r = post("group_join", new JSONObject().put("dev", Scores.device(c)).put("n", me).put("code", code.trim().toUpperCase(Locale.ROOT)));
        if (r.optBoolean("ok")) add(c, r.optString("code"), r.optString("name"));
        return r;
    }
    static void leave(Context c, String code) throws Exception {
        post("group_leave", new JSONObject().put("dev", Scores.device(c)).put("code", code));
        remove(c, code);
    }
    static JSONObject board(String code) throws Exception { return new JSONObject(U.http(Scores.SERVER + "?a=group&c=" + code, null)); }

    // ------------------------------------------------------------ Fragenpakete
    static JSONObject pack(String code) throws Exception { return new JSONObject(U.http(Scores.SERVER + "?a=pack&c=" + code.trim().toUpperCase(Locale.ROOT), null)); }
    static List<Bank.Q> packQuestions(JSONObject p, int n) {
        List<Bank.Q> out = new ArrayList<>();
        JSONArray a = p.optJSONArray("q"); if (a == null) return out;
        Random rnd = new Random();
        List<Integer> idx = new ArrayList<>(); for (int i = 0; i < a.length(); i++) idx.add(i);
        Collections.shuffle(idx, rnd);
        for (int k = 0; k < idx.size() && out.size() < n; k++) {
            JSONObject q = a.optJSONObject(idx.get(k)); JSONArray an = q.optJSONArray("a");
            if (an == null || an.length() != 4) continue;
            Bank.Raw r = new Bank.Raw(); r.id = q.optString("id"); r.cat = "pack"; r.text = q.optString("q"); r.level = 2;
            r.a = new String[]{an.optString(0), an.optString(1), an.optString(2), an.optString(3)};
            out.add(Bank.make(r, rnd));
        }
        return out;
    }
}
