package de.midnox.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.util.*;

/** Einstellungen direkt am Fernseher. OK / links / rechts aendert den Wert. */
public class SettingsActivity extends Activity {
    LinearLayout list;
    SharedPreferences pr;
    TextView updateInfo;

    /** Eine Zeile: Name links, Wert rechts; im Fokus mit Verlaufsrahmen. */
    class Row extends LinearLayout {
        final TextView name, value;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Runnable onOk, onLeft, onRight;
        Row(String label) {
            super(SettingsActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Style.px(22), Style.px(12), Style.px(22), Style.px(12));
            setFocusable(true); setClickable(true); setWillNotDraw(false);
            name = Style.text(getContext(), label, 15, 0xFFE6E8F2);
            value = Style.text(getContext(), "", 14, 0xFFB4B8CC);
            addView(name, new LayoutParams(0, -2, 1));
            addView(value);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { if (onOk != null) onOk.run(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) { invalidate(); value.setTextColor(has ? Color.WHITE : 0xFFB4B8CC); } });
        }
        @Override public boolean onKeyDown(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && onLeft != null) { onLeft.run(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && onRight != null) { onRight.run(); return true; }
            return super.onKeyDown(k, e);
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp;
            RectF r = new RectF(1 * dp, 1 * dp, getWidth() - 1 * dp, getHeight() - 1 * dp);
            p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(isFocused() ? 0xFF1C1B29 : 0x8015141F);
            c.drawRoundRect(r, 12 * dp, 12 * dp, p);
            if (isFocused()) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(Style.brand(r.left, r.right));
                c.drawRoundRect(r, 12 * dp, 12 * dp, p);
            }
            super.onDraw(c);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        pr = Style.prefs(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Style.px(64), Style.px(32), Style.px(64), 0);
        col.addView(new Style.GradientText(this, "Einstellungen", 32));
        TextView hint = Style.text(this, "OK oder ◀ ▶ ändert den Wert  ·  Zurück schließt", 12, Style.MUTED);
        hint.setPadding(Style.px(2), Style.px(6), 0, Style.px(12));
        col.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, 0, 0, Style.px(40));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(Style.px(640), -1));
        root.addView(col);
        setContentView(root);
        build();
    }

    @Override protected void onRestart() { super.onRestart(); if (haState != null) refreshConnections(); }

    LinearLayout cur;   // aktuelle (aufklappbare) Rubrik

    /** Rubrik-Kopf: OK klappt auf und zu; der Zustand bleibt gespeichert. */
    void section(final String t) {
        final Row head = new Row(t);
        head.name.setLetterSpacing(0.22f);
        head.name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        final LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(Style.px(18), Style.px(2), 0, Style.px(8));
        final String key = "sec_open_" + t;
        final Runnable show = new Runnable() { public void run() {
            boolean open = pr.getBoolean(key, false);
            group.setVisibility(open ? View.VISIBLE : View.GONE);
            head.value.setText(open ? "\u25BE" : "\u25B8");
        }};
        head.onOk = new Runnable() { public void run() { pr.edit().putBoolean(key, !pr.getBoolean(key, false)).apply(); show.run(); } };
        head.onRight = new Runnable() { public void run() { pr.edit().putBoolean(key, true).apply(); show.run(); } };
        head.onLeft = new Runnable() { public void run() { pr.edit().putBoolean(key, false).apply(); show.run(); } };
        show.run();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Style.px(list.getChildCount() == 0 ? 0 : 6);
        list.addView(head, lp);
        list.addView(group, new LinearLayout.LayoutParams(-1, -2));
        cur = group;
    }
    Row add(String label) {
        Row r = new Row(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Style.px(6);
        (cur != null ? cur : list).addView(r, lp);
        return r;
    }
    void toggle(String label, final String key, final boolean def, final boolean inverted) {
        final Row r = add(label);
        final Runnable upd = new Runnable() { public void run() {
            boolean v = pr.getBoolean(key, def);
            r.value.setText((inverted ? !v : v) ? "An" : "Aus");
        }};
        upd.run();
        r.onOk = r.onLeft = r.onRight = new Runnable() { public void run() {
            pr.edit().putBoolean(key, !pr.getBoolean(key, def)).apply();
            upd.run();
            if (key.startsWith("kino")) startService(new Intent(SettingsActivity.this, KinoService.class));
        }};
    }

    void build() {
        list.removeAllViews();

        section("DARSTELLUNG");
        final Row theme = add("Thema");
        final Runnable showTheme = new Runnable() { public void run() {
            String id = pr.getString("theme", "auto");
            int i = Arrays.asList(Theme.IDS).indexOf(id);
            String cur = Theme.current(SettingsActivity.this).id;
            theme.value.setText(Theme.NAMES[Math.max(0, i)] + (id.equals("auto") ? "  (heute: " + Theme.NAMES[Math.max(0, Arrays.asList(Theme.IDS).indexOf(cur))] + ")" : ""));
        }};
        showTheme.run();
        final Runnable next = new Runnable() { public void run() { cycleTheme(1); showTheme.run(); } };
        theme.onOk = next; theme.onRight = next;
        theme.onLeft = new Runnable() { public void run() { cycleTheme(-1); showTheme.run(); } };

        final Row fx = add("Animierte Deko");
        final String[] fxN = {"Aus", "Dezent", "Voll"};
        final Runnable fxShow = new Runnable() { public void run() { fx.value.setText("\u25C0  " + fxN[pr.getInt("fx_level", 2)] + "  \u25B6"); } };
        fxShow.run();
        fx.onOk = fx.onRight = new Runnable() { public void run() { pr.edit().putInt("fx_level", (pr.getInt("fx_level", 2) + 1) % 3).apply(); fxShow.run(); SetupActivity.changed = true; } };
        fx.onLeft = new Runnable() { public void run() { pr.edit().putInt("fx_level", (pr.getInt("fx_level", 2) + 2) % 3).apply(); fxShow.run(); SetupActivity.changed = true; } };
        toggle("Beschreibung im Kopfbereich", "hero_text", true, false);
        toggle("midnox Radio auf der Startseite", "hide_radio", false, true);

        section("WETTER");
        toggle("Wetter unter der Uhr anzeigen", "weather_on", true, false);
        weatherSource();

        section("BILDSCHIRMSCHONER");
        screensaver();

        section("REIHEN AUF DER STARTSEITE");
        toggle("Weiterschauen", "hide_resume", false, true);
        toggle("Zuhause (Home Assistant)", "hide_home", false, true);
        try {
            JSONArray libs = new JSONArray(pr.getString("emby_libs", "[]"));
            for (int i = 0; i < libs.length(); i++) {
                JSONObject l = libs.getJSONObject(i);
                toggle("Neu in " + l.getString("name"), "hide_" + l.getString("id"), false, true);
            }
        } catch (Exception ignored) {}

        section("KINO-MODUS");
        toggle("Licht automatisch dimmen, wenn Emby abspielt", "kino_on", true, false);
        toggle("Nur wenn es draußen dunkel ist", "kino_dark", true, false);
        final Row kinoState = add("Status");
        kinoState.value.setText(pr.getString("kino_status", "–"));
        kinoState.setFocusable(true);
        percent("Ambient-Helligkeit bei Wiedergabe", "kino_amb", 20);
        percent("Helligkeit bei Pause", "kino_pause", 25);

        section("LAMPEN F\u00dcR DEN KINO-MODUS");
        lampLoading = add("Lampen werden geladen \u2026");
        if (Style.loadConfig(this) != null) loadLamps(); else lampLoading.name.setText("Erst Home Assistant verbinden");

        section("VERBINDUNGEN");
        connections();

        section("SYSTEM");
        final Row upd = add("Nach Updates suchen");
        upd.value.setText("Version " + version());
        upd.onOk = new Runnable() { public void run() {
            upd.value.setText("Suche …");
            Updater.check(SettingsActivity.this, new Updater.Callback() { public void result(JSONObject u) {
                if (u == null) upd.value.setText(Updater.lastStatus.startsWith("Fehler") ? "Fehler \u2013 siehe Protokoll" : "Aktuell (" + version() + ")");
                else { upd.value.setText("Installiere " + u.optString("versionName") + " …"); Updater.install(SettingsActivity.this, u); }
            }});
        }};
        Row ip = add("Gerät");
        ip.value.setText(ownIp() + "  ·  " + Build.MODEL);
        Row fire = add("Fire TV-Einstellungen öffnen");
        fire.value.setText("›");
        fire.onOk = new Runnable() { public void run() {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.settings.v2");
            if (i == null) i = new Intent(android.provider.Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Style.start(SettingsActivity.this, i);
        }};
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    int lampSection;
    Row lampLoading;

    void percent(String label, final String key, final int def) {
        final Row r = add(label);
        final Runnable upd = new Runnable() { public void run() { r.value.setText("\u25C0  " + pr.getInt(key, def) + " %  \u25B6"); } };
        upd.run();
        r.onRight = r.onOk = new Runnable() { public void run() { pr.edit().putInt(key, Math.min(100, pr.getInt(key, def) + 5)).apply(); upd.run(); } };
        r.onLeft = new Runnable() { public void run() { pr.edit().putInt(key, Math.max(5, pr.getInt(key, def) - 5)).apply(); upd.run(); } };
    }

    /** Lampen aus Home Assistant laden; Wohnzimmer zuerst. */
    void loadLamps() {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject cfg = Style.loadConfig(SettingsActivity.this);
                final List<JSONArray> lamps = KinoService.allLights(cfg.getString("url").replaceAll("/+$", ""), cfg.getString("token"));
                Collections.sort(lamps, new Comparator<JSONArray>() { public int compare(JSONArray a, JSONArray b) {
                    boolean wa = a.optString(2).equalsIgnoreCase("Wohnzimmer"), wb = b.optString(2).equalsIgnoreCase("Wohnzimmer");
                    if (wa != wb) return wa ? -1 : 1;
                    int c = a.optString(2).compareToIgnoreCase(b.optString(2));
                    return c != 0 ? c : a.optString(1).compareToIgnoreCase(b.optString(1));
                }});
                h.post(new Runnable() { public void run() { showLamps(lamps); } });
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { lampLoading.name.setText("Lampen konnten nicht geladen werden"); lampLoading.value.setText(e.getClass().getSimpleName()); } });
            }
        }}).start();
    }

    void showLamps(List<JSONArray> lamps) {
        final JSONObject modes;
        JSONObject m;
        try { m = new JSONObject(pr.getString("kino_lights", "")); }
        catch (Exception e) { // noch nichts gespeichert: Standard wie bisher
            m = new JSONObject();
            try {
                for (JSONArray l : lamps) if (l.optString(2).equalsIgnoreCase("Wohnzimmer"))
                    m.put(l.optString(0), l.optString(1).equalsIgnoreCase("TV LED") ? "ambient" : "off");
            } catch (Exception ignored) {}
        }
        modes = m;
        ViewGroup parent = (ViewGroup) lampLoading.getParent();
        int pos = parent.indexOfChild(lampLoading);
        parent.removeView(lampLoading);
        final String[] keys = {"off", "ambient", ""};
        final String[] names = {"Ausschalten", "Ambient", "Nicht \u00e4ndern"};
        for (final JSONArray l : lamps) {
            final String id = l.optString(0);
            final Row r = new Row(l.optString(1) + (l.optString(2).isEmpty() ? "" : "   \u00b7  " + l.optString(2)));
            final Runnable upd = new Runnable() { public void run() {
                String mode = modes.optString(id, "");
                int i = Arrays.asList(keys).indexOf(mode);
                r.value.setText(names[i < 0 ? 2 : i]);
            }};
            upd.run();
            final int[] dir = {1};
            Runnable cyc = new Runnable() { public void run() {
                int i = Arrays.asList(keys).indexOf(modes.optString(id, ""));
                if (i < 0) i = 2;
                i = (i + dir[0] + keys.length) % keys.length;
                try { if (keys[i].isEmpty()) modes.remove(id); else modes.put(id, keys[i]); } catch (Exception ignored) {}
                pr.edit().putString("kino_lights", modes.toString()).apply();
                upd.run();
            }};
            r.onOk = r.onRight = cyc;
            final Runnable back = cyc;
            r.onLeft = new Runnable() { public void run() { dir[0] = -1; back.run(); dir[0] = 1; } };
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Style.px(6);
            parent.addView(r, pos++, lp);
        }
    }

    // ------------------------------------------------------------ Wetter
    void weatherSource() {
        final Row r = add("Quelle");
        final Row now = add("Aktuell");
        now.value.setText("\u2026");
        final List<String[]> list = new ArrayList<>();
        final Runnable show = new Runnable() { public void run() {
            String id = pr.getString("weather_entity", "");
            String name = list.isEmpty() ? "" : list.get(0)[1];
            for (String[] e : list) if (e[0].equals(id)) name = e[1];
            r.value.setText(list.isEmpty() ? "keine Wetter-Entit\u00e4t gefunden" : name + (list.size() > 1 ? "   \u25C0 \u25B6" : ""));
            new Thread(new Runnable() { public void run() {
                final Weather.Now n = Weather.fetch(SettingsActivity.this);
                ui.post(new Runnable() { public void run() { now.value.setText(n == null ? "\u2013" : Weather.line(n)); } });
            }}).start();
        }};
        final int[] dir = {1};
        final Runnable cyc = new Runnable() { public void run() {
            if (list.isEmpty()) return;
            int i = -1;
            for (int k = 0; k < list.size(); k++) if (list.get(k)[0].equals(pr.getString("weather_entity", ""))) i = k;
            if (i < 0) i = 0;
            i = (i + dir[0] + list.size()) % list.size();
            pr.edit().putString("weather_entity", list.get(i)[0]).apply();
            show.run();
        }};
        r.onOk = r.onRight = cyc;
        r.onLeft = new Runnable() { public void run() { dir[0] = -1; cyc.run(); dir[0] = 1; } };
        r.value.setText("lade \u2026");
        if (Style.loadConfig(this) == null) { r.value.setText("erst Home Assistant verbinden"); now.value.setText("\u2013"); return; }
        new Thread(new Runnable() { public void run() {
            List<String[]> l;
            try { l = Weather.entities(SettingsActivity.this); } catch (Exception e) { l = new ArrayList<>(); }
            final List<String[]> f = l;
            ui.post(new Runnable() { public void run() { list.addAll(f); show.run(); } });
        }}).start();
    }

    // ------------------------------------------------------------ Bildschirmschoner
    void screensaver() {
        final int[] steps = {0, 2, 4, 5, 10, 15, 30};
        final Row after = add("Starten nach");
        final Runnable show = new Runnable() { public void run() {
            int m = pr.getInt("ss_idle", 4);
            after.value.setText(m == 0 ? "Aus" : "\u25C0  " + m + " Min.  \u25B6");
        }};
        show.run();
        final int[] dir = {1};
        final Runnable cyc = new Runnable() { public void run() {
            int m = pr.getInt("ss_idle", 4), i = 0;
            for (int k = 0; k < steps.length; k++) if (steps[k] == m) i = k;
            i = (i + dir[0] + steps.length) % steps.length;
            pr.edit().putInt("ss_idle", steps[i]).apply();
            show.run();
        }};
        after.onOk = after.onRight = cyc;
        after.onLeft = new Runnable() { public void run() { dir[0] = -1; cyc.run(); dir[0] = 1; } };
        toggle("Nur midnox.de (ohne Filmbilder)", "ss_midnox_only", false, false);
        Row test = add("Jetzt ansehen");
        test.value.setText("\u203a");
        test.onOk = new Runnable() { public void run() { startActivity(new Intent(SettingsActivity.this, ScreensaverActivity.class)); } };
        Row sys = add("Auch in anderen Apps");
        sys.value.setText("am PC: 11_bildschirmschoner.bat");
    }

    // ------------------------------------------------------------ Verbindungen
    Row haState, haUrl, haTok, emState, emUrl, emKey, emUser;
    final List<String> haFound = new ArrayList<>();
    final List<String> embyFound = new ArrayList<>();
    final List<String[]> embyUsers = new ArrayList<>();   // {Id, Name}
    final Handler ui = new Handler(Looper.getMainLooper());

    interface Done { void value(String v); }

    JSONObject cfg() { JSONObject c = Style.loadConfig(this); return c == null ? new JSONObject() : c; }
    JSONObject embyCfg(JSONObject c) {
        JSONObject e = c.optJSONObject("emby");
        if (e == null) { e = new JSONObject(); try { c.put("emby", e); } catch (Exception ignored) {} }
        return e;
    }
    void store(JSONObject c) {
        try { Style.saveConfig(this, c); SetupActivity.changed = true; } catch (Exception e) { Toast.makeText(this, "Speichern fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        refreshConnections();
    }
    static String mask(String t) { return t == null || t.isEmpty() ? "fehlt" : "gespeichert  \u2022\u2022\u2022\u2022" + t.substring(Math.max(0, t.length() - 4)); }

    /** Eingabefeld mit Bildschirmtastatur. */
    void ask(String title, String hint, String current, final Done done) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(current == null ? "" : current);
        et.setHint(hint);
        et.setSelection(et.getText().length());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Style.px(20), Style.px(8), Style.px(20), 0);
        box.addView(et);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title).setView(box)
            .setPositiveButton("Speichern", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { done.value(et.getText().toString().trim()); } })
            .setNegativeButton("Abbrechen", null)
            .show();
        et.requestFocus();
    }

    /** Adresse aendern: OK = eintippen, links/rechts = gefundene Server durchschalten. */
    void urlRow(final Row r, final String title, final List<String> found, final boolean emby) {
        r.onOk = new Runnable() { public void run() {
            JSONObject c = cfg();
            String cur = emby ? embyCfg(c).optString("url") : c.optString("url");
            if (cur.isEmpty() && !found.isEmpty()) cur = found.get(0);
            ask(title, emby ? "http://192.168.x.x:8096" : "http://192.168.x.x:8123", cur, new Done() { public void value(String v) { setUrl(emby, v); } });
        }};
        final int[] dir = {1};
        final Runnable cyc = new Runnable() { public void run() {
            if (found.isEmpty()) { r.value.setText("keine Server gefunden \u2013 OK zum Eintippen"); return; }
            JSONObject c = cfg();
            String cur = emby ? embyCfg(c).optString("url") : c.optString("url");
            int i = (found.indexOf(cur) + dir[0] + found.size()) % found.size();
            setUrl(emby, found.get(i));
        }};
        r.onRight = cyc;
        r.onLeft = new Runnable() { public void run() { dir[0] = -1; cyc.run(); dir[0] = 1; } };
    }
    void setUrl(boolean emby, String v) {
        v = v.replaceAll("/+$", "");
        if (!v.isEmpty() && !v.startsWith("http")) v = "http://" + v;
        JSONObject c = cfg();
        try { if (emby) { embyCfg(c).put("url", v).put("server", ""); } else c.put("url", v); } catch (Exception ignored) {}
        store(c);
    }

    void connections() {
        Row phone = add("Mit Handy einrichten");
        phone.value.setText("QR-Code  \u203a");
        phone.onOk = new Runnable() { public void run() { startActivity(new Intent(SettingsActivity.this, SetupActivity.class)); } };

        haState = add("Home Assistant");
        haUrl = add("    Adresse");
        haTok = add("    Zugangstoken");
        emState = add("Emby");
        emUrl = add("    Server");
        emKey = add("    API-Schl\u00fcssel");
        emUser = add("    Benutzer");
        haState.onOk = emState.onOk = new Runnable() { public void run() { refreshConnections(); } };

        urlRow(haUrl, "Home Assistant \u2013 Adresse", haFound, false);
        urlRow(emUrl, "Emby \u2013 Server-Adresse", embyFound, true);
        haTok.onOk = new Runnable() { public void run() {
            ask("Langlebiger Zugangstoken", "Home Assistant \u2192 Profil \u2192 Sicherheit", "", new Done() { public void value(String v) {
                if (v.isEmpty()) return;
                JSONObject c = cfg(); try { c.put("token", v); } catch (Exception ignored) {} store(c);
            }});
        }};
        emKey.onOk = new Runnable() { public void run() {
            ask("Emby API-Schl\u00fcssel", "Emby \u2192 Einstellungen \u2192 API-Schl\u00fcssel", "", new Done() { public void value(String v) {
                if (v.isEmpty()) return;
                JSONObject c = cfg(); try { embyCfg(c).put("key", v); } catch (Exception ignored) {} store(c);
            }});
        }};
        final int[] dir = {1};
        final Runnable cycUser = new Runnable() { public void run() {
            if (embyUsers.isEmpty()) { emUser.value.setText("erst Server + Schl\u00fcssel eintragen"); return; }
            JSONObject c = cfg();
            String cur = embyCfg(c).optString("user");
            int i = -1;
            for (int k = 0; k < embyUsers.size(); k++) if (embyUsers.get(k)[0].equals(cur)) i = k;
            i = (i + dir[0] + embyUsers.size()) % embyUsers.size();
            try { embyCfg(c).put("user", embyUsers.get(i)[0]); } catch (Exception ignored) {}
            store(c);
        }};
        emUser.onOk = emUser.onRight = cycUser;
        emUser.onLeft = new Runnable() { public void run() { dir[0] = -1; cycUser.run(); dir[0] = 1; } };

        refreshConnections();
        new Thread(new Runnable() { public void run() {   // Server im Netzwerk suchen
            final List<String> ha = Discovery.homeAssistant(SettingsActivity.this);
            final List<JSONObject> em = Discovery.emby();
            ui.post(new Runnable() { public void run() {
                haFound.clear(); haFound.addAll(ha);
                embyFound.clear(); for (JSONObject o : em) embyFound.add(o.optString("Address").replaceAll("/+$", ""));
            }});
        }}).start();
    }

    void refreshConnections() {
        final JSONObject c = cfg();
        final JSONObject e = embyCfg(c);
        haUrl.value.setText(c.optString("url").isEmpty() ? "nicht gesetzt  \u2013  OK" : c.optString("url"));
        haTok.value.setText(mask(c.optString("token")));
        emUrl.value.setText(e.optString("url").isEmpty() ? "nicht gesetzt  \u2013  OK" : e.optString("url"));
        emKey.value.setText(mask(e.optString("key")));
        emUser.value.setText(e.optString("user").isEmpty() ? "nicht gew\u00e4hlt" : "\u2026");
        haState.value.setText("pr\u00fcfe \u2026");
        emState.value.setText("pr\u00fcfe \u2026");
        new Thread(new Runnable() { public void run() {
            String haS, emS, userS = "nicht gew\u00e4hlt";
            String url = c.optString("url").replaceAll("/+$", "");
            if (url.isEmpty()) haS = "nicht eingerichtet";
            else try { Style.httpGet(url + "/api/", c.optString("token")); haS = "verbunden \u2713"; }
            catch (Exception x) { haS = String.valueOf(x.getMessage()).contains("401") ? "Token ung\u00fcltig" : "nicht erreichbar"; }
            String eu = e.optString("url").replaceAll("/+$", ""), key = e.optString("key");
            final List<String[]> users = new ArrayList<>();
            if (eu.isEmpty() || key.isEmpty()) emS = "nicht eingerichtet";
            else try {
                JSONArray a = new JSONArray(Style.httpGet(eu + "/emby/Users?api_key=" + URLEncoder.encode(key, "UTF-8"), null));
                for (int i = 0; i < a.length(); i++) users.add(new String[]{a.getJSONObject(i).optString("Id"), a.getJSONObject(i).optString("Name")});
                emS = "verbunden \u2713";
                for (String[] u : users) if (u[0].equals(e.optString("user"))) userS = u[1];
                if (users.isEmpty()) {   // Benutzer-Token sieht ggf. nur sich selbst
                    JSONObject me = new JSONObject(Style.httpGet(eu + "/emby/Users/" + e.optString("user") + "?api_key=" + URLEncoder.encode(key, "UTF-8"), null));
                    users.add(new String[]{me.optString("Id"), me.optString("Name")}); userS = me.optString("Name");
                }
            } catch (Exception x) { emS = String.valueOf(x.getMessage()).contains("401") ? "Schl\u00fcssel ung\u00fcltig" : "nicht erreichbar"; }
            final String a1 = haS, b1 = emS, u1 = userS;
            ui.post(new Runnable() { public void run() {
                haState.value.setText(a1); emState.value.setText(b1); emUser.value.setText(u1);
                embyUsers.clear(); embyUsers.addAll(users);
            }});
        }}).start();
    }

    void cycleTheme(int d) {
        List<String> ids = Arrays.asList(Theme.IDS);
        int i = ids.indexOf(pr.getString("theme", "auto"));
        i = (i + d + ids.size()) % ids.size();
        pr.edit().putString("theme", ids.get(i)).apply();
    }

    String version() {
        try { PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); return pi.versionName; } catch (Exception e) { return "?"; }
    }

    static String ownIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "?";
    }
}
