package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Leiste mit Home-Assistant-Werten + Hinweise (Versteigerung, Muell, Akku). */
public class HaPanel extends LinearLayout {
    static class Item { String label, main, sub; Item(String l, String m, String s) { label = l; main = m; sub = s; } }

    final HomeActivity act; final float dp;
    final Handler ui = new Handler(Looper.getMainLooper());
    final LinearLayout cards;
    final TextView status;
    volatile int gen;

    HaPanel(HomeActivity a) {
        super(a);
        act = a; dp = Style.dp;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        cards = new LinearLayout(a);
        cards.setOrientation(HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        Style.Fit fit = new Style.Fit(a);
        fit.addView(cards);
        addView(fit, new LayoutParams(-2, -2));
        status = Style.text(a, "", 12, Style.MUTED);
        status.setPadding(0, (int) (6 * dp), 0, 0);
        addView(status);
    }

    void start() {
        final int g = ++gen;
        new Thread(new Runnable() { public void run() {
            while (g == gen) {
                refresh();
                if (g != gen) return;
                try { Thread.sleep(30000); } catch (InterruptedException e) { return; }
            }
        }}, "ha-refresh").start();
    }
    void stop() { gen++; }

    void refresh() {
        final List<Item> items = new ArrayList<>();
        final List<String> alerts = new ArrayList<>();
        String err = null;
        JSONObject cfg = Style.loadConfig(act);
        if (cfg == null || !cfg.has("url")) { post(items, "", alerts); return; }
        try {
            JSONArray states = new JSONArray(Style.httpGet(cfg.getString("url").replaceAll("/+$", "") + "/api/states", cfg.getString("token")));
            Map<String, JSONObject> byId = new HashMap<>(), byName = new HashMap<>();
            for (int i = 0; i < states.length(); i++) {
                JSONObject s = states.getJSONObject(i);
                byId.put(s.getString("entity_id"), s);
                JSONObject at = s.optJSONObject("attributes");
                if (at != null && at.has("friendly_name")) byName.put(at.getString("friendly_name").toLowerCase(Locale.ROOT), s);
            }
            JSONArray tiles = cfg.optJSONArray("tiles");
            if (tiles == null || tiles.length() == 0) tiles = autoTiles(states);   // ohne eigene Auswahl: Temperaturen automatisch
            for (int i = 0; tiles != null && i < tiles.length(); i++) {
                JSONObject t = tiles.getJSONObject(i);
                if ("dienst".equals(t.optString("format"))) {
                    Item d = dienst(t.getString("label"), find(t.optJSONArray("main"), byId, byName));
                    if (d != null) items.add(d);
                    continue;
                }
                String main = fmt(find(t.optJSONArray("main"), byId, byName), t.optString("main_unit", ""), t.optInt("decimals", 1));
                String sub = fmt(find(t.optJSONArray("sub"), byId, byName), t.optString("sub_unit", ""), t.optInt("sub_decimals", 0));
                if (main == null) continue;
                items.add(new Item(t.getString("label"), main, sub == null ? "" : sub));
            }
            // Live-Nachricht aus Home Assistant (Textfeld "midnox TV Nachricht")
            JSONObject msg = byId.get(cfg.optString("message_entity", "input_text.midnox_tv_nachricht"));
            if (msg != null) {
                String m = msg.optString("state", "").trim();
                if (!m.isEmpty() && !m.equals("unknown") && !m.equals("unavailable")) act.message = m; else act.message = null;
            } else act.message = null;
            JSONArray rules = cfg.optJSONArray("alerts");
            for (int i = 0; rules != null && i < rules.length(); i++) {
                try { alert(rules.getJSONObject(i), states, byId, byName, alerts); } catch (Exception ignored) {}
            }
        } catch (Exception e) { err = "Home Assistant nicht erreichbar"; }
        post(items, err == null ? "" : err, alerts);
    }

    // ---------- Hinweise ----------
    static void alert(JSONObject r, JSONArray states, Map<String, JSONObject> byId, Map<String, JSONObject> byName, List<String> out) throws JSONException {
        String type = r.getString("type"), text = r.optString("text", "{name}: {state}");
        if ("waste_tomorrow".equals(type)) {
            Calendar tom = Calendar.getInstance(); tom.add(Calendar.DAY_OF_YEAR, 1);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < states.length(); i++) {
                JSONObject s = states.getJSONObject(i);
                String id = s.getString("entity_id");
                if (!id.startsWith("sensor.") && !id.startsWith("calendar.")) continue;
                JSONObject a = s.optJSONObject("attributes");
                String name = a == null ? id : a.optString("friendly_name", id);
                String hay = (id + " " + name).toLowerCase(Locale.GERMANY);
                if (!hay.matches(".*(abfall|müll|muell|tonne|waste|papier|wertstoff|gelber sack|gelbe).*")) continue;
                String st = s.optString("state").trim().toLowerCase(Locale.GERMANY);
                boolean hit = a != null && a.has("daysTo") && a.optInt("daysTo", -1) == 1;
                if (!hit) hit = st.equals("1") || st.contains("morgen") || st.contains("tomorrow");
                if (!hit) hit = sameDay(parseDate(s.optString("state")), tom);
                if (!hit && a != null) hit = sameDay(parseDate(a.optString("start_time", a.optString("next_date", ""))), tom);
                if (hit) {
                    String label = name.replaceAll("(?i)\\s*(abfall|abholung|nächste|naechste)\\s*", " ").trim();
                    if (seen.add(label)) out.add(fill(text, label, s.optString("state"), ""));
                }
            }
            return;
        }
        JSONObject s = find(r.optJSONArray("entity"), byId, byName);
        if (s == null) return;
        String st = s.optString("state");
        JSONObject a = s.optJSONObject("attributes");
        String name = a == null ? "" : a.optString("friendly_name");
        double v = r.optDouble("value", 0);
        if ("attr_gt".equals(type)) {
            double av = a == null ? 0 : a.optDouble(r.getString("attr"), 0);
            if (av > v) out.add(fill(text, name, st, fmtNum(av)));
        } else if ("below".equals(type)) {
            try { if (Double.parseDouble(st) < v) out.add(fill(text, name, fmtNum(Double.parseDouble(st)), "")); } catch (NumberFormatException ignored) {}
        } else if ("above".equals(type)) {
            try { if (Double.parseDouble(st) > v) out.add(fill(text, name, fmtNum(Double.parseDouble(st)), "")); } catch (NumberFormatException ignored) {}
        } else if ("state_is".equals(type)) {
            if (st.equalsIgnoreCase(r.optString("value"))) out.add(fill(text, name, st, ""));
        }
    }
    static String fmtNum(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.GERMANY, "%.1f", d); }
    static String fill(String t, String name, String state, String attr) {
        return t.replace("{name}", name).replace("{state}", state).replace("{attr}", attr);
    }
    static Date parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] f = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy"};
        for (String p : f) {
            try { SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.GERMANY); sdf.setLenient(false); return sdf.parse(s.length() > 19 ? s.substring(0, 19) : s); }
            catch (Exception ignored) {}
        }
        return null;
    }
    static boolean sameDay(Date d, Calendar c) {
        if (d == null) return false;
        Calendar x = Calendar.getInstance(); x.setTime(d);
        return same(x, c);
    }
    static boolean same(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---------- Sensoren ----------
    static JSONObject find(JSONArray keys, Map<String, JSONObject> byId, Map<String, JSONObject> byName) {
        if (keys == null) return null;
        for (int i = 0; i < keys.length(); i++) {
            String k = keys.optString(i);
            JSONObject s = byId.get(k);
            if (s == null) s = byName.get(k.toLowerCase(Locale.ROOT));
            if (s == null) {
                String kl = k.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, JSONObject> e : byName.entrySet()) if (e.getKey().contains(kl)) { s = e.getValue(); break; }
            }
            if (s != null) {
                String st = s.optString("state");
                if (!st.equals("unavailable") && !st.equals("unknown") && !st.isEmpty()) return s;
            }
        }
        return null;
    }

    /** Standard-Kacheln fuer neue Nutzer: bis zu 6 Temperatur-Sensoren (mit Luftfeuchte, falls im selben Geraet). */
    static JSONArray autoTiles(JSONArray states) throws JSONException {
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < states.length() && out.length() < 6; i++) {
            JSONObject s = states.getJSONObject(i);
            String id = s.getString("entity_id");
            JSONObject a = s.optJSONObject("attributes");
            if (!id.startsWith("sensor.") || a == null) continue;
            if (!"temperature".equals(a.optString("device_class")) || !"measurement".equals(a.optString("state_class"))) continue;
            String st = s.optString("state");
            try { Double.parseDouble(st); } catch (Exception e) { continue; }
            String name = a.optString("friendly_name", id).replaceAll("(?i)\\s*(temperatur|temperature|temp)\\s*$", "").trim();
            if (name.isEmpty() || !seen.add(name.toLowerCase(Locale.ROOT))) continue;
            String hum = id.replaceAll("(?i)temperature|temperatur|_temp$", "humidity");
            out.put(new JSONObject().put("label", name).put("main", new JSONArray().put(id)).put("main_unit", "\u00b0").put("decimals", 1)
                    .put("sub", new JSONArray().put(hum)).put("sub_unit", " %").put("sub_decimals", 0));
        }
        return out;
    }

    /** Naechster Dienst: "Morgen · 03:45–11:53" und darunter die Dienstbezeichnung. */
    static Item dienst(String label, JSONObject s) {
        if (s == null) return null;
        JSONObject a = s.optJSONObject("attributes");
        if (a == null) return new Item(label, s.optString("state"), "");
        String von = a.optString("start_time"), bis = a.optString("end_time"), start = a.optString("start");
        String day = "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).parse(start);
            Calendar c = Calendar.getInstance(); c.setTime(d);
            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            if (same(c, today)) day = "Heute";
            else if (same(c, tomorrow)) day = "Morgen";
            else day = new SimpleDateFormat("EE dd.MM.", Locale.GERMANY).format(d);
        } catch (Exception ignored) {}
        String main = von.isEmpty() ? s.optString("state") : (day.isEmpty() ? "" : day + " · ") + von + "–" + bis;
        return new Item(label, main, von.isEmpty() ? "" : s.optString("state"));
    }

    static String fmt(JSONObject s, String unit, int decimals) {
        if (s == null) return null;
        String st = s.optString("state");
        try { return String.format(Locale.GERMANY, "%." + decimals + "f", Double.parseDouble(st)) + unit; }
        catch (NumberFormatException e) { return st; }
    }

    void post(final List<Item> items, final String msg, final List<String> alerts) {
        ui.post(new Runnable() { public void run() {
            cards.removeAllViews();
            for (Item it : items) {
                LayoutParams lp = new LayoutParams(-2, (int) (50 * dp));
                lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
                cards.addView(new Card(getContext(), it), lp);
            }
            status.setText(msg);
            status.setVisibility(msg.isEmpty() ? GONE : VISIBLE);
            act.setAlerts(alerts);
        }});
    }

    class Card extends LinearLayout {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Card(Context c, Item it) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding((int) (18 * dp), (int) (4 * dp), (int) (18 * dp), (int) (4 * dp));
            setWillNotDraw(false);
            TextView l = Style.text(c, it.label.toUpperCase(Locale.GERMANY), 10, Style.MUTED);
            l.setLetterSpacing(0.25f);
            addView(l);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.BOTTOM);
            row.setPadding(0, (int) (4 * dp), 0, 0);
            TextView m = Style.text(c, it.main, 16, Color.WHITE);
            m.setSingleLine(true);
            m.setMaxWidth((int) (240 * dp));
            m.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(m);
            if (!it.sub.isEmpty()) {
                TextView s = Style.text(c, "  " + it.sub, 12, 0xFF9AA0B8);
                s.setSingleLine(true);
                s.setPadding(0, 0, 0, (int) (2 * dp));
                row.addView(s);
            }
            addView(row);
        }
        @Override protected void onDraw(Canvas c) {
            RectF r = new RectF(0, 0, getWidth(), getHeight());
            float rad = 12 * dp;
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xB315141F);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1 * dp); p.setColor(0x22FFFFFF);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, Style.C1, Style.C3, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(6 * dp, 10 * dp, 9 * dp, getHeight() - 10 * dp), 2 * dp, 2 * dp, p);
            super.onDraw(c);
        }
    }
}
