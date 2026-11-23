package de.midnox.rezepte;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Alles, was auf dem Gerät liegt: lokale Rezepte, Kochplan, Einkaufsliste, Einstellungen, Bilder.
 * Rezepte mit Speicherort „cloud“ liegen auf midnox.de (Cloud.java); hier nur ihr Zwischenspeicher.
 */
final class Store {
    static File dir, imgDir, cimgDir;
    static final Object lock = new Object();
    static final LinkedHashMap<String, JSONObject> local = new LinkedHashMap<>();
    static JSONObject plan, shop, settings;
    static final AtomicLong ver = new AtomicLong(1);   // steigt bei jeder Änderung → Browser im Netz laden neu

    static void init(Context c) {
        synchronized (lock) {
            if (dir != null) return;
            dir = c.getFilesDir(); imgDir = new File(dir, "img"); cimgDir = new File(dir, "cimg"); imgDir.mkdirs(); cimgDir.mkdirs();
            try { JSONArray a = new JSONArray(U.readText(new File(dir, "recipes.json"), "[]")); for (int i = 0; i < a.length(); i++) { JSONObject r = a.getJSONObject(i); local.put(r.getString("id"), r); } } catch (Exception ignored) {}
            plan = obj("plan.json"); shop = obj("shop.json"); settings = obj("settings.json");
            try {
                if (!plan.has("days")) plan.put("days", new JSONObject());
                if (!shop.has("items")) shop.put("items", new JSONArray());
                if (!settings.has("slots")) settings.put("slots", new JSONArray().put("Mittag").put("Abend"));
                if (!settings.has("persons")) settings.put("persons", 2);
                if (!settings.has("cloudUrl")) settings.put("cloudUrl", "https://midnox.de/midnoxtv/rezepte/");
                if (!settings.has("cloudKey")) settings.put("cloudKey", "");
                if (!settings.has("defStore")) settings.put("defStore", "local");
                if (!settings.has("bg")) settings.put("bg", false);
            } catch (JSONException ignored) {}
        }
        Cloud.loadCache();
    }
    static JSONObject obj(String name) { try { return new JSONObject(U.readText(new File(dir, name), "{}")); } catch (Exception e) { return new JSONObject(); } }
    static void saveObj(String name, JSONObject o) { try { U.writeText(new File(dir, name), o.toString()); } catch (Exception ignored) {} ver.incrementAndGet(); }
    static void saveLocal() {
        JSONArray a = new JSONArray(); for (JSONObject r : local.values()) a.put(r);
        try { U.writeText(new File(dir, "recipes.json"), a.toString()); } catch (Exception ignored) {}
        ver.incrementAndGet();
    }

    static String setting(String k, String def) { synchronized (lock) { return settings.optString(k, def); } }
    static boolean bg() { synchronized (lock) { return settings != null && settings.optBoolean("bg", false); } }

    /** Einstellungen für Browser: der Cloud-Schlüssel verlässt das Gerät nie */
    static JSONObject publicSettings() throws JSONException {
        synchronized (lock) {
            JSONObject o = new JSONObject(settings.toString());
            o.put("cloudKey", settings.optString("cloudKey").isEmpty() ? "" : "••••••••");
            o.put("hasKey", !settings.optString("cloudKey").isEmpty());
            return o;
        }
    }
    static void putSettings(JSONObject n) throws JSONException {
        synchronized (lock) {
            for (Iterator<String> it = n.keys(); it.hasNext(); ) {
                String k = it.next();
                if (k.equals("hasKey")) continue;
                if (k.equals("cloudKey") && n.optString(k).startsWith("••")) continue;   // unverändert
                settings.put(k, n.get(k));
            }
            saveObj("settings.json", settings);
        }
        Cloud.reset();
    }

    // ---- Rezepte (lokal) ----
    static JSONObject getLocal(String id) { synchronized (lock) { return local.get(id); } }
    static void putLocal(JSONObject r) throws JSONException { synchronized (lock) { r.put("store", "local"); local.put(r.getString("id"), r); saveLocal(); } }
    static void delLocal(String id) { synchronized (lock) { if (local.remove(id) != null) saveLocal(); } new File(imgDir, id + ".jpg").delete(); }

    static File localImg(String id) { return new File(imgDir, id.replaceAll("[^\\w-]", "") + ".jpg"); }
    static File cloudImg(String id, long upd) { return new File(cimgDir, id.replaceAll("[^\\w-]", "") + "_" + upd + ".jpg"); }
    static void dropCloudImg(String id) { File[] fs = cimgDir.listFiles(); if (fs != null) for (File f : fs) if (f.getName().startsWith(id + "_")) f.delete(); }
}
