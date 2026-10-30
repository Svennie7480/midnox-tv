package de.midnox.tv;

import android.app.Activity;
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
        col.addView(sv, new LinearLayout.LayoutParams(Style.px(560), -1));
        root.addView(col);
        setContentView(root);
        build();
    }

    void section(String t) {
        TextView v = Style.text(this, t, 11, Style.MUTED);
        v.setLetterSpacing(0.3f);
        v.setPadding(Style.px(4), Style.px(16), 0, Style.px(6));
        list.addView(v);
    }
    Row add(String label) {
        Row r = new Row(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Style.px(6);
        list.addView(r, lp);
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

        section("SYSTEM");
        final Row upd = add("Nach Updates suchen");
        upd.value.setText("Version " + version());
        upd.onOk = new Runnable() { public void run() {
            upd.value.setText("Suche …");
            Updater.check(SettingsActivity.this, new Updater.Callback() { public void result(JSONObject u) {
                if (u == null) upd.value.setText("Aktuell (" + version() + ")");
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
        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
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
