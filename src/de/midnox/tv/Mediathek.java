package de.midnox.tv;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Öffentliche Mediatheken (ARD, ZDF, Arte, 3sat, BR, NDR, WDR …) über die freie
 * MediathekViewWeb-API – eine Abfrage für alle, kein Konto, kein Token.
 * Liefert direkte Stream-Adressen, die der midnox-Live-Player abspielen kann.
 */
final class Mediathek {
    static final String API = "https://mediathekviewweb.de/api/query";

    static final class Hit {
        String channel, topic, title, desc, url, website;
        long duration;   // Sekunden
    }

    /** Sucht in allen öffentlichen Mediatheken. Gibt eine leere Liste bei Fehler/kein Netz zurück. */
    static List<Hit> search(String query, int size) {
        List<Hit> out = new ArrayList<>();
        try {
            JSONObject body = new JSONObject()
                .put("queries", new JSONArray().put(new JSONObject().put("fields", new JSONArray().put("title").put("topic")).put("query", query)))
                .put("sortBy", "timestamp").put("sortOrder", "desc").put("future", false).put("offset", 0).put("size", size);
            HttpURLConnection h = (HttpURLConnection) new URL(API).openConnection();
            h.setConnectTimeout(6000); h.setReadTimeout(10000); h.setDoOutput(true); h.setRequestMethod("POST");
            h.setRequestProperty("Content-Type", "text/plain");   // API-Eigenheit: JSON, aber als text/plain
            byte[] b = body.toString().getBytes("UTF-8");
            h.setFixedLengthStreamingMode(b.length);
            OutputStream o = h.getOutputStream(); o.write(b); o.close();
            if (h.getResponseCode() != 200) return out;
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int r;
            InputStream in = h.getInputStream(); while ((r = in.read(buf)) > 0) bo.write(buf, 0, r); in.close();
            JSONObject res = new JSONObject(bo.toString("UTF-8")).optJSONObject("result");
            JSONArray a = res == null ? null : res.optJSONArray("results");
            Set<String> seen = new HashSet<>();
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject x = a.getJSONObject(i);
                String url = firstNonEmpty(x.optString("url_video_hd"), x.optString("url_video"), x.optString("url_video_low"));
                if (url.isEmpty()) continue;
                String title = x.optString("title").trim(), topic = x.optString("topic").trim();
                String key = (topic + "|" + title).toLowerCase(Locale.GERMANY);
                if (!seen.add(key)) continue;   // Dubletten (gleiche Sendung in mehreren Auflösungen) meiden
                Hit hit = new Hit();
                hit.channel = x.optString("channel"); hit.topic = topic; hit.title = title.isEmpty() ? topic : title;
                hit.desc = x.optString("description"); hit.url = url.replaceFirst("^http://", "https://");
                hit.website = x.optString("url_website"); hit.duration = x.optLong("duration");
                out.add(hit);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static String firstNonEmpty(String... v) { for (String s : v) if (s != null && !s.isEmpty()) return s; return ""; }

    static String durationText(long sec) {
        if (sec <= 0) return "";
        long m = sec / 60;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }
}
