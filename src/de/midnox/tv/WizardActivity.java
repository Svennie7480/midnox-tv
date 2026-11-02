package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Einrichtungsassistent fuer Einsteiger: fuehrt Schritt fuer Schritt durch Entwickleroptionen, ADB-Debugging und
 * die Freigabe; danach richtet sich der Launcher per ADB selbst ein (Startseite, Rechte) und raeumt auf Wunsch auf.
 * Jeder Schritt prueft sich alle 1,5 Sekunden selbst – man muss nur tun, was auf dem Bildschirm steht.
 */
public class WizardActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    static final boolean AMAZON = "Amazon".equalsIgnoreCase(Build.MANUFACTURER);
    static final String[] TITLES = {"Entwickleroptionen", "ADB-Debugging", "Verbindung erlauben", "midnox als Startseite", "Aufräumen (optional)", "Fertig"};
    LinearLayout steps;
    TextView title, body, note;
    LinearLayout buttons;
    int shown = -1;
    volatile boolean adbOk, connecting, setupDone, setupRunning, cleaned;
    volatile String setupLog = "";

    // ------------------------------------------------------------ Zustaende
    boolean devOn() { return Settings.Global.getInt(getContentResolver(), "development_settings_enabled", 0) == 1 || adbOn(); }
    boolean adbOn() { return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1; }
    boolean isHome() {
        try {
            ResolveInfo r = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) { return false; }
    }
    /** Erster Schritt, der noch offen ist. */
    int current() {
        if (!devOn()) return 0;
        if (!adbOn()) return 1;
        if (!adbOk) return 2;
        if (!setupDone) return 3;
        if (!cleaned && !Style.prefs(this).getBoolean("wiz_clean_skip", false)) return 4;
        return 5;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        cleaned = Style.prefs(this).getBoolean("wiz_cleaned", false);
        setupDone = Style.prefs(this).getBoolean("wiz_setup", false) && isHome();

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Style.px(70), Style.px(46), Style.px(70), Style.px(40));
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(new Style.GradientText(this, "Einrichtung", 32), new LinearLayout.LayoutParams(Style.px(300), Style.px(48)));
        TextView sub = Style.text(this, "In wenigen Schritten startklar", 14, Style.MUTED);
        sub.setPadding(0, Style.px(2), 0, Style.px(22));
        left.addView(sub);
        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        left.addView(steps);
        row.addView(left, new LinearLayout.LayoutParams(Style.px(330), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Style.px(40), Style.px(30), Style.px(40), Style.px(30));
        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        card.setColor(0xF0121120); card.setCornerRadius(Style.px(24)); card.setStroke(Style.px(1), 0xFF2A2840);
        right.setBackground(card);
        title = Style.text(this, "", 26, Color.WHITE);
        title.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
        body = Style.text(this, "", 18, 0xFFD6D8E6);
        body.setLineSpacing(Style.px(7), 1f);
        body.setPadding(0, Style.px(18), 0, Style.px(18));
        note = Style.text(this, "", 14, 0xFF9EC5FF);
        note.setLineSpacing(Style.px(4), 1f);
        buttons = new LinearLayout(this);
        buttons.setPadding(0, Style.px(22), 0, 0);
        right.addView(title); right.addView(body); right.addView(note); right.addView(buttons);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -2, 1f); rl.gravity = Gravity.CENTER_VERTICAL;
        row.addView(right, rl);
        setContentView(root);
        tick.run();
    }

    @Override protected void onResume() { super.onResume(); shown = -1; h.removeCallbacks(tick); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    final Runnable tick = new Runnable() { public void run() {
        int cur = current();
        if (cur == 2 && !connecting) tryConnect();
        if (cur == 3 && !setupRunning) runSetup();
        drawSteps(cur);
        if (cur != shown) { shown = cur; showStep(cur); }
        else if (cur == 2 || cur == 3) note.setText(cur == 2 ? (Adb.status.isEmpty() ? "Warte auf die Freigabe …" : "Warte auf die Freigabe …  (" + Adb.status + ")") : setupLog);
        h.postDelayed(this, 1500);
    }};

    void drawSteps(int cur) {
        steps.removeAllViews();
        for (int i = 0; i < TITLES.length; i++) {
            boolean done = i < cur, now = i == cur;
            TextView t = Style.text(this, (done ? "✓  " : now ? "●  " : "○  ") + TITLES[i], 17, done ? 0xFF34D399 : now ? Color.WHITE : Style.MUTED);
            if (now) t.setTypeface(Typeface.create(Style.POP, Typeface.BOLD));
            t.setPadding(0, Style.px(9), 0, Style.px(9));
            steps.addView(t);
        }
    }

    void showStep(int s) {
        buttons.removeAllViews();
        note.setText("");
        title.setText((s < 5 ? "Schritt " + (s + 1) + ": " : "") + TITLES[s]);
        switch (s) {
            case 0:
                body.setText(AMAZON
                    ? "Damit sich midnox TV selbst einrichten kann, schalten wir die Entwickleroptionen frei – das ist harmlos und geht so:\n\n1.  Unten auf „Einstellungen öffnen“ drücken\n2.  „Mein Fire TV“ → „Info“ öffnen\n3.  Auf den Namen deines Geräts (ganz oben) 7-mal hintereinander OK drücken\n4.  Es erscheint „Du bist jetzt Entwickler“ – dann zweimal ZURÜCK"
                    : "Damit sich midnox TV selbst einrichten kann, schalten wir die Entwickleroptionen frei – das ist harmlos und geht so:\n\n1.  Unten auf „Einstellungen öffnen“ drücken\n2.  „Geräteeinstellungen“ → „Info“ öffnen\n3.  Auf „Build“ 7-mal hintereinander OK drücken\n4.  Es erscheint „Du bist jetzt Entwickler“ – dann zurück hierher");
                note.setText("Dieser Bildschirm erkennt von selbst, wenn es geklappt hat.");
                button("Einstellungen öffnen", new Runnable() { public void run() { open(AMAZON ? "android.settings.DEVICE_INFO_SETTINGS" : Settings.ACTION_DEVICE_INFO_SETTINGS); } });
                button("Später", skip);
                break;
            case 1:
                body.setText(AMAZON
                    ? "Jetzt das ADB-Debugging einschalten:\n\n1.  „Einstellungen öffnen“ drücken\n2.  „Mein Fire TV“ → „Entwickleroptionen“\n3.  „ADB-Debugging“ auf AN stellen\n4.  Zurück hierher"
                    : "Jetzt das USB-Debugging einschalten:\n\n1.  „Einstellungen öffnen“ drücken\n2.  „Entwickleroptionen“ öffnen\n3.  „USB-Debugging“ (bzw. „ADB-Debugging“) einschalten\n4.  Zurück hierher");
                note.setText("Damit kann midnox TV Apps aus dem midnox Store installieren, über anderen Apps einblenden und als Startseite starten. Es bleibt alles in deinem Heimnetz.");
                button("Einstellungen öffnen", new Runnable() { public void run() { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); } });
                button("Später", skip);
                break;
            case 2:
                body.setText("Gleich erscheint eine Frage vom Fernseher:\n\n„USB-Debugging zulassen?“\n\n1.  Haken setzen bei „Von diesem Computer immer zulassen“\n2.  „OK“ drücken\n\nDer „Computer“ ist dabei midnox TV selbst.");
                note.setText("Warte auf die Freigabe …");
                button("Nochmal fragen", new Runnable() { public void run() { Adb.close(); connecting = false; } });
                button("Später", skip);
                break;
            case 3:
                body.setText("midnox TV richtet sich jetzt selbst ein:\n\n•  wird deine Startseite (Home-Taste)\n•  darf Tore, Spielstände und Hinweise über anderen Apps einblenden\n•  darf Apps aus dem midnox Store installieren\n•  darf Dateien vom Handy speichern");
                note.setText(setupLog.isEmpty() ? "Einen Moment …" : setupLog);
                break;
            case 4:
                body.setText(AMAZON
                    ? "Möchtest du Werbung und unnötige Amazon-Dienste abschalten?\n\nDas macht den Fernseher spürbar schneller und ruhiger: Werbe-Kennung, Inhalts-Erkennung, Vorschau-Videos, Shopping und Nutzungs-Statistiken.\n\nPrime Video und deine Apps bleiben. Alles lässt sich in den Einstellungen wieder einschalten."
                    : "Möchtest du unnötige Dienste abschalten? Auf diesem Gerät gibt es dafür nichts Wichtiges zu tun – du kannst einfach weiter.");
                if (AMAZON) button("Ja, aufräumen", new Runnable() { public void run() { clean(true); } });
                button(AMAZON ? "Nein, so lassen" : "Weiter", new Runnable() { public void run() { Style.prefs(WizardActivity.this).edit().putBoolean("wiz_clean_skip", true).apply(); shown = -1; } });
                break;
            case 5:
                Style.prefs(this).edit().putBoolean("wiz_done", true).apply();
                body.setText("midnox TV ist startklar.\n\nTipp: Mit dem Handy richtest du Home Assistant und Emby bequem ein – und in den Einstellungen findest du die PC-Verwaltung für alles Weitere.");
                button("Zur Startseite", new Runnable() { public void run() { home(); } });
                button("Mit Handy einrichten", new Runnable() { public void run() { startActivity(new Intent(WizardActivity.this, SetupActivity.class)); finish(); } });
                break;
        }
        if (buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
    }

    final Runnable skip = new Runnable() { public void run() {
        Style.prefs(WizardActivity.this).edit().putLong("wiz_later", System.currentTimeMillis()).apply();
        home();
    }};
    void home() { startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); }

    void button(String s, Runnable r) {
        Pill p = new Pill(this, s, r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = Style.px(14);
        buttons.addView(p, lp);
    }
    void open(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {} }
    }

    // ------------------------------------------------------------ Verbindung und Selbst-Einrichtung
    void tryConnect() {
        connecting = true;
        new Thread(new Runnable() { public void run() {
            try { if (Adb.shell(WizardActivity.this, "echo ok").contains("ok")) { adbOk = true; Style.prefs(WizardActivity.this).edit().putBoolean("adb_ok", true).apply(); } }
            catch (Exception ignored) {}
            if (!adbOk) { try { Thread.sleep(3000); } catch (InterruptedException ignored) {} connecting = false; }
        }}).start();
    }

    void runSetup() {
        setupRunning = true;
        new Thread(new Runnable() { public void run() {
            String pkg = getPackageName();
            StringBuilder log = new StringBuilder();
            step(log, "Startseite", "cmd package set-home-activity " + pkg + "/.HomeActivity");
            step(log, "Einblendungen", "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
            step(log, "Apps installieren", "appops set " + pkg + " REQUEST_INSTALL_PACKAGES allow");
            step(log, "Dateien", "pm grant " + pkg + " android.permission.WRITE_EXTERNAL_STORAGE");
            step(log, "Dateien lesen", "pm grant " + pkg + " android.permission.READ_EXTERNAL_STORAGE");
            setupDone = true;
            Style.prefs(WizardActivity.this).edit().putBoolean("wiz_setup", true).apply();
        }}).start();
    }
    void step(StringBuilder log, String what, String cmd) {
        String r;
        try { r = Adb.shell(this, cmd).trim(); } catch (Exception e) { r = "Fehler"; }
        boolean ok = r.isEmpty() || r.toLowerCase(Locale.ROOT).contains("success") || !r.toLowerCase(Locale.ROOT).matches(".*(error|exception|fehler|unknown).*");
        log.append(ok ? "✓ " : "✕ ").append(what).append("   ");
        setupLog = log.toString();
    }

    /** Amazon-Bloatware nur deaktivieren (nie loeschen); rueckgaengig ueber Einstellungen. */
    static final String[] BLOAT = {
        "com.amazon.tv.acr", "com.amazon.tv.fw.metrics", "com.amazon.client.metrics.api", "com.amazon.dp.logger", "com.amazon.device.logmanager",
        "com.amazon.perfcollection", "com.amazon.device.crashmanager", "com.amazon.wirelessmetrics.service", "com.amazon.hybridadidservice",
        "com.amazon.ftvads.deeplinking", "com.amazon.shoptv.client", "com.amazon.shoptv.firetv.client", "com.amazon.sneakpeek",
        "com.amazon.storm.lightning.tutorial", "com.amazon.tmm.tutorial", "com.amazon.tv.releasenotes", "com.amazon.device.sale.service"
    };
    void clean(final boolean on) {
        buttons.removeAllViews();
        note.setText(on ? "Räume auf …" : "Stelle wieder her …");
        new Thread(new Runnable() { public void run() {
            int n = 0;
            for (String p : BLOAT) try { String r = Adb.shell(WizardActivity.this, (on ? "pm disable-user --user 0 " : "pm enable ") + p); if (r.contains("new state")) n++; } catch (Exception ignored) {}
            final int done = n;
            Style.prefs(WizardActivity.this).edit().putBoolean("wiz_cleaned", on).apply();
            cleaned = on;
            h.post(new Runnable() { public void run() { note.setText(done + " Dienste " + (on ? "abgeschaltet" : "wieder eingeschaltet")); shown = -1; } });
        }}).start();
    }

    /** Beim Start der Startseite: Assistent zeigen, solange ADB fehlt (hoechstens alle 3 Tage nach "Spaeter"). */
    static boolean needed(Context c) {
        android.content.SharedPreferences pr = Style.prefs(c);
        if (pr.getBoolean("wiz_done", false) || pr.getBoolean("adb_ok", false)) return false;
        return System.currentTimeMillis() - pr.getLong("wiz_later", 0) > 3 * 86400000L;
    }
    /** Fuer die Einstellungen: Aufraeumen rueckgaengig machen. */
    static void undoClean(final Context c) {
        new Thread(new Runnable() { public void run() {
            for (String p : BLOAT) try { Adb.shell(c, "pm enable " + p); } catch (Exception ignored) {}
            Style.prefs(c).edit().putBoolean("wiz_cleaned", false).apply();
        }}).start();
    }

    @Override public void onBackPressed() { skip.run(); }
}
