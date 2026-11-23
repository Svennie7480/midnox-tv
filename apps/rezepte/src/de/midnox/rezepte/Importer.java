package de.midnox.rezepte;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Holt ein Rezept von einer Webseite (Chefkoch, Lecker, Eat Smarter, Blogs …) über die eingebetteten schema.org-Daten. */
final class Importer {
    static JSONObject fromUrl(String url) throws Exception {
        String html;
        try { html = new String(U.http(url, "GET", null, null, 10000, 6 << 20), "UTF-8"); }
        catch (java.net.UnknownHostException e) { throw new java.io.IOException("Seite nicht erreichbar – Link prüfen"); }
        catch (Exception e) { String m = String.valueOf(e.getMessage()); throw new java.io.IOException(m.contains("403") || m.contains("429") ? "Die Seite blockiert den Abruf – Rezept bitte von Hand anlegen" : "Seite nicht abrufbar (" + m + ")"); }
        JSONObject r = null;
        Matcher m = Pattern.compile("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find() && r == null) {
            String js = m.group(1).trim();
            try { r = find(js.startsWith("[") ? new JSONArray(js) : new JSONObject(js)); } catch (Exception ignored) {}
        }
        JSONObject out = new JSONObject();
        out.put("src", url);
        if (r == null) {   // keine Rezeptdaten: wenigstens Titel und Bild
            out.put("title", unesc(meta(html, "og:title", title(html))));
            String im = meta(html, "og:image", ""); if (!im.isEmpty()) out.put("imgUrl", abs(url, im));
            out.put("ingr", new JSONArray()); out.put("steps", new JSONArray());
            out.put("partial", true);
            return out;
        }
        out.put("title", unesc(r.optString("name", "Rezept")).trim());
        String desc = unesc(r.optString("description", "")).trim(); if (!desc.isEmpty()) out.put("notes", desc);
        String im = image(r.opt("image")); if (!im.isEmpty()) out.put("imgUrl", abs(url, im));
        int portions = portionsOf(r.opt("recipeYield")); if (portions > 0) out.put("portions", portions);
        int t = dur(r.optString("totalTime", "")); if (t == 0) t = dur(r.optString("prepTime", "")) + dur(r.optString("cookTime", ""));
        if (t > 0) out.put("time", t);
        JSONArray ingr = new JSONArray(); Object ri = r.opt("recipeIngredient"); if (ri == null) ri = r.opt("ingredients");
        if (ri instanceof JSONArray) for (int i = 0; i < ((JSONArray) ri).length(); i++) { String s = clean(((JSONArray) ri).optString(i)); if (!s.isEmpty()) ingr.put(s); }
        out.put("ingr", ingr);
        JSONArray steps = new JSONArray(); steps(r.opt("recipeInstructions"), steps); out.put("steps", steps);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String k : new String[]{"recipeCategory", "recipeCuisine", "keywords"}) {
            Object v = r.opt(k); List<String> l = new ArrayList<>();
            if (v instanceof JSONArray) for (int i = 0; i < ((JSONArray) v).length(); i++) l.add(((JSONArray) v).optString(i));
            else if (v != null) l.addAll(Arrays.asList(v.toString().split(",")));
            for (String s : l) { s = unesc(s).trim(); if (s.length() > 1 && s.length() < 24 && tags.size() < (k.equals("keywords") ? 6 : 8)) tags.add(s.substring(0, 1).toUpperCase(Locale.GERMAN) + s.substring(1)); }
        }
        out.put("tags", new JSONArray(tags));
        return out;
    }

    static JSONObject find(Object o) throws JSONException {
        if (o instanceof JSONArray) { for (int i = 0; i < ((JSONArray) o).length(); i++) { JSONObject r = find(((JSONArray) o).get(i)); if (r != null) return r; } return null; }
        if (!(o instanceof JSONObject)) return null;
        JSONObject j = (JSONObject) o;
        Object t = j.opt("@type");
        if (t != null && t.toString().contains("Recipe")) return j;
        if (j.has("@graph")) return find(j.get("@graph"));
        if (j.has("mainEntity")) return find(j.get("mainEntity"));
        return null;
    }
    static void steps(Object o, JSONArray out) {
        if (o == null) return;
        if (o instanceof String) { for (String p : ((String) o).replaceAll("(?i)<br\\s*/?>|</p>|</li>", "\n").split("\n+")) { p = clean(p); if (!p.isEmpty()) out.put(p); } return; }
        if (o instanceof JSONArray) { for (int i = 0; i < ((JSONArray) o).length(); i++) steps(((JSONArray) o).opt(i), out); return; }
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            if (j.has("itemListElement")) { String n = clean(j.optString("name")); if (!n.isEmpty() && j.optString("@type").contains("Section")) out.put("## " + n); steps(j.opt("itemListElement"), out); return; }
            String tx = j.optString("text", j.optString("name", "")); steps(tx, out);
        }
    }
    static String image(Object o) {
        if (o == null) return "";
        if (o instanceof String) return (String) o;
        if (o instanceof JSONArray) { JSONArray a = (JSONArray) o; return a.length() == 0 ? "" : image(a.opt(0)); }
        if (o instanceof JSONObject) return ((JSONObject) o).optString("url", ((JSONObject) o).optString("contentUrl", ""));
        return "";
    }
    static int portionsOf(Object o) {
        if (o instanceof JSONArray) { for (int i = 0; i < ((JSONArray) o).length(); i++) { int n = portionsOf(((JSONArray) o).opt(i)); if (n > 0) return n; } return 0; }
        if (o == null) return 0;
        Matcher m = Pattern.compile("(\\d+)").matcher(o.toString()); return m.find() ? Math.min(99, Integer.parseInt(m.group(1))) : 0;
    }
    /** ISO-8601-Dauer (PT1H30M, P0DT0H45M) → Minuten */
    static int dur(String s) {
        Matcher m = Pattern.compile("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").matcher(s == null ? "" : s.trim());
        if (!m.find()) return 0;
        int d = m.group(1) == null ? 0 : Integer.parseInt(m.group(1)), h = m.group(2) == null ? 0 : Integer.parseInt(m.group(2)), mi = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return d * 1440 + h * 60 + mi;
    }
    static String clean(String s) { return unesc(s == null ? "" : s.replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim(); }
    static String unesc(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü").replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü").replace("&szlig;", "ß");
    }
    static String meta(String html, String prop, String def) {
        Matcher m = Pattern.compile("<meta[^>]+(?:property|name)=[\"']" + Pattern.quote(prop) + "[\"'][^>]*content=[\"']([^\"']*)", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']" + Pattern.quote(prop) + "[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1) : def;
    }
    static String title(String html) { Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html); return m.find() ? clean(m.group(1)) : "Rezept"; }
    static String abs(String base, String u) { try { return new java.net.URL(new java.net.URL(base), u).toString(); } catch (Exception e) { return u; } }
}
