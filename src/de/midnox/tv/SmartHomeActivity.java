package de.midnox.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/**
 * Smart-Home-Leiste: links Einschlaf-Timer und Raeume (Bereiche aus Home Assistant),
 * rechts alle steuerbaren Geraete des Raums. OK schaltet, links/rechts dimmt bzw. regelt.
 */
public class SmartHomeActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    String base, token;
    final List<String[]> rooms = new ArrayList<>();          // {area_id, name}
    final Map<String, List<String>> roomEntities = new HashMap<>();
    final Map<String, JSONObject> states = new HashMap<>();
    LinearLayout roomList, devList;
    TextView roomTitle, hint;
    String room;
    Row roomRow, timerRow;
    final Map<String, Row> devRows = new HashMap<>();
    static final String DOMAINS = "light|switch|cover|climate|fan|media_player|scene|script|input_boolean|vacuum";

    /** Zeile im midnox-Stil (wie in den Einstellungen). */
    class Row extends LinearLayout {
        final TextView name, value; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Runnable onOk, onLeft, onRight, onFocus;
        boolean adjustable, selected;
        Row(String label) {
            super(SmartHomeActivity.this);
            setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Style.px(18), Style.px(11), Style.px(18), Style.px(11));
            setFocusable(true); setClickable(true); setWillNotDraw(false);
            name = Style.text(getContext(), label, 15, 0xFFE6E8F2);
            name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            value = Style.text(getContext(), "", 13, 0xFFB4B8CC);
            value.setSingleLine(true);
            addView(name, new LayoutParams(0, -2, 1));
            addView(value);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { if (onOk != null) onOk.run(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                invalidate(); value.setTextColor(has ? Color.WHITE : 0xFFB4B8CC);
                if (has && onFocus != null) onFocus.run();
            }});
        }
        @Override public boolean onKeyDown(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && onLeft != null) { onLeft.run(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && onRight != null) { onRight.run(); return true; }
            return super.onKeyDown(k, e);
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp;
            RectF r = new RectF(1 * dp, 1 * dp, getWidth() - 1 * dp, getHeight() - 1 * dp);
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            p.setColor(isFocused() ? 0xFF1C1B29 : selected ? 0xCC1A1928 : 0x8015141F);
            c.drawRoundRect(r, 12 * dp, 12 * dp, p);
            if (isFocused() || selected) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth((isFocused() ? 2 : 1) * dp); p.setShader(Style.brand(r.left, r.right));
                c.drawRoundRect(r, 12 * dp, 12 * dp, p);
            }
            super.onDraw(c);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xE6050409);
        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(Style.px(48), Style.px(30), Style.px(48), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(new Style.GradientText(this, "Zuhause", 30));
        TextView sub = Style.text(this, "OK schaltet  ·  ◀ ▶ dimmt / regelt  ·  Zurück = Räume", 11, Style.MUTED);
        sub.setPadding(0, Style.px(4), 0, Style.px(14));
        left.addView(sub);
        ScrollView ls = new ScrollView(this);
        ls.setVerticalScrollBarEnabled(false);
        roomList = new LinearLayout(this);
        roomList.setOrientation(LinearLayout.VERTICAL);
        ls.addView(roomList);
        left.addView(ls);
        cols.addView(left, new LinearLayout.LayoutParams(Style.px(270), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Style.px(32), Style.px(6), 0, 0);
        roomTitle = Style.text(this, "", 11, Style.MUTED);
        roomTitle.setLetterSpacing(0.3f);
        roomTitle.setPadding(Style.px(4), Style.px(14), 0, Style.px(10));
        right.addView(roomTitle);
        ScrollView ds = new ScrollView(this);
        ds.setVerticalScrollBarEnabled(false);
        ds.setClipToPadding(false);
        ds.setPadding(0, 0, 0, Style.px(40));
        devList = new LinearLayout(this);
        devList.setOrientation(LinearLayout.VERTICAL);
        ds.addView(devList);
        right.addView(ds);
        hint = Style.text(this, "", 13, Style.MUTED);
        right.addView(hint);
        cols.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(cols);
        setContentView(root);

        // Einschlaf-Timer ganz oben
        timerRow = addRoomRow("☾  Einschlaf-Timer");
        final Runnable showT = new Runnable() { public void run() { timerRow.value.setText("◀  " + Sleep.label(SmartHomeActivity.this) + "  ▶"); } };
        showT.run();
        timerRow.onOk = timerRow.onRight = new Runnable() { public void run() { Sleep.cycle(SmartHomeActivity.this, 1); showT.run(); } };
        timerRow.onLeft = new Runnable() { public void run() { Sleep.cycle(SmartHomeActivity.this, -1); showT.run(); } };
        timerRow.requestFocus();

        JSONObject cfg = Style.loadConfig(this);
        if (cfg == null) { hint.setText("Home Assistant ist nicht verbunden – Einstellungen → Verbindungen."); return; }
        base = cfg.optString("url").replaceAll("/+$", ""); token = cfg.optString("token");
        hint.setText("Räume werden geladen …");
        new Thread(new Runnable() { public void run() { loadRooms(); } }).start();
    }

    Row addRoomRow(String label) {
        Row r = new Row(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Style.px(6);
        roomList.addView(r, lp);
        return r;
    }

    void loadRooms() {
        try {
            String tpl = "{% set ns = namespace(r=[]) %}{% for a in areas() %}"
                + "{% set e = area_entities(a) | select('match','^(" + DOMAINS + ")[.]') | list %}"
                + "{% if e | count > 0 %}{% set ns.r = ns.r + [[a, area_name(a), e]] %}{% endif %}{% endfor %}{{ ns.r | tojson }}";
            final JSONArray arr = new JSONArray(Style.httpPost(base + "/api/template", token, new JSONObject().put("template", tpl).toString()));
            loadStates();
            h.post(new Runnable() { public void run() { showRooms(arr); } });
        } catch (final Exception e) {
            h.post(new Runnable() { public void run() { hint.setText("Home Assistant: " + e.getMessage()); } });
        }
    }

    void loadStates() throws Exception {
        JSONArray a = new JSONArray(Style.httpGet(base + "/api/states", token));
        synchronized (states) { for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); states.put(o.getString("entity_id"), o); } }
    }

    void showRooms(JSONArray arr) {
        hint.setText("");
        List<JSONArray> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.optJSONArray(i));
        Collections.sort(list, new Comparator<JSONArray>() { public int compare(JSONArray x, JSONArray y) { return x.optString(1).compareToIgnoreCase(y.optString(1)); } });
        String last = Style.prefs(this).getString("sh_room", "");
        Row lastRow = null;
        for (JSONArray r : list) {
            final String id = r.optString(0), name = r.optString(1);
            List<String> ents = new ArrayList<>();
            JSONArray e = r.optJSONArray(2);
            for (int k = 0; e != null && k < e.length(); k++) {
                JSONObject st; synchronized (states) { st = states.get(e.optString(k)); }
                if (st != null && !"unavailable".equals(st.optString("state"))) ents.add(e.optString(k));
            }
            if (ents.isEmpty()) continue;
            Collections.sort(ents, new Comparator<String>() { public int compare(String x, String y) {
                int c = order(x) - order(y); return c != 0 ? c : friendly(x).compareToIgnoreCase(friendly(y));
            }});
            rooms.add(new String[]{id, name});
            roomEntities.put(id, ents);
            final Row row = addRoomRow(name);
            row.value.setText(String.valueOf(ents.size()));
            row.onFocus = new Runnable() { public void run() { selectRoom(id, name, row); } };
            row.onRight = new Runnable() { public void run() { if (devList.getChildCount() > 0) devList.getChildAt(0).requestFocus(); } };
            if (id.equals(last)) lastRow = row;
        }
        if (lastRow != null) selectRoom(lastRow == null ? null : roomIdOf(lastRow), lastRow.name.getText().toString(), lastRow);
        else if (!rooms.isEmpty()) selectRoom(rooms.get(0)[0], rooms.get(0)[1], (Row) roomList.getChildAt(1));
        h.postDelayed(refresh, 4000);
    }

    String roomIdOf(Row r) { int i = roomList.indexOfChild(r) - 1; return i >= 0 && i < rooms.size() ? rooms.get(i)[0] : null; }

    static int order(String id) {
        String d = id.substring(0, id.indexOf('.'));
        String[] o = {"light", "switch", "input_boolean", "fan", "cover", "climate", "media_player", "vacuum", "scene", "script"};
        for (int i = 0; i < o.length; i++) if (o[i].equals(d)) return i;
        return 99;
    }
    String friendly(String id) {
        JSONObject s; synchronized (states) { s = states.get(id); }
        String n = s == null ? id : s.optJSONObject("attributes").optString("friendly_name", id);
        return n;
    }

    void selectRoom(String id, String name, Row row) {
        if (id == null) return;
        if (roomRow != null) { roomRow.selected = false; roomRow.invalidate(); }
        roomRow = row; row.selected = true; row.invalidate();
        if (id.equals(room)) return;
        room = id;
        Style.prefs(this).edit().putString("sh_room", id).apply();
        roomTitle.setText(name.toUpperCase(Locale.GERMANY));
        devList.removeAllViews(); devRows.clear();
        String roomName = name;
        for (final String eid : roomEntities.get(id)) {
            String label = friendly(eid);
            // Raumnamen am Anfang weglassen ("Wohnzimmer Deckenlampe" -> "Deckenlampe")
            if (label.toLowerCase(Locale.GERMANY).startsWith(roomName.toLowerCase(Locale.GERMANY) + " ")) label = label.substring(roomName.length() + 1);
            final Row r = new Row(label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Style.px(6);
            devList.addView(r, lp);
            devRows.put(eid, r);
            wire(eid, r);
            render(eid, r);
        }
    }

    // ------------------------------------------------------------ Geraete
    JSONObject st(String id) { synchronized (states) { return states.get(id); } }
    static String dom(String id) { return id.substring(0, id.indexOf('.')); }

    void wire(final String id, final Row r) {
        final String d = dom(id);
        final Runnable back = new Runnable() { public void run() { if (roomRow != null) roomRow.requestFocus(); } };
        r.onLeft = back;
        switch (d) {
            case "light":
                r.onOk = svc("light", "toggle", id, null);
                if (dimmable(id)) {
                    r.adjustable = true;
                    r.onLeft = svc("light", "turn_on", id, "{\"brightness_step_pct\":-10}");
                    r.onRight = svc("light", "turn_on", id, "{\"brightness_step_pct\":10}");
                }
                break;
            case "switch": case "input_boolean": case "fan":
                r.onOk = svc(d, "toggle", id, null); break;
            case "cover":
                r.onOk = svc("cover", "toggle", id, null);
                r.adjustable = true;
                r.onLeft = svc("cover", "close_cover", id, null);
                r.onRight = svc("cover", "open_cover", id, null);
                break;
            case "climate":
                r.adjustable = true;
                r.onLeft = new Runnable() { public void run() { temp(id, -0.5); } };
                r.onRight = new Runnable() { public void run() { temp(id, 0.5); } };
                break;
            case "media_player":
                r.onOk = svc("media_player", "media_play_pause", id, null);
                r.adjustable = true;
                r.onLeft = svc("media_player", "volume_down", id, null);
                r.onRight = svc("media_player", "volume_up", id, null);
                break;
            case "scene": r.onOk = svc("scene", "turn_on", id, null); break;
            case "script": r.onOk = svc("script", "turn_on", id, null); break;
            case "vacuum":
                r.onOk = new Runnable() { public void run() {
                    JSONObject s = st(id);
                    boolean cleaning = s != null && "cleaning".equals(s.optString("state"));
                    svc("vacuum", cleaning ? "return_to_base" : "start", id, null).run();
                }};
                break;
        }
    }

    boolean dimmable(String id) {
        JSONObject s = st(id);
        JSONArray modes = s == null ? null : s.optJSONObject("attributes").optJSONArray("supported_color_modes");
        if (modes == null) return s != null && s.optJSONObject("attributes").has("brightness");
        for (int i = 0; i < modes.length(); i++) if (!"onoff".equals(modes.optString(i))) return true;
        return false;
    }

    Runnable svc(final String domain, final String service, final String id, final String extra) {
        return new Runnable() { public void run() {
            final Row r = devRows.get(id);
            if (r != null) r.value.setText("…");
            new Thread(new Runnable() { public void run() {
                try {
                    JSONObject body = extra == null ? new JSONObject() : new JSONObject(extra);
                    body.put("entity_id", id);
                    Style.httpPost(base + "/api/services/" + domain + "/" + service, token, body.toString());
                    Thread.sleep(500);
                    JSONObject s = new JSONObject(Style.httpGet(base + "/api/states/" + id, token));
                    synchronized (states) { states.put(id, s); }
                } catch (Exception ignored) {}
                h.post(new Runnable() { public void run() { Row rr = devRows.get(id); if (rr != null) render(id, rr); } });
            }}).start();
        }};
    }

    void temp(String id, double d) {
        JSONObject s = st(id);
        double t = s == null ? Double.NaN : s.optJSONObject("attributes").optDouble("temperature", Double.NaN);
        if (Double.isNaN(t)) return;
        svc("climate", "set_temperature", id, "{\"temperature\":" + (t + d) + "}").run();
    }

    void render(String id, Row r) {
        JSONObject s = st(id);
        if (s == null) return;
        String state = s.optString("state");
        JSONObject a = s.optJSONObject("attributes");
        boolean on = "on".equals(state) || "open".equals(state) || "playing".equals(state) || "cleaning".equals(state);
        String v;
        switch (dom(id)) {
            case "light":
                v = on ? (a.has("brightness") ? "An  ·  " + Math.round(a.optDouble("brightness") / 2.55) + " %" : "An") : "Aus";
                break;
            case "switch": case "input_boolean": case "fan": v = on ? "An" : "Aus"; break;
            case "cover":
                v = de(state) + (a.has("current_position") ? "  ·  " + a.optInt("current_position") + " %" : ""); break;
            case "climate":
                v = (a.has("current_temperature") ? fmt(a.optDouble("current_temperature")) + "  →  " : "") + (a.has("temperature") ? fmt(a.optDouble("temperature")) : de(state));
                break;
            case "media_player":
                String t = a.optString("media_title", "");
                v = de(state) + (t.isEmpty() ? "" : "  ·  " + t) + (a.has("volume_level") ? "  ·  " + Math.round(a.optDouble("volume_level") * 100) + " %" : "");
                break;
            case "scene": case "script": v = "Starten  ›"; break;
            case "vacuum": v = de(state); break;
            default: v = state;
        }
        if (v.length() > 46) v = v.substring(0, 45) + "…";
        r.value.setText(r.adjustable ? "◀  " + v + "  ▶" : v);
        r.name.setTextColor(on ? Color.WHITE : 0xFFE6E8F2);
        r.name.setAlpha(on || dom(id).equals("scene") || dom(id).equals("script") ? 1f : 0.75f);
    }

    static String fmt(double t) { return String.format(Locale.GERMANY, "%.1f°", t); }
    static String de(String s) {
        switch (s) {
            case "on": return "An"; case "off": return "Aus"; case "open": return "Offen"; case "closed": return "Geschlossen";
            case "opening": return "Öffnet"; case "closing": return "Schließt"; case "playing": return "Spielt";
            case "paused": return "Pausiert"; case "idle": return "Bereit"; case "standby": return "Standby";
            case "heat": return "Heizen"; case "cool": return "Kühlen"; case "auto": return "Auto";
            case "cleaning": return "Saugt"; case "docked": return "Station"; case "returning": return "Fährt zurück";
            default: return s;
        }
    }

    final Runnable refresh = new Runnable() { public void run() {
        new Thread(new Runnable() { public void run() {
            try { loadStates(); } catch (Exception ignored) {}
            h.post(new Runnable() { public void run() { for (Map.Entry<String, Row> e : devRows.entrySet()) render(e.getKey(), e.getValue()); } });
        }}).start();
        h.postDelayed(this, 4000);
    }};

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f != null && f.getParent() == devList && roomRow != null) { roomRow.requestFocus(); return true; }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_MENU) { finish(); return true; }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
